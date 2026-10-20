package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A pending refresh of the Jira change view (project page, issue page and remote link) for one issue. Every deployment
 * referencing the issue adds its own request, so a refresh only removes the requests it has read and a request added
 * while it runs stays pending.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JiraChangeViewRequest {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String issueKey;

    @Column(nullable = false)
    private String projectKey;

    @Column(nullable = false)
    private ZonedDateTime requestedAt;

    private JiraChangeViewRequest(String issueKey, String projectKey, ZonedDateTime requestedAt) {
        this.id = UUID.randomUUID();
        this.issueKey = issueKey;
        this.projectKey = projectKey;
        this.requestedAt = requestedAt;
    }

    public static JiraChangeViewRequest create(String issueKey, String projectKey, ZonedDateTime requestedAt) {
        return new JiraChangeViewRequest(issueKey, projectKey, requestedAt);
    }
}
