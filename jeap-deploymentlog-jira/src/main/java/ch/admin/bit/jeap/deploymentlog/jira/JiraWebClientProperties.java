package ch.admin.bit.jeap.deploymentlog.jira;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

    @PostConstruct
    void init() {
        log.info("Jira configuration: {}", this);
    }
}
