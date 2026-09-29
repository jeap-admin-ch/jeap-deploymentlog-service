package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class DeploymentStagingService {
    private final VersionDeploymentRepository repository;
    private final StagingProperties properties;
    private final StagingEnvironmentResolver stageResolver;

    public void prepare(Deployment deployment, Collection<String> finalEnvironments) {
        deployment.setFinalDeploymentEnvironments(finalEnvironments == null ? List.of() : finalEnvironments.stream()
                .map(name -> name.trim().toUpperCase(Locale.ROOT)).toList());
        if (!properties.isEnabled() || deployment.getSequence() == DeploymentSequence.UNDEPLOYED
                || !deployment.getDeploymentTypes().contains(DeploymentType.CODE)) {
            return;
        }
        List<Environment> stages = stageResolver.relevantEnvironments();
        int stageIndex = -1;
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getId().equals(deployment.getEnvironment().getId())) {
                stageIndex = i;
            }
        }
        if (stageIndex < 0) {
            return;
        }
        repository.lockComponent(deployment.getComponentVersion().getComponent().getId());
        deployment.classify(classify(deployment, stages, stageIndex));
    }

    private DeploymentStagingType classify(Deployment deployment, List<Environment> stages, int stageIndex) {
        String version = deployment.getComponentVersion().getVersionName();
        String environment = deployment.getEnvironment().getName();
        List<StagingHistoryEntry> history = repository.history(deployment.getComponentVersion().getComponent().getId())
                .stream().filter(entry -> !entry.deploymentId().equals(deployment.getId()))
                .filter(entry -> !entry.startedAt().isAfter(deployment.getStartedAt())).toList();
        List<StagingHistoryEntry> stageHistory = history.stream()
                .filter(entry -> entry.environment().equals(environment)).toList();
        boolean currentlyDeployed = stageHistory.stream().filter(entry -> succeededBefore(entry, deployment))
                .max(Comparator.comparing(StagingHistoryEntry::endedAt).thenComparing(StagingHistoryEntry::deploymentId))
                .filter(entry -> !entry.undeployment() && entry.version().equals(version)).isPresent();
        if (currentlyDeployed) {
            return DeploymentStagingType.RETRY;
        }
        if (stageHistory.stream().anyMatch(entry -> entry.version().equals(version)
                && !entry.undeployment() && succeededBefore(entry, deployment))) {
            return DeploymentStagingType.ROLLBACK;
        }
        // An unsuccessful or still running attempt is retried, but is never evidence of successful staging.
        if (stageHistory.stream().anyMatch(entry -> entry.version().equals(version) && !entry.undeployment())) {
            return DeploymentStagingType.RETRY;
        }
        if (stageIndex == 0 || history.stream().anyMatch(entry -> entry.version().equals(version)
                && entry.environment().equals(stages.get(stageIndex - 1).getName())
                && !entry.undeployment() && succeededBefore(entry, deployment))) {
            return DeploymentStagingType.NEW;
        }
        return DeploymentStagingType.AD_HOC;
    }

    private boolean succeededBefore(StagingHistoryEntry entry, Deployment deployment) {
        return entry.state() == DeploymentState.SUCCESS && entry.endedAt() != null
                && !entry.endedAt().isAfter(deployment.getStartedAt());
    }

}
