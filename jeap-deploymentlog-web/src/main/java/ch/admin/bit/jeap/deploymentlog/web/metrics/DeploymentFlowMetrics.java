package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowOpenMetricsChangedEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.OpenFlowMetricIdentity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
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

    private final MeterRegistry meterRegistry;
    private final FlowRepository flowRepository;
    private final Map<OpenFlowKey, AtomicLong> openFlowGauges = new ConcurrentHashMap<>();
    private final Map<OpenFlowKey, Set<UUID>> openFlowIds = new HashMap<>();
    private long localGaugeRevision;

    public DeploymentFlowMetrics(MeterRegistry meterRegistry, FlowRepository flowRepository) {
        this.meterRegistry = meterRegistry;
        this.flowRepository = flowRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deploymentReachedTerminalState(DeploymentTerminalMetricEvent event) {
        String result = switch (event.state()) {
            case SUCCESS -> "success";
            case FAILURE -> "failed";
            default -> null;
        };
        if (result == null) {
            return;
        }

        Counter.builder(DEPLOYMENT_COUNTER)
                .tags(SYSTEM, event.system(),
                        COMPONENT, event.component(),
                        ENVIRONMENT, event.environment(),
                        "result", result)
                .register(meterRegistry)
                .increment();

        validDuration(event.startedAt(), event.endedAt(), "deployment", event.externalId())
                .ifPresent(duration -> Timer.builder(DEPLOYMENT_DURATION)
                        .tags(SYSTEM, event.system(),
                                COMPONENT, event.component(),
                                ENVIRONMENT, event.environment())
                        .register(meterRegistry)
                        .record(duration));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void flowReachedTerminalState(FlowTerminalMetricEvent event) {
        if (event.state() != FlowState.CLOSED && event.state() != FlowState.ABORTED) {
            return;
        }
        String type = normalized(event.type());
        Counter.builder(FLOW_COUNTER)
                .tags(SYSTEM, event.system(),
                        COMPONENT, event.component(),
                        "type", type,
                        "state", normalized(event.state()))
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
                                    "type", type)
                            .register(meterRegistry)
                            .record(duration);
                    if (event.type() == FlowType.ROLLBACK) {
                        Timer.builder(FLOW_RECOVERY_DURATION)
                                .tags(SYSTEM, event.system(),
                                        COMPONENT, event.component(),
                                        ENVIRONMENT, event.finalEnvironment())
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
            ids.add(event.flowId());
        } else {
            ids.remove(event.flowId());
        }
        gauge(key).set(ids.size());
        localGaugeRevision++;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeOpenFlowGauges() {
        flowRepository.countOpenFlowsBySystemComponentAndType().forEach(value ->
                gauge(new OpenFlowKey(value.system(), value.component(), value.type())));
        refreshOpenFlowGauges();
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
                            "type", normalized(key.type()))
                    .register(meterRegistry);
            return value;
        });
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
}
