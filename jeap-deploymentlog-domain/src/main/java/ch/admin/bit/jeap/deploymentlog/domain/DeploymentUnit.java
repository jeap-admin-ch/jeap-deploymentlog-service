package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Builder
@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Embeddable
public class DeploymentUnit {

    @Enumerated(EnumType.STRING)
    @NonNull
    private DeploymentUnitType type;

    @NonNull
    private String coordinates;

    @NonNull
    private String artifactRepositoryUrl;

}
