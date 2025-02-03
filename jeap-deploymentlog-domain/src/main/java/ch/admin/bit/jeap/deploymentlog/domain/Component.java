package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.*;

import jakarta.persistence.*;
import java.util.Set;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Entity
public class Component {

    @Getter
    @Id
    private UUID id;

    @Getter
    private String name;

    @Getter
    private boolean active;

    @OneToMany(mappedBy = "component",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true)
    @ToString.Exclude
    private Set<ComponentVersion> componentVersions;

    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private System system;

    public Component(@NonNull String name, System system) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.system = system;
        this.active = true;
    }

    public void inactive() {
        this.active = false;
    }

    public void updateSystem(System system) {
        this.system = system;
    }
}
