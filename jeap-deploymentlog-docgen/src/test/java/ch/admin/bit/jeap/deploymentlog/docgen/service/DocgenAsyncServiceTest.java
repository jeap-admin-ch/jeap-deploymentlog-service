package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.JiraAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.model.DeploymentLetterPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.GeneratedDeploymentPageDto;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRefreshTask;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionResult;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncServiceTest.TestConfig;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {DeploymentAsyncExecutorConfiguration.class, DocgenAsyncService.class, DocgenLocks.class})
@Import(TestConfig.class)
class DocgenAsyncServiceTest {

    @MockitoBean
    private DocumentationGenerator documentationGenerator;

    @MockitoBean
    private LockProvider lockProvider;

    @MockitoBean
    private DeploymentRepository deploymentRepository;

    @MockitoBean
    private DeploymentService deploymentService;

    @MockitoBean
    private DataRetentionRepository dataRetentionRepository;

    @MockitoBean
    private JiraAdapter jiraAdapter;

    @MockitoBean
    private DeploymentPageRepository deploymentPageRepository;

    @MockitoBean
    private ConfluenceAdapter confluenceAdapter;

    @Autowired
    private DocgenLocks docgenLocks;

    @Autowired
    private DocgenAsyncService docgenAsyncService;

    @Autowired
    private DocgenTaskDispatcher taskDispatcher;

    private final SimpleLock simpleLockMock = mock(SimpleLock.class);

