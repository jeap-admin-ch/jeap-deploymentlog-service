package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowTest {

    @Test
    void successfulTargetDeploymentClosesFlowWithoutChangingMasterData() {
        Component component = new Component("service", new System("SYSTEM"));
        Environment dev = new Environment("DEV");
        Environment prod = new Environment("PROD");
        Deployment initial = deployment("initial", ZonedDateTime.parse("2026-08-21T08:00:00+02:00"), component, dev);
        Deployment target = deployment("target", initial.getStartedAt().plusHours(2), component, prod);
        Flow flow = Flow.start(FlowType.RETRY, initial, prod);
        flow.add(target);
        target.success(target.getStartedAt().plusMinutes(5), "done");

        assertThat(flow.closeIfTargetReached(target)).isTrue();

        assertThat(flow.getState()).isEqualTo(FlowState.CLOSED);
        assertThat(flow.getType()).isEqualTo(FlowType.RETRY);
        assertThat(flow.getBornAt()).isEqualTo(initial.getStartedAt());
        assertThat(flow.getFinalDeploymentEnvironment()).isSameAs(prod);
        assertThat(flow.closeIfTargetReached(target)).isFalse();
    }

    @Test
    void failedTargetAndSuccessfulOtherStageLeaveFlowOpen() {
        Component component = new Component("service", new System("SYSTEM"));
        Environment dev = new Environment("DEV");
        Environment prod = new Environment("PROD");
        Deployment initial = deployment("initial", ZonedDateTime.now(), component, dev);
        Flow flow = Flow.start(FlowType.NEW, initial, prod);
        initial.success(ZonedDateTime.now(), "done");

        assertThat(flow.closeIfTargetReached(initial)).isFalse();

        Deployment target = deployment("target", ZonedDateTime.now().plusMinutes(1), component, prod);
        flow.add(target);
        target.failed(ZonedDateTime.now().plusMinutes(2), "failed");
        assertThat(flow.closeIfTargetReached(target)).isFalse();

        Deployment unrelatedTarget = deployment("unrelated", ZonedDateTime.now().plusMinutes(3), component, prod);
        unrelatedTarget.success(ZonedDateTime.now().plusMinutes(4), "done");
        assertThat(flow.closeIfTargetReached(unrelatedTarget)).isFalse();
        assertThat(flow.getState()).isEqualTo(FlowState.OPEN);
    }

    @Test
    void abortIsTerminalIdempotentAndKeepsOriginalAbortingFlow() {
        Component component = new Component("service", new System("SYSTEM"));
        Environment dev = new Environment("DEV");
        Environment prod = new Environment("PROD");
        ZonedDateTime committedAt = ZonedDateTime.parse("2026-08-20T08:00:00+02:00");
        Flow older = Flow.start(FlowType.AD_HOC,
                deployment("old", ZonedDateTime.now(), committedAt, component, dev), prod);
        Flow winner = Flow.start(FlowType.NEW,
                deployment("winner", ZonedDateTime.now(), committedAt.plusDays(1), component, dev), prod);
        Flow laterWinner = Flow.start(FlowType.ROLLBACK,
                deployment("later", ZonedDateTime.now(), committedAt.plusDays(2), component, dev), prod);
        Deployment tooLate = deployment("too-late", ZonedDateTime.now(), component, dev);

        assertThat(older.abortBy(winner)).isTrue();
        assertThat(older.abortBy(laterWinner)).isFalse();

        assertThat(older.getState()).isEqualTo(FlowState.ABORTED);
        assertThat(older.getAbortedBy()).isSameAs(winner);
        assertThat(older.getType()).isEqualTo(FlowType.AD_HOC);
        assertThatThrownBy(() -> older.add(tooLate))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sameOrOlderComponentVersionCannotAbortOpenFlow() {
        Component component = new Component("service", new System("SYSTEM"));
        Environment dev = new Environment("DEV");
        Environment prod = new Environment("PROD");
        ZonedDateTime committedAt = ZonedDateTime.parse("2026-08-20T08:00:00+02:00");
        Flow flow = Flow.start(FlowType.NEW,
                deployment("current", ZonedDateTime.now(), committedAt, component, dev), prod);
        Flow sameAge = Flow.start(FlowType.NEW,
                deployment("same-age", ZonedDateTime.now(), committedAt, component, dev), prod);
        Flow older = Flow.start(FlowType.NEW,
                deployment("older", ZonedDateTime.now(), committedAt.minusDays(1), component, dev), prod);

        assertThatThrownBy(() -> flow.abortBy(sameAge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only a strictly newer component version can abort this flow");
        assertThatThrownBy(() -> flow.abortBy(older))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only a strictly newer component version can abort this flow");
        assertThat(flow.getState()).isEqualTo(FlowState.OPEN);
        assertThat(flow.getAbortedBy()).isNull();
    }

    @Test
    void closedFlowCannotBeAbortedAndAbortedFlowCannotBeClosed() {
        Component component = new Component("service", new System("SYSTEM"));
        Environment prod = new Environment("PROD");
        Deployment closingDeployment = deployment("closing", ZonedDateTime.now(), component, prod);
        Flow closed = Flow.start(FlowType.NEW, closingDeployment, prod);
        closingDeployment.success(ZonedDateTime.now(), "done");
        closed.closeIfTargetReached(closingDeployment);
        Flow winner = Flow.start(FlowType.NEW,
                deployment("winner", ZonedDateTime.now(),
                        closingDeployment.getComponentVersion().getCommittedAt().plusDays(1), component, prod), prod);

        assertThat(closed.abortBy(winner)).isFalse();
        assertThat(closed.abortBy(closed)).isFalse();
        assertThat(closed.getState()).isEqualTo(FlowState.CLOSED);

        Deployment abortedDeployment = deployment("aborted", ZonedDateTime.now(), component, prod);
        Flow aborted = Flow.start(FlowType.NEW, abortedDeployment, prod);
        aborted.abortBy(winner);
        abortedDeployment.success(ZonedDateTime.now(), "done");
        assertThat(aborted.closeIfTargetReached(abortedDeployment)).isFalse();
        assertThat(aborted.getState()).isEqualTo(FlowState.ABORTED);
    }

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
        return deployment(externalId, startedAt,
                ZonedDateTime.parse("2026-08-20T08:00:00+02:00"), component, environment);
    }

    private Deployment deployment(String externalId,
                                  ZonedDateTime startedAt,
                                  ZonedDateTime committedAt,
                                  Component component,
                                  Environment environment) {
        ComponentVersion version = ComponentVersion.builder()
                .versionName("1.0.0")
                .versionControlUrl("https://git")
                .commitRef("ref")
                .committedAt(committedAt)
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
