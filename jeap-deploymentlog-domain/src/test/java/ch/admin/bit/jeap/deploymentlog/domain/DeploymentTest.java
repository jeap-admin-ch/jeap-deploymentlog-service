package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentTest {

    @Test
    void deploymentStarted_stateIsStarted() {
        assertThat(getDeployment().getState()).isEqualTo(DeploymentState.STARTED);
    }

    @Test
    void deploymentFailed_stateIsFailure() {
        final Deployment deployment = getDeployment();
        deployment.failed(ZonedDateTime.now(), null);
        assertThat(deployment.getState()).isEqualTo(DeploymentState.FAILURE);
    }

    @Test
    void deploymentSuccess_stateIsSuccess() {
        final Deployment deployment = getDeployment();
        deployment.success(ZonedDateTime.now(), null);
        assertThat(deployment.getState()).isEqualTo(DeploymentState.SUCCESS);
    }

    private static Deployment getDeployment() {
        return Deployment.builder().externalId("test")
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(new Environment())
                .target(new DeploymentTarget())
                .componentVersion(new ComponentVersion())
                .links(Collections.emptySet())
                .sequence(DeploymentSequence.NEW)
                .build();
    }

}
