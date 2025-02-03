package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "SYSTEM_ALIAS_NAME_UK", columnNames = {"name"})})
public class SystemAlias {

    @Id
    private UUID id;

    @NonNull
    private String name;

    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private System system;

    public SystemAlias(@NonNull String name, @NonNull System system) {
        this.id = UUID.randomUUID();
        this.name = name.toLowerCase();
        this.system = system;
    }
}
