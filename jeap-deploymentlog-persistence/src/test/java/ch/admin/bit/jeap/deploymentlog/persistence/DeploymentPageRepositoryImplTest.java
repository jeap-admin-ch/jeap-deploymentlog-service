package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class DeploymentPageRepositoryImplTest {

    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private DeploymentPageRepository deploymentPageRepository;

    @Test
    void findDeploymentPageByDeploymentId() {
        Environment environmentDev = new Environment("dev");
        environmentRepository.save(environmentDev);
        Deployment deployment = generateDeploymentTestData("system", environmentDev, TestDataFactory.createDeploymentTarget());

        Optional<DeploymentPage> deploymentPageByDeploymentId = deploymentPageRepository.findDeploymentPageByDeploymentId(deployment.getId());

        assertTrue(deploymentPageByDeploymentId.isPresent());
        assertEquals(deployment.getId(), deploymentPageByDeploymentId.get().getDeploymentId());
    }

    @Test
    void getSystemDeploymentPagesForEnvironments() {
        DeploymentTarget deploymentTargetCf = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("dev");
        environmentRepository.save(environmentDev);
        Environment environmentRef = new Environment("ref");
        environmentRepository.save(environmentRef);
        Deployment deployment = generateDeploymentTestData("system1", environmentDev, deploymentTargetCf);
        generateDeploymentTestData("system2", environmentDev, deploymentTargetCf);
        generateDeploymentTestData("system3", environmentRef, deploymentTargetCf);

        UUID systemId = deployment.getComponentVersion().getComponent().getSystem().getId();

        List<DeploymentPage> pages = deploymentPageRepository.getSystemDeploymentPagesForEnvironments(systemId, List.of(environmentDev));

        assertEquals(1, pages.size());
        assertEquals(deployment.getId(), pages.getFirst().getDeploymentId());
    }


    @Test
    void getDeploymentPagesForSystem() {
        DeploymentTarget deploymentTargetCf = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("dev");
        environmentRepository.save(environmentDev);
        Environment environmentRef = new Environment("ref");
        environmentRepository.save(environmentRef);
        String pageId1 = UUID.randomUUID().toString();
        Deployment deployment1 = generateDeploymentTestData("system1", environmentDev, deploymentTargetCf, pageId1);
        String pageId2 = UUID.randomUUID().toString();
        Deployment deployment2 = generateDeploymentTestData(deployment1.getComponentVersion().getComponent().getSystem(), environmentRef, deploymentTargetCf, pageId2);
        generateDeploymentTestData("system2", environmentDev, deploymentTargetCf);
        generateDeploymentTestData("system3", environmentRef, deploymentTargetCf);

        UUID systemId = deployment1.getComponentVersion().getComponent().getSystem().getId();

        List<DeploymentPageQueryResult> deploymentPagesForSystem = deploymentPageRepository.getDeploymentPagesForSystem(systemId);

        assertEquals(2, deploymentPagesForSystem.size());

        assertThat(deploymentPagesForSystem).containsExactly(new DeploymentPageQueryResult(deployment1.getId(), pageId1), new DeploymentPageQueryResult(deployment2.getId(), pageId2));
    }

    private Deployment generateDeploymentTestData(System system, Environment environment, DeploymentTarget deploymentTarget, String pageId) {
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
        Deployment deployment = deploymentRepository.save(Deployment.builder()
                .externalId(UUID.randomUUID().toString())
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
                .build());
        deploymentPageRepository.save(DeploymentPage.builder()
                .id(UUID.randomUUID())
                .deploymentId(deployment.getId())
                .pageId(pageId)
                .lastUpdatedAt(ZonedDateTime.now())
                .deploymentStateTimestamp(deployment.getLastModified())
                .build());
        return deployment;
    }

    private Deployment generateDeploymentTestData(String systemName, Environment environment, DeploymentTarget deploymentTarget) {
        System system = new System(systemName);
        systemRepository.save(system);
        return generateDeploymentTestData(system, environment, deploymentTarget, UUID.randomUUID().toString());
    }

    private Deployment generateDeploymentTestData(String systemName, Environment environment, DeploymentTarget deploymentTarget, String pageId) {
        System system = new System(systemName);
        systemRepository.save(system);
        return generateDeploymentTestData(system, environment, deploymentTarget, pageId);
    }
}
