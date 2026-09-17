package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDeploymentDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentPageDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

@Component
class VersionFlowDiagramRenderer {

    private static final List<String> KNOWN_STAGES = List.of("DEV", "INT", "TEST", "REF", "ABN", "PROD");
    private static final List<String> FLOW_COLORS = List.of(
            "#0052cc", "#6554c0", "#00875a", "#ff8b00", "#de350b", "#00a3bf", "#403294", "#36b37e");
    private static final DateTimeFormatter MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SECOND_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int LEFT_MARGIN = 155;
    private static final int RIGHT_MARGIN = 320;
    private static final int TOP_MARGIN = 50;
    private static final int BOTTOM_MARGIN = 65;
    private static final int MIN_STAGE_SPACING = 120;
    private static final int ORDINAL_SPACING = 44;
    private static final int LANE_SPACING = 10;
    private static final String TEXT_START = "<text x=\"";
    private static final String Y_ATTRIBUTE = "\" y=\"";
    private static final String TEXT_END = "</text>";

    String render(ComponentPageDto page) {
        DiagramLayout layout = createLayout(page.getFlows());
        return """
                <ac:structured-macro ac:name="expand">
                <ac:parameter ac:name="title">Version Flows Diagram</ac:parameter>
                <ac:rich-text-body>
                %s
                </ac:rich-text-body>
                </ac:structured-macro>
                """.formatted(renderSvg(layout));
    }

    DiagramLayout createLayout(List<ComponentFlowDto> flows) {
        List<ComponentFlowDto> safeFlows = flows == null ? List.of() : flows;
        List<String> stages = orderedStages(safeFlows);

        TreeSet<Instant> timestamps = new TreeSet<>();
        List<MutableFlow> mutableFlows = createMutableFlows(safeFlows, timestamps);
        List<Instant> ordinalTimestamps = List.copyOf(timestamps);
        assignOrdinals(mutableFlows, ordinalTimestamps);

        Map<String, List<StageRun>> runsByStage = identifyStageRuns(mutableFlows);
        Map<String, Integer> maxLaneByStage = allocateLanes(runsByStage);
        Map<String, Integer> stageX = calculateStagePositions(stages, maxLaneByStage);

        int height = TOP_MARGIN + BOTTOM_MARGIN
                + Math.max(1, ordinalTimestamps.size() - 1) * ORDINAL_SPACING;
        int width = stages.isEmpty()
                ? LEFT_MARGIN + RIGHT_MARGIN + 240
                : stageX.get(stages.getLast()) + RIGHT_MARGIN
                + Math.max(0, maxLaneByStage.getOrDefault(stages.getLast(), 1) - 1) * LANE_SPACING;

        List<FlowPath> paths = createPaths(mutableFlows, ordinalTimestamps.size(), stageX);
        return new DiagramLayout(List.copyOf(stages), List.copyOf(ordinalTimestamps), List.copyOf(paths),
                timestampLabels(ordinalTimestamps),
                Map.copyOf(stageX), width, height);
    }

    private List<MutableFlow> createMutableFlows(List<ComponentFlowDto> flows, TreeSet<Instant> timestamps) {
        List<MutableFlow> mutableFlows = new ArrayList<>();
        for (int flowIndex = 0; flowIndex < flows.size(); flowIndex++) {
            ComponentFlowDto flow = flows.get(flowIndex);
            List<MutablePoint> points = new ArrayList<>();
            List<ComponentFlowDeploymentDto> deployments =
                    flow.getDeployments() == null ? List.of() : flow.getDeployments();
            for (int deploymentIndex = 0; deploymentIndex < deployments.size(); deploymentIndex++) {
                ComponentFlowDeploymentDto deployment = deployments.get(deploymentIndex);
                String stage = normalizeStage(deployment.getStage());
                if (stage == null || deployment.getStartedAtInstant() == null) {
                    continue;
                }
                timestamps.add(deployment.getStartedAtInstant());
                points.add(new MutablePoint(deployment, stage, deploymentIndex));
            }
            points.sort(Comparator.comparing(MutablePoint::timestamp)
                    .thenComparingInt(MutablePoint::sourceIndex));
            mutableFlows.add(new MutableFlow(flowIndex, flow.getVersion(), points));
        }
        return mutableFlows;
    }

