package ch.admin.bit.jeap.deploymentlog.docgen;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
final class JiraIssueKey {

    private static final Pattern PATTERN = Pattern.compile("^([A-Z][A-Z0-9_]*)-([1-9][0-9]*)$");

    private JiraIssueKey() {
    }

    static Optional<Parsed> parse(String rawKey) {
        String normalized = rawKey == null ? "" : rawKey.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            log.warn("Skipping syntactically invalid Jira issue key: {}", safeForLog(rawKey));
            return Optional.empty();
        }
        return Optional.of(new Parsed(normalized, matcher.group(1)));
    }

    private static String safeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80) + "...";
    }

    record Parsed(String issueKey, String projectKey) {
    }
}
