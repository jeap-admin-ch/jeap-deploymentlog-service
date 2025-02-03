package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

import jakarta.persistence.Embeddable;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Embeddable
@EqualsAndHashCode
public class DeploymentTarget {

    @NonNull
    private String type;

    @NonNull
    private String url;

    @NonNull
    private String details;


    public DeploymentTarget(@NonNull String type, @NonNull String url, @NonNull String details) {
        this.type = type;
        this.url = url;
        this.details = details;
    }
}