    private void assignOrdinals(List<MutableFlow> flows, List<Instant> ordinalTimestamps) {
        Map<Instant, Integer> ordinalByTimestamp = new HashMap<>();
        for (int i = 0; i < ordinalTimestamps.size(); i++) {
            ordinalByTimestamp.put(ordinalTimestamps.get(i), i);
        }
        flows.forEach(flow -> flow.points().forEach(
                point -> point.ordinal = ordinalByTimestamp.get(point.timestamp())));
    }

    private List<FlowPath> createPaths(List<MutableFlow> mutableFlows, int timestampCount,
                                       Map<String, Integer> stageX) {
        List<FlowPath> paths = new ArrayList<>();
        for (MutableFlow flow : mutableFlows) {
            List<DiagramPoint> points = new ArrayList<>();
            for (int i = 0; i < flow.points().size(); i++) {
                MutablePoint point = flow.points().get(i);
                int x = stageX.get(point.stage) + point.lane * LANE_SPACING;
                int y = TOP_MARGIN + (timestampCount - 1 - point.ordinal) * ORDINAL_SPACING;
                points.add(new DiagramPoint(point.stage, point.deployment.getState(),
                        point.deployment.getStartedAt(), point.deployment.getPageUrl(),
                        point.timestamp(), point.ordinal, point.lane, x, y, i == 0));
            }
            if (!points.isEmpty()) {
                paths.add(new FlowPath(flow.version(), FLOW_COLORS.get(flow.index() % FLOW_COLORS.size()),
                        List.copyOf(points)));
            }
        }
        return paths;
    }

