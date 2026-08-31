package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.JiraAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.model.GeneratedDeploymentPageDto;
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
    private final Counter errorCounter;
    private final DocgenLocks locks;
    private final JiraAdapter jiraAdapter;

    public DocgenAsyncService(DocumentationGenerator documentationGenerator, DeploymentRepository deploymentRepository, MeterRegistry meterRegistry, DocgenLocks locks, JiraAdapter jiraAdapter) {
        this.documentationGenerator = documentationGenerator;
        this.deploymentRepository = deploymentRepository;
        this.locks = locks;
        this.errorCounter = meterRegistry.counter("deploymentlog.docgen.deploymentpages.error");
        this.jiraAdapter = jiraAdapter;
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
       documentationGenerator.generateJiraLinksForSystem(systemName, from, to);
    }

    private void generateDeploymentPages(UUID deploymentId, String systemName, String componentName) {
        try {
            final GeneratedDeploymentPageDto generatedDeploymentPageDto = documentationGenerator.generateDeploymentPages(deploymentId);
            if (generatedDeploymentPageDto == null || generatedDeploymentPageDto.getDeploymentLetterPageDto() == null) {
                errorCounter.increment();
                log.warn("Generated deployment page data is incomplete for deployment {}. Skipping Jira issue link update.", value(DEPLOYMENT_ID, deploymentId));
                return;
            }
            Set<String> jiraIssueKeys = generatedDeploymentPageDto.getDeploymentLetterPageDto().getChangeJiraIssueKeys();
            if (jiraIssueKeys != null && !jiraIssueKeys.isEmpty()) {
                jiraAdapter.updateJiraIssuesWithConfluenceLink(generatedDeploymentPageDto);
            }
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
}
