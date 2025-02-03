package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toSet;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {
    private static final int GENERATOR_LAG_MAX_AGE_DAYS = 7;

    private final DeploymentService deploymentService;
    private final DocgenAsyncService docgenAsyncService;
    private final ConfluenceAdapter confluenceAdapter;
    private final DeploymentPageRepository pageRepository;
    private final SchedulingConfigProperties configProperties;
    private final MeterRegistry meterRegistry;
    private AtomicLong deploymentPageGenerationLagCounter;

    @Scheduled(cron = "${jeap.deploymentlog.documentation-generator.scheduled.cron:'-'}")
    @SchedulerLock(name = "generate-missing-pages", lockAtLeastFor = "60s", lockAtMostFor = "5m")
    public void generateMissingPages() {
        LockAssert.assertLocked();
        log.debug("Checking for missing pages that need to be generated...");

        List<UUID> deploymentIds = deploymentService.getMissingDeploymentPages(
                configProperties.getRetriedPagesLimit(),
                configProperties.getMinAgeMinutes(),
                configProperties.getMaxAgeMinutes());
        if (!deploymentIds.isEmpty()) {
            log.warn("Re-generating {} pages: {}", deploymentIds.size(), deploymentIds);
        }
        deploymentIds.forEach(docgenAsyncService::triggerDocgenForDeployment);

        log.debug("Missing page check finished");
    }

    @Scheduled(cron = "${jeap.deploymentlog.documentation-generator.housekeeping.cron:'-'}")
    @SchedulerLock(name = "outdated-page-housekeeping", lockAtLeastFor = "60s", lockAtMostFor = "30m")
    public void outdatedPageHousekeeping() {
        LockAssert.assertLocked();
        Duration minAge = Duration.ofDays(GENERATOR_LAG_MAX_AGE_DAYS);
        log.debug("Checking for {} old pages that need to be deleted...", minAge);

        List<DeploymentPage> outdatedPages =
                deploymentService.getOutdatedNonProductiveDeploymentPages(minAge, configProperties.getKeepDeploymentPagePerEnvCount());
        outdatedPages.forEach(this::deletePage);

        Set<UUID> deletedPageDeploymentIds = outdatedPages.stream()
                .map(DeploymentPage::getDeploymentId)
                .collect(toSet());
        updateDeploymentListPages(deletedPageDeploymentIds);

        log.debug("Old page check finished");
    }

    private void updateDeploymentListPages(Set<UUID> deploymentIds) {
        Set<SystemEnv> systemEnvs = deploymentService.getSystemAndEnvsForDeploymentIds(deploymentIds);
        docgenAsyncService.triggerUpdateDeploymentListPages(systemEnvs);
    }

    private void deletePage(DeploymentPage deploymentPage) {
        try {
            log.info("Deleting outdated deployment page {}", deploymentPage);
            confluenceAdapter.deletePage(deploymentPage.getPageId());
            pageRepository.delete(deploymentPage);
        } catch (Exception ex) {
            log.error("Failed to delete page {}", deploymentPage, ex);
        }
    }

    @PostConstruct
    @Scheduled(fixedRate = 15, timeUnit = MINUTES)
    public void updateMetrics() {
        long deploymentPageGenerationLag = deploymentService.countMissingDeploymentPages(GENERATOR_LAG_MAX_AGE_DAYS);

        if (deploymentPageGenerationLagCounter == null) {
            deploymentPageGenerationLagCounter = meterRegistry.gauge("deploymentlog.docgen.deploymentpages.lag",
                    new AtomicLong(deploymentPageGenerationLag));
        }

        //noinspection ConstantConditions
        deploymentPageGenerationLagCounter.set(deploymentPageGenerationLag);
    }
}