    @Test
    void explicitGenerationResumesRepairBeforeFailedRemoteAttempt() {
        UUID deploymentId = UUID.randomUUID();
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        when(deploymentRepository.getPageGenerationRequestId(deploymentId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(documentationGenerator.generateDeploymentPages(deploymentId))
                .thenThrow(new IllegalStateException("Confluence unavailable"));

        docgenAsyncService.triggerDocgenForDeployment(deploymentId);

        await().until(taskDispatcher::isIdle);
        var order = inOrder(deploymentService, lockProvider, documentationGenerator);
        order.verify(deploymentService).resumePageGeneration(deploymentId);
        order.verify(lockProvider).lock(any());
        order.verify(documentationGenerator).generateDeploymentPages(deploymentId);
        verify(deploymentService, never()).completePageGenerationRequest(any(), any());
    }

    @Test
    void successfulGenerationAcknowledgesCapturedRequest() {
        UUID deploymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        when(deploymentRepository.getPageGenerationRequestId(deploymentId)).thenReturn(Optional.of(requestId));
        when(documentationGenerator.generateDeploymentPages(deploymentId)).thenReturn(generatedDeploymentPageDtoWithoutJiraIssueKeys());

        docgenAsyncService.triggerDocgenForDeployment(deploymentId);

        await().until(taskDispatcher::isIdle);
        var order = inOrder(deploymentRepository, documentationGenerator, deploymentService);
        order.verify(deploymentRepository).getPageGenerationRequestId(deploymentId);
        order.verify(documentationGenerator).generateDeploymentPages(deploymentId);
        order.verify(deploymentService).completePageGenerationRequest(deploymentId, requestId);
    }

    @Test
    void deferredGenerationDoesNotAcknowledgePendingRequest() {
        UUID deploymentId = UUID.randomUUID();
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        when(deploymentRepository.getPageGenerationRequestId(deploymentId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(documentationGenerator.generateDeploymentPages(deploymentId)).thenReturn(null);

        docgenAsyncService.triggerDocgenForDeployment(deploymentId);

        await().until(taskDispatcher::isIdle);
        verify(deploymentService, never()).completePageGenerationRequest(any(), any());
    }

    @Test
    void automaticRepairDoesNotResumeSuppressedPages() {
        UUID deploymentId = UUID.randomUUID();
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));

        docgenAsyncService.triggerRepairDocgenForDeployment(deploymentId);

        await().until(taskDispatcher::isIdle);
        verify(deploymentService, never()).resumePageGeneration(any());
        verify(deploymentService).markPageGenerationAttempted(deploymentId);
    }

    @Test
    void repairRechecksEligibilityAfterAcquiringSystemLock() {
        UUID deploymentId = UUID.randomUUID();
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        when(deploymentRepository.isPageGenerationRepairRequired(deploymentId)).thenReturn(false);

        docgenAsyncService.triggerRepairDocgenForDeployment(deploymentId);

        await().until(taskDispatcher::isIdle);
        var order = inOrder(lockProvider, deploymentRepository, simpleLockMock);
        order.verify(lockProvider).lock(any());
        order.verify(deploymentRepository).isPageGenerationRepairRequired(deploymentId);
        order.verify(simpleLockMock).unlock();
        verify(documentationGenerator, never()).generateDeploymentPages(any());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void queuedAndRejectedRepairsAreNotMarkedAsAttempted() throws InterruptedException {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        DocgenTaskDispatcher smallDispatcher = new DocgenTaskDispatcher(1, 10);
        DocgenAsyncService service = new DocgenAsyncService(documentationGenerator, deploymentRepository,
                dataRetentionRepository, new SimpleMeterRegistry(), docgenLocks, smallDispatcher, deploymentService);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        smallDispatcher.submitBackground("blocker", () -> {
            running.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            org.junit.jupiter.api.Assertions.assertTrue(running.await(10, TimeUnit.SECONDS));
            UUID queued = UUID.randomUUID();
            UUID rejected = UUID.randomUUID();
            service.triggerRepairDocgenForDeployment(queued);
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.core.task.TaskRejectedException.class,
                    () -> service.triggerRepairDocgenForDeployment(rejected));
            verifyNoInteractions(deploymentService);
            release.countDown();
            await().until(smallDispatcher::isIdle);
            verify(deploymentService).markPageGenerationAttempted(queued);
            verify(deploymentService, never()).markPageGenerationAttempted(rejected);
        } finally {
            release.countDown();
            smallDispatcher.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
        docgenLocks.setTryAcquireTimeout(Duration.ofMinutes(3));
        reset(simpleLockMock);
        when(deploymentRepository.getSystemNameForDeployment(any())).thenReturn("systemName");
        when(deploymentRepository.isPageGenerationRepairRequired(any())).thenReturn(true);
    }

    @Test
    void triggerDocgenForDeployment() {
        Optional<SimpleLock> presentLock = Optional.of(simpleLockMock);
        when(lockProvider.lock(any())).thenReturn(presentLock);
        when(documentationGenerator.generateDeploymentPages(any())).thenReturn(generatedDeploymentPageDtoWithoutJiraIssueKeys());

        List<UUID> uuids = Stream.generate(UUID::randomUUID)
                .limit(10).toList();

        uuids.forEach(uuid -> docgenAsyncService.triggerDocgenForDeployment(uuid));

        uuids.forEach(uuid ->
                verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis())).generateDeploymentPages(uuid));
        await().until(this::asyncTaskExecutorIsDone);
        verify(simpleLockMock, times(10)).unlock();
    }

    @Test
    void triggerDocgenForDeployment_whenGeneratorReturnsNull_thenLockReleased() {
        Optional<SimpleLock> presentLock = Optional.of(simpleLockMock);
        when(lockProvider.lock(any())).thenReturn(presentLock);
        UUID deploymentId = UUID.randomUUID();
        when(documentationGenerator.generateDeploymentPages(deploymentId)).thenReturn(null);

        docgenAsyncService.triggerDocgenForDeployment(deploymentId);

        await().until(this::asyncTaskExecutorIsDone);
        verify(documentationGenerator).generateDeploymentPages(deploymentId);
        verify(simpleLockMock).unlock();
    }

    @Test
    void triggerDocgenForDeployment_whenLockingFails_thenExpectNoExceptionAndNoPagesGenerated() {
        docgenLocks.setTryAcquireTimeout(Duration.ofSeconds(5));
        when(lockProvider.lock(any())).thenReturn(Optional.empty());
        UUID deploymentId = UUID.randomUUID();

        docgenAsyncService.triggerDocgenForDeployment(deploymentId);

        await().until(this::asyncTaskExecutorIsDone);
        verify(documentationGenerator, never()).generateDeploymentPages(any());
    }

    @Test
    void triggerDocgenForSystem() {
        Optional<SimpleLock> presentLock = Optional.of(simpleLockMock);
        when(lockProvider.lock(any())).thenReturn(presentLock);

        docgenAsyncService.triggerDocgenForSystem("systemName", null);

        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis())).generateAllPagesForSystem("systemName", null);
        verify(simpleLockMock).unlock();
        await().until(this::asyncTaskExecutorIsDone);
    }

