package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentPageNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidDeploymentStateForUpdateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentPageRepository deploymentPageRepository;
    private final SystemRepository systemRepository;
    private final EnvironmentRepository environmentRepository;
    private final SystemService systemService;
    private final EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;

    @SuppressWarnings("java:S107")
    public UUID createDeployment(String externalId,
                                 String versionName,
                                 ZonedDateTime taggedAt,
                                 String versionCtrlUrl,
                                 String commitRef,
                                 ZonedDateTime committedAt,
                                 boolean publishedVersion,
                                 String systemName,
                                 String componentName,
                                 String environmentName,
                                 DeploymentTarget target,
                                 ZonedDateTime startedAt,
                                 String startedBy,
                                 DeploymentUnit deploymentUnit,
                                 Set<Link> links,
                                 Map<String, String> properties,
                                 Set<String> referenceIdentifiers,
                                 String changelogComment,
                                 String changelogComparedToVersion,
                                 Set<String> changelogJiraIssueKeys,
                                 String remedyChangeId) {

        final ch.admin.bit.jeap.deploymentlog.domain.Component component = systemService.retrieveOrCreateComponent(systemName, componentName);
        final Environment environment = retrieveOrCreateEnvironmentByName(environmentName);

        final ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName(versionName)
                .taggedAt(taggedAt)
                .versionControlUrl(versionCtrlUrl)
                .commitRef(commitRef)
                .committedAt(committedAt)
                .publishedVersion(publishedVersion)
                .component(component)
                .deploymentUnit(deploymentUnit)
                .build();

        final Deployment deployment = Deployment.builder()
                .externalId(externalId)
                .startedAt(startedAt)
                .startedBy(startedBy)
                .environment(environment)
                .target(target)
                .componentVersion(componentVersion)
                .links(links)
                .changelog(createChangelog(changelogComment, changelogComparedToVersion, changelogJiraIssueKeys))
                .sequence(getDeploymentSequence(component, environment, versionName))
                .remedyChangeId(remedyChangeId)
                .properties(properties)
                .referenceIdentifiers(referenceIdentifiers)
                .build();

        return deploymentRepository.save(deployment).getId();
    }

    @SuppressWarnings("java:S107")
    public UUID createUndeployment(Deployment previousDeployment,
                                   String externalId,
                                   String systemName,
                                   String componentName,
                                   String environmentName,
                                   ZonedDateTime startedAt,
                                   String startedBy,
                                   String remedyChangeId) {

        final ch.admin.bit.jeap.deploymentlog.domain.Component component = systemService.retrieveOrCreateComponent(systemName, componentName);
        final Environment environment = retrieveOrCreateEnvironmentByName(environmentName);

        final ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName("(undeployed)")
                .taggedAt(previousDeployment.getComponentVersion().getTaggedAt())
                .versionControlUrl(previousDeployment.getComponentVersion().getVersionControlUrl())
                .commitRef(previousDeployment.getComponentVersion().getCommitRef())
                .committedAt(previousDeployment.getComponentVersion().getCommittedAt())
                .publishedVersion(previousDeployment.getComponentVersion().isPublishedVersion())
                .component(component)
                .deploymentUnit(previousDeployment.getComponentVersion().getDeploymentUnit())
                .build();

        final Deployment deployment = Deployment.builder()
                .externalId(externalId)
                .startedAt(startedAt)
                .startedBy(startedBy)
                .environment(environment)
                .target(previousDeployment.getTarget())
                .componentVersion(componentVersion)
                .sequence(DeploymentSequence.UNDEPLOYED)
                .remedyChangeId(remedyChangeId)
                .build();

        return deploymentRepository.save(deployment).getId();
    }

    private DeploymentSequence getDeploymentSequence(ch.admin.bit.jeap.deploymentlog.domain.Component component, Environment environment, String versionName) {
        final Optional<EnvironmentComponentVersionState> currentDeploymentState = environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environment, component);
        if (currentDeploymentState.isEmpty()) {
            return DeploymentSequence.FIRST;
        }
        else {
            if (currentDeploymentState.get().getComponentVersion().getVersionName().equals(versionName)){
                return DeploymentSequence.REPEATED;
            }
        }
        return DeploymentSequence.NEW;
    }

    private Changelog createChangelog(String changelogComment, String changelogComparedToVersion, Set<String> changelogJiraIssueKeys) {
        if (changelogComment != null || changelogJiraIssueKeys != null) {
            return Changelog.builder()
                    .comment(changelogComment)
                    .jiraIssueKeys(changelogJiraIssueKeys)
                    .comparedToVersion(changelogComparedToVersion)
                    .build();
        }
        return null;
    }

    public UUID updateState(String externalId, DeploymentState state, String stateMessage, ZonedDateTime endedAt, Map<String, String> properties) throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {
        final Deployment deployment = retrieveDeploymentByExternalId(externalId);

        deployment.getProperties().putAll(properties);

        switch (state) {
            case SUCCESS -> {
                deployment.success(endedAt, stateMessage);
                if (deployment.getSequence() != DeploymentSequence.UNDEPLOYED) {
                    updateEnvironmentComponentVersionState(deployment);
                }
            }
            case FAILURE -> deployment.failed(endedAt, stateMessage);
            default -> throw new InvalidDeploymentStateForUpdateException(state);
        }

        return deployment.getId();
    }

    @TransactionalReadReplica
    public Optional<Deployment> findByExternalId(String externalId) {
        log.debug("Find the deployment with externalId '{}'", externalId);
        return deploymentRepository.findByExternalId(externalId);
    }

    @TransactionalReadReplica
    public Deployment getDeployment(String externalId) throws DeploymentNotFoundException {
        log.debug("Retrieve the deployment with externalId '{}'", externalId);
        return retrieveDeploymentByExternalId(externalId);
    }

    @TransactionalReadReplica
    public DeploymentPage getDeploymentPage(String externalId) throws DeploymentNotFoundException, DeploymentPageNotFoundException {
        log.debug("Retrieve the deployment page for the deployment with externalId '{}'", externalId);
        return deploymentPageRepository.findDeploymentPageByDeploymentId(retrieveDeploymentByExternalId(externalId).getId()).orElseThrow(() -> new DeploymentPageNotFoundException(externalId));
    }

    private Deployment retrieveDeploymentByExternalId(String externalId) throws DeploymentNotFoundException {
        return deploymentRepository.findByExternalId(externalId).orElseThrow(() -> new DeploymentNotFoundException(externalId));
    }

    private Environment retrieveOrCreateEnvironmentByName(String name) {
        return environmentRepository.findByName(name).orElseGet(() -> environmentRepository.save(new Environment(name)));
    }

    private void updateEnvironmentComponentVersionState(Deployment deployment) {
        final Optional<EnvironmentComponentVersionState> snapshot = environmentComponentVersionStateRepository.findByEnvironmentAndComponent(deployment.getEnvironment(), deployment.getComponentVersion().getComponent());

        if (snapshot.isPresent()) {
            log.info("Updating current EnvironmentComponentVersionState {} with new version '{}' and deployment '{}'", snapshot.get(), deployment.getComponentVersion().getVersionName(), deployment.getEndedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            snapshot.get().updateVersion(deployment.getComponentVersion(), deployment);
        } else {
            final EnvironmentComponentVersionState envSaved = environmentComponentVersionStateRepository.save(EnvironmentComponentVersionState.fromDeployment(deployment));
            log.info("Created new EnvironmentComponentVersionState {}", envSaved);
        }
    }

    @TransactionalReadReplica
    public List<UUID> getMissingDeploymentPages(int limit, long minAgeMinutes, long maxAgeMinutes) {
        ZonedDateTime from = ZonedDateTime.now().minusMinutes(maxAgeMinutes);
        ZonedDateTime to = ZonedDateTime.now().minusMinutes(minAgeMinutes);
        return deploymentRepository.getDeploymentIdsWithMissingOrOutdatedGeneratedPages(limit, from, to);
    }

    @TransactionalReadReplica
    public long countMissingDeploymentPages(int maxAgeDays) {
        ZonedDateTime from = ZonedDateTime.now().minusDays(maxAgeDays);
        return deploymentRepository.countDeploymentsWithMissingOrOutdatedGeneratedPages(from);
    }

    @TransactionalReadReplica
    public List<DeploymentPage> getOutdatedNonProductiveDeploymentPages(Duration minAge, int keepAtLeastPageCount) {
        ZonedDateTime to = ZonedDateTime.now().minus(minAge);
        return systemRepository.getAllSystemIds().stream()
                .flatMap(systemId -> getOutdatedNonProductiveDeploymentPagesForSystem(systemId, keepAtLeastPageCount, to))
                .toList();
    }

    private Stream<DeploymentPage> getOutdatedNonProductiveDeploymentPagesForSystem(UUID systemId, int keepAtLeastPageCount, ZonedDateTime to) {
        List<Environment> envs = environmentRepository.findNonProductiveEnvironmentsForSystemId(systemId);
        return envs.stream()
                .flatMap(env -> getOutdatedPagesForEnvironment(systemId, keepAtLeastPageCount, to, env));
    }

    private Stream<DeploymentPage> getOutdatedPagesForEnvironment(UUID systemId, int keepAtLeastPageCount, ZonedDateTime to, Environment env) {
        List<DeploymentPage> deploymentPages = deploymentPageRepository.getSystemDeploymentPagesForEnvironments(systemId, List.of(env));

        return deploymentPages.stream()
                .skip(keepAtLeastPageCount)
                .filter(page -> page.getDeploymentStateTimestamp().isBefore(to))
                .filter(this::canDeleteDeploymentPage);
    }

    boolean canDeleteDeploymentPage(DeploymentPage page) {
        // A deployment page for a component is only deleted if
        // - It is not the last successful deployment of the component
        // - It is not the last deployment of the component, regardless of state (i.e. if there is no successful deployment yet)
        // - It is not a deployment of the component that is newer than the last successful deployment

        Optional<Deployment> deploymentOptional = deploymentRepository.findById(page.getDeploymentId());
        if (deploymentOptional.isEmpty()) {
            // Deployment doesn't event exist - delete page
            return true;
        }
        Deployment deployment = deploymentOptional.get();

        return !pageShowsLastSuccessfulDeploymentOrNewer(page, deployment) &&
                !pageShowsNewestDeployment(page, deployment);
    }

    private boolean pageShowsLastSuccessfulDeploymentOrNewer(DeploymentPage page, Deployment deployment) {
        Optional<Deployment> lastSuccess = deploymentRepository.getLastSuccessfulDeploymentForComponent(
                deployment.getComponentVersion().getComponent(),
                deployment.getEnvironment());
        if (lastSuccess.isEmpty()) {
            return false;
        }
        Deployment lastSuccessfulDeployment = lastSuccess.get();

        boolean pageIsForLastSuccess = lastSuccessfulDeployment.getId().equals(page.getDeploymentId());
        boolean pageIsForDeploymentNewerThanLastSuccess = deployment.getStartedAt().isAfter(lastSuccessfulDeployment.getStartedAt());

        return pageIsForLastSuccess || pageIsForDeploymentNewerThanLastSuccess;
    }

    private boolean pageShowsNewestDeployment(DeploymentPage page, Deployment deployment) {
        Optional<Deployment> lastDeployment = deploymentRepository.getLastDeploymentForComponent(
                deployment.getComponentVersion().getComponent(),
                deployment.getEnvironment());
        // There is a last deployment for the component, and this is the page for it - keep it
        return lastDeployment.isPresent() && lastDeployment.get().getId().equals(page.getDeploymentId());
    }

    @TransactionalReadReplica
    public Set<SystemEnv> getSystemAndEnvsForDeploymentIds(Set<UUID> deploymentIds) {
        return deploymentIds.stream()
                .map(deploymentRepository::getById)
                .map(SystemEnv::from)
                .collect(toSet());
    }

    @TransactionalReadReplica
    public Deployment getLastDeploymentForComponent(ch.admin.bit.jeap.deploymentlog.domain.Component component, Environment env){
        return deploymentRepository.getLastDeploymentForComponent(component, env)
            .orElseThrow(() -> new IllegalStateException(String.format("Last deployment not found for component '%s' in environment '%s'", component.getName(), env.getName())));
    }

}
