package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.ZonedDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
public class ArtifactVersion {

    @Id
    private UUID id;

    @NonNull
    private String coordinates;

    @NonNull
    private String buildJobLink;

    @NonNull
    private ZonedDateTime createdAt;

    @Builder
    private ArtifactVersion(@NonNull UUID id, @NonNull String coordinates, @NonNull String buildJobLink) {
        this.id = id;
        this.coordinates = coordinates;
        this.buildJobLink = buildJobLink;
        this.createdAt = ZonedDateTime.now();
    }
}
