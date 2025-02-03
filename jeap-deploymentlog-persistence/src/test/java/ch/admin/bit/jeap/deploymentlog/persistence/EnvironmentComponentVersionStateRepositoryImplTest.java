package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

        List<ComponentVersionSummary> components = environmentComponentVersionStateRepository.getDeployedComponentsOnEnvironment(environmentDev);

        assertThat(components)
                .hasSize(1)
                .allMatch(componentVersionSummary -> componentVersionSummary.getComponentName().equals("Microservice A"));
    }
}
