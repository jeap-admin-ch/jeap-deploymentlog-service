package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Value
@Builder
@Schema(description = "Persisted deployment information. Internal database identifiers are never exposed.")
public class DeploymentReadDto {

    String externalId;
    ZonedDateTime startedAt;
    ZonedDateTime endedAt;
    DeploymentState state;
    String stateMessage;
    DeploymentSequence sequence;
    Set<DeploymentType> deploymentTypes;
    DeploymentStagingType stagingType;
    Set<String> finalDeploymentEnvironments;
    String startedBy;
    DeploymentEnvironmentDto environment;
    DeploymentComponentVersionDto componentVersion;
    DeploymentTarget target;
    Set<LinkDto> links;
    Map<String, String> properties;
    Set<String> referenceIdentifiers;
    DeploymentChangelogDto changelog;
    String remedyChangeId;

    public static DeploymentReadDto of(Deployment deployment) {
        return DeploymentReadDto.builder()
                .externalId(deployment.getExternalId())
                .startedAt(deployment.getStartedAt())
                .endedAt(deployment.getEndedAt())
                .state(deployment.getState())
                .stateMessage(deployment.getStateMessage())
                .sequence(deployment.getSequence())
                .deploymentTypes(deployment.getDeploymentTypes() == null
                        ? Set.of() : Set.copyOf(deployment.getDeploymentTypes()))
                .stagingType(deployment.getStagingType())
                .finalDeploymentEnvironments(new TreeSet<>(deployment.getFinalDeploymentEnvironments()))
                .startedBy(deployment.getStartedBy())
                .environment(DeploymentEnvironmentDto.of(deployment.getEnvironment()))
                .componentVersion(DeploymentComponentVersionDto.of(deployment.getComponentVersion()))
                .target(deployment.getTarget())
                .links(LinkDto.allOf(deployment.getLinks()))
                .properties(new TreeMap<>(deployment.getProperties()))
                .referenceIdentifiers(deployment.getReferenceIdentifiers() == null
                        ? Set.of() : new TreeSet<>(deployment.getReferenceIdentifiers()))
                .changelog(DeploymentChangelogDto.of(deployment.getChangelog()))
                .remedyChangeId(deployment.getRemedyChangeId())
                .build();
    }

    @Value
    public static class DeploymentEnvironmentDto {
        String name;
        int stagingOrder;
        boolean productive;
        boolean development;

        static DeploymentEnvironmentDto of(Environment environment) {
            return new DeploymentEnvironmentDto(environment.getName(), environment.getStagingOrder(),
                    environment.isProductive(), environment.isDevelopment());
        }
    }

    @Value
    public static class DeploymentSystemDto {
        String name;
    }

    @Value
    public static class DeploymentComponentDto {
        String name;
        boolean active;
        DeploymentSystemDto system;

        static DeploymentComponentDto of(Component component) {
            return new DeploymentComponentDto(component.getName(), component.isActive(),
                    new DeploymentSystemDto(component.getSystem().getName()));
        }
    }

    @Value
    public static class DeploymentComponentVersionDto {
        String versionName;
        ZonedDateTime taggedAt;
        VersionNumber versionNumber;
        String versionControlUrl;
        String commitRef;
        ZonedDateTime committedAt;
        boolean publishedVersion;
        DeploymentUnit deploymentUnit;
        DeploymentComponentDto component;

        static DeploymentComponentVersionDto of(ComponentVersion componentVersion) {
            return new DeploymentComponentVersionDto(
                    componentVersion.getVersionName(), componentVersion.getTaggedAt(),
                    componentVersion.getVersionNumber(), componentVersion.getVersionControlUrl(),
                    componentVersion.getCommitRef(), componentVersion.getCommittedAt(),
                    componentVersion.isPublishedVersion(), componentVersion.getDeploymentUnit(),
                    DeploymentComponentDto.of(componentVersion.getComponent()));
        }
    }

    @Value
    public static class DeploymentChangelogDto {
        String comparedToVersion;
        String comment;
        Set<String> jiraIssueKeys;

        static DeploymentChangelogDto of(Changelog changelog) {
            if (changelog == null) {
                return null;
            }
            Set<String> normalizedKeys = new TreeSet<>();
            if (changelog.getJiraIssueKeys() != null) {
                changelog.getJiraIssueKeys().stream()
                        .filter(Objects::nonNull)
                        .map(key -> key.trim().toUpperCase(Locale.ROOT))
                        .forEach(normalizedKeys::add);
            }
            return new DeploymentChangelogDto(changelog.getComparedToVersion(), changelog.getComment(), normalizedKeys);
        }
    }
}
