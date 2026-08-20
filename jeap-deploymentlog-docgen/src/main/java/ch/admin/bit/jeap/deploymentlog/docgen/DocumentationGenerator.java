package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.*;
import ch.admin.bit.jeap.deploymentlog.docgen.service.GeneratorService;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentationGenerator {

    public static final String UNDEPLOY_PAGE_SUFFIX = " (Undeploy)";
    private static final String DEPLOYMENT_HISTORY_OVERVIEW_ROOT_PAGE_TITLE = "_Deployment History Overview";
    private static final DateTimeFormatter MIN_STARTED_AT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ConfluenceAdapter confluenceAdapter;
    private final JiraAdapter jiraAdapter;
    private final TemplateRenderer templateRenderer;
    private final DocumentationGeneratorConfluenceProperties props;
    private final SystemRepository systemRepository;
    private final EnvironmentRepository environmentRepository;
    private final GeneratorService generatorService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentPageRepository deploymentPageRepository;
    private final SystemPageRepository systemPageRepository;
    private final EnvironmentHistoryPageRepository environmentHistoryPageRepository;
    private final DeploymentListPageRepository deploymentListPageRepository;

    @Timed("deploymentlog_generate_deployment_page")
    @Transactional
    public GeneratedDeploymentPageDto generateDeploymentPages(UUID deploymentId) {
        String rootPageId = props.getRootPageId();

        Deployment deployment = deploymentRepository.getById(deploymentId);
        Environment environment = deployment.getEnvironment();
        System system = deployment.getComponentVersion().getComponent().getSystem();

        String systemPageId = generateSystemPage(rootPageId, system);
        String deploymentListParentPageId = generateDeploymentHistoryPageForEnvironment(systemPageId, environment, system);
        int year = deployment.getStartedAt().getYear();
        String deploymentLetterParentPageId = generateDeploymentListPage(deploymentListParentPageId, environment, system, year);
        generateDeploymentHistoryOverviewPageForEnvironment(rootPageId, environment);
        if (deployment.getSequence() != DeploymentSequence.UNDEPLOYED) {
            return generateDeploymentLetter(deploymentLetterParentPageId, deployment);
        } else {
            return generateUndeploymentLetter(deploymentLetterParentPageId, deployment);
        }
    }

    @Transactional
    public void migrateSystem(System system) {
        log.info("Retrieve the deployments for the system '{}'", system.getName());
        List<DeploymentPageQueryResult> deployments = deploymentPageRepository.getDeploymentPagesForSystem(system.getId());

        String rootPageId = props.getRootPageId();

        Optional<SystemPage> existingSystemPage = systemPageRepository.findSystemPageBySystemId(system.getId());
        String existingSystemPageId = null;
        if (existingSystemPage.isPresent()) {
            existingSystemPageId = existingSystemPage.get().getSystemPageId();
        }

        log.info("Generating pages of system '{}'", system.getName());
        String systemPageId = generateSystemPage(rootPageId, system);

        log.info("Moving {} deployment pages of system '{}'", deployments.size(), system.getName());
        moveDeploymentPages(system, systemPageId, deployments);

        Iterable<Environment> environmentList = environmentRepository.findAll();
        environmentList.forEach(environment -> generateDeploymentHistoryOverviewPageForEnvironment(rootPageId, environment));

        if (existingSystemPageId != null) {
            log.info("Deleting old system page with id '{}' and all child pages", existingSystemPageId);
            confluenceAdapter.deletePageAndChildPages(existingSystemPageId);
        }
    }

    @Transactional
    public void mergeSystems(System system, System oldSystem) {
        log.info("Retrieve the deployments for the system '{}' to merge into '{}'", oldSystem.getName(), system.getName());
        List<DeploymentPageQueryResult> deployments = deploymentPageRepository.getDeploymentPagesForSystem(oldSystem.getId());

        String rootPageId = props.getRootPageId();

        final SystemPage existingSystemPage = systemPageRepository.findSystemPageBySystemId(system.getId()).orElseThrow(() -> new IllegalStateException("SystemPage for " + system.getName() + " not found"));

        log.info("Moving {} deployment pages of system '{}' to system '{}'", deployments.size(), oldSystem.getName(), system.getName());
        moveDeploymentPages(system, existingSystemPage.getSystemPageId(), deployments);

        Iterable<Environment> environmentList = environmentRepository.findAll();
        environmentList.forEach(environment -> generateDeploymentHistoryOverviewPageForEnvironment(rootPageId, environment));

        Optional<SystemPage> existingOldSystemPage = systemPageRepository.findSystemPageBySystemId(oldSystem.getId());
        if (existingOldSystemPage.isPresent()) {
            String existingOldSystemPageId = existingOldSystemPage.get().getSystemPageId();
            log.info("Deleting old system page with id '{}' and all child pages", existingOldSystemPageId);
            confluenceAdapter.deletePageAndChildPages(existingOldSystemPageId);
            systemPageRepository.deleteSystemPage(existingOldSystemPage.get());
        }

        environmentHistoryPageRepository.deleteEnvironmentHistoryPageBySystemId(oldSystem.getId());
        deploymentListPageRepository.deleteDeploymentListPageBySystemId(oldSystem.getId());
    }

    private void moveDeploymentPages(System system, String systemPageId, List<DeploymentPageQueryResult> deployments) {
        Map<Environment, String> environmentPagesByEnvironment = new HashMap<>();
        Map<String, String> environmentPagesByEnvironmentAndYear = new HashMap<>();

        for (DeploymentPageQueryResult deploymentInfo : deployments) {
            Deployment deployment = deploymentRepository.getById(deploymentInfo.id());
            Environment environment = deployment.getEnvironment();
            String deploymentListParentPageId;
            String deploymentLetterParentPageId;

            // Environment page
            if (environmentPagesByEnvironment.containsKey(environment)) {
                deploymentListParentPageId = environmentPagesByEnvironment.get(environment);
            } else {
                deploymentListParentPageId = generateDeploymentHistoryPageForEnvironment(systemPageId, environment, system);
                environmentPagesByEnvironment.put(environment, deploymentListParentPageId);
            }

            // Environment page pro year
            int year = deployment.getStartedAt().getYear();
            if (environmentPagesByEnvironmentAndYear.containsKey(environment.getName() + year)) {
                deploymentLetterParentPageId = environmentPagesByEnvironmentAndYear.get(environment.getName() + year);
            } else {
                deploymentLetterParentPageId = generateDeploymentListPage(deploymentListParentPageId, environment, system, year);
                environmentPagesByEnvironmentAndYear.put(environment.getName() + year, deploymentLetterParentPageId);
            }

            // Moving Page
            confluenceAdapter.movePage(deploymentLetterParentPageId, deploymentInfo.pageId());
        }
    }

    private String generateSystemPage(String rootPageId, System system) {
        String pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(rootPageId, system.getName(),
                () -> templateRenderer.renderSystemPage(generatorService.createSystemPageDto(system)));
        generatorService.persistSystemPage(system, pageId);
        return pageId;
    }

    private String generateDeploymentHistoryPageForEnvironment(String systemPageId, Environment environment, System system) {
        String pageTitle = DeploymentHistoryPageDto.pageTitle(system.getName(), environment.getName());
        String pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(systemPageId, pageTitle,
                () -> templateRenderer.renderDeploymentHistoryPage(createDeploymentHistoryPageDto(system, environment)));
        generatorService.persistDeploymentHistoryPage(system, environment, pageId);
        return pageId;
    }

    private DeploymentHistoryPageDto createDeploymentHistoryPageDto(System system, Environment environment) {
        List<DeploymentDto> deploymentDtoList = generatorService.getDeploymentsForSystemAndEnv(system, environment, props.getDeploymentHistoryMaxShow());
        return DeploymentHistoryPageDto.builder()
                .systemName(system.getName())
                .environmentName(environment.getName())
                .deployments(deploymentDtoList)
                .deploymentHistoryMaxShow(props.getDeploymentHistoryMaxShow())
                .build();
    }

    private void generateDeploymentHistoryOverviewPageForEnvironment(String rootPageId, Environment environment) {
        final String deploymentOverviewPageId = confluenceAdapter.addOrUpdatePageUnderAncestor(rootPageId,
                DEPLOYMENT_HISTORY_OVERVIEW_ROOT_PAGE_TITLE, templateRenderer::renderDeploymentHistoryOverviewRootPage);
        String pageTitle = DeploymentHistoryOverviewPageDto.pageTitle(environment.getName());
        confluenceAdapter.addOrUpdatePageUnderAncestor(deploymentOverviewPageId, pageTitle,
                () -> templateRenderer.renderDeploymentHistoryOverviewPage(createDeploymentHistoryOverviewPageDto(environment)));
    }

    private DeploymentHistoryOverviewPageDto createDeploymentHistoryOverviewPageDto(Environment environment) {
        ZonedDateTime minStartedAt = ZonedDateTime.now().minus(props.getDeploymentHistoryOverviewMaxTime());
        List<DeploymentDto> deploymentDtoList = generatorService.getDeploymentsForEnv(environment, minStartedAt, props.getDeploymentHistoryMaxShow());
        return DeploymentHistoryOverviewPageDto.builder()
                .environmentName(environment.getName())
                .deployments(deploymentDtoList)
                .deploymentHistoryMaxShow(props.getDeploymentHistoryMaxShow())
                .deploymentHistoryOverviewMinStartedAt(minStartedAt.format(MIN_STARTED_AT_FORMATTER))
                .build();
    }

    private String generateDeploymentListPage(String parentPageId, Environment environment, System system, int year) {
        DeploymentListPageDto deploymentListPageDto = new DeploymentListPageDto(environment.getName(), system.getName(), year);
        String pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(parentPageId, deploymentListPageDto.getPageTitle(),
                templateRenderer::renderDeploymentListPage);
        generatorService.persistDeploymentListPage(system, environment, pageId, year);
        return pageId;
    }

    @Transactional
    public void generateAllPages() {
        String rootPageId = props.getRootPageId();
        List<System> systemList = systemRepository.findAll();
        systemList.forEach(system -> recursivelyGenerateSystemPage(rootPageId, system, null));
        Iterable<Environment> environmentList = environmentRepository.findAll();
        environmentList.forEach(environment -> generateDeploymentHistoryOverviewPageForEnvironment(rootPageId, environment));
    }

    @Transactional
    public void generateAllPagesForSystem(String systemName, Integer year) {
        String rootPageId = props.getRootPageId();
        System system = systemRepository.findByNameIgnoreCase(systemName).orElseThrow();
        recursivelyGenerateSystemPage(rootPageId, system, year);
        Iterable<Environment> environmentList = environmentRepository.findAll();
        environmentList.forEach(environment -> generateDeploymentHistoryOverviewPageForEnvironment(rootPageId, environment));
    }

    @Transactional(readOnly = true)
    public void generateJiraLinksForSystem(String systemName, ZonedDateTime from, ZonedDateTime to) {
        System system = systemRepository.findByNameIgnoreCase(systemName).orElseThrow();
        List<Deployment> deployments = deploymentRepository.findAllDeploymentsForSystemStartedBetween(system, from, to);
        log.info("Found {} deployments for system '{}' between {} and {}", deployments.size(), systemName, from, to);
        for (Deployment deployment : deployments) {
            if (deployment.getChangelog() != null) {
                Set<String> jiraIssueKeys = deployment.getChangelog().getJiraIssueKeys();
                if (jiraIssueKeys != null && !jiraIssueKeys.isEmpty()) {
                    deploymentPageRepository.findDeploymentPageByDeploymentId(deployment.getId())
                            .ifPresent(deploymentPage -> jiraAdapter.updateJiraIssuesWithConfluenceLink(jiraIssueKeys, deploymentPage.getPageId()));
                }
            }
        }
    }

    private void recursivelyGenerateSystemPage(String rootPageId, System system, Integer year) {
        String systemPageId = generateSystemPage(rootPageId, system);
        recursivelyGenerateDeploymentHistory(systemPageId, system, year);
    }

    private void recursivelyGenerateDeploymentHistory(String systemPageId, System system, Integer year) {
        List<Environment> environmentList = generatorService.getEnvironmentsForSystem(system);
        environmentList.forEach(environment -> recursivelyGenerateDeploymentHistoryPageForEnvironment(systemPageId, environment, system, year));
    }

    private void recursivelyGenerateDeploymentHistoryPageForEnvironment(String systemPageId, Environment environment, System system, Integer year) {
        String pageId = generateDeploymentHistoryPageForEnvironment(systemPageId, environment, system);
        List<Integer> yearList = generatorService.getDeploymentsYearsForSystemAndEnv(system, environment);
        if (year != null) {
            yearList = yearList.stream().filter(y -> y.equals(year)).toList();
        }
        yearList.forEach(currentYear -> recursivelyGenerateDeploymentListPage(pageId, environment, system, currentYear));
    }

    private void recursivelyGenerateDeploymentListPage(String parentPageId, Environment environment, System system, int year) {
        String pageId = generateDeploymentListPage(parentPageId, environment, system, year);
        List<DeploymentLetterPageDto> deploymentLetterPageDtoList = generatorService.getDeploymentsForYearForSystemAndEnv(year, system, environment);
        for (DeploymentLetterPageDto deploymentLetterPageDto : deploymentLetterPageDtoList) {
            if (DeploymentSequence.UNDEPLOYED.getLabel().equals(deploymentLetterPageDto.getSequence())) {
                generateUndeploymentLetter(pageId, deploymentLetterPageDto);
            } else {
                generateDeploymentLetter(pageId, deploymentLetterPageDto);
            }
        }
    }

    private GeneratedDeploymentPageDto generateDeploymentLetter(String parentPageId, Deployment deployment) {
        DeploymentLetterPageDto deploymentLetterPageDto = generatorService.createDeploymentLetterPageDto(deployment);
        return generateDeploymentLetter(parentPageId, deploymentLetterPageDto);
    }

    private GeneratedDeploymentPageDto generateDeploymentLetter(String parentPageId, DeploymentLetterPageDto deploymentLetterPageDto) {
        String pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(parentPageId, deploymentLetterPageDto.getPageTitle(),
                () -> templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto));
        generatorService.persistDeploymentPage(
                UUID.fromString(deploymentLetterPageDto.getDeploymentId()),
                pageId,
                deploymentLetterPageDto.getDeploymentStateTimestamp());

        return GeneratedDeploymentPageDto.builder()
                .deploymentLetterPageDto(deploymentLetterPageDto)
                .pageId(pageId)
                .build();
    }

    private GeneratedDeploymentPageDto generateUndeploymentLetter(String parentPageId, DeploymentLetterPageDto deploymentLetterPageDto) {
        String pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(parentPageId, deploymentLetterPageDto.getPageTitle(),
                () -> templateRenderer.renderUndeploymentLetterPage(deploymentLetterPageDto));
        generatorService.persistDeploymentPage(
                UUID.fromString(deploymentLetterPageDto.getDeploymentId()),
                pageId,
                deploymentLetterPageDto.getDeploymentStateTimestamp());
        return GeneratedDeploymentPageDto.builder()
                .deploymentLetterPageDto(deploymentLetterPageDto)
                .pageId(pageId)
                .build();
    }

    @Timed("update_deployment_history_pages")
    @Transactional
    public void updateDeploymentHistoryPages(Collection<SystemEnv> envsBySystems) {
        for (SystemEnv systemEnv : envsBySystems) {
            System system = systemRepository.getById(systemEnv.getSystemId());
            Environment env = environmentRepository.getById(systemEnv.getEnvId());
            Optional<SystemPage> systemPage = systemPageRepository.findSystemPageBySystemId(systemEnv.getSystemId());
            if (systemPage.isPresent()) {
                String systemPageId = systemPage.get().getSystemPageId();
                generateDeploymentHistoryPageForEnvironment(systemPageId, env, system);
            }
        }
    }

    private GeneratedDeploymentPageDto generateUndeploymentLetter(String parentPageId, Deployment deployment) {
        DeploymentLetterPageDto undeploymentLetterPageDto = generatorService.createUndeploymentLetterPageDto(deployment);
        return generateUndeploymentLetter(parentPageId, undeploymentLetterPageDto);
    }
}
