package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentationStructurePage {

    @Id
    private String structureKey;

    private String pageId;

    private String parentPageId;

    private ZonedDateTime lastUpdatedAt;

    public static DocumentationStructurePage create(String structureKey, String pageId, String parentPageId) {
        return new DocumentationStructurePage(structureKey, pageId, parentPageId, ZonedDateTime.now());
    }

    public void updateLocation(String pageId, String parentPageId) {
        this.pageId = pageId;
        this.parentPageId = parentPageId;
        this.lastUpdatedAt = ZonedDateTime.now();
    }
}
