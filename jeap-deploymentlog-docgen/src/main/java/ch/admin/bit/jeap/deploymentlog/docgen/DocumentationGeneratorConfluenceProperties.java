package ch.admin.bit.jeap.deploymentlog.docgen;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.deploymentlog.documentation-generator.confluence", ignoreUnknownFields = false)
@Slf4j
public class DocumentationGeneratorConfluenceProperties {

    /**
     * Name of the root page under which all deployments are listed
     */
    private String deploymentsPageName;

    /**
     * Key of the confluence space into which the documentation is generated
     */
    private String spaceKey;

    private String url;

    /**
     * Number of deployments shown on DeploymentHistory Page
     * Default is 50
     */
    private int deploymentHistoryMaxShow = 50;

    /**
     * Show deployments started after this value [duration]. Default is 7 days
     */
    private Duration deploymentHistoryOverviewMaxTime = Duration.of(7, ChronoUnit.DAYS);

    private String username;

    @ToString.Exclude
    private String password;

    private boolean mockConfluenceClient = false;

    private Duration retryOnConflictWaitDuration = Duration.ofSeconds(10);

    @PostConstruct
    void init() {
        log.info("Confluence configuration: {}", this);
    }
}
