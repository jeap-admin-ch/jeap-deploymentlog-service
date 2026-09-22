package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricValue;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentStartedMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.FlowOpenMetricsChangedEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.OpenFlowMetricIdentity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class DeploymentFlowMetrics {

    static final String DEPLOYMENT_COUNTER = "deployment_counter";
    static final String DEPLOYMENT_DURATION = "deployment_duration_seconds";
    static final String FLOW_COUNTER = "flow_counter";
    static final String FLOW_OPEN = "flow_open";
    static final String FLOW_DURATION = "flow_duration_seconds";
    static final String FLOW_RECOVERY_DURATION = "flow_recovery_duration_seconds";
    public static final String SYSTEM = "system";
    public static final String COMPONENT = "component";
    public static final String ENVIRONMENT = "environment";
    public static final String DEPLOYMENT_TYPE = "deployment_type";
    public static final String FAILED = "failed";
    public static final String CANCELLED = "cancelled";
    public static final String SUCCESS = "success";

    private final MeterRegistry meterRegistry;
    private final DeploymentRepository deploymentRepository;
    private final FlowRepository flowRepository;
    private final Map<DeploymentCounterKey, AtomicLong> deploymentCounters = new ConcurrentHashMap<>();
    private final Map<OpenFlowKey, AtomicLong> openFlowGauges = new ConcurrentHashMap<>();
    private final Map<OpenFlowKey, Set<UUID>> openFlowIds = new HashMap<>();
    private long localGaugeRevision;

    public DeploymentFlowMetrics(MeterRegistry meterRegistry,
                                 DeploymentRepository deploymentRepository,
                                 FlowRepository flowRepository) {
        this.meterRegistry = meterRegistry;
        this.deploymentRepository = deploymentRepository;
        this.flowRepository = flowRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deploymentStarted(DeploymentStartedMetricEvent event) {
        event.deploymentTypes().forEach(deploymentType -> registerDeploymentMeters(
                new DeploymentMetricIdentity(event.system(), event.component(), event.environment(), deploymentType)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deploymentReachedTerminalState(DeploymentTerminalMetricEvent event) {
        String result = switch (event.state()) {
            case SUCCESS -> SUCCESS;
            case FAILURE -> FAILED;
            case CANCELLED -> CANCELLED;
            default -> null;
        };
        if (result == null) {
            return;
        }

        java.util.Optional<Duration> duration =
                validDuration(event.startedAt(), event.endedAt(), "deployment", event.externalId());
        event.deploymentTypes().stream().sorted().forEach(deploymentType -> {
            DeploymentMetricIdentity identity = new DeploymentMetricIdentity(
                    event.system(), event.component(), event.environment(), deploymentType);
            registerDeploymentMeters(identity);

            duration.ifPresent(value -> Timer.builder(DEPLOYMENT_DURATION)
                    .tags(SYSTEM, event.system(),
                            COMPONENT, event.component(),
                            ENVIRONMENT, event.environment(),
                            DEPLOYMENT_TYPE, deploymentType.name())
                    .register(meterRegistry)
                    .record(value));
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void flowReachedTerminalState(FlowTerminalMetricEvent event) {
        if (event.state() != FlowState.CLOSED && event.state() != FlowState.ABORTED) {
            return;
        }
        String type = normalized(event.type());
        registerFlowMeters(event.system(), event.component(), event.finalEnvironment(), event.type());
        Counter.builder(FLOW_COUNTER)
                .tags(SYSTEM, event.system(),
                        COMPONENT, event.component(),
                        "type", type,
                        "state", normalized(event.state()),
                        DEPLOYMENT_TYPE, DeploymentType.CODE.name())
                .register(meterRegistry)
                .increment();

        if (event.state() != FlowState.CLOSED) {
            return;
        }
        validDuration(event.bornAt(), event.endedAt(), "flow", event.flowId().toString())
                .ifPresent(duration -> {
                    Timer.builder(FLOW_DURATION)
                            .tags(SYSTEM, event.system(),
                                    COMPONENT, event.component(),
                                    "type", type,
                                    DEPLOYMENT_TYPE, DeploymentType.CODE.name())
                            .register(meterRegistry)
                            .record(duration);
                    if (event.type() == FlowType.ROLLBACK) {
                        Timer.builder(FLOW_RECOVERY_DURATION)
                                .tags(SYSTEM, event.system(),
                                        COMPONENT, event.component(),
                                        ENVIRONMENT, event.finalEnvironment(),
                                        DEPLOYMENT_TYPE, DeploymentType.CODE.name())
                                .register(meterRegistry)
                                .record(duration);
                    }
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public synchronized void openFlowsChanged(FlowOpenMetricsChangedEvent event) {
        OpenFlowKey key = new OpenFlowKey(event.system(), event.component(), event.type());
        Set<UUID> ids = openFlowIds.computeIfAbsent(key, ignored -> new HashSet<>());
        if (event.open()) {
            registerFlowMeters(event.system(), event.component(), event.finalEnvironment(), event.type());
            ids.add(event.flowId());
        } else {
            ids.remove(event.flowId());
        }
        gauge(key).set(ids.size());
        localGaugeRevision++;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeMetrics() {
        deploymentRepository.findDeploymentMetricIdentities().forEach(this::registerDeploymentMeters);
        refreshDeploymentMetricValues();
        flowRepository.findFlowMetricIdentities().forEach(this::registerFlowMeters);
        flowRepository.countOpenFlowsBySystemComponentAndType().forEach(value ->
                gauge(new OpenFlowKey(value.system(), value.component(), value.type())));
        refreshOpenFlowGauges();
    }

    @Scheduled(fixedDelayString = "${jeap.deploymentlog.metrics.deployment-refresh-interval:PT30S}")
    public void refreshDeploymentMetrics() {
        deploymentRepository.findStartedDeploymentMetricIdentities().forEach(this::registerDeploymentMeters);
        refreshDeploymentMetricValues();
    }

    @Scheduled(fixedDelayString = "${jeap.deploymentlog.metrics.deployment-refresh-interval:PT30S}")
    @SchedulerLock(name = "reconcile-terminal-deployment-metrics", lockAtMostFor = "1m")
    public void reconcileDeploymentMetrics() {
        deploymentRepository.reconcileTerminalDeploymentMetrics();
        refreshDeploymentMetricValues();
    }

    @Scheduled(fixedDelayString = "${jeap.deploymentlog.metrics.flow-open-refresh-interval:PT30S}")
    public void refreshOpenFlowGaugesOnSchedule() {
        refreshOpenFlowGauges();
    }

    void refreshOpenFlowGauges() {
        long revisionBeforeRefresh;
        synchronized (this) {
            revisionBeforeRefresh = localGaugeRevision;
        }
        Map<OpenFlowKey, Set<UUID>> currentOpenFlowIds = new HashMap<>();
        for (OpenFlowMetricIdentity value : flowRepository.findOpenFlowsForMetrics()) {
            OpenFlowKey key = new OpenFlowKey(value.system(), value.component(), value.type());
            registerFlowMeters(value.system(), value.component(), value.finalEnvironment(), value.type());
            currentOpenFlowIds.computeIfAbsent(key, ignored -> new HashSet<>()).add(value.flowId());
        }

        synchronized (this) {
            if (revisionBeforeRefresh != localGaugeRevision) {
                return;
            }
            openFlowIds.clear();
            openFlowIds.putAll(currentOpenFlowIds);
            openFlowGauges.values().forEach(value -> value.set(0));
            currentOpenFlowIds.forEach((key, ids) -> gauge(key).set(ids.size()));
        }
    }

    private AtomicLong gauge(OpenFlowKey key) {
        return openFlowGauges.computeIfAbsent(key, ignored -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(FLOW_OPEN, value, AtomicLong::doubleValue)
                    .tags(SYSTEM, key.system(),
                            COMPONENT, key.component(),
                            "type", normalized(key.type()),
                            DEPLOYMENT_TYPE, DeploymentType.CODE.name())
                    .register(meterRegistry);
            return value;
        });
    }

    private void registerDeploymentMeters(DeploymentMetricIdentity identity) {
        for (String result : Set.of(SUCCESS, FAILED, CANCELLED)) {
            deploymentCounter(identity, result);
        }
        Timer.builder(DEPLOYMENT_DURATION)
                .tags(SYSTEM, identity.system(),
                        COMPONENT, identity.component(),
                        ENVIRONMENT, identity.environment(),
                        DEPLOYMENT_TYPE, identity.deploymentType().name())
                .register(meterRegistry);
    }

    private void refreshDeploymentMetricValues() {
        deploymentRepository.findDeploymentMetricValues().forEach(value -> {
            DeploymentMetricIdentity identity = new DeploymentMetricIdentity(
                    value.system(), value.component(), value.environment(), value.deploymentType());
            registerDeploymentMeters(identity);
            deploymentCounter(identity, result(value)).accumulateAndGet(value.value(), Math::max);
        });
    }

    private AtomicLong deploymentCounter(DeploymentMetricIdentity identity, String result) {
        DeploymentCounterKey key = new DeploymentCounterKey(
                identity.system(), identity.component(), identity.environment(), identity.deploymentType(), result);
        return deploymentCounters.computeIfAbsent(key, ignored -> {
            AtomicLong value = new AtomicLong();
            FunctionCounter.builder(DEPLOYMENT_COUNTER, value, AtomicLong::doubleValue)
                    .tags(SYSTEM, key.system(),
                            COMPONENT, key.component(),
                            ENVIRONMENT, key.environment(),
                            "result", key.result(),
                            DEPLOYMENT_TYPE, key.deploymentType().name())
                    .register(meterRegistry);
            return value;
        });
    }

    private static String result(DeploymentMetricValue value) {
        return switch (value.state()) {
            case SUCCESS -> SUCCESS;
            case FAILURE -> FAILED;
            case CANCELLED -> CANCELLED;
            default -> throw new IllegalArgumentException("Expected a terminal deployment state");
        };
    }

    private void registerFlowMeters(String system, String component, String finalEnvironment, FlowType flowType) {
        String type = normalized(flowType);
        for (String state : Set.of("closed", "aborted")) {
            Counter.builder(FLOW_COUNTER)
                    .tags(SYSTEM, system,
                            COMPONENT, component,
                            "type", type,
                            "state", state,
                            DEPLOYMENT_TYPE, DeploymentType.CODE.name())
                    .register(meterRegistry);
        }
        Timer.builder(FLOW_DURATION)
                .tags(SYSTEM, system,
                        COMPONENT, component,
                        "type", type,
                        DEPLOYMENT_TYPE, DeploymentType.CODE.name())
                .register(meterRegistry);
        if (flowType == FlowType.ROLLBACK) {
            Timer.builder(FLOW_RECOVERY_DURATION)
                    .tags(SYSTEM, system,
                            COMPONENT, component,
                            ENVIRONMENT, finalEnvironment,
                            DEPLOYMENT_TYPE, DeploymentType.CODE.name())
                    .register(meterRegistry);
        }
    }

    private void registerFlowMeters(FlowMetricIdentity identity) {
        registerFlowMeters(identity.system(), identity.component(), identity.finalEnvironment(), identity.type());
    }

    private java.util.Optional<Duration> validDuration(ZonedDateTime startedAt,
                                                        ZonedDateTime endedAt,
                                                        String subject,
                                                        String id) {
        if (startedAt == null || endedAt == null) {
            log.warn("Not recording {} duration for {} because startedAt or endedAt is missing", subject, id);
            return java.util.Optional.empty();
        }
        Duration duration = Duration.between(startedAt, endedAt);
        if (duration.isNegative()) {
            log.warn("Not recording {} duration for {} because endedAt is before startedAt", subject, id);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(duration);
    }

    private static String normalized(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private record OpenFlowKey(String system, String component, FlowType type) {
    }

    private record DeploymentCounterKey(String system,
                                        String component,
                                        String environment,
                                        DeploymentType deploymentType,
                                        String result) {
    }
}
