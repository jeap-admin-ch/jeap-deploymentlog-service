package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.*;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "SYSTEM_NAME_UK", columnNames = {"name"})})
public class System {

    @Id
    private UUID id;

    @NonNull
    private String name;

    @OneToMany(mappedBy = "system",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true)
    @ToString.Exclude
    private Set<Component> components;

    @OneToMany(mappedBy = "system",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true)
    private Set<SystemAlias> aliases;

    public System(@NonNull String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.components = new HashSet<>();
        this.aliases = new HashSet<>();
    }

    public void updateName(String name) {
        this.name = name;
    }
}
