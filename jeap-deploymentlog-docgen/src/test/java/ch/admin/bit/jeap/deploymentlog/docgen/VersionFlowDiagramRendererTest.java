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
        String content = renderer.render(ComponentPageDto.builder().flows(List.of(flow)).build());

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
                .extracting(DiagramPoint::firstChronological)
                .containsExactly(true, false, false, false);
        assertThat(layout.flows().getFirst().points())
                .extracting(DiagramPoint::y)
                .containsExactly(182, 138, 94, 50);
        assertThat(content).contains(
                "y=\"186\" font-size=\"10\" fill=\"#5e6c84\">" + formattedMinute("2026-09-01T10:00:00Z"),
                "y=\"54\" font-size=\"10\" fill=\"#5e6c84\">" + formattedMinute("2026-09-01T13:00:00Z"));
    }

    @Test
    void keepsOnePositiveLaneForAStageRunAndAssignsOverlappingRunsToAdditionalLanes() {
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
    void rendersCollapsedStructuredHtmlMacroWithSelfContainedScrollableDiagram() {
        ComponentFlowDto flow = flow("1.0.0",
                deployment("DEV", "SUCCESS", "2026-09-01T10:00:00Z"),
                deployment("REF", "STARTED", "2026-09-01T11:00:00Z"));
        DiagramPoint firstPoint = renderer.createLayout(List.of(flow)).flows().getFirst().points().getFirst();

        String content = renderer.render(ComponentPageDto.builder().flows(List.of(flow)).build());

        assertThat(content)
                .startsWith("<ac:structured-macro ac:name=\"expand\">")
                .contains("<ac:parameter ac:name=\"title\">Version Flows Diagram</ac:parameter>")
                .contains("<ac:structured-macro ac:name=\"html\" ac:schema-version=\"1\">")
                .contains("<ac:plain-text-body><![CDATA[<div class=\"chart-shell\"><svg")
                .contains("max-height:70vh;overflow:auto", "<svg xmlns=\"http://www.w3.org/2000/svg\"",
                        "aria-label=\"Deployment-Verlauf je Version\"", "<polyline", "<circle", "<title>")
                .contains("<circle cx=\"" + firstPoint.x() + "\"",
                        "<text x=\"" + (firstPoint.x() + 13) + "\"")
                .contains(">✓</text>", ">◷</text>")
                .contains("DEV</text>", "REF</text>", ">Stage</text>")
                .containsOnlyOnce(">1.0.0</text>")
                .contains("]]></ac:plain-text-body>")
                .doesNotContain("plantuml", "PlantUML", "@startuml", "@enduml", "<ac:image",
                        "ri:attachment", "<script", "</script>", "JavaScript", "javascript",
                        "document.", "createElementNS", "JSON", "\"stages\":", "\"flows\":");
    }

    @Test
    void securelyEscapesSvgValuesAndSplitsCdataTerminators() {
        ComponentFlowDto flow = flow("release \"quoted\" 'single' <b>& ]]>",
                ComponentFlowDeploymentDto.builder()
                        .stage("dev<&]]>")
                        .state("SUCCESS")
                        .startedAt("line1\nline2<&]]>")
                        .startedAtInstant(Instant.parse("2026-09-01T10:00:00Z"))
                        .pageUrl("https://example.invalid/?a=1&b=\"quoted\"")
                        .build());

        String content = renderer.render(ComponentPageDto.builder().flows(List.of(flow)).build());

        assertThat(content)
                .contains("release &quot;quoted&quot; &apos;single&apos; &lt;b&gt;&amp; ]]&gt;",
                        "DEV&lt;&amp;]]&gt;", "line1\nline2&lt;&amp;]]&gt;",
                        "href=\"https://example.invalid/?a=1&amp;b=&quot;quoted&quot;\"")
                .doesNotContain("<b>", "<script", "</script>", "\"stages\":", "\"flows\":")
                .endsWith("</ac:structured-macro>\n");
        assertThat(renderer.splitCdata("before]]>after"))
                .isEqualTo("before]]]]><![CDATA[>after");
    }

    @Test
    void rendersOnlyDiagramFieldsAndNoJsonModel() {
        ComponentFlowDto flow = ComponentFlowDto.builder()
                .version("release-1")
                .versionControlUrl("https://git.example/repository")
                .jiraIssues(List.of())
                .deployments(List.of(ComponentFlowDeploymentDto.builder()
                        .stage("DEV").state("SUCCESS").startedAt("display time")
                        .startedAtInstant(Instant.parse("2026-09-01T10:00:00Z"))
                        .pageUrl("https://confluence.example/page").build()))
                .build();

        String content = renderer.render(ComponentPageDto.builder()
                .componentName("<component>")
                .flows(List.of(flow))
                .build());

        assertThat(content)
                .contains("data-version=\"release-1\"", "data-stage=\"DEV\"",
                        "<title>release-1 | DEV | display time | SUCCESS</title>",
                        "href=\"https://confluence.example/page\"")
                .doesNotContain("git.example", "<component>", "\"version\":", "\"stage\":", "\"state\":");
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
    void safelyRendersEmptyDiagramAndIgnoresNullOrIncompleteDeployments() {
        ComponentFlowDto incomplete = ComponentFlowDto.builder().version("1.0.0")
                .deployments(java.util.Arrays.asList(
                        null,
                        ComponentFlowDeploymentDto.builder().stage("DEV").state("SUCCESS").build(),
                        ComponentFlowDeploymentDto.builder().startedAtInstant(Instant.EPOCH).state("FAILURE").build(),
                        ComponentFlowDeploymentDto.builder().startedAtInstant(Instant.EPOCH).stage(" ").build()))
                .build();

        DiagramLayout layout = renderer.createLayout(java.util.Arrays.asList(null, incomplete));
        String content = renderer.render(ComponentPageDto.builder().flows(List.of(incomplete)).build());

        assertThat(layout.flows()).isEmpty();
        assertThat(content)
                .contains("Keine Deployment-Daten für das Diagramm vorhanden.")
                .contains("<div class=\"chart-shell\"><svg", "</svg></div>")
                .doesNotContain("plantuml", "@startuml", "@enduml", "<script", "JavaScript",
                        "document.", "createElementNS", "\"stages\":", "\"flows\":");
    }

    private FlowPath flowPath(DiagramLayout layout, String version) {
        return layout.flows().stream()
                .filter(flow -> version.equals(flow.version()))
                .findFirst()
                .orElseThrow();
    }

    private ComponentFlowDto flow(String version, ComponentFlowDeploymentDto... deployments) {
        return ComponentFlowDto.builder().version(version).deployments(List.of(deployments)).build();
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
