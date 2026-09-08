package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.JiraAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.model.DeploymentLetterPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.GeneratedDeploymentPageDto;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    @Qualifier(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    private ThreadPoolTaskExecutor taskExecutor;

    private final SimpleLock simpleLockMock = mock(SimpleLock.class);

    @TestConfiguration
    static class TestConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @BeforeEach
    void setUp() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
        docgenLocks.setTryAcquireTimeout(Duration.ofMinutes(3));
        reset(simpleLockMock);
        when(deploymentRepository.getSystemNameForDeployment(any())).thenReturn("systemName");
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
    void triggerDocumentationStructureReconciliation() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLockMock));

        docgenAsyncService.triggerDocumentationStructureReconciliation();

        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis()))
                .reconcileDocumentationStructure();
        verify(simpleLockMock).unlock();
        await().until(this::asyncTaskExecutorIsDone);
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

        docgenAsyncService.triggerUpdatesAfterDataRetention(refreshTask);

        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis()))
                .updatePagesAfterDataRetention(refreshTask.result());
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
                .when(documentationGenerator).updatePagesAfterDataRetention(any());

        docgenAsyncService.triggerUpdatesAfterDataRetention(refreshTask);

        await().until(this::asyncTaskExecutorIsDone);
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
        verify(documentationGenerator, never()).updatePagesAfterDataRetention(any());
        verify(dataRetentionRepository, never()).deletePendingRefreshTask(any());
    }

    private boolean asyncTaskExecutorIsDone() {
        return taskExecutor.getActiveCount() == 0 && taskExecutor.getThreadPoolExecutor().getCompletedTaskCount() > 0;
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
