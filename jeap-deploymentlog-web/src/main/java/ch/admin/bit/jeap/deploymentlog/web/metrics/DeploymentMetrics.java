package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentStartedMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import io.micrometer.core.instrument.FunctionCounter;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class DeploymentMetrics {

    static final String DEPLOYMENT_COUNTER = "deployment_counter";
    static final String DEPLOYMENT_DURATION = "deployment_duration_seconds";
    public static final String SYSTEM = "system";
    public static final String COMPONENT = "component";
    public static final String ENVIRONMENT = "environment";
    public static final String DEPLOYMENT_TYPE = "deployment_type";
    public static final String FAILED = "failed";
    public static final String CANCELLED = "cancelled";
    public static final String SUCCESS = "success";

    private final MeterRegistry meterRegistry;
    private final DeploymentRepository deploymentRepository;
    private final Map<DeploymentCounterKey, AtomicLong> deploymentCounters = new ConcurrentHashMap<>();
    public DeploymentMetrics(MeterRegistry meterRegistry,
                                 DeploymentRepository deploymentRepository) {
        this.meterRegistry = meterRegistry;
        this.deploymentRepository = deploymentRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deploymentStarted(DeploymentStartedMetricEvent event) {
        event.deploymentTypes().forEach(deploymentType -> registerDeploymentMeters(
                new DeploymentMetricIdentity(event.system(), event.component(), event.environment(), deploymentType)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deploymentReachedTerminalState(DeploymentTerminalMetricEvent event) {
        String result = terminalResult(event.state());
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

    @EventListener(ApplicationReadyEvent.class)
    public void initializeMetrics() {
        deploymentRepository.findDeploymentMetricIdentities().forEach(this::registerDeploymentMeters);
        refreshDeploymentMetricValues();

    }

    @Scheduled(fixedDelayString = "${jeap.deploymentlog.metrics.deployment-refresh-interval:PT30S}",
            scheduler = DeploymentMetricsSchedulingConfiguration.METRICS_TASK_SCHEDULER)
    public void refreshDeploymentMetrics() {
        deploymentRepository.findStartedDeploymentMetricIdentities().forEach(this::registerDeploymentMeters);
        refreshDeploymentMetricValues();
    }

    @Scheduled(fixedDelayString = "${jeap.deploymentlog.metrics.deployment-refresh-interval:PT30S}",
            scheduler = DeploymentMetricsSchedulingConfiguration.METRICS_TASK_SCHEDULER)
    @SchedulerLock(name = "reconcile-terminal-deployment-metrics", lockAtMostFor = "1m")
    public void reconcileDeploymentMetrics() {
        deploymentRepository.reconcileTerminalDeploymentMetrics();
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
            String result = terminalResult(value.state());
            if (result == null) {
                log.warn("Ignoring deployment metric value with non-terminal state {} for {}/{}/{} ({})",
                        value.state(), value.system(), value.component(), value.environment(), value.deploymentType());
                return;
            }
            DeploymentMetricIdentity identity = new DeploymentMetricIdentity(
                    value.system(), value.component(), value.environment(), value.deploymentType());
            registerDeploymentMeters(identity);
            deploymentCounter(identity, result).accumulateAndGet(value.value(), Math::max);
        });
    }

    private AtomicLong deploymentCounter(DeploymentMetricIdentity identity, String result) {
        DeploymentCounterKey key = new DeploymentCounterKey(identity, result);
        return deploymentCounters.computeIfAbsent(key, ignored -> {
            AtomicLong value = new AtomicLong();
            FunctionCounter.builder(DEPLOYMENT_COUNTER, value, AtomicLong::doubleValue)
                    .tags(SYSTEM, key.identity().system(),
                            COMPONENT, key.identity().component(),
                            ENVIRONMENT, key.identity().environment(),
                            "result", key.result(),
                            DEPLOYMENT_TYPE, key.identity().deploymentType().name())
                    .register(meterRegistry);
            return value;
        });
    }

    private static String terminalResult(DeploymentState state) {
        return switch (state) {
            case SUCCESS -> SUCCESS;
            case FAILURE -> FAILED;
            case CANCELLED -> CANCELLED;
            default -> null;
        };
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

    private record DeploymentCounterKey(DeploymentMetricIdentity identity, String result) {
    }
}
