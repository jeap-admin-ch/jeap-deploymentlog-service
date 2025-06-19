package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
        assertEquals(2, deploymentRepository.countDeploymentsWithMissingOrOutdatedGeneratedPages(ZonedDateTime.now().minusDays(30)));
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
    void getLastSuccessfulDeploymentForComponentBeforeVersion_previousVersionFound_returnPreviousVersion() {
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
        previousVersion.success(previousVersion.getStartedAt().plusMinutes(1), "success");
        deploymentRepository.save(currentVersion);
        deploymentRepository.save(previousVersion);

        String versionName = "2";

        Optional<Deployment> lastSuccessfulDeployment = deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(component, env, versionName);
        assertTrue(lastSuccessfulDeployment.isPresent());
        assertEquals(previousVersion.getId(), lastSuccessfulDeployment.get().getId());

    }

    @Test
    void getLastSuccessfulDeploymentForComponentBeforeVersion_previousVersionNotFound_returnEmpty() {
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
        failedVersion.failed(previousVersion.getStartedAt().plusMinutes(1), "failed");
        deploymentRepository.save(currentVersion);
        deploymentRepository.save(previousVersion);

        String versionName = "2";

        Optional<Deployment> lastSuccessfulDeployment = deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(component, env, versionName);
        assertTrue(lastSuccessfulDeployment.isEmpty());

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
