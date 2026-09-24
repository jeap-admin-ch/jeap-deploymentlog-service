package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
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
class VersionDeploymentRepositoryImplTest {
    @Autowired VersionDeploymentRepository versions;
    @Autowired DeploymentRepository deployments;
    @Autowired EnvironmentRepository environments;
    @Autowired SystemRepository systems;
    @Autowired ComponentRepository components;
    @Autowired EntityManager em;
    Environment ref;
    Environment prod;
    Component component;
    ZonedDateTime now = ZonedDateTime.parse("2026-09-25T10:00:00Z");

    @BeforeEach void setup() {
        ref = environments.save(new Environment("REF"));
        ref.setDevelopment(true);
        prod = environments.save(new Environment("PROD"));
        component = components.save(new Component("service", systems.save(new System("SYSTEM"))));
    }

    @Test void groupsBusinessVersionDespiteDifferentComponentVersionIdsAndRepeatedStages() {
        Deployment first = deployment("1.0", ref, 0);
        Deployment end = deployment("1.0", prod, 1);
        Deployment retry = deployment("1.0", ref, 2);
        deployment("2.0", ref, 3);
        em.flush(); em.clear();
        var groups = versions.findLatestVersions(component.getId(), 2);
        assertThat(groups).hasSize(2);
        assertThat(groups.get(1)).extracting(Deployment::getId)
                .containsExactly(first.getId(), end.getId(), retry.getId());
        assertThat(versions.findLatestVersions(component.getId(), 1)).hasSize(1);
        assertThat(versions.existsForComponent(component.getId())).isTrue();
    }

    @Test void derivesRelevanceFromStagesAndIncludesUnclassifiedHistoricalDeployments() {
        Deployment historical = deployment("1.0", ref, 0);
        historical.classify(null);
        Environment outside = environments.save(new Environment("OUTSIDE"));
        outside.setStagingOrder(-1);
        deployment("outside", outside, 1);
        Deployment config = deployment("config", ref, 2);
        config.getDeploymentTypes().clear();
        config.getDeploymentTypes().add(DeploymentType.CONFIG);
        em.flush();

        assertThat(versions.findLatestVersions(component.getId(), 10)).singleElement()
                .satisfies(group -> assertThat(group).containsExactly(historical));
        ref.setDevelopment(false);
        prod.setDevelopment(true);
        em.flush();
        assertThat(versions.findLatestVersions(component.getId(), 10)).isEmpty();
        assertThat(versions.existsForComponent(component.getId())).isFalse();
    }

    @Test void readsRetainedOldDeploymentsDirectlyWithoutReclassification() {
        Deployment old = deployment("1.0", ref, 0);
        old.classify(null);
        old.success(now.plusMinutes(5), "done");
        em.flush();
        assertThat(versions.history(component.getId())).singleElement()
                .satisfies(entry -> assertThat(entry.deploymentId()).isEqualTo(old.getId()));
        assertThat(versions.metrics("REF", "PROD").getFirst().startVersions()).isEqualTo(1);
        assertThat(old.getStagingType()).isNull();
    }

    @Test void metricsCountDistinctSuccessfulVersionsAndFirstSuccessfulLatency() {
        Deployment failed = deployment("1.0", ref, 0);
        failed.failed(now.plusMinutes(1), "failed");
        success(deployment("1.0", ref, 1));
        success(deployment("1.0", ref, 2));
        success(deployment("1.0", prod, 3));
        success(deployment("1.0", prod, 4));
        success(deployment("2.0", ref, 5));
        var metric = versions.metrics("REF", "PROD").getFirst();
        assertThat(metric.startVersions()).isEqualTo(2);
        assertThat(metric.endVersions()).isEqualTo(1);
        assertThat(metric.latencyCount()).isEqualTo(1);
        assertThat(metric.latencySeconds()).isEqualTo(7200);
    }

