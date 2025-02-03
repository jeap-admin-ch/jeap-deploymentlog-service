package ch.admin.bit.jeap.deploymentlog.docgen.service;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.deploymentlog.documentation-generator.scheduled")
@Slf4j
class SchedulingConfigProperties {

    private int retriedPagesLimit = 50;

    private long minAgeMinutes = 5;
    private long maxAgeMinutes = Duration.ofHours(24).toMinutes();
    // Property listed here to log it in init(), used as expression in SchedulingService#generateMissingPages()
    private String cron;
    private int keepDeploymentPagePerEnvCount = 200;

    @PostConstruct
    void init() {
        log.info("Scheduling configuration: {}", this);
        if (maxAgeMinutes <= minAgeMinutes || minAgeMinutes < 0) {
            throw new IllegalArgumentException("Bad configuration: Must be 0 <= minAgeMinutes < maxAgeMinutes");
        }
    }
}
