package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;

import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class DocgenAsyncService {

    private static final String SYSTEM_NAME = "systemName";
    private static final String COMPONENT_NAME = "componentName";
    public static final String DEPLOYMENT_ID = "deploymentId";

    private final DocumentationGenerator documentationGenerator;
    private final DeploymentRepository deploymentRepository;
    private final DataRetentionRepository dataRetentionRepository;
    private final Counter errorCounter;
    private final DocgenLocks locks;

    public DocgenAsyncService(DocumentationGenerator documentationGenerator, DeploymentRepository deploymentRepository,
                              DataRetentionRepository dataRetentionRepository, MeterRegistry meterRegistry,
                              DocgenLocks locks) {
        this.documentationGenerator = documentationGenerator;
        this.deploymentRepository = deploymentRepository;
        this.dataRetentionRepository = dataRetentionRepository;
        this.locks = locks;
        this.errorCounter = meterRegistry.counter("deploymentlog.docgen.deploymentpages.error");
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerDocgenForUndeployment(String systemName, UUID deploymentId) {
        triggerDeploymentPageGeneration(deploymentId, systemName);
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerDocgenForDeployment(UUID deploymentId) {
        triggerDeploymentPageGeneration(deploymentId, null);
    }

    private void triggerDeploymentPageGeneration(UUID deploymentId, String knownSystemName) {
        String systemName = knownSystemName;
        String componentName = null;
        try {
            if (systemName == null) {
                systemName = deploymentRepository.getSystemNameForDeployment(deploymentId);
            }
            componentName = deploymentRepository.getComponentNameForDeployment(deploymentId);
            String lockedSystemName = systemName;
            String loggedComponentName = componentName;
            locks.runIfLockAquiredBeforeTimeout(systemName, () ->
                    generateDeploymentPages(deploymentId, lockedSystemName, loggedComponentName));
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Failed to trigger page generation for deployment {}, system {} and component {}",
                    value(DEPLOYMENT_ID, deploymentId), value(SYSTEM_NAME, systemName),
                    value(COMPONENT_NAME, componentName), ex);
        }
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerDocgenForSystem(String systemName, Integer year) {
        locks.runIfLockAquiredBeforeTimeout(systemName, () ->
                documentationGenerator.generateAllPagesForSystem(systemName, year));
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerDocumentationStructureReconciliation() {
        runLockedForSystem("documentation-structure", documentationGenerator::reconcileDocumentationStructure);
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerGenerateJiraLinksForSystem(String systemName, ZonedDateTime from, ZonedDateTime to) {
        runLockedForSystem(systemName, () ->
                documentationGenerator.generateJiraLinksForSystem(systemName, from, to));
    }

    private void generateDeploymentPages(UUID deploymentId, String systemName, String componentName) {
        try {
            documentationGenerator.generateDeploymentPages(deploymentId);
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Failed to generate pages for deployment {}, system {} and component {}",
                    value(DEPLOYMENT_ID, deploymentId), value(SYSTEM_NAME, systemName),
                    value(COMPONENT_NAME, componentName), ex);
        }
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerMigrationForSystem(System system) {
        locks.runIfLockAquiredBeforeTimeout(system.getName(), () ->
                migrateSystem(system));
    }

    private void migrateSystem(System system) {
        try {
            documentationGenerator.migrateSystem(system);
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Failed to generate pages for system {}", value(SYSTEM_NAME, system.getName()), ex);
        }
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerMergeSystems(System system, System oldSystem) {
        locks.runIfLockAquiredBeforeTimeout(system.getName(), () ->
                mergeSystems(system, oldSystem));
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
            locks.runIfLockAquiredBeforeTimeout(systemName, task);
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Docgen failed for system {}", value(SYSTEM_NAME, systemName), ex);
        }
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerUpdateDeploymentListPages(String systemName, Collection<SystemEnv> systemEnvs) {
        // Update deployment history page per system (docgen lock is held per system name to avoid race conditions when
        // generating confluence pages). One task per system, so that a system waiting for its lock does not hold up
        // the other systems of the same batch.
        runLockedForSystem(systemName, () -> documentationGenerator.updateDeploymentHistoryPages(systemEnvs));
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerUpdatesAfterDataRetention(DataRetentionRefreshTask refreshTask) {
        DataRetentionResult result = refreshTask.result();
        List<String> affectedSystemNames = result.systemEnvironments().stream()
                .map(SystemEnv::getSystemName)
                .distinct()
                .sorted()
                .toList();
        runLockedForSystems(affectedSystemNames, 0, () -> {
            documentationGenerator.updatePagesAfterDataRetention(result);
            dataRetentionRepository.deletePendingRefreshTask(refreshTask.id());
        });
    }

    private void runLockedForSystems(List<String> systemNames, int index, Runnable task) {
        if (index == systemNames.size()) {
            task.run();
            return;
        }
        runLockedForSystem(systemNames.get(index), () -> runLockedForSystems(systemNames, index + 1, task));
    }
}
