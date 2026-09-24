package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Deployments of a version remain independent after an earlier deployment reaches PROD. */
class DeploymentVersionHistoryTest extends StagingTestSupport {
    @Test void laterDeploymentOfSameVersionStillHasItsOwnTypeAndTargets() {
        Deployment first = deployment(prod);
        service.prepare(first, List.of());
        first.success(now.plusMinutes(5), "done");
        history(entry("1.0", prod, DeploymentState.SUCCESS, 2));
        Deployment next = deployment(prod);
        service.prepare(next, List.of("PROD"));
        assertThat(first.getStagingType()).isEqualTo(DeploymentStagingType.AD_HOC);
        assertThat(next.getStagingType()).isEqualTo(DeploymentStagingType.RETRY);
        assertThat(first.getFinalDeploymentEnvironments()).isEmpty();
        assertThat(next.getFinalDeploymentEnvironments()).containsExactly("PROD");
        assertThat(first.getComponentVersion().getId()).isNotEqualTo(next.getComponentVersion().getId());
        assertThat(first.getComponentVersion().getVersionName()).isEqualTo(next.getComponentVersion().getVersionName());
    }
    @Test void historicalDeploymentsRemainUnclassified() {
        Deployment historic = deployment(ref);
        assertThat(historic.getStagingType()).isNull();
        assertThat(historic.getFinalDeploymentEnvironments()).isEmpty();
    }
}
