package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void generateMissingPages() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props, meterRegistryMock);
        UUID outdatedDeploymentId = UUID.randomUUID();
        when(deploymentServiceMock.getMissingDeploymentPages(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(outdatedDeploymentId));

        schedulingService.generateMissingPages();

        verify(docgenAsyncServiceMock).triggerDocgenForDeployment(outdatedDeploymentId);
    }

    @Test
    void generateMissingPages_regeneratesOutdatedAggregatePages() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props, meterRegistryMock);
        SystemEnv outdatedSystemEnv = new SystemEnv(UUID.randomUUID(), "SYSTEM A", UUID.randomUUID());
        when(deploymentServiceMock.getMissingDeploymentPages(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of());
        when(deploymentServiceMock.getOutdatedAggregatePages(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(outdatedSystemEnv));

        schedulingService.generateMissingPages();

        verify(docgenAsyncServiceMock).triggerRegenerateAggregatePages(List.of(outdatedSystemEnv));
    }

    @Test
    void generateMissingPages_skipsAggregatePagesAlreadyCoveredByARegeneratedDeployment() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props, meterRegistryMock);
        UUID outdatedDeploymentId = UUID.randomUUID();
        SystemEnv outdatedSystemEnv = new SystemEnv(UUID.randomUUID(), "SYSTEM A", UUID.randomUUID());
        when(deploymentServiceMock.getMissingDeploymentPages(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(outdatedDeploymentId));
        when(deploymentServiceMock.getOutdatedAggregatePages(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(outdatedSystemEnv));
        // Re-generating the deployment already refreshes the aggregate pages of this system and environment
        when(deploymentServiceMock.getSystemAndEnvsForDeploymentIds(Set.of(outdatedDeploymentId)))
                .thenReturn(Set.of(outdatedSystemEnv));

        schedulingService.generateMissingPages();

        verify(docgenAsyncServiceMock).triggerDocgenForDeployment(outdatedDeploymentId);
        verify(docgenAsyncServiceMock, never()).triggerRegenerateAggregatePages(any());
    }

    @Test
    void outdatedPageHousekeeping() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        SchedulingConfigProperties props = new SchedulingConfigProperties();
        SchedulingService schedulingService = new SchedulingService(
                deploymentServiceMock, docgenAsyncServiceMock, confluenceAdapter, deploymentPageRepository, props, meterRegistryMock);
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

        schedulingService.outdatedPageHousekeeping();

        verify(confluenceAdapter).deletePage(pageId);
        verify(deploymentPageRepository).delete(deploymentPage);
    }
}