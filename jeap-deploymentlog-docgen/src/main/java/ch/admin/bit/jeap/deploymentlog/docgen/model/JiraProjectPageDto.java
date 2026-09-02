package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Value
@Builder
public class JiraProjectPageDto {
    String projectKey;
    String confluenceSpaceKey;
    Duration activityPeriod;
    List<JiraProjectIssueDto> issues;

    public String getActivityPeriodDisplay() {
        if (activityPeriod == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        addPart(parts, activityPeriod.toDaysPart(), "Tag", "Tage");
        addPart(parts, activityPeriod.toHoursPart(), "Stunde", "Stunden");
        addPart(parts, activityPeriod.toMinutesPart(), "Minute", "Minuten");
        addPart(parts, activityPeriod.toSecondsPart(), "Sekunde", "Sekunden");
        addPart(parts, activityPeriod.toMillisPart(), "Millisekunde", "Millisekunden");
        int remainingNanos = activityPeriod.toNanosPart() % 1_000_000;
        addPart(parts, remainingNanos, "Nanosekunde", "Nanosekunden");
        return String.join(" ", parts);
    }

    private static void addPart(List<String> parts, long value, String singular, String plural) {
        if (value != 0) {
            parts.add(value + " " + (value == 1 ? singular : plural));
        }
    }
}
