package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class DeploymentRepositoryImplTest {

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private JpaDeploymentPageRepository jpaDeploymentPageRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByExternalId_deploymentFound() {
        String externalId = "test1";
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environment = new Environment("test");
        environmentRepository.save(environment);
        System system = new System("test");
        systemRepository.save(system);
        Component component = new Component("test", system);
        componentRepository.save(component);
        ComponentVersion componentVersion = ComponentVersion.builder()
                .commitRef("test")
                .taggedAt(ZonedDateTime.now())
                .committedAt(ZonedDateTime.now())
                .versionControlUrl("test")
                .publishedVersion(false)
                .component(component)
                .versionName("1.2.3-4")
                .deploymentUnit(DeploymentUnit.builder()
                        .artifactRepositoryUrl("test")
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("test")
                        .build())
                .build();

        deploymentRepository.save(Deployment.builder()
                .externalId(externalId)
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(environment)
                .target(deploymentTarget)
                .componentVersion(componentVersion)
                .changelog(Changelog.builder()
                        .comment("comment")
                        .comparedToVersion("1.0.0")
                        .jiraIssueKeys(Set.of("1", "2"))
                        .build())
                .sequence(DeploymentSequence.NEW)
                .deploymentTypes(Set.of(DeploymentType.CODE, DeploymentType.CONFIG))
                .build());

        Optional<Deployment> deployment = deploymentRepository.findByExternalId(externalId);
        assertThat(deployment).isPresent();
        assertThat(deployment.get().getChangelog().getJiraIssueKeys()).isEqualTo(Set.of("1", "2"));

        assertThat(deploymentRepository.findByExternalId("fake")).isNotPresent();
    }

    @Test
    void findByExternalIdForUpdate_deploymentFound() {
        Environment environment = environmentRepository.save(new Environment("DEV"));
        System system = systemRepository.save(new System("SYSTEM"));
        Component component = componentRepository.save(new Component("service", system));
        Deployment deployment = deploymentRepository.save(TestDataFactory.createDeployment(
                environment, component, ZonedDateTime.now(), TestDataFactory.createDeploymentTarget()));
        entityManager.flush();
        entityManager.clear();

        assertThat(deploymentRepository.findByExternalIdForUpdate(deployment.getExternalId()))
                .hasValueSatisfying(found -> {
                    assertThat(found.getId()).isEqualTo(deployment.getId());
                    assertThat(found.getExternalId()).isEqualTo(deployment.getExternalId());
                    assertThat(found.getState()).isEqualTo(DeploymentState.STARTED);
                });
    }

    @Test
    void findByExternalIdForUpdate_unknownExternalId() {
        assertThat(deploymentRepository.findByExternalIdForUpdate("unknown-external-id")).isEmpty();
    }

    @Test
    void findDeploymentAndEnvironments() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("DEV");
        environmentRepository.save(environmentDev);
        Environment environmentRef = new Environment("REF");
        environmentRepository.save(environmentRef);

        System systemA = new System("System A");
        systemRepository.save(systemA);
        System systemB = new System("System B");
        systemRepository.save(systemB);

        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);
        Component systemBMicroserviceB = new Component("Microservice B", systemB);
        componentRepository.save(systemBMicroserviceB);

        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentRef, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemBMicroserviceB, ZonedDateTime.now(), deploymentTarget));

        List<Deployment> deploymentListDev = deploymentRepository.findAllDeploymentForSystemAndEnv(systemA, environmentDev);

        assertEquals(2, deploymentListDev.size());

        List<Deployment> deploymentListRef = deploymentRepository.findAllDeploymentForSystemAndEnv(systemA, environmentRef);

        assertEquals(1, deploymentListRef.size());


        List<Environment> environmentListSystemA = environmentRepository.findEnvironmentsForSystem(systemA);
        assertEquals(2, environmentListSystemA.size());

        List<Environment> environmentListSystemB = environmentRepository.findEnvironmentsForSystem(systemB);
        assertEquals(1, environmentListSystemB.size());
    }

    @Test
    void findAllDeploymentsYearsForSystemAndEnv() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("DEV");
        environmentRepository.save(environmentDev);
        Environment environmentRef = new Environment("REF");
        environmentRepository.save(environmentRef);

        System systemA = new System("System A");
        systemRepository.save(systemA);

        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);

        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, createZoneDateTime(2021), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, createZoneDateTime(2022), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentRef, systemAMicroserviceA, createZoneDateTime(2019), deploymentTarget));

        List<Integer> yearListDev = deploymentRepository.findAllDeploymentsYearsForSystemAndEnv(systemA, environmentDev);
        assertEquals(yearListDev, List.of(2021, 2022));

        List<Integer> yearListRef = deploymentRepository.findAllDeploymentsYearsForSystemAndEnv(systemA, environmentRef);
        assertEquals(yearListRef, List.of(2019));
    }

    @Test
    void findAllDeploymentsForSystemStartedBetween() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environment = new Environment("DEV");
        environmentRepository.save(environment);

        System systemA = new System("System A");
        systemRepository.save(systemA);

        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);

        deploymentRepository.save(TestDataFactory.createDeployment(environment, systemAMicroserviceA, ZonedDateTime.now().minusDays(20), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environment, systemAMicroserviceA, ZonedDateTime.now().minusDays(18), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environment, systemAMicroserviceA, ZonedDateTime.now().minusDays(10), deploymentTarget));

        List<Deployment> results = deploymentRepository.findAllDeploymentsForSystemStartedBetween(systemA, ZonedDateTime.now().minusDays(11), ZonedDateTime.now());
        assertThat(results).hasSize(1);

        results = deploymentRepository.findAllDeploymentsForSystemStartedBetween(systemA, ZonedDateTime.now().minusDays(21), ZonedDateTime.now().minusDays(12));
        assertThat(results).hasSize(2);
    }

    @Test
    void findsActiveJiraIssueDeploymentsAndFiltersStatusHistoryToCode() {
        Environment environment = environmentRepository.save(new Environment("DEV"));
        System system = systemRepository.save(new System("System A"));
        Component component = componentRepository.save(new Component("component", system));
        ZonedDateTime now = ZonedDateTime.now();

        Deployment recentCode = deploymentWithIssue(environment, component, now.minusDays(1), "jeap-1",
                Set.of(DeploymentType.CODE));
        Deployment oldCode = deploymentWithIssue(environment, component, now.minusDays(60), "JEAP-1",
                Set.of(DeploymentType.CODE));
        Deployment recentConfig = deploymentWithIssue(environment, component, now.minusDays(2), "JEAP-1",
                Set.of(DeploymentType.CONFIG));
        deploymentRepository.save(recentCode);
        deploymentRepository.save(oldCode);
        deploymentRepository.save(recentConfig);

        assertThat(deploymentRepository.findDeploymentsWithJiraIssuesStartedAtOrAfter(now.minusDays(30)))
                .extracting(Deployment::getId)
                .containsExactlyInAnyOrder(recentCode.getId(), recentConfig.getId());
        assertThat(deploymentRepository.findCodeDeploymentsForJiraIssues(Set.of("JEAP-1")))
                .extracting(Deployment::getId)
                .containsExactlyInAnyOrder(recentCode.getId(), oldCode.getId());
        assertThat(deploymentRepository.findDeploymentsForJiraIssues(Set.of("JEAP-1")))
                .extracting(Deployment::getId)
                .containsExactlyInAnyOrder(recentCode.getId(), oldCode.getId(), recentConfig.getId());
    }

    @Test
    void existsCodeDeploymentForComponentDistinguishesConfigCodeAndMixedDeployments() {
        Environment environment = environmentRepository.save(new Environment("CODE-CHECK"));
        System system = systemRepository.save(new System("code-check-system"));
        Component configOnly = componentRepository.save(new Component("config-only", system));
        Component codeOnly = componentRepository.save(new Component("code-only", system));
        Component mixed = componentRepository.save(new Component("mixed", system));
        DeploymentTarget target = TestDataFactory.createDeploymentTarget();

        Deployment configDeployment = TestDataFactory.createDeployment(
                environment, configOnly, ZonedDateTime.now(), target);
        configDeployment.getDeploymentTypes().add(DeploymentType.CONFIG);
        deploymentRepository.save(configDeployment);
        Deployment codeDeployment = TestDataFactory.createDeployment(
                environment, codeOnly, ZonedDateTime.now(), target);
        codeDeployment.getDeploymentTypes().add(DeploymentType.CODE);
        deploymentRepository.save(codeDeployment);
        Deployment mixedDeployment = TestDataFactory.createDeployment(
                environment, mixed, ZonedDateTime.now(), target);
        mixedDeployment.getDeploymentTypes().addAll(Set.of(DeploymentType.CODE, DeploymentType.CONFIG));
        deploymentRepository.save(mixedDeployment);
        entityManager.flush();
        entityManager.clear();

        assertThat(deploymentRepository.existsCodeDeploymentForComponent(configOnly.getId())).isFalse();
        assertThat(deploymentRepository.existsCodeDeploymentForComponent(codeOnly.getId())).isTrue();
        assertThat(deploymentRepository.existsCodeDeploymentForComponent(mixed.getId())).isTrue();
    }

    @Test
    void findsDistinctMetricIdentitiesForStartedDeployments() {
        Environment environment = environmentRepository.save(new Environment("METRICS"));
        System system = systemRepository.save(new System("metric-system"));
        Component startedComponent = componentRepository.save(new Component("started", system));
        Component completedComponent = componentRepository.save(new Component("completed", system));
        DeploymentTarget target = TestDataFactory.createDeploymentTarget();

        Deployment started = TestDataFactory.createDeployment(
                environment, startedComponent, ZonedDateTime.now(), target);
        started.getDeploymentTypes().addAll(Set.of(DeploymentType.CODE, DeploymentType.CONFIG));
        deploymentRepository.save(started);

        Deployment completed = TestDataFactory.createDeployment(
                environment, completedComponent, ZonedDateTime.now(), target);
        completed.getDeploymentTypes().add(DeploymentType.CODE);
        ZonedDateTime completedAt = ZonedDateTime.now().plusMinutes(1);
        completed.success(completedAt, "done");
        deploymentRepository.save(completed);
        entityManager.flush();
        deploymentRepository.reconcileTerminalDeploymentMetrics();
        deploymentRepository.reconcileTerminalDeploymentMetrics();

        assertThat(deploymentRepository.findStartedDeploymentMetricIdentities()).containsExactlyInAnyOrder(
                new DeploymentMetricIdentity("metric-system", "started", "METRICS", DeploymentType.CODE),
                new DeploymentMetricIdentity("metric-system", "started", "METRICS", DeploymentType.CONFIG));
        assertThat(deploymentRepository.findDeploymentMetricIdentities()).containsExactlyInAnyOrder(
                new DeploymentMetricIdentity("metric-system", "started", "METRICS", DeploymentType.CODE),
                new DeploymentMetricIdentity("metric-system", "started", "METRICS", DeploymentType.CONFIG),
                new DeploymentMetricIdentity("metric-system", "completed", "METRICS", DeploymentType.CODE));
        assertThat(deploymentRepository.findDeploymentMetricValues()).containsExactly(
                new DeploymentMetricValue("metric-system", "completed", "METRICS", DeploymentType.CODE,
                        DeploymentState.SUCCESS, 1));
    }

    private Deployment deploymentWithIssue(Environment environment, Component component, ZonedDateTime startedAt,
                                           String issueKey, Set<DeploymentType> deploymentTypes) {
        ComponentVersion componentVersion = ComponentVersion.builder()
                .commitRef("test")
                .taggedAt(startedAt)
                .committedAt(startedAt)
                .versionControlUrl("test")
                .publishedVersion(false)
                .component(component)
                .versionName(UUID.randomUUID().toString())
                .deploymentUnit(DeploymentUnit.builder()
                        .artifactRepositoryUrl("test")
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("test")
                        .build())
                .build();
        return Deployment.builder()
                .externalId(UUID.randomUUID().toString())
                .startedAt(startedAt)
                .startedBy("user")
                .environment(environment)
                .componentVersion(componentVersion)
                .changelog(Changelog.builder().jiraIssueKeys(Set.of(issueKey)).build())
                .sequence(DeploymentSequence.NEW)
                .deploymentTypes(deploymentTypes)
                .build();
    }

    @Test
    void findDeploymentForSystemAndEnvLimited() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("DEV");
        environmentRepository.save(environmentDev);

        System systemA = new System("System A");
        systemRepository.save(systemA);

        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);

        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));


        List<Deployment> deploymentListDev = deploymentRepository
                .findDeploymentForSystemAndEnvLimited(systemA, environmentDev, 2);

        assertEquals(2, deploymentListDev.size());
    }

    @Test
    void findDeploymentForEnvLimited() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("DEV");
        environmentRepository.save(environmentDev);

        System systemA = new System("System A");
        systemRepository.save(systemA);

        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);

        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now().minusHours(1), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now().minusHours(2), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now().minusHours(2), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now().plusHours(1), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now().plusHours(2), deploymentTarget));

        ZonedDateTime minStartedAt = ZonedDateTime.now();

        List<Deployment> deploymentListDev = deploymentRepository.findDeploymentForEnvLimited(environmentDev, minStartedAt, 2);

        assertEquals(2, deploymentListDev.size());
    }

    /**
     * Helper Method to create a special ZoneDateTime Object
     *
     * @param year as int
     * @return ZoneDateTime with variable year, date and time is fix
     */
    private ZonedDateTime createZoneDateTime(int year) {
        return ZonedDateTime.of(year, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault());
    }

    @Test
    void getDeploymentIdsMissingOrOutdatedGeneratedPages() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("DEV");
        environmentRepository.save(environmentDev);
        System systemA = new System("System A");
        systemRepository.save(systemA);
        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);

        Deployment deploymentWithoutPage = TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget);
        Deployment oldDeploymentWithoutPage = TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now().minusYears(1), deploymentTarget);
        Deployment deploymentWithPage = TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget);
        Deployment deploymentWithOutdatedPage = TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget);
        deploymentRepository.save(deploymentWithoutPage);
        deploymentRepository.save(oldDeploymentWithoutPage);
        deploymentRepository.save(deploymentWithPage);
        deploymentRepository.save(deploymentWithOutdatedPage);
        // Up-to-date page for deploymentWithPage
        jpaDeploymentPageRepository.save(DeploymentPage.builder()
                .id(UUID.randomUUID())
                .deploymentId(deploymentWithPage.getId())
                .pageId("1")
                .lastUpdatedAt(ZonedDateTime.now())
                .deploymentStateTimestamp(deploymentWithPage.getLastModified())
                .build());
        // 1 day old page for deploymentWithOutdatedPage
        jpaDeploymentPageRepository.save(DeploymentPage.builder()
                .id(UUID.randomUUID())
                .deploymentId(deploymentWithOutdatedPage.getId())
                .pageId("2")
                .lastUpdatedAt(ZonedDateTime.now())
                .deploymentStateTimestamp(deploymentWithOutdatedPage.getLastModified().minusDays(1))
                .build());
        // No page for deploymentWithoutPage
        // No page for oldDeploymentWithoutPage (too old to trigger re-generation run)

        List<UUID> result = deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(10,
                ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1));

        assertEquals(
                Set.of(deploymentWithoutPage.getId(), deploymentWithOutdatedPage.getId()),
                Set.copyOf(result),
                "Should not return deploymentWithPage as it is up-to-date and has a generated page");

        List<UUID> allMissingPages = deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(
                10, ZonedDateTime.now().plusHours(1));
        assertEquals(oldDeploymentWithoutPage.getId(), allMissingPages.getFirst(),
                "The repair queue should include and prioritize the oldest missing page");
        assertEquals(Set.of(oldDeploymentWithoutPage.getId(), deploymentWithoutPage.getId(),
                        deploymentWithOutdatedPage.getId()), Set.copyOf(allMissingPages));
        assertThat(deploymentRepository.isPageGenerationRepairRequired(deploymentWithoutPage.getId())).isTrue();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(deploymentWithPage.getId())).isFalse();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(deploymentWithOutdatedPage.getId())).isTrue();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(UUID.randomUUID())).isFalse();

        deploymentRepository.markPageGenerationAttempted(
                List.of(oldDeploymentWithoutPage.getId()), ZonedDateTime.now());
        List<UUID> nextRepairBatch = deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(
                1, ZonedDateTime.now().plusHours(1));
        assertThat(nextRepairBatch)
                .as("a failed oldest page must not starve untried missing pages")
                .isNotEmpty()
                .doesNotContain(oldDeploymentWithoutPage.getId());

        // Housekeeping compares persisted timestamps, with the database's microsecond precision.
        entityManager.clear();
        ZonedDateTime persistedTimestamp = deploymentRepository.getById(deploymentWithoutPage.getId()).getLastModified();
        deploymentRepository.suppressPageGeneration(deploymentWithoutPage.getId(), persistedTimestamp);
        assertThat(deploymentRepository.isPageGenerationRepairRequired(deploymentWithoutPage.getId())).isFalse();
        List<UUID> repairableAfterSuppression = deploymentRepository
                .getDeploymentIdsWithMissingOrOutdatedGeneratedPages(10, ZonedDateTime.now().plusHours(1));
        assertThat(repairableAfterSuppression)
                .as("housekeeping-suppressed pages must not be recreated by automatic repair")
                .isNotEmpty()
                .doesNotContain(deploymentWithoutPage.getId());
        assertEquals(1, deploymentRepository.countDeploymentsWithMissingOrOutdatedGeneratedPages(ZonedDateTime.now().minusDays(30)));

        deploymentRepository.resumePageGeneration(deploymentWithoutPage.getId());
        entityManager.clear();
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(
                10, ZonedDateTime.now().plusHours(1)))
                .as("an explicit retry becomes repairable before any page is written")
                .contains(deploymentWithoutPage.getId());

        assertEquals(2, deploymentRepository.countDeploymentsWithMissingOrOutdatedGeneratedPages(ZonedDateTime.now().minusDays(30)));

        deploymentRepository.completePageGenerationRequest(deploymentWithoutPage.getId(),
                deploymentRepository.getPageGenerationRequestId(deploymentWithoutPage.getId()).orElseThrow());
        entityManager.clear();
        Deployment updatedDeployment = deploymentRepository.getById(deploymentWithoutPage.getId());
        ZonedDateTime oldPageTimestamp = updatedDeployment.getLastModified();
        updatedDeployment.success(ZonedDateTime.now(), "updated during housekeeping");
        entityManager.flush();
        deploymentRepository.suppressPageGeneration(updatedDeployment.getId(), oldPageTimestamp);
        entityManager.clear();
        Deployment afterHousekeeping = deploymentRepository.getById(updatedDeployment.getId());
        assertThat(afterHousekeeping.getState()).isEqualTo(DeploymentState.SUCCESS);
        assertThat(afterHousekeeping.isPageGenerationSuppressed()).isFalse();
    }

    @Test
    void repairAgeWindowRetainsPendingRequestsAndRotatesAttempts() {
        ZonedDateTime now = ZonedDateTime.now().withNano(0);
        ZonedDateTime from = now.minusDays(7);
        ZonedDateTime to = now.minusMinutes(5);
        Environment environment = environmentRepository.save(new Environment("DEV"));
        System system = systemRepository.save(new System("repair-window"));
        Component component = componentRepository.save(new Component("component", system));
        DeploymentTarget target = TestDataFactory.createDeploymentTarget();
        Deployment old = deploymentRepository.save(TestDataFactory.createDeployment(
                environment, component, from.minusSeconds(1), target));
        Deployment boundary = deploymentRepository.save(TestDataFactory.createDeployment(
                environment, component, from, target));
        Deployment recent = deploymentRepository.save(TestDataFactory.createDeployment(
                environment, component, now.minusDays(2), target));
        Deployment young = deploymentRepository.save(TestDataFactory.createDeployment(
                environment, component, to.plusSeconds(1), target));
        entityManager.flush();

        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(50, from, to))
                .containsExactly(boundary.getId(), recent.getId());
        deploymentRepository.markPageGenerationAttempted(List.of(boundary.getId()), now);
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(1, from, to))
                .containsExactly(recent.getId());

        deploymentRepository.resumePageGeneration(old.getId());
        deploymentRepository.resumePageGeneration(young.getId());
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(50, from, to))
                .containsExactly(old.getId(), recent.getId(), boundary.getId());
        deploymentRepository.completePageGenerationRequest(old.getId(),
                deploymentRepository.getPageGenerationRequestId(old.getId()).orElseThrow());
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(50, from, to))
                .doesNotContain(old.getId(), young.getId());
    }

    @Test
    void pendingGenerationSurvivesHousekeepingAndAcknowledgesOnlyTheCapturedRequest() {
        Environment environment = new Environment("DEV");
        environmentRepository.save(environment);
        System system = new System("pending-generation");
        systemRepository.save(system);
        Component component = new Component("component", system);
        componentRepository.save(component);
        Deployment deployment = deploymentRepository.save(TestDataFactory.createDeployment(environment, component,
                ZonedDateTime.now().minusDays(30), TestDataFactory.createDeploymentTarget()));
        jpaDeploymentPageRepository.save(DeploymentPage.builder().id(UUID.randomUUID())
                .deploymentId(deployment.getId()).pageId("existing-page").lastUpdatedAt(ZonedDateTime.now())
                .deploymentStateTimestamp(deployment.getLastModified()).build());
        entityManager.flush();
        entityManager.clear();
        UUID id = deployment.getId();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isFalse();

        deploymentRepository.resumePageGeneration(id);
        UUID firstRequest = deploymentRepository.getPageGenerationRequestId(id).orElseThrow();
        // The worker may time out waiting for the system lock before housekeeping completes.
        deploymentRepository.suppressPageGeneration(id, ZonedDateTime.now().plusDays(1));
        entityManager.clear();
        assertThat(deploymentRepository.getById(id).isPageGenerationSuppressed()).isFalse();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isTrue();
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(10, ZonedDateTime.now()))
                .contains(id);
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(
                10, ZonedDateTime.now().minusDays(7), ZonedDateTime.now().minusMinutes(5)))
                .as("a pending request for an old, already tracked page must survive the repair age limit")
                .contains(id);

        deploymentRepository.resumePageGeneration(id);
        UUID laterRequest = deploymentRepository.getPageGenerationRequestId(id).orElseThrow();
        assertThat(laterRequest).isNotEqualTo(firstRequest);
        deploymentRepository.completePageGenerationRequest(id, firstRequest);
        assertThat(deploymentRepository.getPageGenerationRequestId(id)).contains(laterRequest);
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isTrue();

        deploymentRepository.completePageGenerationRequest(id, laterRequest);
        assertThat(deploymentRepository.getPageGenerationRequestId(id)).isEmpty();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isFalse();
        deploymentRepository.suppressPageGeneration(id, ZonedDateTime.now().plusDays(1));
        entityManager.clear();
        assertThat(deploymentRepository.getById(id).isPageGenerationSuppressed()).isTrue();
    }

    @Test
    void releasesLegacyMissingPagesInBoundedRepairWindowBatches() {
        Environment dev = new Environment("DEV");
        environmentRepository.save(dev);
        System system = new System("legacy");
        systemRepository.save(system);
        Component component = new Component("component", system);
        componentRepository.save(component);
        DeploymentTarget target = TestDataFactory.createDeploymentTarget();
        ZonedDateTime now = ZonedDateTime.now();
        Deployment outsideWindow = deploymentRepository.save(
                TestDataFactory.createDeployment(dev, component, now.minusDays(8), target));
        Deployment oldest = deploymentRepository.save(
                TestDataFactory.createDeployment(dev, component, now.minusDays(6), target));
        Deployment middle = deploymentRepository.save(
                TestDataFactory.createDeployment(dev, component, now.minusDays(5), target));
        Deployment newest = deploymentRepository.save(
                TestDataFactory.createDeployment(dev, component, now.minusDays(4), target));
        entityManager.flush();
        entityManager.createNativeQuery("update deployment set page_generation_legacy_unclassified = true")
                .executeUpdate();
        entityManager.clear();
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(20, now.plusDays(1))).isEmpty();

        assertThat(deploymentRepository.releaseLegacyPageGeneration(
                2, now.minusDays(7), now.minusMinutes(5))).isEqualTo(2);
        entityManager.clear();
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(20, now.plusDays(1)))
                .containsExactly(oldest.getId(), middle.getId());
        assertThat(deploymentRepository.getById(newest.getId()).isPageGenerationLegacyUnclassified()).isTrue();
        assertThat(deploymentRepository.getById(outsideWindow.getId()).isPageGenerationLegacyUnclassified()).isTrue();

        assertThat(deploymentRepository.releaseLegacyPageGeneration(
                2, now.minusDays(7), now.minusMinutes(5))).isEqualTo(1);
        entityManager.clear();
        assertThat(deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(20, now.plusDays(1)))
                .containsExactly(oldest.getId(), middle.getId(), newest.getId())
                .doesNotContain(outsideWindow.getId());
    }

    @Test
    void saveDeployment_unparseableVersionNumber() {
        String externalId = "test1";
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environment = new Environment("test");
        environmentRepository.save(environment);
        System system = new System("test");
        systemRepository.save(system);
        Component component = new Component("test", system);
        componentRepository.save(component);
        ComponentVersion componentVersion = ComponentVersion.builder()
                .commitRef("test")
                .taggedAt(ZonedDateTime.now())
                .committedAt(ZonedDateTime.now())
                .versionControlUrl("test")
                .publishedVersion(false)
                .component(component)
                .versionName("na")
                .deploymentUnit(DeploymentUnit.builder()
                        .artifactRepositoryUrl("test")
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("test")
                        .build())
                .build();

        deploymentRepository.save(Deployment.builder()
                .externalId(externalId)
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(environment)
                .target(deploymentTarget)
                .componentVersion(componentVersion)
                .properties(Map.of("key", "value"))
                .referenceIdentifiers(Set.of("key"))
                .changelog(Changelog.builder()
                        .comment("comment")
                        .comparedToVersion("1.0.0")
                        .jiraIssueKeys(Set.of("1", "2"))
                        .build())
                .sequence(DeploymentSequence.NEW)
                .build());

        Optional<Deployment> deployment = deploymentRepository.findByExternalId(externalId);
        assertThat(deployment)
                .isPresent();
        assertThat(deployment.get().getReferenceIdentifiers())
                .isEqualTo(Set.of("key"));
        ComponentVersion savedComponentVersion = deployment.get().getComponentVersion();
        assertThat(savedComponentVersion.getVersionName())
                .isEqualTo("na");
        assertThat(savedComponentVersion.getVersionNumber())
                .isNull();
    }

    @Test
    void getLastDeploymentForComponent_success() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = new Environment("DEV");
        environmentRepository.save(env);
        System system = new System("System A");
        systemRepository.save(system);
        Component component = new Component("Microservice A", system);
        componentRepository.save(component);

        Deployment newestDeployment = TestDataFactory.createDeployment(env, component, ZonedDateTime.now(), deploymentTarget);
        Deployment oneDayOldDeployment = TestDataFactory.createDeployment(env, component, ZonedDateTime.now().minusDays(1), deploymentTarget);
        deploymentRepository.save(newestDeployment);
        deploymentRepository.save(oneDayOldDeployment);

        Optional<Deployment> lastDeployment = deploymentRepository
                .getLastDeploymentForComponent(component, env);

        assertTrue(lastDeployment.isPresent());
        assertEquals(newestDeployment.getId(), lastDeployment.get().getId());
    }

    @Test
    void updateDeploymentProperties() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = new Environment("DEV");
        environmentRepository.save(env);
        System system = new System("System A");
        systemRepository.save(system);
        Component component = new Component("Microservice A", system);
        componentRepository.save(component);
        Deployment deployment = TestDataFactory.createDeployment(env, component, ZonedDateTime.now(), deploymentTarget);
        deploymentRepository.save(deployment);
        entityManager.flush();

        Deployment deploymentToUpdate = deploymentRepository.findByExternalId(deployment.getExternalId()).orElseThrow();
        deploymentToUpdate.getProperties().putAll(Map.of("key1", "value1", "key2", "value2"));
        deploymentRepository.save(deploymentToUpdate);
        entityManager.flush();

        Deployment updatedDeployment = deploymentRepository.findByExternalId(deployment.getExternalId()).orElseThrow();
        assertThat(updatedDeployment.getProperties())
                .containsEntry("key1", "value1")
                .containsEntry("key2", "value2")
                .hasSize(2);
    }

    @Test
    void getLastDeploymentForComponent_differentEnvAndComponentFilters_shouldApplyFilter() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = new Environment("DEV");
        environmentRepository.save(env);
        Environment otherEnv = new Environment("REF");
        environmentRepository.save(otherEnv);
        System system = new System("System");
        systemRepository.save(system);
        Component component = new Component("Microservice A", system);
        componentRepository.save(component);
        Component otherComponent = new Component("Microservice B", system);
        componentRepository.save(otherComponent);

        Deployment deploymentDifferentEnv = TestDataFactory.createDeployment(otherEnv, component, ZonedDateTime.now().minusDays(1), deploymentTarget);
        Deployment deploymentDifferentComponent = TestDataFactory.createDeployment(env, otherComponent, ZonedDateTime.now().minusDays(1), deploymentTarget);
        deploymentRepository.save(deploymentDifferentComponent);
        deploymentRepository.save(deploymentDifferentEnv);

        Optional<Deployment> lastDeployment = deploymentRepository
                .getLastDeploymentForComponent(component, env);
        assertTrue(lastDeployment.isEmpty());

        Optional<Deployment> lastDeploymentOtherComponent = deploymentRepository
                .getLastDeploymentForComponent(otherComponent, env);
        assertFalse(lastDeploymentOtherComponent.isEmpty());
        assertEquals(deploymentDifferentComponent.getId(), lastDeploymentOtherComponent.get().getId());

        Optional<Deployment> lastDeploymentOtherEnv = deploymentRepository
                .getLastDeploymentForComponent(component, otherEnv);
        assertFalse(lastDeploymentOtherEnv.isEmpty());
        assertEquals(deploymentDifferentEnv.getId(), lastDeploymentOtherEnv.get().getId());
    }

    @Test
    void getLastSuccessfulDeploymentForComponent() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = new Environment("DEV");
        environmentRepository.save(env);
        System system = new System("System A");
        systemRepository.save(system);
        Component component = new Component("Microservice A", system);
        componentRepository.save(component);
        Component otherComponent = new Component("Microservice B", system);
        componentRepository.save(otherComponent);
        Environment otherEnv = new Environment("REF");
        environmentRepository.save(otherEnv);

        Deployment lastSuccess = TestDataFactory.createDeployment(env, component, ZonedDateTime.now().minusDays(1), deploymentTarget);
        lastSuccess.success(lastSuccess.getStartedAt().plusMinutes(1), "success");
        Deployment newerButFailed = TestDataFactory.createDeployment(env, component, ZonedDateTime.now(), deploymentTarget);
        newerButFailed.failed(newerButFailed.getStartedAt().plusMinutes(1), "failed");
        deploymentRepository.save(lastSuccess);
        deploymentRepository.save(newerButFailed);

        Optional<Deployment> lastSuccessfulDeployment = deploymentRepository
                .getLastSuccessfulDeploymentForComponent(component, env);
        assertTrue(lastSuccessfulDeployment.isPresent());
        assertEquals(lastSuccess.getId(), lastSuccessfulDeployment.get().getId());

        Optional<Deployment> lastSuccessfulDeploymentNoComponentMatch = deploymentRepository
                .getLastSuccessfulDeploymentForComponent(otherComponent, env);
        Optional<Deployment> lastSuccessfulDeploymentNoEnvMatch = deploymentRepository
                .getLastSuccessfulDeploymentForComponent(component, otherEnv);

        assertTrue(lastSuccessfulDeploymentNoComponentMatch.isEmpty());
        assertTrue(lastSuccessfulDeploymentNoEnvMatch.isEmpty());
    }

    @Test
    void getLastSuccessfulCodeDeploymentForComponentBeforeVersion_previousVersionFound_returnPreviousVersion() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = new Environment("DEV");
        environmentRepository.save(env);
        System system = new System("System A");
        systemRepository.save(system);
        Component component = new Component("Microservice A", system);
        componentRepository.save(component);


        Deployment currentVersion = TestDataFactory.createDeployment(env, component, ZonedDateTime.now().minusDays(1), "2", deploymentTarget);
        currentVersion.success(currentVersion.getStartedAt().plusMinutes(1), "success");
        Deployment previousVersion = TestDataFactory.createDeployment(env, component, ZonedDateTime.now(), "1", deploymentTarget);
        previousVersion.getDeploymentTypes().add(DeploymentType.CODE);
        previousVersion.success(previousVersion.getStartedAt().plusMinutes(1), "success");
        deploymentRepository.save(currentVersion);
        deploymentRepository.save(previousVersion);

        String versionName = "2";

        Optional<Deployment> lastSuccessfulDeployment = deploymentRepository.getLastSuccessfulCodeDeploymentForComponentDifferentToVersion(component, env, versionName);
        assertTrue(lastSuccessfulDeployment.isPresent());
        assertEquals(previousVersion.getId(), lastSuccessfulDeployment.get().getId());

    }

    @Test
    void getLastSuccessfulCodeDeploymentForComponentBeforeVersion_previousVersionNotFound_returnEmpty() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = new Environment("DEV");
        environmentRepository.save(env);
        System system = new System("System A");
        systemRepository.save(system);
        Component component = new Component("Microservice A", system);
        componentRepository.save(component);


        Deployment currentVersion = TestDataFactory.createDeployment(env, component, ZonedDateTime.now().minusDays(1), "2", deploymentTarget);
        currentVersion.success(currentVersion.getStartedAt().plusMinutes(1), "success");
        Deployment previousVersion = TestDataFactory.createDeployment(env, component, ZonedDateTime.now(), "2", deploymentTarget);
        previousVersion.success(previousVersion.getStartedAt().plusMinutes(1), "success");
        Deployment failedVersion = TestDataFactory.createDeployment(env, component, ZonedDateTime.now(), "1", deploymentTarget);
        failedVersion.getDeploymentTypes().add(DeploymentType.CODE);
        failedVersion.failed(previousVersion.getStartedAt().plusMinutes(1), "failed");
        deploymentRepository.save(currentVersion);
        deploymentRepository.save(previousVersion);

        String versionName = "2";

        Optional<Deployment> lastSuccessfulDeployment = deploymentRepository.getLastSuccessfulCodeDeploymentForComponentDifferentToVersion(component, env, versionName);
        assertTrue(lastSuccessfulDeployment.isEmpty());

    }

    @Test
    void getLastSuccessfulCodeDeploymentForComponentDifferentToVersion_skipsConfigOnlyDeployment() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = environmentRepository.save(new Environment("DEV"));
        System system = systemRepository.save(new System("System A"));
        Component component = componentRepository.save(new Component("Microservice A", system));

        Deployment previousCodeDeployment = TestDataFactory.createDeployment(
                env, component, ZonedDateTime.now().minusDays(2), "1", deploymentTarget);
        previousCodeDeployment.getDeploymentTypes().add(DeploymentType.CODE);
        previousCodeDeployment.success(previousCodeDeployment.getStartedAt().plusMinutes(1), "success");

        Deployment newerConfigDeployment = TestDataFactory.createDeployment(
                env, component, ZonedDateTime.now().minusDays(1), "config-change", deploymentTarget);
        newerConfigDeployment.getDeploymentTypes().add(DeploymentType.CONFIG);
        newerConfigDeployment.success(newerConfigDeployment.getStartedAt().plusMinutes(1), "success");

        deploymentRepository.save(previousCodeDeployment);
        deploymentRepository.save(newerConfigDeployment);

        Optional<Deployment> result = deploymentRepository
                .getLastSuccessfulCodeDeploymentForComponentDifferentToVersion(component, env, "2");

        assertThat(result)
                .map(Deployment::getId)
                .contains(previousCodeDeployment.getId());
    }

    @Test
    void getLastSuccessfulCodeDeploymentForComponentDifferentToVersion_includesLegacyUntypedDeployment() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = environmentRepository.save(new Environment("DEV"));
        System system = systemRepository.save(new System("System A"));
        Component component = componentRepository.save(new Component("Microservice A", system));

        Deployment legacyUntypedDeployment = TestDataFactory.createDeployment(
                env, component, ZonedDateTime.now().minusDays(2), "1", deploymentTarget);
        legacyUntypedDeployment.success(legacyUntypedDeployment.getStartedAt().plusMinutes(1), "success");

        Deployment newerConfigDeployment = TestDataFactory.createDeployment(
                env, component, ZonedDateTime.now().minusDays(1), "config-change", deploymentTarget);
        newerConfigDeployment.getDeploymentTypes().add(DeploymentType.CONFIG);
        newerConfigDeployment.success(newerConfigDeployment.getStartedAt().plusMinutes(1), "success");

        deploymentRepository.save(legacyUntypedDeployment);
        deploymentRepository.save(newerConfigDeployment);

        Optional<Deployment> result = deploymentRepository
                .getLastSuccessfulCodeDeploymentForComponentDifferentToVersion(component, env, "2");

        assertThat(result)
                .map(Deployment::getId)
                .contains(legacyUntypedDeployment.getId());
    }

    @Test
    void getLastSuccessfulCodeDeploymentForComponentDifferentToVersion_skipsUntypedUndeployment() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment env = environmentRepository.save(new Environment("DEV"));
        System system = systemRepository.save(new System("System A"));
        Component component = componentRepository.save(new Component("Microservice A", system));

        Deployment previousCodeDeployment = TestDataFactory.createDeployment(
                env, component, ZonedDateTime.now().minusDays(2), "1", deploymentTarget);
        previousCodeDeployment.getDeploymentTypes().add(DeploymentType.CODE);
        previousCodeDeployment.success(previousCodeDeployment.getStartedAt().plusMinutes(1), "success");

        Deployment newerUntypedUndeployment = TestDataFactory.createDeployment(
                env, component, ZonedDateTime.now().minusDays(1), "(undeployed)", deploymentTarget,
                DeploymentSequence.UNDEPLOYED);
        newerUntypedUndeployment.success(newerUntypedUndeployment.getStartedAt().plusMinutes(1), "success");

        deploymentRepository.save(previousCodeDeployment);
        deploymentRepository.save(newerUntypedUndeployment);

        Optional<Deployment> result = deploymentRepository
                .getLastSuccessfulCodeDeploymentForComponentDifferentToVersion(component, env, "2");

        assertThat(result)
                .map(Deployment::getId)
                .contains(previousCodeDeployment.getId());
    }

    @Test
    void getSystemNameForEnvironment() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("DEV");
        environmentRepository.save(environmentDev);
        System systemA = new System("System A");
        systemRepository.save(systemA);
        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);
        Deployment deployment = TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget);
        deploymentRepository.save(deployment);

        String systemNameForDeployment = deploymentRepository.getSystemNameForDeployment(deployment.getId());

        assertEquals(systemA.getName(), systemNameForDeployment);
    }
}
