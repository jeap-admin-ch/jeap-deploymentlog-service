package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class EnvironmentComponentVersionStateRepositoryImplTest {

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;

    @Autowired
    private EntityManager entityManager;

    private Environment environmentDev;
    private Environment environmentRef;
    private System systemA;
    private System systemB;
    private Component systemAMicroserviceA;
    private Component systemBMicroserviceB;
    private Deployment deploymentDevAA;
    private Deployment deploymentRefAA;


    @BeforeEach
    void beforeEach() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        environmentDev = new Environment("DEV");
        environmentDev.setStagingOrder(0);
        environmentRepository.save(environmentDev);
        environmentRef = new Environment("REF");
        environmentRef.setStagingOrder(1);
        environmentRepository.save(environmentRef);

        systemA = new System("System A");
        systemRepository.save(systemA);
        systemB = new System("System B");
        systemRepository.save(systemB);

        systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);
        systemBMicroserviceB = new Component("Microservice B", systemB);
        componentRepository.save(systemBMicroserviceB);

        deploymentDevAA = deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRefAA = deploymentRepository.save(TestDataFactory.createDeployment(environmentRef, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemBMicroserviceB, ZonedDateTime.now(), deploymentTarget));
    }

    @Test
    void findByEnvironmentAndComponent() {

        //1. After Deployments, but now success update, has to be Empty
        Optional<EnvironmentComponentVersionState> ecvs1Opt = environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environmentDev, systemAMicroserviceA);
        assertTrue(ecvs1Opt.isEmpty());

        //2. After the deployment is successfull, there has to be a entry
        deploymentDevAA.success(ZonedDateTime.now(), null);

        EnvironmentComponentVersionState envSaved = environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deploymentDevAA));
        assertNotNull(envSaved);

        Optional<EnvironmentComponentVersionState> ecvs2Opt = environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environmentDev, systemAMicroserviceA);
        assertTrue(ecvs2Opt.isPresent());
    }

    @Test
    void findComponentsBySystem()  {
        deploymentDevAA.success(ZonedDateTime.now(), "great success");
        EnvironmentComponentVersionState envSaved = environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deploymentDevAA));
        assertNotNull(envSaved);
        List<Component> componentListSystemA = environmentComponentVersionStateRepository.findComponentsBySystem(systemA);
        assertEquals(1, componentListSystemA.size());
        List<Component> componentListSystemB = environmentComponentVersionStateRepository.findComponentsBySystem(systemB);
        assertEquals(0, componentListSystemB.size());
    }

    @Test
    void findComponentsBySystem_leavesOutAComponentUndeployedEverywhere() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        deploymentDevAA.success(ZonedDateTime.now(), "great success");
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deploymentDevAA));
        Deployment undeploymentRef = deploymentRepository.save(TestDataFactory.createDeployment(environmentRef, systemAMicroserviceA,
                ZonedDateTime.now(), "(undeployed)", deploymentTarget, DeploymentSequence.UNDEPLOYED));
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(undeploymentRef));
        entityManager.flush();

        assertThat(environmentComponentVersionStateRepository.findComponentsBySystem(systemA))
                .as("still deployed on DEV")
                .extracting(Component::getName)
                .containsExactly("Microservice A");

        Deployment undeploymentDev = deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA,
                ZonedDateTime.now(), "(undeployed)", deploymentTarget, DeploymentSequence.UNDEPLOYED));
        environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environmentDev, systemAMicroserviceA).orElseThrow()
                .updateVersion(undeploymentDev.getComponentVersion(), undeploymentDev);
        entityManager.flush();

        assertThat(environmentComponentVersionStateRepository.findComponentsBySystem(systemA))
                .as("undeployed from every environment")
                .isEmpty();
    }

    @Test
    void deleteUndeployedBefore_deletesOnlyTombstonesOlderThanTheCutoff() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(730);
        Deployment oldUndeployment = deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA,
                cutoff.minusDays(1), "(undeployed)", deploymentTarget, DeploymentSequence.UNDEPLOYED));
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(oldUndeployment));
        Deployment recentUndeployment = deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemBMicroserviceB,
                cutoff.plusDays(1), "(undeployed)", deploymentTarget, DeploymentSequence.UNDEPLOYED));
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(recentUndeployment));
        Deployment oldDeployment = deploymentRepository.save(TestDataFactory.createDeployment(environmentRef, systemAMicroserviceA,
                cutoff.minusDays(1), deploymentTarget));
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(oldDeployment));
        entityManager.flush();

        int deleted = environmentComponentVersionStateRepository.deleteUndeployedBefore(cutoff);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environmentDev, systemAMicroserviceA))
                .as("old tombstone").isEmpty();
        assertThat(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environmentDev, systemBMicroserviceB))
                .as("recent tombstone").isPresent();
        assertThat(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environmentRef, systemAMicroserviceA))
                .as("old but live deployment").isPresent();
    }

    @Test
    void findByComponentIn() {
        deploymentDevAA.success(ZonedDateTime.now(), "great success");
        deploymentRefAA.success(ZonedDateTime.now(), "great success");
        EnvironmentComponentVersionState devSaved = environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deploymentDevAA));
        assertNotNull(devSaved);
        EnvironmentComponentVersionState refSaved = environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deploymentRefAA));
        assertNotNull(refSaved);

        List<EnvironmentComponentVersionState> environmentComponentVersionStateList = environmentComponentVersionStateRepository.findByComponentIn(Collections.singleton(systemAMicroserviceA));
        assertEquals(2, environmentComponentVersionStateList.size());
        assertThat(environmentComponentVersionStateList).contains(devSaved);
        assertThat(environmentComponentVersionStateList).contains(refSaved);
    }

    @Test
    void getDeployedComponentsOnEnvironment() {
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deploymentDevAA));
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deploymentRefAA));
        entityManager.flush();

        List<ComponentVersionSummary> components = environmentComponentVersionStateRepository.getDeployedComponentsOnEnvironment(environmentDev, false);

        assertThat(components)
                .hasSize(1)
                .allMatch(componentVersionSummary -> componentVersionSummary.getComponentName().equals("Microservice A"))
                .allMatch(componentVersionSummary -> !componentVersionSummary.getUndeployed());
    }

    @Test
    void getDeployedComponentsOnEnvironment_excludesUndeployedComponentsUnlessIncluded() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Deployment undeployment = deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemBMicroserviceB, ZonedDateTime.now(),
                "(undeployed)", deploymentTarget, DeploymentSequence.UNDEPLOYED));
        environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(undeployment));
        entityManager.flush();

        List<ComponentVersionSummary> withoutUndeployed = environmentComponentVersionStateRepository.getDeployedComponentsOnEnvironment(environmentDev, false);
        assertThat(withoutUndeployed).isEmpty();

        List<ComponentVersionSummary> withUndeployed = environmentComponentVersionStateRepository.getDeployedComponentsOnEnvironment(environmentDev, true);
        assertThat(withUndeployed)
                .hasSize(1)
                .allMatch(componentVersionSummary -> componentVersionSummary.getComponentName().equals("Microservice B"))
                .allMatch(ComponentVersionSummary::getUndeployed)
                .allMatch(componentVersionSummary -> componentVersionSummary.getVersion() == null)
                .allMatch(componentVersionSummary -> componentVersionSummary.getUndeployedAt() != null);
    }
}
