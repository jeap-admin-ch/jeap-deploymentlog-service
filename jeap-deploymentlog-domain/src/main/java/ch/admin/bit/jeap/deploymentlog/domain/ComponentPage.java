package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComponentPage {

    @Id
    private UUID componentId;

    @Column(nullable = false, unique = true)
    private String pageId;

    @Column(nullable = false)
    private String parentPageId;

    @Column(nullable = false)
    private ZonedDateTime lastUpdatedAt;

    @Column(name = "cleanup_attempted_at")
    private ZonedDateTime cleanupAttemptedAt;

    private ComponentPage(UUID componentId, String pageId, String parentPageId) {
        this.componentId = componentId;
        updateLocation(pageId, parentPageId);
    }

    public static ComponentPage create(UUID componentId, String pageId, String parentPageId) {
        return new ComponentPage(componentId, pageId, parentPageId);
    }

    public void updateLocation(String pageId, String parentPageId) {
        this.pageId = pageId;
        this.parentPageId = parentPageId;
        this.lastUpdatedAt = ZonedDateTime.now();
        this.cleanupAttemptedAt = null;
    }
}
