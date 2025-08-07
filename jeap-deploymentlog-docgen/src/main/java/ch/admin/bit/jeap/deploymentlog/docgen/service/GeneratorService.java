package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGeneratorProperties;
import ch.admin.bit.jeap.deploymentlog.docgen.model.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeneratorService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EnvironmentRepository environmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final SystemPageRepository systemPageRepository;
    private final EnvironmentHistoryPageRepository environmentHistoryPageRepository;
    private final DeploymentListPageRepository deploymentListPageRepository;
    private final DeploymentPageRepository deploymentPageRepository;
    private final EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ReferenceRepository referenceRepository;
    private final DocumentationGeneratorProperties documentationGeneratorProperties;

    public SystemPageDto createSystemPageDto(System system) {

        List<Environment> environmentList = environmentRepository.findEnvironmentsForSystem(system);

        List<String> environmentNamesList = environmentList.stream()
                .map(Environment::getName)
                .toList();

        List<Component> componentList =
                environmentComponentVersionStateRepository.findComponentsBySystem(system);

        List<ComponentDto> componentDtos = componentList.stream()
                .map(component -> ComponentDto.builder()
                        .componentName(component.getName())
                        .componentEnvDtoList(createComponentEnvDtoList(component, environmentList))
                        .build())
                .sorted(Comparator.comparing(ComponentDto::getComponentName))
                .toList();
        return SystemPageDto.builder()
                .name(system.getName())
                .environmentNamesArrayList(environmentNamesList)
                .componentList(componentDtos)
                .build();
    }

    private List<ComponentEnvDto> createComponentEnvDtoList(Component component,
                                                            List<Environment> environmentList) {
        List<ComponentEnvDto> componentEnvDtoList = new ArrayList<>();

        environmentList.forEach(environment -> {
            Optional<EnvironmentComponentVersionState> envCompOpt = environmentComponentVersionStateRepository.findLastByEnvironmentAndComponentAndDeploymentTypeCode(environment, component);
            if (envCompOpt.isPresent()) {
                EnvironmentComponentVersionState envCompVersionState = envCompOpt.get();
                ComponentEnvDto componentEnvDto = ComponentEnvDto.builder()
                        .versionNumber(envCompVersionState.getComponentVersion().getVersionNumber())
                        .versionName(envCompVersionState.getComponentVersion().getVersionName())
                        .versionControlUrl(envCompVersionState.getComponentVersion().getVersionControlUrl())
                        .deployedAt(getStartedAtFormatted(envCompVersionState.getDeployment()))
                        .deploymentLetterUrl(createDeploymentLetterLink(envCompVersionState.getDeployment()))
                        .color(Color.NONE)
                        .developmentEnvironment(environment.isDevelopment())
                        .build();
                componentEnvDtoList.add(componentEnvDto);
            } else {
                ComponentEnvDto componentEnvDto = ComponentEnvDto.builder()
                        .color(Color.NONE)
                        .build();
                componentEnvDtoList.add(componentEnvDto);
            }
        });

        //Set the Color 1: Check if all Versions are equal
        List<ComponentEnvDto> componentNonDevelopmentEnvDtoList = componentEnvDtoList.stream()
                .filter(env -> !env.isDevelopmentEnvironment())
                .toList();
        if (verifyAllVersionAreEquals(componentNonDevelopmentEnvDtoList)) {
            componentNonDevelopmentEnvDtoList.forEach(dto -> dto.setColor(Color.ALL_IDENTICAL));
            return componentEnvDtoList;
        }

        //Set the Color 2: Check if the next Stage is missing or has a higher version
        for (int index = 0; index < componentNonDevelopmentEnvDtoList.size(); index++) {
            ComponentEnvDto actualComponentEnvDto = componentNonDevelopmentEnvDtoList.get(index);
            try {
                ComponentEnvDto nextcomponentEnvDto = componentNonDevelopmentEnvDtoList.get(index + 1);
                Color detectedColor = verifyNextStage(actualComponentEnvDto, nextcomponentEnvDto);
                actualComponentEnvDto.setColor(detectedColor);
            } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
                break;
            }
        }

        return componentEnvDtoList;
    }

    private String getStartedAtFormatted(Deployment deployment) {
        return deployment.getStartedAt()
                .withZoneSameInstant(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER);
    }

    private String getEndedAtFormatted(Deployment deployment) {
        return deployment.getEndedAt() == null ? "" : deployment.getEndedAt()
                .withZoneSameInstant(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER);
    }

    /**
     * Verify if all versions are equal
     *
     * @param componentEnvDtoList List of all Enviroments of a Component
     * @return true if all Version are equal, false if not
     */
    private boolean verifyAllVersionAreEquals(List<ComponentEnvDto> componentEnvDtoList) {
        if (componentEnvDtoList.stream().allMatch(dto -> dto.getVersionNumber() == null)) {
            return false;
        }

        return componentEnvDtoList.stream()
                       .map(ComponentEnvDto::getVersionNumber)
                       .distinct()
                       .count() <= 1;
    }

    /**
     * Returns the color depending on the Version of the next Stage.
     *
     * @param actualStage as ComponentEnvDto
     * @param nextStage   as ComponentEnvDto
     * @return Color.HIGHER_THAN_NEXT_STAGE when next Stage Version is lower
     * Color.MISSES_NEXT_STAGE when the Next stage is missing
     */
    private Color verifyNextStage(ComponentEnvDto actualStage, ComponentEnvDto nextStage) {
        if ((actualStage.getVersionNumber() == null) && (nextStage.getVersionNumber() == null)) {
            return Color.NONE;
        }

        if ((nextStage == null) || (nextStage.getVersionNumber() == null)) {
            return Color.MISSES_NEXT_STAGE;
        }

        if (actualStage.getVersionNumber() == null) {
            return Color.NONE;
        }

        if (isNextVersionLower(actualStage.getVersionNumber(), nextStage.getVersionNumber())) {
            return Color.HIGHER_THAN_NEXT_STAGE;
        }
        return Color.NONE;
    }

    /**
     * Checks if the nextVersion lower than the actual Version
     *
     * @param actualVersion as String
     * @param nextVersion   as String
     * @return true, if nextVersion is lower
     */
    private boolean isNextVersionLower(VersionNumber actualVersion, VersionNumber nextVersion) {
        return nextVersion.compareTo(actualVersion) < 0;
    }

    /**
     * Return a String that can be used as Link in the Template
     */
    private String createDeploymentLetterLink(Deployment deployment) {
        String componentName = deployment.getComponentVersion().getComponent().getName();
        String startedAtFormatted = getStartedAtFormatted(deployment);
        String deploymentLetterLink = startedAtFormatted + " " + componentName + " (" + deployment.getEnvironment().getName() + ")";
        if (DeploymentSequence.UNDEPLOYED.equals(deployment.getSequence())) {
            deploymentLetterLink += DocumentationGenerator.UNDEPLOY_PAGE_SUFFIX;
        }
        return deploymentLetterLink;
    }

    public List<Environment> getEnvironmentsForSystem(System system) {
        return environmentRepository.findEnvironmentsForSystem(system);
    }

    public List<DeploymentDto> getDeploymentsForSystemAndEnv(System system, Environment environment, int maxShow) {
        List<Deployment> deploymentList = deploymentRepository.findDeploymentForSystemAndEnvLimited(system, environment, maxShow);
        return deploymentList.stream().map(deployment -> DeploymentDto.builder()
                        .deploymentId(deployment.getId().toString())
                        .component(deployment.getComponentVersion().getComponent().getName())
                        .startedAt(getStartedAtFormatted(deployment))
                        .duration(getDurationFormatted(deployment))
                        .version(deployment.getComponentVersion().getVersionName())
                        .versionControlUrl(deployment.getComponentVersion().getVersionControlUrl())
                        .deploymentLetterLink(createDeploymentLetterLink(deployment))
                        .startedBy(deployment.getStartedBy())
                        .state(deployment.getState().toString())
                        .deploymentTypes(getDeploymentTypesAsString(deployment.getDeploymentTypes()))
                        .build())
                .toList();
    }

    public List<DeploymentDto> getDeploymentsForEnv(Environment environment, ZonedDateTime minStartedAt, int maxShow) {
        List<Deployment> deploymentList = deploymentRepository.findDeploymentForEnvLimited(environment, minStartedAt, maxShow);
        return deploymentList.stream().map(deployment -> DeploymentDto.builder()
                        .deploymentId(deployment.getId().toString())
                        .component(deployment.getComponentVersion().getComponent().getName())
                        .system(deployment.getComponentVersion().getComponent().getSystem().getName())
                        .startedAt(getStartedAtFormatted(deployment))
                        .duration(getDurationFormatted(deployment))
                        .version(deployment.getComponentVersion().getVersionName())
                        .versionControlUrl(deployment.getComponentVersion().getVersionControlUrl())
                        .deploymentLetterLink(createDeploymentLetterLink(deployment))
                        .startedBy(deployment.getStartedBy())
                        .state(deployment.getState().toString())
                        .deploymentTypes(getDeploymentTypesAsString(deployment.getDeploymentTypes()))
                        .build())
                .toList();
    }

    public List<Integer> getDeploymentsYearsForSystemAndEnv(System system, Environment environment) {
        return deploymentRepository.findAllDeploymentsYearsForSystemAndEnv(system, environment);
    }

    public List<DeploymentLetterPageDto> getDeploymentsForYearForSystemAndEnv(int year, System system, Environment environment) {
        List<Deployment> deploymentList = deploymentRepository.findAllDeploymentForSystemAndEnv(system, environment);
        return deploymentList.stream()
                .filter(deployment -> deployment.getStartedAt().getYear() == year)
                .map(this::createDeploymentLetterPageDto)
                .toList();
    }

    public DeploymentLetterPageDto createDeploymentLetterPageDto(Deployment deployment) {
        final DeploymentLetterPageDto.DeploymentLetterPageDtoBuilder dtoBuilder = DeploymentLetterPageDto.builder()
                .deploymentId(deployment.getId().toString())
                .externalId(deployment.getExternalId())
                .environmentName(deployment.getEnvironment().getName())
                .startedAt(getStartedAtFormatted(deployment))
                .endedAt(getEndedAtFormatted(deployment))
                .startedBy(deployment.getStartedBy())
                .state(deployment.getState().toString())
                .stateMessage(emptyStringForNull(deployment.getStateMessage()))
                .duration(getDurationFormatted(deployment))
                .componentName(deployment.getComponentVersion().getComponent().getName())
                .version(deployment.getComponentVersion().getVersionName())
                .versionControlUrl(deployment.getComponentVersion().getVersionControlUrl())
                .links(getLinks(deployment))
                .properties(new TreeMap<>(deployment.getProperties()))
                .changeComment(getChangeLogComment(deployment))
                .changeComparedToVersion(getChangeLogComparedToVersion(deployment))
                .changeJiraIssueKeys(getChangeLogJiraIssueKeys(deployment))
                .deploymentStateTimestamp(deployment.getLastModified())
                .deploymentUnitType(deployment.getComponentVersion().getDeploymentUnit().getType().getLabel())
                .deploymentUnitCoordinates(formatCoordinates(deployment))
                .deploymentUnitRepositoryUrl(deployment.getComponentVersion().getDeploymentUnit().getArtifactRepositoryUrl())
                .sequence(deployment.getSequence().getLabel())
                .remedyChangeId(deployment.getRemedyChangeId())
                .remedyChangeLink(getRemedyChangeLink(deployment.getRemedyChangeId()))
                .buildJobLinks(getBuildJobLinks(deployment.getComponentVersion().getDeploymentUnit(), deployment.getReferenceIdentifiers()))
                .deploymentTypes(getDeploymentTypesAsString(deployment.getDeploymentTypes()));

        final DeploymentTarget target = deployment.getTarget();
        if (target != null) {
            dtoBuilder
                    .targetType(target.getType())
                    .targetUrl(target.getUrl())
                    .targetDetails(target.getDetails());
        }

        return dtoBuilder.build();
    }

    private static String getDeploymentTypesAsString(Set<DeploymentType> deploymentTypes) {
        if (deploymentTypes.isEmpty()) {
            return "-";
        }
        return String.join(", ", deploymentTypes.stream().map(Enum::name).toList());
    }

    public DeploymentLetterPageDto createUndeploymentLetterPageDto(Deployment deployment) {
        final DeploymentLetterPageDto.DeploymentLetterPageDtoBuilder dtoBuilder = DeploymentLetterPageDto.builder()
                .deploymentId(deployment.getId().toString())
                .externalId(deployment.getExternalId())
                .environmentName(deployment.getEnvironment().getName())
                .startedAt(getStartedAtFormatted(deployment))
                .endedAt(getEndedAtFormatted(deployment))
                .startedBy(deployment.getStartedBy())
                .state(deployment.getState().toString())
                .stateMessage(emptyStringForNull(deployment.getStateMessage()))
                .duration(getDurationFormatted(deployment))
                .componentName(deployment.getComponentVersion().getComponent().getName())
                .deploymentStateTimestamp(deployment.getLastModified())
                .deploymentUnitType(deployment.getComponentVersion().getDeploymentUnit().getType().getLabel())
                .deploymentUnitCoordinates(formatCoordinates(deployment))
                .deploymentUnitRepositoryUrl(deployment.getComponentVersion().getDeploymentUnit().getArtifactRepositoryUrl())
                .sequence(deployment.getSequence().getLabel())
                .remedyChangeId(deployment.getRemedyChangeId())
                .remedyChangeLink(getRemedyChangeLink(deployment.getRemedyChangeId()))
                .buildJobLinks(getBuildJobLinks(deployment.getComponentVersion().getDeploymentUnit(), deployment.getReferenceIdentifiers()));

        return dtoBuilder.build();
    }

    private Set<String> getBuildJobLinks(DeploymentUnit deploymentUnit, Set<String> referenceIdentifiers) {
        Set<String> buildJobLinks = new HashSet<>();

        // First, attempt to find the build job links by matching reference IDs to known references
        if (referenceIdentifiers != null) {
            referenceIdentifiers.forEach(refIdOnDeployment ->
                    buildJobLinks.addAll(referenceRepository.findAllByReferenceIdentifier(refIdOnDeployment).stream()
                            .filter(ref -> ref.getType() == ReferenceType.BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION)
                            .map(Reference::getUri)
                            .collect(Collectors.toSet())));
        }

        // Second, attempt to find build job links by deployment unit coordinates
        if (!StringUtils.hasText(deploymentUnit.getCoordinates())) {
            return buildJobLinks;
        }
        buildJobLinks.addAll(artifactVersionRepository.findAllByCoordinates(deploymentUnit.getCoordinates()).stream()
                .map(ArtifactVersion::getBuildJobLink)
                .collect(Collectors.toSet()));
        return buildJobLinks;
    }

    private String getRemedyChangeLink(String remedyChangeId) {
        if (remedyChangeId == null) {
            return null;
        }
        return documentationGeneratorProperties.getRemedyChangeLinkRootUrlWithTrailingSlash() + remedyChangeId;
    }

    private String formatCoordinates(Deployment deployment) {
        String coordinates = deployment.getComponentVersion().getDeploymentUnit().getCoordinates();
        return coordinates.isEmpty() ? "Link" : coordinates;
    }

    private static String emptyStringForNull(String str) {
        return str == null ? "" : str;
    }

    private String getDurationFormatted(Deployment deployment) {
        if (deployment.getEndedAt() != null) {
            Duration duration = Duration.between(deployment.getStartedAt(), deployment.getEndedAt());
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
        } else {
            return "";
        }
    }

    private Set<String> getChangeLogJiraIssueKeys(Deployment deployment) {
        Changelog changelog = deployment.getChangelog();
        if (changelog != null) {
            return new HashSet<>(changelog.getJiraIssueKeys());
        } else return Set.of();
    }

    private String getChangeLogComparedToVersion(Deployment deployment) {
        Changelog changelog = deployment.getChangelog();
        if (changelog != null) {
            return changelog.getComparedToVersion();
        } else return "";
    }

    private String getChangeLogComment(Deployment deployment) {
        Changelog changelog = deployment.getChangelog();
        if (changelog != null) {
            return changelog.getComment();
        } else return "";
    }

    private List<LinkDto> getLinks(Deployment deployment) {
        return deployment.getLinks().stream()
                .sorted(Comparator.comparing(Link::getLabel))
                .map(link -> LinkDto.builder()
                        .linkLabel(link.getLabel())
                        .linkUrl(link.getUrl())
                        .build())
                .toList();
    }

    /**
     * Persists the generated SystemPage or update the Timestamp
     */
    public void persistSystemPage(System system, String pageId) {
        Optional<SystemPage> systemPageOpt = systemPageRepository.findSystemPageBySystemId(system.getId());
        systemPageOpt.ifPresentOrElse(systemPage -> {
            systemPage.setLastUpdatedAt(ZonedDateTime.now());
            systemPage.setSystemPageId(pageId);
            systemPageRepository.save(systemPage);
        }, () -> {
            SystemPage systemPage = SystemPage.builder()
                    .id(UUID.randomUUID())
                    .systemId(system.getId())
                    .systemPageId(pageId)
                    .lastUpdatedAt(ZonedDateTime.now())
                    .build();
            systemPageRepository.save(systemPage);
        });
    }

    public void persistDeploymentHistoryPage(System system, Environment environment, String pageId) {
        Optional<EnvironmentHistoryPage> environmentHistoryPageOpt = environmentHistoryPageRepository
                .findEnvironmentHistoryPageBySystemIdAndEnvironmentId(system.getId(), environment.getId());
        environmentHistoryPageOpt.ifPresentOrElse(environmentHistoryPage -> {
            environmentHistoryPage.setLastUpdatedAt(ZonedDateTime.now());
            environmentHistoryPage.setPageId(pageId);
            environmentHistoryPageRepository.save(environmentHistoryPage);
        }, () -> {
            EnvironmentHistoryPage environmentHistoryPage = EnvironmentHistoryPage.builder()
                    .id(UUID.randomUUID())
                    .systemId(system.getId())
                    .environmentId(environment.getId())
                    .pageId(pageId)
                    .lastUpdatedAt(ZonedDateTime.now())
                    .build();
            environmentHistoryPageRepository.save(environmentHistoryPage);
        });
    }

    public void persistDeploymentListPage(System system, Environment environment, String pageId, int year) {
        Optional<DeploymentListPage> deploymentListPageOpt = deploymentListPageRepository
                .findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(system.getId(), environment.getId(), year);

        deploymentListPageOpt.ifPresentOrElse(deploymentListPage -> {
            deploymentListPage.setLastUpdatedAt(ZonedDateTime.now());
            deploymentListPage.setPageId(pageId);
            deploymentListPageRepository.save(deploymentListPage);
        }, () -> {
            DeploymentListPage deploymentListPage = DeploymentListPage.builder()
                    .id(UUID.randomUUID())
                    .systemId(system.getId())
                    .environmentId(environment.getId())
                    .pageId(pageId)
                    .lastUpdatedAt(ZonedDateTime.now())
                    .year(year)
                    .build();
            deploymentListPageRepository.save(deploymentListPage);
        });
    }

    public void persistDeploymentPage(UUID deploymentId, String pageId, ZonedDateTime deploymentStateTimestamp) {
        Optional<DeploymentPage> deploymentPageOpt = deploymentPageRepository.findDeploymentPageByDeploymentId(deploymentId);

        deploymentPageOpt.ifPresentOrElse(deploymentPage -> {
            deploymentPage.setLastUpdatedAt(ZonedDateTime.now());
            deploymentPage.setDeploymentStateTimestamp(deploymentStateTimestamp);
            deploymentPage.setPageId(pageId);
            deploymentPageRepository.save(deploymentPage);
        }, () -> {
            DeploymentPage deploymentPage = DeploymentPage.builder()
                    .id(UUID.randomUUID())
                    .deploymentId(deploymentId)
                    .pageId(pageId)
                    .lastUpdatedAt(ZonedDateTime.now())
                    .deploymentStateTimestamp(deploymentStateTimestamp)
                    .build();
            deploymentPageRepository.save(deploymentPage);
        });
    }
}
