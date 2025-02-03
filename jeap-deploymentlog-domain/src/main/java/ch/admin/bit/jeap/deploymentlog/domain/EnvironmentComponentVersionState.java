package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@Getter
@Entity
public class EnvironmentComponentVersionState {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NonNull
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @NonNull
    private Component component;

    @ManyToOne(fetch = FetchType.LAZY)
    @NonNull
    private ComponentVersion componentVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @NonNull
    private Deployment deployment;

   private EnvironmentComponentVersionState(@NonNull Environment environment,
                                             @NonNull Component component,
                                             @NonNull ComponentVersion componentVersion,
                                             @NonNull Deployment deployment) {
        this.id = UUID.randomUUID();
        this.environment = environment;
        this.component = component;
        this.componentVersion = componentVersion;
        this.deployment = deployment;
    }

    public static EnvironmentComponentVersionState fromDeployment(Deployment deployment) {
        return new EnvironmentComponentVersionState(deployment.getEnvironment(),
                deployment.getComponentVersion().getComponent(),
                deployment.getComponentVersion(),
                deployment);
    }

    public void updateVersion(ComponentVersion componentVersion, Deployment deployment){
        this.componentVersion = componentVersion;
        this.deployment = deployment;
    }

    @Override
    public String toString() {
        return """
                EnvironmentComponentVersionState{\
                environment=\
                """ + environment.getName() +
                "component=" + component.getName() +
                "componentVersion=" + componentVersion.getVersionName() +
                "startedAt=" + deployment.getStartedAt() +
                "deployedAt=" + deployment.getEndedAt() +
                '}';
    }
}
