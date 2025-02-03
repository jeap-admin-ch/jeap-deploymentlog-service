package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

/**
 * A reference associated with a deployment, pointing to a specific URL.
 * Can be used to associate a link to a deployment, before the deployment actually happens.
 * At the moment used to link build jobs to deployments via the pair GIT_URL:&lt;version&gt;.
 */
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Reference {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @NonNull
    private String referenceIdentifier;

    @NonNull
    @Enumerated(EnumType.STRING)
    private ReferenceType type;

    @NonNull
    private String uri;

    @Builder
    @SuppressWarnings("java:S107")
    private Reference(UUID id, String referenceIdentifier, ReferenceType type, String uri) {
        this.id = id;
        this.referenceIdentifier = referenceIdentifier;
        this.type = type;
        this.uri = uri;
    }
}
