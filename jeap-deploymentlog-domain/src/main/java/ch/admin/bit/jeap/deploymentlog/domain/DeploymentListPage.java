package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.*;

import jakarta.persistence.Column;
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
public class DeploymentListPage {

    @Id
    private UUID id;

    @NonNull
    private UUID systemId;

    @NonNull
    private UUID environmentId;

    @Column(name="year_")
    private int year;

    @Setter
    private String pageId;

    @NonNull
    @Setter
    private ZonedDateTime lastUpdatedAt;

}
