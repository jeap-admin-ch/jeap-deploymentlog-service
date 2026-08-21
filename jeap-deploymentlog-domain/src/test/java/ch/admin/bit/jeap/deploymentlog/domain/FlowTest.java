package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTest {

    @Test
    void deploymentsAreOrderedByStartTime() {
        Component component = new Component("service", new System("SYSTEM"));
        Environment dev = new Environment("DEV");
        Environment prod = new Environment("PROD");
        ZonedDateTime initialStart = ZonedDateTime.parse("2026-08-21T08:00:00+02:00");
        Deployment initial = deployment("initial", initialStart, component, dev);
        Deployment last = deployment("last", initialStart.plusHours(2), component, prod);
        Deployment middle = deployment("middle", initialStart.plusHours(1), component, dev);
        Flow flow = Flow.start(FlowType.NEW, initial, prod);

        flow.add(last);
        flow.add(middle);

        assertThat(flow.getDeployments()).extracting(Deployment::getExternalId)
                .containsExactly("initial", "middle", "last");
        assertThat(flow.getBornAt()).isEqualTo(initialStart);
    }

    private Deployment deployment(String externalId,
                                  ZonedDateTime startedAt,
                                  Component component,
                                  Environment environment) {
        ComponentVersion version = ComponentVersion.builder()
                .versionName("1.0.0")
                .versionControlUrl("https://git")
                .commitRef("ref")
                .committedAt(ZonedDateTime.parse("2026-08-20T08:00:00+02:00"))
                .component(component)
                .deploymentUnit(DeploymentUnit.builder()
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("image:1.0.0")
                        .artifactRepositoryUrl("https://registry")
                        .build())
                .build();
        return Deployment.builder()
                .externalId(externalId)
                .startedAt(startedAt)
                .startedBy("tester")
                .environment(environment)
                .componentVersion(version)
                .sequence(DeploymentSequence.NEW)
                .deploymentTypes(Set.of(DeploymentType.CODE))
                .build();
    }
}