    @Test
    void triggerDocumentationStructureReconciliationAcquiresStructureLockExactlyOnce() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        doAnswer(invocation -> {
            docgenLocks.runWithDocumentationStructureLock(() -> null);
            return null;
        }).when(documentationGenerator).reconcileDocumentationStructure();

        docgenAsyncService.triggerDocumentationStructureReconciliation();

        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis()))
                .reconcileDocumentationStructure();
        await().until(this::asyncTaskExecutorIsDone);
        verify(lockProvider).lock(any());
        verify(simpleLockMock).unlock();
    }

    @Test
    void triggerGenerateJiraLinksForSystemUsesSystemLock() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        ZonedDateTime from = ZonedDateTime.now().minusDays(2);
        ZonedDateTime to = ZonedDateTime.now();

        docgenAsyncService.triggerGenerateJiraLinksForSystem("systemName", from, to);

        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis()))
                .generateJiraLinksForSystem("systemName", from, to);
        verify(simpleLockMock, timeout(Duration.ofSeconds(10).toMillis())).unlock();
        await().until(this::asyncTaskExecutorIsDone);
    }

    @Test
    void triggerUpdateDeploymentListPages() {
        Optional<SimpleLock> presentLock = Optional.of(simpleLockMock);
        when(lockProvider.lock(any())).thenReturn(presentLock);
        SystemEnv systemEnv = new SystemEnv(UUID.randomUUID(), "systemName", UUID.randomUUID());
        List<SystemEnv> systemEnvs = List.of(systemEnv);

        docgenAsyncService.triggerUpdateDeploymentListPages(systemEnv.getSystemName(), systemEnvs);

        await().until(this::asyncTaskExecutorIsDone);
        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis())).updateDeploymentHistoryPages(systemEnvs);
        verify(simpleLockMock, timeout(Duration.ofSeconds(10).toMillis())).unlock();
    }

    @Test
    void historyRefreshesForDifferentEnvironmentsAreNotDeduplicated() throws InterruptedException {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        taskDispatcher.submitBackground("blocker", () -> {
            running.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            org.junit.jupiter.api.Assertions.assertTrue(running.await(10, TimeUnit.SECONDS));
            UUID systemId = UUID.randomUUID();
            List<SystemEnv> first = List.of(new SystemEnv(systemId, "system", UUID.randomUUID()));
            List<SystemEnv> second = List.of(new SystemEnv(systemId, "system", UUID.randomUUID()));
            docgenAsyncService.triggerUpdateDeploymentListPages("system", first);
            docgenAsyncService.triggerUpdateDeploymentListPages("system", second);
            release.countDown();
            await().until(taskDispatcher::isIdle);
            verify(documentationGenerator).updateDeploymentHistoryPages(first);
            verify(documentationGenerator).updateDeploymentHistoryPages(second);
        } finally {
            release.countDown();
        }
    }

    @Test
    void triggerUpdateDeploymentListPages_whenAcquiringTheLockFails_thenNoExceptionEscapes() {
        when(lockProvider.lock(any())).thenThrow(new IllegalStateException("database not available"));
        SystemEnv systemEnv = new SystemEnv(UUID.randomUUID(), "systemName", UUID.randomUUID());

        docgenAsyncService.triggerUpdateDeploymentListPages(systemEnv.getSystemName(), List.of(systemEnv));

        // Without the guard in runLockedForSystem this would escape the async task and, in a scheduled batch, skip
        // every system that has not been processed yet
        await().until(this::asyncTaskExecutorIsDone);
        verify(documentationGenerator, never()).updateDeploymentHistoryPages(any());
    }

    @Test
    void triggerUpdatesAfterDataRetentionLocksAllAffectedSystems() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        DataRetentionResult result = new DataRetentionResult(
                Set.of(new SystemEnv(UUID.randomUUID(), "system-b", UUID.randomUUID()),
                        new SystemEnv(UUID.randomUUID(), "system-a", UUID.randomUUID())),
                Set.of(), Set.of(), Set.of(), 1, 0, Set.of(UUID.randomUUID()));
        DataRetentionRefreshTask refreshTask = DataRetentionRefreshTask.from(result);
        when(documentationGenerator.updatePagesAfterDataRetentionIfStructureAvailable(refreshTask.result())).thenReturn(true);

        docgenAsyncService.triggerUpdatesAfterDataRetention(refreshTask);

        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis()))
                .updatePagesAfterDataRetentionIfStructureAvailable(refreshTask.result());
        verify(dataRetentionRepository, timeout(Duration.ofSeconds(10).toMillis()))
                .deletePendingRefreshTask(refreshTask.id());
        verify(lockProvider, timeout(Duration.ofSeconds(10).toMillis()).times(2)).lock(any());
        verify(simpleLockMock, timeout(Duration.ofSeconds(10).toMillis()).times(2)).unlock();
        await().until(this::asyncTaskExecutorIsDone);
    }

    @Test
    void failedDataRetentionRefreshRemainsPending() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        DataRetentionResult result = new DataRetentionResult(
                Set.of(new SystemEnv(UUID.randomUUID(), "systemName", UUID.randomUUID())),
                Set.of(), Set.of(), Set.of(), 1, 0, Set.of(UUID.randomUUID()));
        DataRetentionRefreshTask refreshTask = DataRetentionRefreshTask.from(result);
        doThrow(new IllegalStateException("Confluence unavailable"))
                .when(documentationGenerator).updatePagesAfterDataRetentionIfStructureAvailable(any());

        docgenAsyncService.triggerUpdatesAfterDataRetention(refreshTask);

        await().until(this::asyncTaskExecutorIsDone);
        verify(dataRetentionRepository, never()).deletePendingRefreshTask(any());
    }

    @Test
    void deferredDataRetentionRefreshRemainsPending() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));
        DataRetentionRefreshTask refreshTask = DataRetentionRefreshTask.from(new DataRetentionResult(
                Set.of(new SystemEnv(UUID.randomUUID(), "systemName", UUID.randomUUID())),
                Set.of(), Set.of(), Set.of(), 1, 0, Set.of(UUID.randomUUID())));
        when(documentationGenerator.updatePagesAfterDataRetentionIfStructureAvailable(refreshTask.result())).thenReturn(false);

        docgenAsyncService.triggerUpdatesAfterDataRetention(refreshTask);

        await().until(this::asyncTaskExecutorIsDone);
        verify(documentationGenerator).updatePagesAfterDataRetentionIfStructureAvailable(refreshTask.result());
        verify(dataRetentionRepository, never()).deletePendingRefreshTask(any());
    }

    @Test
    void dataRetentionRefreshRemainsPendingWhenLockCannotBeAcquired() {
        docgenLocks.setTryAcquireTimeout(Duration.ZERO);
        when(lockProvider.lock(any())).thenReturn(Optional.empty());
        DataRetentionRefreshTask refreshTask = DataRetentionRefreshTask.from(new DataRetentionResult(
                Set.of(new SystemEnv(UUID.randomUUID(), "systemName", UUID.randomUUID())),
                Set.of(), Set.of(), Set.of(), 1, 0, Set.of(UUID.randomUUID())));

        docgenAsyncService.triggerUpdatesAfterDataRetention(refreshTask);

        await().until(this::asyncTaskExecutorIsDone);
        verify(documentationGenerator, never()).updatePagesAfterDataRetentionIfStructureAvailable(any());
        verify(dataRetentionRepository, never()).deletePendingRefreshTask(any());
    }

    private boolean asyncTaskExecutorIsDone() {
        return taskDispatcher.isIdle();
    }

    private GeneratedDeploymentPageDto generatedDeploymentPageDtoWithoutJiraIssueKeys() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .changeJiraIssueKeys(Set.of())
                .build();
        return GeneratedDeploymentPageDto.builder()
                .deploymentLetterPageDto(deploymentLetterPageDto)
                .pageId("pageId")
                .build();
    }
}