    @Test void autoStagingUsesLatestStartStageRequestRegardlessOfOutcomeAndVersion() {
        Deployment first = deployment("1.0", ref, 0);
        first.setFinalDeploymentEnvironments(List.of("PROD"));
        assertThat(versions.metrics("REF", "PROD").getFirst().autoStagingEnabled()).isTrue();
        Deployment second = deployment("2.0", ref, 1);
        second.failed(now.plusHours(1).plusMinutes(1), "failed");
        assertThat(versions.metrics("REF", "PROD").getFirst().autoStagingEnabled()).isFalse();
        Deployment third = deployment("2.0", ref, 2);
        third.classify(DeploymentStagingType.RETRY);
        third.setFinalDeploymentEnvironments(List.of("PROD"));
        assertThat(versions.metrics("REF", "PROD").getFirst().autoStagingEnabled()).isTrue();
    }

    @Test void rollbackAndDeploymentsOnOtherStagesDoNotChangeAutoStagingStatus() {
        Deployment first = deployment("2.0", ref, 0);
        first.setFinalDeploymentEnvironments(List.of("PROD"));
        Deployment rollback = deployment("1.0", ref, 1);
        rollback.classify(DeploymentStagingType.ROLLBACK);
        success(rollback);
        deployment("2.0", prod, 2);
        assertThat(versions.metrics("REF", "PROD").getFirst().autoStagingEnabled()).isTrue();
    }

    @Test void autoStagingIsUnknownWithoutAnEligibleStartStageDeployment() {
        deployment("1.0", prod, 0);
        assertThat(versions.metrics("REF", "PROD").getFirst().autoStagingEnabled()).isNull();
        Deployment rollback = deployment("1.0", ref, 1);
        rollback.classify(DeploymentStagingType.ROLLBACK);
        rollback.setFinalDeploymentEnvironments(List.of("PROD"));
        assertThat(versions.metrics("REF", "PROD").getFirst().autoStagingEnabled()).isNull();
    }

    @Test void autoStagingUsesConfiguredStartStageAndIgnoresAdHocOnEndStage() {
        Environment start = environments.save(new Environment("INT"));
        deployment("1.0", start, 0);
        assertThat(versions.metrics("INT", "PROD").getFirst().autoStagingEnabled()).isFalse();

        Deployment adHoc = deployment("2.0", prod, 1);
        adHoc.classify(DeploymentStagingType.AD_HOC);
        adHoc.setFinalDeploymentEnvironments(List.of("PROD"));
        Deployment otherStart = deployment("2.0", ref, 2);
        otherStart.setFinalDeploymentEnvironments(List.of("PROD"));
        assertThat(versions.metrics("INT", "PROD").getFirst().autoStagingEnabled()).isFalse();

        Deployment latest = deployment("3.0", start, 3);
        latest.setFinalDeploymentEnvironments(List.of("PROD"));
        Deployment rollback = deployment("1.0", start, 4);
        rollback.classify(DeploymentStagingType.ROLLBACK);
        assertThat(versions.metrics("INT", "PROD").getFirst().autoStagingEnabled()).isTrue();
    }

    @Test void historyAndMetricsOnlyIncludeRetainedDeployments() {
        Deployment first = deployment("1.0", ref, 0);
        success(first);
        em.remove(first); em.flush(); em.clear();
        assertThat(versions.history(component.getId())).isEmpty();
        assertThat(versions.metrics("REF", "PROD")).isEmpty();
    }

    Deployment deployment(String version, Environment env, int hours) {
        Deployment d = TestDataFactory.createDeployment(env, component, now.plusHours(hours), version,
                now.minusDays(1), TestDataFactory.createDeploymentTarget());
        d.getDeploymentTypes().add(DeploymentType.CODE);
        d.classify(DeploymentStagingType.NEW);
        return deployments.save(d);
    }
    void success(Deployment d) {
        d.success(d.getStartedAt().plusMinutes(5), "done");
    }
}
