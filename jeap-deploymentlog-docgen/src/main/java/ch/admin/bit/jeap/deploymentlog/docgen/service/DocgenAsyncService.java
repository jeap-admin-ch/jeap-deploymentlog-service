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

import static java.util.stream.Collectors.groupingBy;
import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class DocgenAsyncService {

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
        locks.runIfLockAquiredBeforeTimeout(systemName, () ->
                generateDeploymentPages(deploymentId));
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerDocgenForDeployment(UUID deploymentId) {
        String systemName = deploymentRepository.getSystemNameForDeployment(deploymentId);
        locks.runIfLockAquiredBeforeTimeout(systemName, () ->
                generateDeploymentPages(deploymentId));
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerDocgenForSystem(String systemName, Integer year) {
        locks.runIfLockAquiredBeforeTimeout(systemName, () ->
                documentationGenerator.generateAllPagesForSystem(systemName, year));
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerGenerateJiraLinksForSystem(String systemName, ZonedDateTime from, ZonedDateTime to) {
       documentationGenerator.generateJiraLinksForSystem(systemName, from, to);
    }

    private void generateDeploymentPages(UUID deploymentId) {
        try {
            final GeneratedDeploymentPageDto generatedDeploymentPageDto = documentationGenerator.generateDeploymentPages(deploymentId);
            Set<String> jiraIssueKeys = generatedDeploymentPageDto.getDeploymentLetterPageDto().getChangeJiraIssueKeys();
            if (jiraIssueKeys != null && !jiraIssueKeys.isEmpty()) {
                jiraAdapter.updateJiraIssuesWithConfluenceLink(generatedDeploymentPageDto);
            }
        } catch (Exception ex) {
            errorCounter.increment();
            log.warn("Failed to generate pages for deployment {}", value("deploymentId", deploymentId), ex);
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
            log.warn("Failed to generate pages for system {}", value("systemName", system.getName()), ex);
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
            log.warn("Failed to generate pages for system {}", value("systemName", system.getName()), ex);
        }
    }

    @Async(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    public void triggerUpdateDeploymentListPages(Collection<SystemEnv> envsBySystems) {
        // Update deployment history page per system (docgen lock is held per system name to avoid race conditions when
        // generating confluence pages)
        envsBySystems.stream().collect(groupingBy(SystemEnv::getSystemName))
                .forEach((systemName, systemEnvs) ->
                        locks.runIfLockAquiredBeforeTimeout(systemName, () ->
                                documentationGenerator.updateDeploymentHistoryPages(systemEnvs)));
    }
}
