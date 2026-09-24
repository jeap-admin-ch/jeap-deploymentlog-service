package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowStageProperties;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import ch.admin.bit.jeap.deploymentlog.domain.VersionDeploymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "jeap.deploymentlog.flow.enabled=false")
@ContextConfiguration(classes = PersistenceConfiguration.class)
class DisabledFlowComponentPagesTest {
    @Autowired private VersionDeploymentRepository versions;
    @Autowired private ComponentPageRepository pages;
    @Autowired private ComponentRepository components;
    @Autowired private SystemRepository systems;
    @Autowired private EnvironmentRepository environments;
    @Autowired private FlowStageProperties properties;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void skipsStageResolutionAndKeepsTrackedPages(boolean ambiguousStages) {
        assertThat(properties.isEnabled()).isFalse();
        if (ambiguousStages) {
            for (String name : new String[]{"FIRST", "SECOND"}) {
                Environment environment = new Environment(name);
                environment.setDevelopment(true);
                environment.setProductive(true);
                environments.save(environment);
            }
        }
        Component component = components.save(new Component("service", systems.save(new System("SYS"))));
        pages.save(ComponentPage.create(component.getId(), "existing-page", "parent"));
        entityManager.flush();
        entityManager.clear();

        assertThat(versions.findLatestVersions(component.getId(), 50)).isEmpty();
        assertThat(versions.existsForComponent(component.getId())).isFalse();
        assertThat(pages.findCleanupCandidates(50)).isEmpty();
        assertThat(pages.markCleanupAttemptedIfNoFlow(component.getId(), ZonedDateTime.now())).isZero();
        assertThat(pages.deleteIfNoFlow(component.getId())).isZero();
        assertThat(pages.findByComponentId(component.getId())).get().satisfies(page -> {
            assertThat(page.getPageId()).isEqualTo("existing-page");
            assertThat(page.getCleanupAttemptedAt()).isNull();
        });
    }
}
