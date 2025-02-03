package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.*;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
public class ComponentVersion {

    @Id
    private UUID id;

    @NonNull
    private String versionName;

    private ZonedDateTime taggedAt;

    @Embedded
    private VersionNumber versionNumber;

    @NonNull
    private String versionControlUrl;

    @NonNull
    private String commitRef;

    @NonNull
    private ZonedDateTime committedAt;

    private boolean publishedVersion;

    @Embedded
    @NonNull
    private DeploymentUnit deploymentUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @NonNull
    private Component component;

    @SuppressWarnings("java:S107")
    @Builder
    private ComponentVersion(@NonNull String versionName, ZonedDateTime taggedAt, @NonNull String versionControlUrl, @NonNull String commitRef, @NonNull ZonedDateTime committedAt, boolean publishedVersion, @NonNull Component component, @NonNull DeploymentUnit deploymentUnit) {
        this.id = UUID.randomUUID();
        this.versionName = versionName;
        this.taggedAt = taggedAt;
        this.versionNumber = VersionNumber.of(versionName);
        this.versionControlUrl = versionControlUrl;
        this.commitRef = commitRef;
        this.committedAt = committedAt;
        this.publishedVersion = publishedVersion;
        this.deploymentUnit = deploymentUnit;
        this.component = component;
    }

}
