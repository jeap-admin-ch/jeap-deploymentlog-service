package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.JiraAdapter;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncServiceTest.TestConfig;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {DeploymentAsyncExecutorConfiguration.class, DocgenAsyncService.class, DocgenLocks.class})
@Import(TestConfig.class)
class DocgenAsyncServiceTest {

    @MockBean
    private DocumentationGenerator documentationGenerator;

    @MockBean
    private LockProvider lockProvider;

    @MockBean
    private DeploymentRepository deploymentRepository;

    @MockBean
    private JiraAdapter jiraAdapter;

    @MockBean
    private DeploymentPageRepository deploymentPageRepository;

    @MockBean
    private ConfluenceAdapter confluenceAdapter;

    @Autowired
    private DocgenLocks docgenLocks;

    @Autowired
    private DocgenAsyncService docgenAsyncService;

    @Autowired
    @Qualifier(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    private ThreadPoolTaskExecutor taskExecutor;

    @Mock
    private SimpleLock simpleLockMock;

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
        when(deploymentRepository.getSystemNameForDeployment(any())).thenReturn("systemName");
    }

    @Test
    void triggerDocgenForDeployment() {
        Optional<SimpleLock> presentLock = Optional.of(simpleLockMock);
        when(lockProvider.lock(any())).thenReturn(presentLock);

        List<UUID> uuids = Stream.generate(UUID::randomUUID)
                .limit(10).toList();

        uuids.forEach(uuid -> docgenAsyncService.triggerDocgenForDeployment(uuid));

        uuids.forEach(uuid ->
                verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis())).generateDeploymentPages(uuid));
        verify(simpleLockMock, times(10)).unlock();
        await().until(this::asyncTaskExecutorIsDone);
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
    void triggerUpdateDeploymentListPages() {
        Optional<SimpleLock> presentLock = Optional.of(simpleLockMock);
        when(lockProvider.lock(any())).thenReturn(presentLock);
        SystemEnv systemEnv = new SystemEnv(UUID.randomUUID(), "systemName", UUID.randomUUID());
        List<SystemEnv> systemEnvs = List.of(systemEnv);

        docgenAsyncService.triggerUpdateDeploymentListPages(systemEnvs);

        await().until(this::asyncTaskExecutorIsDone);
        verify(documentationGenerator, timeout(Duration.ofSeconds(10).toMillis())).updateDeploymentHistoryPages(systemEnvs);
        verify(simpleLockMock, timeout(Duration.ofSeconds(10).toMillis())).unlock();
    }

    private boolean asyncTaskExecutorIsDone() {
        return taskExecutor.getActiveCount() == 0 && taskExecutor.getThreadPoolExecutor().getCompletedTaskCount() > 0;
    }
}
