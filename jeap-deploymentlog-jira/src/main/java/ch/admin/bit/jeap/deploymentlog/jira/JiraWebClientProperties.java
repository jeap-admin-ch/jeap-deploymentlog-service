package ch.admin.bit.jeap.deploymentlog.jira;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.deploymentlog.jira", ignoreUnknownFields = false)
@Slf4j
public class JiraWebClientProperties {

    private String url;

    private String appId;

    private String username;

    @ToString.Exclude
    private String password;

    private boolean mockJiraClient = false;

    /**
     * Delay in milliseconds before the first retry of a failed jira request (doubled for each further retry).
     */
    private long retryDelayMs = 2000;

    /**
     * Issue type names exempt from the {@code R4DEPLOY} label check of the ready-for-deploy check (they only
     * need to exist). Matched case-insensitively against the issue type name jira returns for the technical
     * user, i.e. the name used by the jira instance's configured issue type scheme, translated into the
     * technical user's language if issue type translations are configured. If an issue type is renamed or
     * translated away from the configured names, it is silently no longer exempt.
     */
    private List<String> labelCheckExemptIssueTypes = List.of("Epic");

    @PostConstruct
    void init() {
        log.info("Jira configuration: {}", this);
    }
}
