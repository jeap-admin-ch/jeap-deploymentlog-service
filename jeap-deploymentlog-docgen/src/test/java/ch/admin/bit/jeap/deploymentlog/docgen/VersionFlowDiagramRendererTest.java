package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.VersionFlowDiagramRenderer.DiagramLayout;
import ch.admin.bit.jeap.deploymentlog.docgen.VersionFlowDiagramRenderer.DiagramPoint;
import ch.admin.bit.jeap.deploymentlog.docgen.VersionFlowDiagramRenderer.FlowPath;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDeploymentDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentPageDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VersionFlowDiagramRendererTest {

    private static final DateTimeFormatter MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SECOND_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final VersionFlowDiagramRenderer renderer = new VersionFlowDiagramRenderer();

    @Test
    void ordersPresentStagesAndUsesOrdinalTimeWithNewestAtTheTop() {
        ComponentFlowDto flow = flow("1.0.0",
                deployment("TEST", "SUCCESS", "2026-09-01T10:00:00Z"),
                deployment("custom-b", "FAILURE", "2026-09-01T11:00:00Z"),
                deployment("DEV", "STARTED", "2026-09-01T12:00:00Z"),
                deployment("custom-a", "CANCELLED", "2026-09-01T13:00:00Z"));

        DiagramLayout layout = renderer.createLayout(List.of(flow));

        assertThat(layout.stages()).containsExactly("DEV", "TEST", "CUSTOM-A", "CUSTOM-B");
        assertThat(layout.ordinalTimestamps()).containsExactly(
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"),
                Instant.parse("2026-09-01T12:00:00Z"),
                Instant.parse("2026-09-01T13:00:00Z"));
        assertThat(layout.timestampLabels()).containsExactly(
                formattedMinute("2026-09-01T10:00:00Z"), formattedMinute("2026-09-01T11:00:00Z"),
                formattedMinute("2026-09-01T12:00:00Z"), formattedMinute("2026-09-01T13:00:00Z"));
        assertThat(layout.flows().getFirst().points())
                .extracting(DiagramPoint::y)
                .containsExactly(182, 138, 94, 50);
        assertThat(layout.flows().getFirst().points())
                .extracting(DiagramPoint::firstChronological)
                .containsExactly(true, false, false, false);
    }

    @Test
    void keepsOnePositiveLaneForAStageRunAndSeparatesOverlappingRunsByTenPixels() {
        ComponentFlowDto first = flow("1.0.0",
                deployment("DEV", "STARTED", "2026-09-01T10:00:00Z"),
                deployment("DEV", "SUCCESS", "2026-09-01T12:00:00Z"),
                deployment("INT", "SUCCESS", "2026-09-01T13:00:00Z"));
        ComponentFlowDto overlapping = flow("2.0.0",
                deployment("DEV", "STARTED", "2026-09-01T11:00:00Z"),
                deployment("DEV", "FAILURE", "2026-09-01T12:30:00Z"));
        ComponentFlowDto reusable = flow("3.0.0",
                deployment("DEV", "SUCCESS", "2026-09-01T14:00:00Z"));

        DiagramLayout layout = renderer.createLayout(List.of(first, overlapping, reusable));

        FlowPath firstPath = flowPath(layout, "1.0.0");
        FlowPath overlappingPath = flowPath(layout, "2.0.0");
        FlowPath reusablePath = flowPath(layout, "3.0.0");
        assertThat(firstPath.points()).extracting(DiagramPoint::lane).containsExactly(0, 0, 0);
        assertThat(overlappingPath.points()).extracting(DiagramPoint::lane).containsExactly(1, 1);
        assertThat(reusablePath.points()).extracting(DiagramPoint::lane).containsExactly(0);
        assertThat(overlappingPath.points().getFirst().x() - firstPath.points().getFirst().x()).isEqualTo(10);
    }

    @Test
    void treatsRunsSharingAnOrdinalTimestampAsOverlapping() {
        ComponentFlowDto first = flow("1.0.0",
                deployment("DEV", "SUCCESS", "2026-09-01T10:00:00Z"));
        ComponentFlowDto second = flow("2.0.0",
                deployment("DEV", "FAILURE", "2026-09-01T10:00:00Z"));

        DiagramLayout layout = renderer.createLayout(List.of(first, second));

        assertThat(flowPath(layout, "1.0.0").points().getFirst().lane()).isZero();
        assertThat(flowPath(layout, "2.0.0").points().getFirst().lane()).isEqualTo(1);
        assertThat(flowPath(layout, "1.0.0").points().getFirst().y())
                .isEqualTo(flowPath(layout, "2.0.0").points().getFirst().y());
    }

    @Test
    void rendersCollapsedLinkedSvgWithStatusIconsTooltipAndNoLegend() {
        ComponentFlowDeploymentDto successful = ComponentFlowDeploymentDto.builder()
                .startedAt("2026-09-01 12:00:00")
                .startedAtInstant(Instant.parse("2026-09-01T10:00:00Z"))
                .stage("DEV<&>")
                .state("SUCCESS")
                .pageUrl("https://confluence.example/page?a=1&b=2")
                .build();
        ComponentFlowDto flow = flow("1.0<&>", successful,
                deployment("PROD", "FAILURE", "2026-09-01T11:00:00Z"),
                deployment("PROD", "CANCELLED", "2026-09-01T12:00:00Z"),
                deployment("PROD", "STARTED", "2026-09-01T13:00:00Z"));
        DiagramPoint firstPoint = renderer.createLayout(List.of(flow)).flows().getFirst().points().getFirst();

        String content = renderer.render(ComponentPageDto.builder().flows(List.of(flow)).build());

        assertThat(content)
                .startsWith("<ac:structured-macro ac:name=\"expand\">")
                .contains("<ac:parameter ac:name=\"title\">Version Flows Diagram</ac:parameter>")
                .contains("<svg", "<polyline", ">✓</text>", ">×</text>", ">−</text>", ">?</text>")
                .contains(">DEV&lt;&amp;&gt;</text>", ">PROD</text>", ">Stage</text>")
                .contains("<circle cx=\"" + firstPoint.x() + "\"",
                        "<text x=\"" + (firstPoint.x() + 15) + "\"",
                        "<rect x=\"" + (firstPoint.x() + 40) + "\"",
                        "<text x=\"" + (firstPoint.x() + 46) + "\"")
                .contains("<title>1.0&lt;&amp;&gt; · DEV&lt;&amp;&gt; · SUCCESS · 2026-09-01 12:00:00</title>")
                .contains("href=\"https://confluence.example/page?a=1&amp;b=2\"")
                .contains("data-version=\"1.0&lt;&amp;&gt;\"")
                .doesNotContain("Legende", "Legend", "icon legend", "1.0<&>");
    }

    @Test
    void includesSecondsOnlyForTimestampsWithinTheSameMinute() {
        ComponentFlowDto flow = flow("1.0.0",
                deployment("DEV", "SUCCESS", "2026-09-01T10:00:01Z"),
                deployment("INT", "SUCCESS", "2026-09-01T10:00:59Z"),
                deployment("PROD", "SUCCESS", "2026-09-01T11:00:00Z"));

        DiagramLayout layout = renderer.createLayout(List.of(flow));

        assertThat(layout.timestampLabels()).containsExactly(
                formattedSecond("2026-09-01T10:00:01Z"), formattedSecond("2026-09-01T10:00:59Z"),
                formattedMinute("2026-09-01T11:00:00Z"));
    }

    @Test
    void safelyRendersEmptyDiagramAndIgnoresDeploymentsWithoutStageOrTimestamp() {
        ComponentFlowDto incomplete = flow("1.0.0",
                ComponentFlowDeploymentDto.builder().stage("DEV").state("SUCCESS").build(),
                ComponentFlowDeploymentDto.builder().startedAtInstant(Instant.EPOCH).state("FAILURE").build(),
                ComponentFlowDeploymentDto.builder().startedAtInstant(Instant.EPOCH).stage(" ").build());

        DiagramLayout layout = renderer.createLayout(List.of(incomplete));
        String content = renderer.render(ComponentPageDto.builder().flows(List.of(incomplete)).build());

        assertThat(layout.flows()).isEmpty();
        assertThat(content).contains("Keine Deployment-Daten für das Diagramm vorhanden.");
    }

    private FlowPath flowPath(DiagramLayout layout, String version) {
        return layout.flows().stream()
                .filter(flow -> version.equals(flow.version()))
                .findFirst()
                .orElseThrow();
    }

    private ComponentFlowDto flow(String version, ComponentFlowDeploymentDto... deployments) {
        return ComponentFlowDto.builder()
                .version(version)
                .deployments(List.of(deployments))
                .build();
    }

    private ComponentFlowDeploymentDto deployment(String stage, String state, String instant) {
        return ComponentFlowDeploymentDto.builder()
                .startedAt(instant)
                .startedAtInstant(Instant.parse(instant))
                .stage(stage)
                .state(state)
                .build();
    }

    private String formattedMinute(String instant) {
        return MINUTE_FORMATTER.format(Instant.parse(instant));
    }

    private String formattedSecond(String instant) {
        return SECOND_FORMATTER.format(Instant.parse(instant));
    }
}
