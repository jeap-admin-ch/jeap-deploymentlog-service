package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTarget;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class EnvironmentRepositoryImplTest {

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Test
    void saveEnvironmentAndFindByName() {
        String envName = "DEV";
        Environment environmentDev = new Environment("DEV");
        environmentRepository.save(environmentDev);

        Optional<Environment> foundEnv = environmentRepository.findByName("DEV");
        assertTrue(foundEnv.isPresent());
        assertEquals(envName, foundEnv.get().getName());

        Optional<Environment> foundEnv2 = environmentRepository.findByName("notExists");
        assertTrue(foundEnv2.isEmpty());
    }

    @Test
    void findEnvironmentsBySytemInTheRightOrder() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentDev = new Environment("DEV");
        environmentDev.setStagingOrder(0);
        environmentRepository.save(environmentDev);

        Environment environmentRef = new Environment("REF");
        environmentRef.setStagingOrder(1);
        environmentRepository.save(environmentRef);

        Environment environmentAbn = new Environment("ABN");
        environmentAbn.setStagingOrder(2);
        environmentRepository.save(environmentAbn);

        Environment environmentProd = new Environment("PROD");
        environmentProd.setStagingOrder(3);
        environmentRepository.save(environmentProd);

        System systemA = new System("System A");
        systemRepository.save(systemA);

        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);

        // First Deployment --> 1 Environment
        deploymentRepository.save(TestDataFactory.createDeployment(environmentDev, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));

        List<Environment> environmentList1 = environmentRepository.findEnvironmentsForSystem(systemA);
        assertEquals(1, environmentList1.size());

        // 2nd Deployment --> Environment DEV and REF
        deploymentRepository.save(TestDataFactory.createDeployment(environmentRef, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        List<Environment> environmentList2 = environmentRepository.findEnvironmentsForSystem(systemA);
        assertEquals(2, environmentList2.size());

        // 3nd Deployment --> Environment DEV, REF & PROD
        deploymentRepository.save(TestDataFactory.createDeployment(environmentProd, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        List<Environment> environmentList3 = environmentRepository.findEnvironmentsForSystem(systemA);
        assertEquals(3, environmentList3.size());

        // 4nd Deployment --> Environment DEV, REF, PROD, ABN --> order has to be DEV, REF, ABN, PROD
        deploymentRepository.save(TestDataFactory.createDeployment(environmentAbn, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        List<Environment> environmentList4 = environmentRepository.findEnvironmentsForSystem(systemA);
        List<String> envNameList = environmentList4.stream().map(Environment::getName).collect(Collectors.toList());

        List<String> expectedList = List.of("DEV", "REF", "ABN", "PROD");
        assertEquals(expectedList, envNameList);
    }

    @Test
    void findNonProductiveEnvironmentsForSystemId() {
        DeploymentTarget deploymentTarget = TestDataFactory.createDeploymentTarget();
        Environment environmentRef = new Environment("REF");
        environmentRepository.save(environmentRef);
        Environment environmentAbn = new Environment("ABN");
        environmentRepository.save(environmentAbn);
        Environment environmentProd = new Environment("PROD");
        environmentProd.setProductive(true);
        environmentRepository.save(environmentProd);
        System systemA = new System("System A");
        systemRepository.save(systemA);
        System systemB = new System("System B");
        systemRepository.save(systemB);
        Component systemAMicroserviceA = new Component("Microservice A", systemA);
        componentRepository.save(systemAMicroserviceA);
        Component systemBMicroserviceB = new Component("Microservice B", systemB);
        componentRepository.save(systemBMicroserviceB);
        deploymentRepository.save(TestDataFactory.createDeployment(environmentAbn, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentProd, systemAMicroserviceA, ZonedDateTime.now(), deploymentTarget));
        deploymentRepository.save(TestDataFactory.createDeployment(environmentRef, systemBMicroserviceB, ZonedDateTime.now(), deploymentTarget));

        List<Environment> envs = environmentRepository.findNonProductiveEnvironmentsForSystemId(systemA.getId());

        assertEquals(1, envs.size());
        assertEquals(environmentAbn.getId(), envs.get(0).getId());
    }
}
