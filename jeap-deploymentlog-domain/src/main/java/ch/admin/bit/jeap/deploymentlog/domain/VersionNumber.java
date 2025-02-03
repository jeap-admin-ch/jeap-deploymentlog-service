package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@Getter
@Embeddable
@Slf4j
@EqualsAndHashCode
public class VersionNumber implements Comparable<VersionNumber> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(?<major>\\d+)(\\.(?<minor>\\d+))?(\\.(?<patch>\\d+))?([._-](?<build>\\d+))?([._-](?<postfix>.+))?$");

    @NonNull
    private BigDecimal majorVersion;

    private BigDecimal minorVersion;

    private BigDecimal patchVersion;

    private BigDecimal buildVersion;

    private String postfix;

    public static VersionNumber of(String version) {
        try {
            Matcher matcher = VERSION_PATTERN.matcher(version);
            if (matcher.matches()) {
                String postfix = matcher.group("postfix");
                BigDecimal major = numberFromMatchedGroup(matcher, "major");
                if (major != null) {
                    return new VersionNumber(
                            major,
                            numberFromMatchedGroup(matcher, "minor"),
                            numberFromMatchedGroup(matcher, "patch"),
                            numberFromMatchedGroup(matcher, "build"),
                            postfix);
                }
            }
        } catch (Exception ex) {
            log.error("Unparseable version number", ex);
        }
        log.warn("The actual version '{}' does not match the pattern", version);
        return null;
    }

    private static BigDecimal numberFromMatchedGroup(Matcher matcher, String groupName) {
        String match = matcher.group(groupName);
        return match == null ? null : new BigDecimal(match);
    }

    @Override
    public String toString() {
        return majorVersion
                + dotVersionNullSafe(minorVersion)
                + dotVersionNullSafe(patchVersion)
                + dotVersionNullSafe(buildVersion)
                + dotVersionNullSafe(postfix);
    }

    private static String dotVersionNullSafe(Object value) {
        return value == null ? "" : "." + value;
    }

    /**
     * Order versions by
     * <ul>
     *     <li>Major, then minor, then patch, then build, then:</li>
     *     <li>Assume the presence of a postfix signifies an unreleased version (SNAPSHOT, alpha, ...) and thus
     *     an earlier version than  a released version with the same numeric version parts.
     *     </li>
     * </ul>
     */
    @Override
    public int compareTo(VersionNumber other) {
        if (other == null) {
            return 1;
        }

        int major = compare(majorVersion, other.majorVersion) * 10000;
        int minor = compare(minorVersion, other.minorVersion) * 1000;
        int patch = compare(patchVersion, other.patchVersion) * 100;
        int build = compare(buildVersion, other.buildVersion) * 10;
        int hasPostfix = comparePostfix(postfix == null, other.postfix == null);

        return major + minor + patch + build + hasPostfix;
    }

    private int comparePostfix(boolean hasPostfix, boolean otherHasPostfix) {
        // Consider postfix < !postfix (returns >0 if !hasPostfix && otherHasPostfix)
        // Example: 1.0-SNAPSHOT < 1.0
        return Boolean.compare(hasPostfix, otherHasPostfix);
    }

    private int compare(BigDecimal thisVersion, BigDecimal otherVersion) {
        if (thisVersion == null && otherVersion == null) {
            return 0;
        } else if (thisVersion == null) {
            // Consider 12.1 < 12.1.1
            return -1;
        } else if (otherVersion == null) {
            // Consider 12.1.1 > 12.1
            return 1;
        }

        return thisVersion.compareTo(otherVersion);
    }
}