    private List<String> timestampLabels(List<Instant> timestamps) {
        Map<String, Long> minuteCounts = timestamps.stream()
                .collect(java.util.stream.Collectors.groupingBy(MINUTE_FORMATTER::format,
                        LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return timestamps.stream()
                .map(timestamp -> minuteCounts.get(MINUTE_FORMATTER.format(timestamp)) > 1
                        ? SECOND_FORMATTER.format(timestamp)
                        : MINUTE_FORMATTER.format(timestamp))
                .toList();
    }

    private List<String> orderedStages(List<ComponentFlowDto> flows) {
        TreeSet<String> unknownStages = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> presentKnownStages = new ArrayList<>();
        for (ComponentFlowDto flow : flows) {
            collectStages(flow, presentKnownStages, unknownStages);
        }
        presentKnownStages.sort(Comparator.comparingInt(KNOWN_STAGES::indexOf));
        presentKnownStages.addAll(unknownStages);
        return presentKnownStages;
    }

    private void collectStages(ComponentFlowDto flow, List<String> presentKnownStages, TreeSet<String> unknownStages) {
        if (flow.getDeployments() == null) {
            return;
        }
        for (ComponentFlowDeploymentDto deployment : flow.getDeployments()) {
            String stage = normalizeStage(deployment.getStage());
            if (stage == null) {
                continue;
            }
            if (KNOWN_STAGES.contains(stage)) {
                if (!presentKnownStages.contains(stage)) {
                    presentKnownStages.add(stage);
                }
            } else {
                unknownStages.add(stage);
            }
        }
    }

    private Map<String, List<StageRun>> identifyStageRuns(List<MutableFlow> flows) {
        Map<String, List<StageRun>> runsByStage = new LinkedHashMap<>();
        for (MutableFlow flow : flows) {
            StageRun currentRun = null;
            for (MutablePoint point : flow.points()) {
                if (currentRun == null || !currentRun.stage.equals(point.stage)) {
                    currentRun = new StageRun(point.stage, flow.index(), point.ordinal, point.ordinal);
                    runsByStage.computeIfAbsent(point.stage, ignored -> new ArrayList<>()).add(currentRun);
                } else {
                    currentRun.endOrdinal = point.ordinal;
                }
                currentRun.points.add(point);
            }
        }
        return runsByStage;
    }

    private Map<String, Integer> allocateLanes(Map<String, List<StageRun>> runsByStage) {
        Map<String, Integer> maxLaneByStage = new HashMap<>();
        runsByStage.forEach((stage, runs) -> {
            runs.sort(Comparator.comparingInt((StageRun run) -> run.startOrdinal)
                    .thenComparingInt(run -> run.endOrdinal)
                    .thenComparingInt(run -> run.flowIndex));
            List<Integer> laneEndOrdinals = new ArrayList<>();
            for (StageRun run : runs) {
                int laneIndex = 0;
                while (laneIndex < laneEndOrdinals.size()
                        && laneEndOrdinals.get(laneIndex) >= run.startOrdinal) {
                    laneIndex++;
                }
                if (laneIndex == laneEndOrdinals.size()) {
                    laneEndOrdinals.add(run.endOrdinal);
                } else {
                    laneEndOrdinals.set(laneIndex, run.endOrdinal);
                }
                int assignedLane = laneIndex;
                run.points.forEach(point -> point.lane = assignedLane);
            }
            maxLaneByStage.put(stage, laneEndOrdinals.size());
        });
        return maxLaneByStage;
    }

    private Map<String, Integer> calculateStagePositions(List<String> stages, Map<String, Integer> maxLaneByStage) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        int x = LEFT_MARGIN;
        for (String stage : stages) {
            positions.put(stage, x);
            int lanes = maxLaneByStage.getOrDefault(stage, 1);
            x += Math.max(MIN_STAGE_SPACING, 80 + (lanes - 1) * LANE_SPACING);
        }
        return positions;
    }

    private String renderSvg(DiagramLayout layout) {
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\"Version Flows\" ")
                .append("width=\"").append(layout.width()).append("\" height=\"").append(layout.height())
                .append("\" viewBox=\"0 0 ").append(layout.width()).append(' ')
                .append(layout.height()).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>");
        if (layout.stages().isEmpty() || layout.flows().isEmpty()) {
            svg.append("<text x=\"20\" y=\"30\" fill=\"#5e6c84\">Keine Deployment-Daten für das Diagramm vorhanden.</text>")
                    .append("</svg>");
            return svg.toString();
        }

        for (int ordinal = 0; ordinal < layout.ordinalTimestamps().size(); ordinal++) {
            int y = TOP_MARGIN + (layout.ordinalTimestamps().size() - 1 - ordinal) * ORDINAL_SPACING;
            svg.append("<line x1=\"").append(LEFT_MARGIN - 10).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(layout.width() - RIGHT_MARGIN + 20).append("\" y2=\"")
                    .append(y).append("\" stroke=\"#f4f5f7\" stroke-width=\"1\"/>")
                    .append(TEXT_START).append("5").append(Y_ATTRIBUTE).append(y + 4)
                    .append("\" font-size=\"10\" fill=\"#5e6c84\">")
                    .append(xml(layout.timestampLabels().get(ordinal))).append(TEXT_END);
        }
        for (String stage : layout.stages()) {
            int x = layout.stageX().get(stage);
            svg.append("<line x1=\"").append(x).append("\" y1=\"35\" x2=\"").append(x)
                    .append("\" y2=\"").append(layout.height() - BOTTOM_MARGIN + 10)
                    .append("\" stroke=\"#dfe1e6\" stroke-width=\"1\"/>")
                    .append(TEXT_START).append(x).append(Y_ATTRIBUTE).append("22\" text-anchor=\"middle\" ")
                    .append("font-weight=\"bold\" fill=\"#172b4d\">").append(xml(stage)).append(TEXT_END)
                    .append(TEXT_START).append(x).append(Y_ATTRIBUTE).append(layout.height() - 35)
                    .append("\" text-anchor=\"middle\" font-weight=\"bold\" fill=\"#172b4d\">")
                    .append(xml(stage)).append(TEXT_END);
        }
        svg.append(TEXT_START).append((LEFT_MARGIN + layout.width() - RIGHT_MARGIN) / 2)
                .append(Y_ATTRIBUTE).append(layout.height() - 10)
                .append("\" text-anchor=\"middle\" font-size=\"12\" fill=\"#5e6c84\">Stage").append(TEXT_END);

        for (FlowPath flow : layout.flows()) {
            if (flow.points().size() > 1) {
                svg.append("<polyline fill=\"none\" stroke=\"").append(flow.color())
                        .append("\" stroke-width=\"2\" points=\"");
                for (DiagramPoint point : flow.points()) {
                    svg.append(point.x()).append(',').append(point.y()).append(' ');
                }
                svg.append("\"/>");
            }
            for (DiagramPoint point : flow.points()) {
                renderPoint(svg, flow, point);
            }
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private void renderPoint(StringBuilder svg, FlowPath flow, DiagramPoint point) {
        String state = point.state() == null ? "UNKNOWN" : point.state().toUpperCase(Locale.ROOT);
        String tooltip = "%s · %s · %s · %s".formatted(
                Objects.toString(flow.version(), ""), point.stage(), state,
                Objects.toString(point.startedAt(), ""));
        if (point.pageUrl() != null && !point.pageUrl().isBlank()) {
            svg.append("<a href=\"").append(xml(point.pageUrl())).append("\">");
        }
        svg.append("<g data-version=\"").append(xml(flow.version())).append("\" data-stage=\"")
                .append(xml(point.stage())).append("\" data-lane=\"").append(point.lane()).append("\">")
                .append("<title>").append(xml(tooltip)).append("</title>")
                .append("<circle cx=\"").append(point.x()).append("\" cy=\"").append(point.y())
                .append("\" r=\"5\" fill=\"").append(flow.color()).append("\"/>")
                .append(TEXT_START).append(point.x() + 15).append(Y_ATTRIBUTE).append(point.y() + 6)
                .append("\" font-size=\"20\" font-weight=\"bold\" fill=\"").append(statusColor(state)).append("\">")
                .append(xml(statusIcon(state))).append(TEXT_END).append("</g>");
        if (point.firstChronological()) {
            int labelWidth = Math.max(90, Math.min(260, Objects.toString(flow.version(), "").length() * 8 + 14));
            svg.append("<rect x=\"").append(point.x() + 40).append(Y_ATTRIBUTE).append(point.y() - 13)
                    .append("\" width=\"").append(labelWidth).append("\" height=\"22\" rx=\"3\" ")
                    .append("fill=\"#ffffff\" fill-opacity=\"0.92\"/>")
                    .append(TEXT_START).append(point.x() + 46).append(Y_ATTRIBUTE).append(point.y() + 3)
                    .append("\" fill=\"").append(flow.color()).append("\" font-size=\"12\" font-weight=\"bold\">")
                    .append(xml(flow.version())).append(TEXT_END);
        }
        if (point.pageUrl() != null && !point.pageUrl().isBlank()) {
            svg.append("</a>");
        }
    }

    private String statusColor(String state) {
        return switch (state) {
            case "SUCCESS" -> "#00875a";
            case "FAILURE" -> "#de350b";
            case "CANCELLED" -> "#6b778c";
            case "STARTED" -> "#0052cc";
            default -> "#6554c0";
        };
    }

    private String statusIcon(String state) {
        return switch (state) {
            case "SUCCESS" -> "✓";
            case "FAILURE" -> "×";
            case "CANCELLED" -> "−";
            case "STARTED" -> "?";
            default -> state.isBlank() ? "•" : state.substring(0, 1);
        };
    }

    private String normalizeStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        return stage.trim().toUpperCase(Locale.ROOT);
    }

    private String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    record DiagramLayout(List<String> stages, List<Instant> ordinalTimestamps, List<FlowPath> flows,
                         List<String> timestampLabels, Map<String, Integer> stageX, int width, int height) {
    }

    record FlowPath(String version, String color, List<DiagramPoint> points) {
    }

    record DiagramPoint(String stage, String state, String startedAt, String pageUrl, Instant timestamp,
                        int ordinal, int lane, int x, int y, boolean firstChronological) {
    }

    private record MutableFlow(int index, String version, List<MutablePoint> points) {
    }

    private static final class MutablePoint {
        private final ComponentFlowDeploymentDto deployment;
        private final String stage;
        private final int sourceIndex;
        private int ordinal;
        private int lane;

        private MutablePoint(ComponentFlowDeploymentDto deployment, String stage, int sourceIndex) {
            this.deployment = deployment;
            this.stage = stage;
            this.sourceIndex = sourceIndex;
        }

        private Instant timestamp() {
            return deployment.getStartedAtInstant();
        }

        private int sourceIndex() {
            return sourceIndex;
        }
    }

    private static final class StageRun {
        private final String stage;
        private final int flowIndex;
        private final int startOrdinal;
        private int endOrdinal;
        private final List<MutablePoint> points = new ArrayList<>();

        private StageRun(String stage, int flowIndex, int startOrdinal, int endOrdinal) {
            this.stage = stage;
            this.flowIndex = flowIndex;
            this.startOrdinal = startOrdinal;
            this.endOrdinal = endOrdinal;
        }
    }
}
