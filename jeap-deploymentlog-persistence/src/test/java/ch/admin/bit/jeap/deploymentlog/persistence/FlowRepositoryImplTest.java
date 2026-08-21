package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class FlowRepositoryImplTest {

    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private EntityManager entityManager;

    private Environment dev;
    private Environment prod;
    private Component component;

    @BeforeEach
    void setUp() {
        dev = environmentRepository.save(new Environment("DEV"));
        prod = environmentRepository.save(new Environment("PROD"));
        System system = systemRepository.save(new System("SYSTEM"));
        component = componentRepository.save(new Component("service", system));
    }

    @Test
    void persistsAndFindsFlowByBusinessVersionAndDeployment() {
        Deployment deployment = deploymentRepository.save(deployment("1.0.0"));
        Flow flow = flowRepository.save(Flow.start(FlowType.NEW, deployment, prod));
        entityManager.flush();
        entityManager.clear();

        List<Flow> openFlows = flowRepository.findOpenFlows(component.getId(), "1.0.0");

        assertThat(openFlows).extracting(Flow::getId).containsExactly(flow.getId());
        assertThat(flowRepository.findByDeploymentId(deployment.getId())).map(Flow::getId).contains(flow.getId());
        assertThat(openFlows.getFirst().getDeployments()).extracting(Deployment::getId)
                .containsExactly(deployment.getId());
    }

    @Test
    void componentCanBeLockedForCrossInstanceSerialization() {
        entityManager.flush();

        flowRepository.lockComponent(component.getId());

        assertThat(entityManager.contains(component)).isTrue();
    }

    private Deployment deployment(String versionName) {
        Deployment deployment = TestDataFactory.createDeployment(dev, component, ZonedDateTime.now(), versionName,
                TestDataFactory.createDeploymentTarget());
        deployment.getDeploymentTypes().add(DeploymentType.CODE);
        return deployment;
    }
}
