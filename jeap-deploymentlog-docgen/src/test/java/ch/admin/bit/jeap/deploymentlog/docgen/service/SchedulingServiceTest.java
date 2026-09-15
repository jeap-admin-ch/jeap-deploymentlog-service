package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRefreshTask;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionResult;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    @Mock
    private DeploymentService deploymentServiceMock;
    @Mock
    private DocgenAsyncService docgenAsyncServiceMock;
    @Mock
    private ConfluenceAdapter confluenceAdapter;
    @Mock
    private MeterRegistry meterRegistryMock;
    @Mock
    private DeploymentPageRepository deploymentPageRepository;
    @Mock
    private DataRetentionRepository dataRetentionRepository;
    @Mock
    private DocgenLocks docgenLocksMock;

    @BeforeEach
    void runTasksProtectedByDocgenLocks() {
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(docgenLocksMock).runIfLockAquiredBeforeTimeout(any(), any());
    }

    @Test
    void generateMissingPages() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props,
                new HousekeepingConfigProperties(), dataRetentionRepository, docgenLocksMock, meterRegistryMock);
        UUID outdatedDeploymentId = UUID.randomUUID();
        when(deploymentServiceMock.getMissingDeploymentPages(50, 5, 10_080))
                .thenReturn(List.of(outdatedDeploymentId));

        schedulingService.generateMissingPages();

        verify(deploymentServiceMock).getMissingDeploymentPages(50, 5, 10_080);
        verify(docgenAsyncServiceMock).triggerRepairDocgenForDeployment(outdatedDeploymentId);
    }

    @Test
    void generateMissingPages_usesConfiguredAgeWindow() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        props.setMaxAgeMinutes(2_880);
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props,
                new HousekeepingConfigProperties(), dataRetentionRepository, docgenLocksMock, meterRegistryMock);
        when(deploymentServiceMock.getMissingDeploymentPages(50, 5, 2_880)).thenReturn(List.of());

        schedulingService.generateMissingPages();

        verify(deploymentServiceMock).getMissingDeploymentPages(50, 5, 2_880);
    }

    @Test
    void generateMissingPages_leavesRemainingPagesForNextRunWhenQueueIsFull() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props,
                new HousekeepingConfigProperties(), dataRetentionRepository, docgenLocksMock, meterRegistryMock);
        UUID firstDeploymentId = UUID.randomUUID();
        UUID secondDeploymentId = UUID.randomUUID();
        when(deploymentServiceMock.getMissingDeploymentPages(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(firstDeploymentId, secondDeploymentId));
        org.mockito.Mockito.doThrow(new TaskRejectedException("queue full"))
                .when(docgenAsyncServiceMock).triggerRepairDocgenForDeployment(firstDeploymentId);

        schedulingService.generateMissingPages();

        verify(docgenAsyncServiceMock).triggerRepairDocgenForDeployment(firstDeploymentId);
        verify(docgenAsyncServiceMock, never()).triggerRepairDocgenForDeployment(secondDeploymentId);
    }

    @Test
    void outdatedPageHousekeeping() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props,
                new HousekeepingConfigProperties(), dataRetentionRepository, docgenLocksMock, meterRegistryMock);
        String pageId = "123";
        DeploymentPage deploymentPage = DeploymentPage.builder()
                .id(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .pageId(pageId)
                .lastUpdatedAt(ZonedDateTime.now())
                .deploymentStateTimestamp(ZonedDateTime.now())
                .build();
        when(deploymentServiceMock.getOutdatedNonProductiveDeploymentPages(Duration.ofDays(7), 200))
                .thenReturn(List.of(deploymentPage));
        when(deploymentServiceMock.getSystemAndEnvsForDeploymentIds(Set.of(deploymentPage.getDeploymentId())))
                .thenReturn(Set.of(new SystemEnv(UUID.randomUUID(), "system", UUID.randomUUID())));

        schedulingService.outdatedPageHousekeeping();

        verify(confluenceAdapter).deletePage(pageId);
        verify(deploymentServiceMock).suppressPageGeneration(deploymentPage);
        verify(docgenLocksMock).runIfLockAquiredBeforeTimeout(eq("system"), any());
        verify(deploymentPageRepository, never()).delete(deploymentPage);
    }

    @Test
    void housekeepingRunsDataRetentionAndTriggersAffectedPageUpdates() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties schedulingProps = new SchedulingConfigProperties();
        HousekeepingConfigProperties housekeepingProps = new HousekeepingConfigProperties();
        housekeepingProps.getConfluencePages().setEnabled(false);
        housekeepingProps.getDataRetention().setEnabled(true);
        housekeepingProps.getDataRetention().setDuration(Duration.ofDays(30));
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository,
                schedulingProps, housekeepingProps, dataRetentionRepository, docgenLocksMock, meterRegistryMock);
        UUID deploymentId = UUID.randomUUID();
        DataRetentionResult result = new DataRetentionResult(
                Set.of(new SystemEnv(UUID.randomUUID(), "system", UUID.randomUUID())),
                Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()), Set.of("JEAP-1"), 1, 0,
                Set.of(deploymentId));
        when(dataRetentionRepository.findDeletionCandidates(any(), eq(500)))
                .thenReturn(List.of(DataRetentionCandidate.standalone(deploymentId, "system")));
        when(dataRetentionRepository.deleteCandidates(eq(Set.of(deploymentId)), any())).thenReturn(result);
        DataRetentionRefreshTask refreshTask = DataRetentionRefreshTask.from(result);
        when(dataRetentionRepository.findPendingRefreshTasks(50)).thenReturn(List.of(refreshTask));

        schedulingService.housekeeping();

        verify(dataRetentionRepository).deleteCandidates(eq(Set.of(deploymentId)), any());
        verify(docgenAsyncServiceMock).triggerUpdatesAfterDataRetention(refreshTask);
    }

    @Test
    void housekeepingRetriesPendingRetentionRefreshWhenRetentionIsDisabled() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        HousekeepingConfigProperties housekeepingProps = new HousekeepingConfigProperties();
        housekeepingProps.getConfluencePages().setEnabled(false);
        DataRetentionRefreshTask refreshTask = DataRetentionRefreshTask.from(new DataRetentionResult(
                Set.of(new SystemEnv(UUID.randomUUID(), "system", UUID.randomUUID())),
                Set.of(), Set.of(), Set.of(), 1, 0, Set.of(UUID.randomUUID())));
        when(dataRetentionRepository.findPendingRefreshTasks(50)).thenReturn(List.of(refreshTask));
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository,
                new SchedulingConfigProperties(), housekeepingProps, dataRetentionRepository, docgenLocksMock,
                meterRegistryMock);

        schedulingService.housekeeping();

        verify(docgenAsyncServiceMock).triggerUpdatesAfterDataRetention(refreshTask);
    }

    @Test
    void failedConfluenceDeletionKeepsDeploymentData() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        HousekeepingConfigProperties housekeepingProps = new HousekeepingConfigProperties();
        housekeepingProps.getConfluencePages().setEnabled(false);
        housekeepingProps.getDataRetention().setEnabled(true);
        housekeepingProps.getDataRetention().setDuration(Duration.ofDays(30));
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository,
                new SchedulingConfigProperties(), housekeepingProps, dataRetentionRepository, docgenLocksMock,
                meterRegistryMock);
        UUID deploymentId = UUID.randomUUID();
        DeploymentPage deploymentPage = DeploymentPage.builder()
                .id(UUID.randomUUID()).deploymentId(deploymentId).pageId("page")
                .lastUpdatedAt(ZonedDateTime.now()).deploymentStateTimestamp(ZonedDateTime.now()).build();
        when(dataRetentionRepository.findDeletionCandidates(any(), eq(500)))
                .thenReturn(List.of(DataRetentionCandidate.standalone(deploymentId, "system")));
        when(deploymentPageRepository.findDeploymentPagesByDeploymentIds(Set.of(deploymentId)))
                .thenReturn(List.of(deploymentPage));
        when(dataRetentionRepository.deleteCandidates(eq(Set.of()), any())).thenReturn(DataRetentionResult.empty());
        org.mockito.Mockito.doThrow(new IllegalStateException("Confluence unavailable"))
                .when(confluenceAdapter).deletePage("page");

        schedulingService.housekeeping();

        verify(dataRetentionRepository).deleteCandidates(eq(Set.of()), any());
    }

    @Test
    void failedPageDeletionKeepsWholeTerminalFlowAndRegeneratesAlreadyDeletedPages() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        HousekeepingConfigProperties housekeepingProps = new HousekeepingConfigProperties();
        housekeepingProps.getConfluencePages().setEnabled(false);
        housekeepingProps.getDataRetention().setEnabled(true);
        housekeepingProps.getDataRetention().setDuration(Duration.ofDays(30));
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository,
                new SchedulingConfigProperties(), housekeepingProps, dataRetentionRepository, docgenLocksMock,
                meterRegistryMock);
        UUID flowId = UUID.randomUUID();
        UUID firstDeploymentId = UUID.randomUUID();
        UUID secondDeploymentId = UUID.randomUUID();
        DeploymentPage firstPage = deploymentPage(firstDeploymentId, "first-page");
        DeploymentPage secondPage = deploymentPage(secondDeploymentId, "second-page");
        when(dataRetentionRepository.findDeletionCandidates(any(), eq(500))).thenReturn(List.of(
                new DataRetentionCandidate(firstDeploymentId, flowId, "system"),
                new DataRetentionCandidate(secondDeploymentId, flowId, "system")));
        when(deploymentPageRepository.findDeploymentPagesByDeploymentIds(
                Set.of(firstDeploymentId, secondDeploymentId))).thenReturn(List.of(firstPage, secondPage));
        when(dataRetentionRepository.deleteCandidates(eq(Set.of()), any())).thenReturn(DataRetentionResult.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
            if ("second-page".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("Confluence unavailable");
            }
            return null;
        }).when(confluenceAdapter).deletePage(any());

        schedulingService.housekeeping();

        verify(deploymentPageRepository, never()).delete(any());
        verify(dataRetentionRepository).deleteCandidates(eq(Set.of()), any());
        verify(docgenAsyncServiceMock).triggerDocgenForDeployment(firstDeploymentId);
    }

    @Test
    void failedDatabaseDeletionRegeneratesPagesDeletedBeforeRollback() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        HousekeepingConfigProperties housekeepingProps = new HousekeepingConfigProperties();
        housekeepingProps.getConfluencePages().setEnabled(false);
        housekeepingProps.getDataRetention().setEnabled(true);
        housekeepingProps.getDataRetention().setDuration(Duration.ofDays(30));
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository,
                new SchedulingConfigProperties(), housekeepingProps, dataRetentionRepository, docgenLocksMock,
                meterRegistryMock);
        UUID deploymentId = UUID.randomUUID();
        DeploymentPage page = deploymentPage(deploymentId, "page");
        when(dataRetentionRepository.findDeletionCandidates(any(), eq(500)))
                .thenReturn(List.of(DataRetentionCandidate.standalone(deploymentId, "system")));
        when(deploymentPageRepository.findDeploymentPagesByDeploymentIds(Set.of(deploymentId)))
                .thenReturn(List.of(page));
        when(dataRetentionRepository.deleteCandidates(eq(Set.of(deploymentId)), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        schedulingService.housekeeping();

        verify(docgenAsyncServiceMock).triggerDocgenForDeployment(deploymentId);
    }

    @Test
    void unavailableSystemLockPreventsRetentionCleanup() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        HousekeepingConfigProperties housekeepingProps = new HousekeepingConfigProperties();
        housekeepingProps.getConfluencePages().setEnabled(false);
        housekeepingProps.getDataRetention().setEnabled(true);
        housekeepingProps.getDataRetention().setDuration(Duration.ofDays(30));
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository,
                new SchedulingConfigProperties(), housekeepingProps, dataRetentionRepository, docgenLocksMock,
                meterRegistryMock);
        UUID deploymentId = UUID.randomUUID();
        when(dataRetentionRepository.findDeletionCandidates(any(), eq(500)))
                .thenReturn(List.of(DataRetentionCandidate.standalone(deploymentId, "system")));
        org.mockito.Mockito.doNothing().when(docgenLocksMock).runIfLockAquiredBeforeTimeout(any(), any());

        schedulingService.housekeeping();

        verify(docgenLocksMock).runIfLockAquiredBeforeTimeout(eq("system"), any());
        verify(dataRetentionRepository, never()).deleteCandidates(any(), any());
        org.mockito.Mockito.verifyNoInteractions(confluenceAdapter, deploymentPageRepository);
    }

    @Test
    void bothManualHousekeepingEntryPointsAreShedLockProtected() throws NoSuchMethodException {
        assertThat(SchedulingService.class.getMethod("housekeeping").getAnnotation(SchedulerLock.class)).isNotNull();
        assertThat(SchedulingService.class.getMethod("outdatedPageHousekeeping")
                .getAnnotation(SchedulerLock.class)).isNotNull();
    }

    private static DeploymentPage deploymentPage(UUID deploymentId, String pageId) {
        return DeploymentPage.builder()
                .id(UUID.randomUUID()).deploymentId(deploymentId).pageId(pageId)
                .lastUpdatedAt(ZonedDateTime.now()).deploymentStateTimestamp(ZonedDateTime.now()).build();
    }
}
