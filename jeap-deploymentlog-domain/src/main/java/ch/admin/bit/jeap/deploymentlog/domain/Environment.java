package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "ENVIRONMENT_NAME_UK", columnNames = {"name"})})
public class Environment {

    @Id
    private UUID id;

    @NonNull
    private String name;

    @Setter
    private int stagingOrder;

    @Setter
    private boolean productive;

    @Setter
    private boolean development;

    public Environment(@NonNull String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.development = name.equalsIgnoreCase("DEV");
        this.productive = name.equalsIgnoreCase("PROD");
        this.stagingOrder = productive ? Integer.MAX_VALUE : 0;
    }
}
