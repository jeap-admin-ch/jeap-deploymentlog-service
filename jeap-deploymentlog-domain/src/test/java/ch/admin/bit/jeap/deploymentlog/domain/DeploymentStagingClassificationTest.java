package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** The former flow classifier now classifies each deployment independently. */
class DeploymentStagingClassificationTest extends StagingTestSupport {
    @Test void firstStartDeploymentIsNew() {
        assertType(ref, DeploymentStagingType.NEW);
    }
    @Test void failedPredecessorDoesNotQualifyAsDeployed() {
        history(entry("1.0", ref, DeploymentState.FAILURE, 2));
        assertType(abn, DeploymentStagingType.AD_HOC);
    }
    @Test void failedAttemptOnSameStageIsRetried() {
        history(entry("1.0", ref, DeploymentState.FAILURE, 2));
        assertType(ref, DeploymentStagingType.RETRY);
    }
    @Test void successfulImmediatePredecessorMakesNewDeployment() {
        history(entry("1.0", ref, DeploymentState.SUCCESS, 2));
        assertType(abn, DeploymentStagingType.NEW);
    }
    @Test void cannotSkipImmediatePredecessor() {
        history(entry("1.0", ref, DeploymentState.SUCCESS, 2));
        assertType(prod, DeploymentStagingType.AD_HOC);
    }
    @Test void currentVersionIsRetried() {
        history(entry("1.0", ref, DeploymentState.SUCCESS, 2));
        assertType(ref, DeploymentStagingType.RETRY);
    }
    @Test void previouslySuccessfulVersionIsRollbackAfterReplacement() {
        history(entry("1.0", ref, DeploymentState.SUCCESS, 3), entry("2.0", ref, DeploymentState.SUCCESS, 2));
        assertType(ref, DeploymentStagingType.ROLLBACK);
    }
    @Test void failedReplacementDoesNotChangeCurrentVersion() {
        history(entry("1.0", ref, DeploymentState.SUCCESS, 3), entry("2.0", ref, DeploymentState.FAILURE, 2));
        assertType(ref, DeploymentStagingType.RETRY);
    }
    @Test void futureSuccessDoesNotQualifyAsPredecessor() {
        history(entry("1.0", ref, DeploymentState.SUCCESS, -2));
        assertType(abn, DeploymentStagingType.AD_HOC);
    }
    @Test void cancelledAttemptCanBeRetriedButDoesNotPermitPromotion() {
        history(entry("1.0", ref, DeploymentState.CANCELLED, 2));
        assertType(ref, DeploymentStagingType.RETRY);
        assertType(abn, DeploymentStagingType.AD_HOC);
    }
    @Test void successfulRemovalMakesFormerVersionARollback() {
        var removed = new StagingHistoryEntry(java.util.UUID.randomUUID(), "(undeployed)", ref.getName(),
                now.minusHours(1), now.minusMinutes(30), DeploymentState.SUCCESS, true);
        history(entry("1.0", ref, DeploymentState.SUCCESS, 2), removed);
        assertType(ref, DeploymentStagingType.ROLLBACK);
    }
    private void assertType(Environment stage, DeploymentStagingType expected) {
        Deployment d = deployment(stage);
        service.prepare(d, List.of());
        assertThat(d.getStagingType()).isEqualTo(expected);
    }
}
