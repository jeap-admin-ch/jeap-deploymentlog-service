package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageCleanupCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRefreshTask;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionResult;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {
    private static final int GENERATOR_LAG_MAX_AGE_DAYS = 7;
    private static final int PENDING_RETENTION_REFRESH_LIMIT = 50;

    private final DeploymentService deploymentService;
    private final DocgenAsyncService docgenAsyncService;
    private final ConfluenceAdapter confluenceAdapter;
    private final DeploymentPageRepository pageRepository;
    private final SchedulingConfigProperties configProperties;
    private final HousekeepingConfigProperties housekeepingConfig;
    private final DataRetentionRepository dataRetentionRepository;
    private final ComponentPageRepository componentPageRepository;
    private final DeploymentRepository deploymentRepository;
    private final DocgenLocks docgenLocks;
    private final MeterRegistry meterRegistry;
    private AtomicLong deploymentPageGenerationLagCounter;

    @Scheduled(cron = "${jeap.deploymentlog.documentation-generator.scheduled.cron:'-'}")
    @SchedulerLock(name = "generate-missing-pages", lockAtLeastFor = "60s", lockAtMostFor = "5m")
    public void generateMissingPages() {
        LockAssert.assertLocked();
        log.debug("Checking for missing pages that need to be generated...");

        int released = deploymentService.releaseLegacyPageGeneration(
                configProperties.getRetriedPagesLimit(),
                configProperties.getMinAgeMinutes(),
                configProperties.getMaxAgeMinutes());
        if (released > 0) {
            log.info("Released {} legacy deployments for bounded page repair", released);
        }

        List<UUID> deploymentIds = deploymentService.getMissingDeploymentPages(
                configProperties.getRetriedPagesLimit(),
                configProperties.getMinAgeMinutes(),
                configProperties.getMaxAgeMinutes());
        if (!deploymentIds.isEmpty()) {
            log.info("Re-generating {} missing or outdated pages: {}", deploymentIds.size(), deploymentIds);
        }
        for (UUID deploymentId : deploymentIds) {
            try {
                docgenAsyncService.triggerRepairDocgenForDeployment(deploymentId);
            } catch (TaskRejectedException ex) {
                log.info("Docgen queue is full; leaving deployment {} and the remaining pages for the next repair run",
                        deploymentId);
                break;
            }
        }

        log.debug("Missing page check finished");
    }

    @Scheduled(cron = "${jeap.deploymentlog.housekeeping.cron:${jeap.deploymentlog.documentation-generator.housekeeping.cron:'-'}}")
    @SchedulerLock(name = "outdated-page-housekeeping", lockAtLeastFor = "60s", lockAtMostFor = "30m")
    public void outdatedPageHousekeeping() {
        runHousekeeping();
    }

    @SchedulerLock(name = "outdated-page-housekeeping", lockAtLeastFor = "60s", lockAtMostFor = "30m")
    public void housekeeping() {
        runHousekeeping();
    }

    private void runHousekeeping() {
        LockAssert.assertLocked();
        try {
            if (housekeepingConfig.getConfluencePages().isEnabled()) {
                runHousekeepingStep("deployment-page cleanup", this::confluencePageHousekeeping);
            }
            if (housekeepingConfig.getDataRetention().isEnabled()) {
                runHousekeepingStep("data retention", this::dataRetentionHousekeeping);
            }
            if (housekeepingConfig.getComponentPages().isEnabled()) {
                runHousekeepingStep("component-page reconciliation", this::componentPageHousekeeping);
            }
        } finally {
            retryPendingDataRetentionRefreshes();
        }
    }

    private void runHousekeepingStep(String name, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ex) {
            log.error("Housekeeping step '{}' failed", name, ex);
        }
    }

    private void componentPageHousekeeping() {
        List<ComponentPageCleanupCandidate> candidates = componentPageRepository.findCleanupCandidates(
                housekeepingConfig.getComponentPages().getBatchSize());
        if (!candidates.isEmpty()) {
            log.info("Reconciling {} tracked component pages without CODE deployments", candidates.size());
        }
        candidates.forEach(this::attemptComponentPageCleanup);
    }

    private void attemptComponentPageCleanup(ComponentPageCleanupCandidate candidate) {
        int marked = componentPageRepository.markCleanupAttemptedIfNoCodeDeployment(
                candidate.componentId(), ZonedDateTime.now());
        if (marked == 0) {
            log.info("Skipping component page {} because component {} is no longer obsolete",
                    candidate.pageId(), candidate.componentId());
            return;
        }
        docgenLocks.runIfLockAquiredBeforeTimeout(
                candidate.systemName(), () -> deleteComponentPageIfStillObsolete(candidate));
    }

    private void deleteComponentPageIfStillObsolete(ComponentPageCleanupCandidate candidate) {
        if (deploymentRepository.existsCodeDeploymentForComponent(candidate.componentId())) {
            log.info("Keeping component page {} because component {} now has a CODE deployment",
                    candidate.pageId(), candidate.componentId());
            return;
        }
        try {
            confluenceAdapter.deletePage(candidate.pageId());
        } catch (RuntimeException ex) {
            log.error("Failed to delete obsolete component page {} for component {}; tracking is retained for retry",
                    candidate.pageId(), candidate.componentId(), ex);
            return;
        }

        int deleted = componentPageRepository.deleteIfNoCodeDeployment(candidate.componentId());
        if (deleted == 0) {
            log.warn("Deleted Confluence component page {} but retained or no longer found tracking for component {}. "
                            + "A concurrent CODE deployment may recreate the page through normal generation",
                    candidate.pageId(), candidate.componentId());
        } else {
            log.info("Deleted obsolete component page {} and its tracking for component {}",
                    candidate.pageId(), candidate.componentId());
        }
    }

    private void retryPendingDataRetentionRefreshes() {
        try {
            List<DataRetentionRefreshTask> tasks = dataRetentionRepository.findPendingRefreshTasks(
                    PENDING_RETENTION_REFRESH_LIMIT);
            if (!tasks.isEmpty()) {
                log.info("Retrying {} pending data-retention documentation refresh tasks", tasks.size());
            }
            tasks.forEach(docgenAsyncService::triggerUpdatesAfterDataRetention);
        } catch (RuntimeException ex) {
            log.error("Failed to load pending data-retention documentation refresh tasks", ex);
        }
    }

    private void confluencePageHousekeeping() {
        Duration minAge = housekeepingConfig.getConfluencePages().getMinAge();
        log.debug("Checking for {} old pages that need to be deleted...", minAge);

        List<DeploymentPage> outdatedPages =
                deploymentService.getOutdatedNonProductiveDeploymentPages(minAge,
                        housekeepingConfig.getConfluencePages().effectiveKeepPerEnvironment(
                                configProperties.getKeepDeploymentPagePerEnvCount()));

        Set<UUID> deletedPageDeploymentIds = outdatedPages.stream()
                .filter(this::deletePageWithSystemLock)
                .map(DeploymentPage::getDeploymentId)
                .collect(toSet());
        updateDeploymentListPages(deletedPageDeploymentIds);

        log.debug("Old page check finished");
    }

    private void dataRetentionHousekeeping() {
        HousekeepingConfigProperties.DataRetention retentionConfig = housekeepingConfig.getDataRetention();
        ZonedDateTime cutoff = ZonedDateTime.now().minus(retentionConfig.getDuration());
        List<DataRetentionCandidate> candidates = dataRetentionRepository.findDeletionCandidates(
                cutoff, retentionConfig.getBatchSize());
        if (candidates.isEmpty()) {
            log.debug("No deployments eligible for data retention before {}", cutoff);
            return;
        }
        List<String> affectedSystemNames = candidates.stream()
                .map(DataRetentionCandidate::systemName)
                .distinct()
                .sorted()
                .toList();
        runWithSystemLocks(affectedSystemNames, 0, () -> deleteRetentionCandidates(candidates, cutoff));
    }

    private void deleteRetentionCandidates(List<DataRetentionCandidate> candidates, ZonedDateTime cutoff) {
        Map<UUID, List<DataRetentionCandidate>> candidatesByUnit = candidates.stream()
                .collect(groupingBy(DataRetentionCandidate::retentionUnitId, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Set<UUID> allCandidateIds = candidates.stream().map(DataRetentionCandidate::deploymentId).collect(toSet());
        Map<UUID, DeploymentPage> pagesByDeploymentId = pageRepository
                .findDeploymentPagesByDeploymentIds(allCandidateIds).stream()
                .collect(java.util.stream.Collectors.toMap(DeploymentPage::getDeploymentId, page -> page));
        Set<UUID> approvedDeploymentIds = new HashSet<>();
        Set<UUID> deploymentsWithDeletedPages = new HashSet<>();

        candidatesByUnit.values().forEach(unit -> {
            List<UUID> unitDeploymentIds = unit.stream().map(DataRetentionCandidate::deploymentId).toList();
            List<DeploymentPage> unitPages = unitDeploymentIds.stream()
                    .map(pagesByDeploymentId::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            PageCleanupResult cleanupResult = deleteRetentionUnitPages(unitPages);
            deploymentsWithDeletedPages.addAll(cleanupResult.deletedPageDeploymentIds());
            if (cleanupResult.successful()) {
                approvedDeploymentIds.addAll(unitDeploymentIds);
            }
        });

        DataRetentionResult result;
        try {
            result = dataRetentionRepository.deleteCandidates(approvedDeploymentIds, cutoff);
        } catch (RuntimeException ex) {
            regenerateDeploymentPages(deploymentsWithDeletedPages);
            throw ex;
        }
        Set<UUID> retainedDeploymentsWithDeletedPages = new HashSet<>(deploymentsWithDeletedPages);
        retainedDeploymentsWithDeletedPages.removeAll(result.deletedDeploymentIds());
        regenerateDeploymentPages(retainedDeploymentsWithDeletedPages);
        if (!result.isEmpty()) {
            log.info("Data retention deleted {} deployments and {} flows before {}",
                    result.deletedDeployments(), result.deletedFlows(), cutoff);
        }
    }

    private void runWithSystemLocks(List<String> systemNames, int index, Runnable task) {
        if (index == systemNames.size()) {
            task.run();
            return;
        }
        docgenLocks.runIfLockAquiredBeforeTimeout(systemNames.get(index),
                () -> runWithSystemLocks(systemNames, index + 1, task));
    }

    private PageCleanupResult deleteRetentionUnitPages(List<DeploymentPage> pages) {
        List<UUID> deletedPageDeploymentIds = new ArrayList<>();
        for (DeploymentPage page : pages) {
            if (!deleteConfluencePage(page)) {
                return new PageCleanupResult(false, Set.copyOf(deletedPageDeploymentIds));
            }
            deletedPageDeploymentIds.add(page.getDeploymentId());
        }
        try {
            pages.forEach(pageRepository::delete);
            return new PageCleanupResult(true, Set.copyOf(deletedPageDeploymentIds));
        } catch (RuntimeException ex) {
            log.error("Failed to delete deployment-page tracking for retention unit; restoring its pages", ex);
            return new PageCleanupResult(false, Set.copyOf(deletedPageDeploymentIds));
        }
    }

    private boolean deleteConfluencePage(DeploymentPage deploymentPage) {
        try {
            if (deploymentPage.getPageId() != null && !deploymentPage.getPageId().isBlank()) {
                confluenceAdapter.deletePage(deploymentPage.getPageId());
            }
            return true;
        } catch (Exception ex) {
            log.error("Failed to delete page {}", deploymentPage, ex);
            return false;
        }
    }

    private void regenerateDeploymentPages(Set<UUID> deploymentIds) {
        deploymentIds.forEach(docgenAsyncService::triggerDocgenForDeployment);
    }

    private record PageCleanupResult(boolean successful, Set<UUID> deletedPageDeploymentIds) {
    }

    private void updateDeploymentListPages(Set<UUID> deploymentIds) {
        Set<SystemEnv> systemEnvs = deploymentService.getSystemAndEnvsForDeploymentIds(deploymentIds);
        systemEnvs.stream().collect(groupingBy(SystemEnv::getSystemName))
                .forEach(docgenAsyncService::triggerUpdateDeploymentListPages);
    }

    private boolean deletePageWithSystemLock(DeploymentPage deploymentPage) {
        Optional<String> systemName = deploymentService.getSystemAndEnvsForDeploymentIds(
                        Set.of(deploymentPage.getDeploymentId())).stream()
                .map(SystemEnv::getSystemName).findFirst();
        if (systemName.isEmpty()) {
            // Orphaned tracking has no deployment that could be repaired concurrently.
            return deletePage(deploymentPage);
        }
        AtomicBoolean deleted = new AtomicBoolean();
        docgenLocks.runIfLockAquiredBeforeTimeout(systemName.get(), () -> deleted.set(deletePage(deploymentPage)));
        return deleted.get();
    }

    private boolean deletePage(DeploymentPage deploymentPage) {
        try {
            log.info("Deleting outdated deployment page {}", deploymentPage);
            if (deploymentPage.getPageId() != null && !deploymentPage.getPageId().isBlank()) {
                confluenceAdapter.deletePage(deploymentPage.getPageId());
            }
            deploymentService.suppressPageGeneration(deploymentPage);
            return true;
        } catch (Exception ex) {
            log.error("Failed to delete page {}", deploymentPage, ex);
            return false;
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
