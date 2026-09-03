package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JiraIssuePage {

    @Id
    private String issueKey;

    @Column(nullable = false)
    private String projectKey;

    @Column(nullable = false, unique = true)
    private String pageId;

    @Column(nullable = false)
    private String parentPageId;

    @Column(nullable = false)
    private ZonedDateTime lastUpdatedAt;

    private JiraIssuePage(String issueKey, String projectKey, String pageId, String parentPageId) {
        this.issueKey = issueKey;
        updateLocation(projectKey, pageId, parentPageId);
    }

    public static JiraIssuePage create(String issueKey, String projectKey, String pageId, String parentPageId) {
        return new JiraIssuePage(issueKey, projectKey, pageId, parentPageId);
    }

    public void updateLocation(String projectKey, String pageId, String parentPageId) {
        this.projectKey = projectKey;
        this.pageId = pageId;
        this.parentPageId = parentPageId;
        this.lastUpdatedAt = ZonedDateTime.now();
    }
}
