package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.ZonedDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Builder
@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
public class DeploymentPage {

    @Id
    private UUID id;

    @NonNull
    private UUID deploymentId;

    @Setter
    private String pageId;

    @NonNull
    @Setter
    private ZonedDateTime lastUpdatedAt;

    @NonNull
    @Setter
    private ZonedDateTime deploymentStateTimestamp;

}
