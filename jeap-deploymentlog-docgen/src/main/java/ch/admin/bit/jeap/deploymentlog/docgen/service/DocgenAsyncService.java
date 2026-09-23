package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class DocgenAsyncService {

    private static final String SYSTEM_NAME = "systemName";
    private static final String COMPONENT_NAME = "componentName";
    private static final String FAILOVER_SUCCESS_EXCEPTION = "FailoverSuccessSQLException";
    private static final String COMMUNICATION_LINK_CHANGED_SQL_STATE = "08S02";
    public static final String DEPLOYMENT_ID = "deploymentId";

    private final DocumentationGenerator documentationGenerator;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentService deploymentService;
    private final DataRetentionRepository dataRetentionRepository;
    private final Counter errorCounter;
    private final DocgenLocks locks;
    private final DocgenTaskDispatcher dispatcher;

    public DocgenAsyncService(DocumentationGenerator documentationGenerator, DeploymentRepository deploymentRepository,
                              DataRetentionRepository dataRetentionRepository, MeterRegistry meterRegistry,
                              DocgenLocks locks, DocgenTaskDispatcher dispatcher, DeploymentService deploymentService) {
        this.documentationGenerator = documentationGenerator;
        this.deploymentRepository = deploymentRepository;
        this.deploymentService = deploymentService;
        this.dataRetentionRepository = dataRetentionRepository;
        this.locks = locks;
        this.dispatcher = dispatcher;
        this.errorCounter = meterRegistry.counter("deploymentlog.docgen.deploymentpages.error");
    }

    public void triggerDocgenForUndeployment(String systemName, UUID deploymentId) {
        deploymentService.resumePageGeneration(deploymentId);
        dispatcher.submitLive(deploymentTaskKey(deploymentId),
                () -> triggerDeploymentPageGeneration(deploymentId, systemName));
    }

    public void triggerDocgenForDeployment(UUID deploymentId) {
        deploymentService.resumePageGeneration(deploymentId);
        dispatcher.submitLive(deploymentTaskKey(deploymentId),
                () -> triggerDeploymentPageGeneration(deploymentId, null));
    }

    public void triggerRepairDocgenForDeployment(UUID deploymentId) {
        dispatcher.submitRepair(deploymentTaskKey(deploymentId),
                () -> triggerDeploymentPageGeneration(deploymentId, null, true));
    }

    private void triggerDeploymentPageGeneration(UUID deploymentId, String knownSystemName) {
        triggerDeploymentPageGeneration(deploymentId, knownSystemName, false);
    }

    private void triggerDeploymentPageGeneration(UUID deploymentId, String knownSystemName, boolean repair) {
        String systemName = knownSystemName;
        String componentName = null;
        try {
            if (repair) {
                deploymentService.markPageGenerationAttempted(deploymentId);
            }
            if (systemName == null) {
                systemName = deploymentRepository.getSystemNameForDeployment(deploymentId);
            }
            componentName = deploymentRepository.getComponentNameForDeployment(deploymentId);
            String lockedSystemName = systemName;
            String loggedComponentName = componentName;
            locks.runIfLockAquiredBeforeTimeout(systemName, () -> {
                // Housekeeping uses the same system lock. Recheck after acquiring it, since a queued repair
                // may have become obsolete or suppressed while waiting for the worker or another instance.
                if (!repair || deploymentRepository.isPageGenerationRepairRequired(deploymentId)) {
                    generateDeploymentPages(deploymentId, lockedSystemName, loggedComponentName);
                }
            });
        } catch (Exception ex) {
            handleDeploymentPageGenerationFailure(deploymentId, systemName, componentName, ex,
                    "Failed to trigger page generation");
        }
    }

    public void triggerDocgenForSystem(String systemName, Integer year) {
        dispatcher.submitBackground("system:" + systemName.toLowerCase(Locale.ROOT) + ":" + year,
                () -> runLockedForSystem(systemName,
                        () -> documentationGenerator.generateAllPagesForSystem(systemName, year)));
    }

    public void triggerDocumentationStructureReconciliation() {
        dispatcher.submitBackground("documentation-structure", this::reconcileDocumentationStructure);
    }

    private void reconcileDocumentationStructure() {
        try {
            // DocumentationGenerator acquires the dedicated global structure lock. Acquiring it here as a
            // system lock as well would resolve to the same non-reentrant ShedLock name and block until timeout.
            documentationGenerator.reconcileDocumentationStructure();
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Documentation structure reconciliation failed", ex);
        }
    }

    public void triggerGenerateJiraLinksForSystem(String systemName, ZonedDateTime from, ZonedDateTime to) {
        String taskKey = "jira-links:" + systemName.toLowerCase(Locale.ROOT) + ":" + from + ":" + to;
        dispatcher.submitBackground(taskKey, () -> runLockedForSystem(systemName, () ->
                documentationGenerator.generateJiraLinksForSystem(systemName, from, to)));
    }

    private void generateDeploymentPages(UUID deploymentId, String systemName, String componentName) {
        try {
            UUID requestId = deploymentRepository.getPageGenerationRequestId(deploymentId).orElse(null);
            if (documentationGenerator.generateDeploymentPages(deploymentId) != null && requestId != null) {
                // A request arriving while generation was running must remain pending for the next run.
                deploymentService.completePageGenerationRequest(deploymentId, requestId);
            }
        } catch (Exception ex) {
            handleDeploymentPageGenerationFailure(deploymentId, systemName, componentName, ex,
                    "Failed to generate pages");
        }
    }

    private void handleDeploymentPageGenerationFailure(UUID deploymentId, String systemName, String componentName,
                                                       Exception ex, String failureMessage) {
        if (isAwsFailoverSuccess(ex)) {
            log.info("Database connection recovered while processing page generation for deployment {}, " +
                            "system {} and component {}; " +
                            "leaving the page-generation request pending for the repair job",
                    value(DEPLOYMENT_ID, deploymentId), value(SYSTEM_NAME, systemName),
                    value(COMPONENT_NAME, componentName));
            log.debug("Page generation interrupted by a recovered database connection", ex);
            return;
        }
        errorCounter.increment();
        log.warn("{} for deployment {}, system {} and component {}", failureMessage,
                value(DEPLOYMENT_ID, deploymentId), value(SYSTEM_NAME, systemName),
                value(COMPONENT_NAME, componentName), ex);
    }

    private static boolean isAwsFailoverSuccess(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            if (current instanceof SQLException sqlException
                    && COMMUNICATION_LINK_CHANGED_SQL_STATE.equals(sqlException.getSQLState())
                    && FAILOVER_SUCCESS_EXCEPTION.equals(sqlException.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public void triggerMigrationForSystem(System system) {
        dispatcher.submitBackground("migration:" + system.getId(), () ->
                runLockedForSystem(system.getName(), () -> migrateSystem(system)));
    }

    private void migrateSystem(System system) {
        try {
            documentationGenerator.migrateSystem(system);
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Failed to generate pages for system {}", value(SYSTEM_NAME, system.getName()), ex);
        }
    }

    public void triggerMergeSystems(System system, System oldSystem) {
        dispatcher.submitBackground("merge:" + system.getId() + ":" + oldSystem.getId(), () ->
                runLockedForSystem(system.getName(), () -> mergeSystems(system, oldSystem)));
    }

    private void mergeSystems(System system, System oldSystem) {
        try {
            documentationGenerator.mergeSystems(system, oldSystem);
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Failed to generate pages for system {}", value(SYSTEM_NAME, system.getName()), ex);
        }
    }

    /**
     * Runs one system of a batch, making sure that a failure - including one while acquiring or releasing the lock -
     * does not abort the systems that have not been processed yet.
     */
    private void runLockedForSystem(String systemName, Runnable task) {
        try {
            locks.runWithSystemLock(systemName, task);
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Docgen failed for system {}", value(SYSTEM_NAME, systemName), ex);
        }
    }

    public void triggerUpdateDeploymentListPages(String systemName, Collection<SystemEnv> systemEnvs) {
        // Update deployment history page per system (docgen lock is held per system name to avoid race conditions when
        // generating confluence pages). One task per system, so that a system waiting for its lock does not hold up
        // the other systems of the same batch.
        List<SystemEnv> requestedEnvironments = List.copyOf(systemEnvs);
        String environmentKey = requestedEnvironments.stream()
                .map(systemEnv -> systemEnv.getSystemId() + ":" + systemEnv.getEnvId())
                .distinct().sorted().collect(Collectors.joining(","));
        dispatcher.submitBackground("deployment-history:" + systemName.toLowerCase(Locale.ROOT) + ":" + environmentKey, () ->
                runLockedForSystem(systemName, () -> documentationGenerator.updateDeploymentHistoryPages(requestedEnvironments)));
    }

    public void triggerUpdatesAfterDataRetention(DataRetentionRefreshTask refreshTask) {
        dispatcher.submitBackground("retention-refresh:" + refreshTask.id(), () ->
                updatePagesAfterDataRetention(refreshTask));
    }

    private void updatePagesAfterDataRetention(DataRetentionRefreshTask refreshTask) {
        DataRetentionResult result = refreshTask.result();
        List<String> affectedSystemNames = result.systemEnvironments().stream()
                .map(SystemEnv::getSystemName)
                .distinct()
                .sorted()
                .toList();
        runLockedForSystems(affectedSystemNames, 0, () -> {
            if (documentationGenerator.updatePagesAfterDataRetentionIfStructureAvailable(result)) {
                dataRetentionRepository.deletePendingRefreshTask(refreshTask.id());
            } else {
                log.info("Documentation structure is busy; keeping data-retention refresh {} pending",
                        refreshTask.id());
            }
        });
    }

    private void runLockedForSystems(List<String> systemNames, int index, Runnable task) {
        if (index == systemNames.size()) {
            task.run();
            return;
        }
        runLockedForSystem(systemNames.get(index), () -> runLockedForSystems(systemNames, index + 1, task));
    }

    private static String deploymentTaskKey(UUID deploymentId) {
        return "deployment:" + deploymentId;
    }
}
