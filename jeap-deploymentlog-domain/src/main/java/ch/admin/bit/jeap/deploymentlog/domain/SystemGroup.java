package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "system_group", uniqueConstraints =
        @UniqueConstraint(name = "system_group_normalized_name_uk", columnNames = "normalized_name"))
@Getter
@NoArgsConstructor(access = PROTECTED)
@ToString
public class SystemGroup {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "normalized_name", nullable = false, columnDefinition = "text")
    private String normalizedName;

    @OneToMany(mappedBy = "systemGroup", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<System> systems = new HashSet<>();

    public SystemGroup(String name) {
        this(SystemGroupName.of(name));
    }

    SystemGroup(SystemGroupName name) {
        this.id = UUID.randomUUID();
        rename(name);
    }

    public void rename(String name) {
        rename(SystemGroupName.of(name));
    }

    void rename(SystemGroupName name) {
        this.name = name.value();
        this.normalizedName = name.normalized();
    }

    void addSystem(System system) {
        systems.add(system);
    }

    void removeSystem(System system) {
        systems.remove(system);
    }
}
