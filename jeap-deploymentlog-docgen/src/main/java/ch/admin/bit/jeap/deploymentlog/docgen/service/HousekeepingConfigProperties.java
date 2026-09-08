package ch.admin.bit.jeap.deploymentlog.docgen.service;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.deploymentlog.housekeeping")
@Slf4j
public class HousekeepingConfigProperties {

    private static final int DEFAULT_RETENTION_BATCH_SIZE = 500;

    private final ConfluencePages confluencePages = new ConfluencePages();
    private final DataRetention dataRetention = new DataRetention();
    private String cron;

    @PostConstruct
    void validate() {
        if (confluencePages.minAge == null || confluencePages.minAge.isNegative()
                || confluencePages.minAge.isZero()) {
            throw new IllegalArgumentException(
                    "jeap.deploymentlog.housekeeping.confluence-pages.min-age must be greater than zero");
        }
        if (confluencePages.keepPerEnvironment != null && confluencePages.keepPerEnvironment < 0) {
            throw new IllegalArgumentException(
                    "jeap.deploymentlog.housekeeping.confluence-pages.keep-per-environment must not be negative");
        }
        if (dataRetention.enabled && (dataRetention.duration == null
                || dataRetention.duration.isNegative() || dataRetention.duration.isZero())) {
            throw new IllegalArgumentException(
                    "jeap.deploymentlog.housekeeping.data-retention.duration must be configured and greater than zero when data retention is enabled");
        }
        if (dataRetention.batchSize <= 0) {
            throw new IllegalArgumentException(
                    "jeap.deploymentlog.housekeeping.data-retention.batch-size must be greater than zero");
        }
        log.info("Housekeeping configuration: {}", this);
    }

    @Data
    public static class ConfluencePages {
        private boolean enabled = true;
        private Duration minAge = Duration.ofDays(7);
        private Integer keepPerEnvironment;

        int effectiveKeepPerEnvironment(int legacyValue) {
            return keepPerEnvironment == null ? legacyValue : keepPerEnvironment;
        }
    }

    @Data
    public static class DataRetention {
        private boolean enabled;
        private Duration duration;
        private int batchSize = DEFAULT_RETENTION_BATCH_SIZE;
    }
}
