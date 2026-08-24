package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Flow {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FlowType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlowState state;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime bornAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @ToString.Exclude
    private ComponentVersion componentVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Environment finalDeploymentEnvironment;

    @ManyToOne(fetch = FetchType.LAZY)
    private Flow abortedBy;

    @OneToMany(mappedBy = "flow", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Deployment> deployments = new ArrayList<>();

    private Flow(@NonNull FlowType type,
                 @NonNull Deployment initialDeployment,
                 @NonNull Environment finalDeploymentEnvironment) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.state = FlowState.OPEN;
        this.bornAt = initialDeployment.getStartedAt();
        this.componentVersion = initialDeployment.getComponentVersion();
        this.finalDeploymentEnvironment = finalDeploymentEnvironment;
        add(initialDeployment);
    }

    public static Flow start(@NonNull FlowType type,
                             @NonNull Deployment initialDeployment,
                             @NonNull Environment finalDeploymentEnvironment) {
        return new Flow(type, initialDeployment, finalDeploymentEnvironment);
    }

    public void add(@NonNull Deployment deployment) {
        if (state != FlowState.OPEN) {
            throw new IllegalStateException("Cannot add a deployment to terminal flow " + id);
        }
        if (!Objects.equals(componentVersion.getComponent().getId(),
                deployment.getComponentVersion().getComponent().getId())
                || !componentVersion.getVersionName()
                .equals(deployment.getComponentVersion().getVersionName())) {
            throw new IllegalArgumentException("Deployment does not belong to flow's business component version");
        }
        if (!deployments.contains(deployment)) {
            deployments.add(deployment);
            deployment.assignTo(this);
        }
    }

    public boolean closeIfTargetReached(@NonNull Deployment deployment) {
        if (state != FlowState.OPEN) {
            return false;
        }
        if (deployments.stream().noneMatch(flowDeployment ->
                Objects.equals(flowDeployment.getId(), deployment.getId()))) {
            return false;
        }
        if (deployment.getState() != DeploymentState.SUCCESS
                || !Objects.equals(finalDeploymentEnvironment.getId(), deployment.getEnvironment().getId())) {
            return false;
        }
        state = FlowState.CLOSED;
        return true;
    }

    public boolean abortBy(@NonNull Flow abortingFlow) {
        if (state != FlowState.OPEN) {
            return false;
        }
        if (Objects.equals(id, abortingFlow.getId())) {
            throw new IllegalArgumentException("A flow cannot abort itself");
        }
        if (!Objects.equals(componentVersion.getComponent().getId(),
                abortingFlow.getComponentVersion().getComponent().getId())) {
            throw new IllegalArgumentException("Only a flow of the same component can abort this flow");
        }
        ZonedDateTime committedAt = componentVersion.getCommittedAt();
        ZonedDateTime abortingCommittedAt = abortingFlow.getComponentVersion().getCommittedAt();
        if (committedAt == null || abortingCommittedAt == null) {
            throw new IllegalStateException("Cannot compare flow versions without componentVersion.committedAt");
        }
        if (!committedAt.isBefore(abortingCommittedAt)) {
            throw new IllegalArgumentException("Only a strictly newer component version can abort this flow");
        }
        state = FlowState.ABORTED;
        abortedBy = abortingFlow;
        return true;
    }

    public List<Deployment> getDeployments() {
        return deployments.stream()
                .sorted(Comparator.comparing(Deployment::getStartedAt).thenComparing(Deployment::getId))
                .toList();
    }
}
