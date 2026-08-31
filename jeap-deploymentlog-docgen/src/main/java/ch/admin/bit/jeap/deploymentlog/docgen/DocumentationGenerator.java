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
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentationGenerator {

    public static final String UNDEPLOY_PAGE_SUFFIX = " (Undeploy)";
    private static final String DEPLOYMENT_HISTORY_OVERVIEW_ROOT_PAGE_TITLE = "_Deployment History Overview";
    private static final String EMPTY_STRUCTURE_PAGE = "<p></p>";
    private static final String CHANGES_PAGE_KEY = "TOP:CHANGES";
    private static final String SYSTEMS_PAGE_KEY = "TOP:SYSTEMS";
    private static final String STAGES_PAGE_KEY = "TOP:STAGES";
    private static final String DEPLOYMENT_HISTORY_PAGE_KEY = "TOP:STAGES:DEPLOYMENT_HISTORY";
    private static final String DEPLOYMENT_HISTORY_PAGE_TITLE = "Deployment History";
    private static final String GROUP_PAGE_KEY_PREFIX = "SYSTEM_GROUP:";
    private static final String SYSTEM_COMPONENTS_PAGE_KEY_PREFIX = "SYSTEM_COMPONENTS:";
    private static final String SYSTEM_DEPLOYMENTS_PAGE_KEY_PREFIX = "SYSTEM_DEPLOYMENTS:";
    private static final String GLOBAL_STAGE_PAGE_KEY_PREFIX = "GLOBAL_STAGE:";
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
    private final DocumentationStructurePageRepository documentationStructurePageRepository;
    private final ComponentPageGenerator componentPageGenerator;

    @Timed("deploymentlog_generate_deployment_page")
    @Transactional
    public GeneratedDeploymentPageDto generateDeploymentPages(UUID deploymentId) {
        Deployment deployment = deploymentRepository.getById(deploymentId);
        Environment environment = deployment.getEnvironment();
        System system = deployment.getComponentVersion().getComponent().getSystem();

        DocumentationStructure structure = synchronizeDocumentationStructure();
        SystemStructure systemStructure = structure.systems().get(system.getId());
        String deploymentListParentPageId = generateDeploymentHistoryPageForEnvironment(
                systemStructure.deploymentsPageId(), environment, system);
        int year = deployment.getStartedAt().getYear();
        String deploymentLetterParentPageId = generateDeploymentListPage(deploymentListParentPageId, environment, system, year);
        generateDeploymentHistoryOverviewPageForEnvironment(structure.stagesPageId(), environment, null);
        GeneratedDeploymentPageDto generatedPage = deployment.getSequence() != DeploymentSequence.UNDEPLOYED
                ? generateDeploymentLetter(deploymentLetterParentPageId, deployment)
                : generateUndeploymentLetter(deploymentLetterParentPageId, deployment);
        componentPageGenerator.generatePage(systemStructure.componentsPageId(),
                deployment.getComponentVersion().getComponent());
        return generatedPage;
    }

    @Transactional
    public void migrateSystem(System system) {
        DocumentationStructure structure = synchronizeDocumentationStructure();
        SystemStructure systemStructure = structure.systems().get(system.getId());
        componentPageGenerator.generatePages(systemStructure.componentsPageId(), system.getComponents());
        recursivelyGenerateDeploymentHistory(systemStructure.deploymentsPageId(), system, null);
        environmentRepository.findAll().forEach(environment ->
                generateDeploymentHistoryOverviewPageForEnvironment(structure.stagesPageId(), environment, null));
    }

    @Transactional
    public void mergeSystems(System system, System oldSystem) {
        log.info("Retrieve the deployments for the system '{}' to merge into '{}'", oldSystem.getName(), system.getName());
        List<DeploymentPageQueryResult> deployments = deploymentPageRepository.getDeploymentPagesForSystem(oldSystem.getId());

        DocumentationStructure structure = synchronizeDocumentationStructure();
        SystemStructure targetStructure = structure.systems().get(system.getId());

        documentationStructurePageRepository.findByStructureKey(systemComponentsPageKey(oldSystem.getId()))
                .map(DocumentationStructurePage::getPageId)
                .ifPresent(oldComponentsPageId -> componentPageGenerator.moveTrackedPages(
                        oldComponentsPageId, targetStructure.componentsPageId()));
        componentPageGenerator.generatePages(targetStructure.componentsPageId(), system.getComponents());
        componentPageGenerator.generatePages(targetStructure.componentsPageId(), oldSystem.getComponents());

        log.info("Moving {} deployment pages of system '{}' to system '{}'", deployments.size(), oldSystem.getName(), system.getName());
        moveDeploymentPages(system, targetStructure.deploymentsPageId(), deployments);

        Iterable<Environment> environmentList = environmentRepository.findAll();
        environmentList.forEach(environment -> generateDeploymentHistoryOverviewPageForEnvironment(
                structure.stagesPageId(), environment, null));

        Optional<SystemPage> existingOldSystemPage = systemPageRepository.findSystemPageBySystemId(oldSystem.getId());
        if (existingOldSystemPage.isPresent()) {
            String existingOldSystemPageId = existingOldSystemPage.get().getSystemPageId();
            log.info("Deleting old system page with id '{}' and all child pages", existingOldSystemPageId);
            confluenceAdapter.deletePageAndChildPages(existingOldSystemPageId);
            systemPageRepository.deleteSystemPage(existingOldSystemPage.get());
            deleteSystemStructureTracking(oldSystem.getId());
        }

        environmentHistoryPageRepository.deleteEnvironmentHistoryPageBySystemId(oldSystem.getId());
        deploymentListPageRepository.deleteDeploymentListPageBySystemId(oldSystem.getId());
    }

    private void moveDeploymentPages(System system, String deploymentsPageId, List<DeploymentPageQueryResult> deployments) {
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
                deploymentListParentPageId = generateDeploymentHistoryPageForEnvironment(deploymentsPageId, environment, system);
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

    private String generateSystemPage(String parentPageId, System system) {
        Supplier<String> content = () -> templateRenderer.renderSystemPage(generatorService.createSystemPageDto(system));
        Optional<SystemPage> trackedPage = systemPageRepository.findSystemPageBySystemId(system.getId());
        String pageId = trackedPage.map(SystemPage::getSystemPageId)
                .or(() -> confluenceAdapter.findPageByTitle(props.getRootPageId(), system.getName()))
                .orElse(null);
        if (pageId == null || !confluenceAdapter.updatePageById(pageId, parentPageId, system.getName(), content,
                trackedPage.map(SystemPage::getParentPageId).map(parent -> !parent.equals(parentPageId)).orElse(true))) {
            pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(parentPageId, system.getName(), content);
        }
        generatorService.persistSystemPage(system, pageId, parentPageId);
        return pageId;
    }

    private String generateDeploymentHistoryPageForEnvironment(String deploymentsPageId, Environment environment,
                                                               System system) {
        String pageTitle = DeploymentHistoryPageDto.pageTitle(system.getName(), environment.getName());
        Supplier<String> content = () -> templateRenderer.renderDeploymentHistoryPage(
                createDeploymentHistoryPageDto(system, environment));
        Optional<EnvironmentHistoryPage> trackedPage = environmentHistoryPageRepository
                .findEnvironmentHistoryPageBySystemIdAndEnvironmentId(system.getId(), environment.getId());
        String pageId = trackedPage.map(EnvironmentHistoryPage::getPageId)
                .or(() -> systemPageRepository.findSystemPageBySystemId(system.getId())
                        .flatMap(systemPage -> confluenceAdapter.findPageByTitle(systemPage.getSystemPageId(), pageTitle)))
                .orElse(null);
        boolean moveRequired = trackedPage.map(EnvironmentHistoryPage::getParentPageId)
                .map(parent -> !parent.equals(deploymentsPageId))
                .orElse(true);
        if (pageId == null || !confluenceAdapter.updatePageById(
                pageId, deploymentsPageId, pageTitle, content, moveRequired)) {
            pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(deploymentsPageId, pageTitle, content);
        }
        generatorService.persistDeploymentHistoryPage(system, environment, pageId, deploymentsPageId);
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

    private void generateDeploymentHistoryOverviewPageForEnvironment(String stagesPageId,
                                                                     Environment environment,
                                                                     String legacyParentPageId) {
        String pageTitle = DeploymentHistoryOverviewPageDto.pageTitle(environment.getName());
        ensureStructurePage(GLOBAL_STAGE_PAGE_KEY_PREFIX + environment.getId(), stagesPageId, pageTitle,
                () -> templateRenderer.renderDeploymentHistoryOverviewPage(
                        createDeploymentHistoryOverviewPageDto(environment)), legacyParentPageId, pageTitle);
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
        Optional<DeploymentListPage> trackedPage = deploymentListPageRepository
                .findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(system.getId(), environment.getId(), year);
        String pageId = trackedPage.map(DeploymentListPage::getPageId).orElse(null);
        boolean moveRequired = trackedPage.map(DeploymentListPage::getParentPageId)
                .map(parent -> !parent.equals(parentPageId))
                .orElse(true);
        if (pageId == null || !confluenceAdapter.updatePageById(pageId, parentPageId,
                deploymentListPageDto.getPageTitle(), templateRenderer::renderDeploymentListPage, moveRequired)) {
            pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(parentPageId,
                    deploymentListPageDto.getPageTitle(), templateRenderer::renderDeploymentListPage);
        }
        generatorService.persistDeploymentListPage(system, environment, pageId, year, parentPageId);
        return pageId;
    }

    @Transactional
    public void generateAllPages() {
        DocumentationStructure structure = synchronizeDocumentationStructure();
        structure.orderedSystems().forEach(system -> {
            SystemStructure systemStructure = structure.systems().get(system.getId());
            componentPageGenerator.generatePages(systemStructure.componentsPageId(), system.getComponents());
            recursivelyGenerateDeploymentHistory(systemStructure.deploymentsPageId(), system, null);
        });
        Iterable<Environment> environmentList = environmentRepository.findAll();
        environmentList.forEach(environment -> generateDeploymentHistoryOverviewPageForEnvironment(
                structure.stagesPageId(), environment, null));
    }

    @Transactional
    public void generateAllPagesForSystem(String systemName, Integer year) {
        System system = systemRepository.findByNameIgnoreCase(systemName).orElseThrow();
        DocumentationStructure structure = synchronizeDocumentationStructure();
        SystemStructure systemStructure = structure.systems().get(system.getId());
        componentPageGenerator.generatePages(systemStructure.componentsPageId(), system.getComponents());
        recursivelyGenerateDeploymentHistory(systemStructure.deploymentsPageId(), system, year);
        Iterable<Environment> environmentList = environmentRepository.findAll();
        environmentList.forEach(environment -> generateDeploymentHistoryOverviewPageForEnvironment(
                structure.stagesPageId(), environment, null));
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

    private void recursivelyGenerateDeploymentHistory(String deploymentsPageId, System system, Integer year) {
        List<Environment> environmentList = generatorService.getEnvironmentsForSystem(system);
        environmentList.forEach(environment -> recursivelyGenerateDeploymentHistoryPageForEnvironment(
                deploymentsPageId, environment, system, year));
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

    // The letter page is rendered from exactly one deployment and is only ever written under the per-system docgen
    // lock, so a re-render on conflict would reproduce the identical content by construction. The supplier therefore
    // deliberately closes over the already built DTO - unlike the aggregate pages above, which have to re-query.
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

    // The letter page is rendered from exactly one deployment and is only ever written under the per-system docgen
    // lock, so a re-render on conflict would reproduce the identical content by construction. The supplier therefore
    // deliberately closes over the already built DTO - unlike the aggregate pages above, which have to re-query.
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
        DocumentationStructure structure = synchronizeDocumentationStructure();
        for (SystemEnv systemEnv : envsBySystems) {
            System system = systemRepository.getById(systemEnv.getSystemId());
            Environment env = environmentRepository.getById(systemEnv.getEnvId());
            SystemStructure systemStructure = structure.systems().get(systemEnv.getSystemId());
            if (systemStructure != null) {
                generateDeploymentHistoryPageForEnvironment(systemStructure.deploymentsPageId(), env, system);
            }
        }
    }

    @Transactional
    public void reconcileDocumentationStructure() {
        synchronizeDocumentationStructure();
    }

    private DocumentationStructure synchronizeDocumentationStructure() {
        String rootPageId = props.getRootPageId();
        ensureStructurePage(CHANGES_PAGE_KEY, rootPageId, "Changes", () -> EMPTY_STRUCTURE_PAGE, null, null);
        String systemsPageId = ensureStructurePage(
                SYSTEMS_PAGE_KEY, rootPageId, "Systems", () -> EMPTY_STRUCTURE_PAGE, null, null);
        String stagesPageId = ensureStructurePage(
                STAGES_PAGE_KEY, rootPageId, "Stages", () -> EMPTY_STRUCTURE_PAGE, null, null);
        removeDeploymentHistoryIntermediatePage(rootPageId, stagesPageId);

        List<System> systems = systemRepository.findAllWithSystemGroup().stream()
                .sorted(Comparator.comparing(System::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(System::getId))
                .toList();
        Map<UUID, SystemGroup> groups = new HashMap<>();
        for (System system : systems) {
            SystemGroup group = system.getSystemGroup();
            if (group != null) {
                groups.put(group.getId(), group);
            }
        }
        List<SystemGroup> orderedGroups = groups.values().stream()
                .sorted(Comparator.comparing(SystemGroup::getNormalizedName).thenComparing(SystemGroup::getId))
                .toList();
        Map<UUID, String> groupPageIds = new HashMap<>();
        for (SystemGroup group : orderedGroups) {
            String pageId = ensureStructurePage(groupPageKey(group.getId()), systemsPageId, group.getName(),
                    () -> EMPTY_STRUCTURE_PAGE, null, null);
            groupPageIds.put(group.getId(), pageId);
        }

        Map<UUID, SystemStructure> systemStructures = new HashMap<>();
        for (System system : systems) {
            String systemParentPageId = system.getSystemGroup() == null
                    ? systemsPageId
                    : groupPageIds.get(system.getSystemGroup().getId());
            String systemPageId = generateSystemPage(systemParentPageId, system);
            String componentsPageId = ensureStructurePage(systemComponentsPageKey(system.getId()), systemPageId,
                    "Components (" + system.getName() + ")", () -> EMPTY_STRUCTURE_PAGE,
                    List.of(new LegacyPageLocation(systemPageId, "Components " + system.getName()),
                            new LegacyPageLocation(systemPageId, "Components")));
            String deploymentsPageId = ensureStructurePage(systemDeploymentsPageKey(system.getId()), systemPageId,
                    "Deployments (" + system.getName() + ")", () -> EMPTY_STRUCTURE_PAGE,
                    List.of(new LegacyPageLocation(systemPageId, "Deployments " + system.getName()),
                            new LegacyPageLocation(systemPageId, "Deployments")));
            systemStructures.put(system.getId(), new SystemStructure(componentsPageId, deploymentsPageId));
        }

        removeObsoleteGroupPages(groups.keySet());
        return new DocumentationStructure(stagesPageId, systemStructures, systems);
    }

    private void removeDeploymentHistoryIntermediatePage(String rootPageId, String stagesPageId) {
        Optional<DocumentationStructurePage> trackedPage = documentationStructurePageRepository
                .findByStructureKey(DEPLOYMENT_HISTORY_PAGE_KEY);
        String pageId = confluenceAdapter.findPageByTitle(stagesPageId, DEPLOYMENT_HISTORY_PAGE_TITLE)
                .or(() -> confluenceAdapter.findPageByTitle(rootPageId, DEPLOYMENT_HISTORY_OVERVIEW_ROOT_PAGE_TITLE))
                .or(() -> trackedPage.map(DocumentationStructurePage::getPageId))
                .orElse(null);
        if (pageId == null) {
            return;
        }

        String legacyPageId = pageId;
        environmentRepository.findAll().forEach(environment ->
                generateDeploymentHistoryOverviewPageForEnvironment(stagesPageId, environment, legacyPageId));
        confluenceAdapter.deletePage(legacyPageId);
        trackedPage.ifPresent(documentationStructurePageRepository::delete);
    }

    private String ensureStructurePage(String structureKey,
                                       String parentPageId,
                                       String title,
                                       Supplier<String> contentSupplier,
                                       String legacyParentPageId,
                                       String legacyTitle) {
        List<LegacyPageLocation> legacyLocations = legacyParentPageId == null || legacyTitle == null
                ? List.of()
                : List.of(new LegacyPageLocation(legacyParentPageId, legacyTitle));
        return ensureStructurePage(structureKey, parentPageId, title, contentSupplier, legacyLocations);
    }

    private String ensureStructurePage(String structureKey,
                                       String parentPageId,
                                       String title,
                                       Supplier<String> contentSupplier,
                                       List<LegacyPageLocation> legacyLocations) {
        Optional<DocumentationStructurePage> trackedPage = documentationStructurePageRepository
                .findByStructureKey(structureKey);
        String pageId = trackedPage.map(DocumentationStructurePage::getPageId).orElse(null);
        String knownParentPageId = trackedPage.map(DocumentationStructurePage::getParentPageId).orElse(null);

        if (pageId == null) {
            pageId = confluenceAdapter.findPageByTitle(parentPageId, title).orElse(null);
            knownParentPageId = pageId == null ? null : parentPageId;
        }
        for (LegacyPageLocation legacyLocation : legacyLocations) {
            if (pageId != null) {
                break;
            }
            pageId = confluenceAdapter.findPageByTitle(
                    legacyLocation.parentPageId(), legacyLocation.title()).orElse(null);
            knownParentPageId = pageId == null ? null : legacyLocation.parentPageId();
        }

        boolean moveRequired = !Objects.equals(knownParentPageId, parentPageId);
        if (pageId == null || !confluenceAdapter.updatePageById(
                pageId, parentPageId, title, contentSupplier, moveRequired)) {
            pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(parentPageId, title, contentSupplier);
        }

        DocumentationStructurePage page = trackedPage.orElse(null);
        if (page == null) {
            page = DocumentationStructurePage.create(structureKey, pageId, parentPageId);
        }
        page.updateLocation(pageId, parentPageId);
        documentationStructurePageRepository.save(page);
        return pageId;
    }

    private void removeObsoleteGroupPages(Set<UUID> activeGroupIds) {
        Set<String> activeKeys = new HashSet<>();
        activeGroupIds.forEach(groupId -> activeKeys.add(groupPageKey(groupId)));
        documentationStructurePageRepository.findAll().stream()
                .filter(page -> page.getStructureKey().startsWith(GROUP_PAGE_KEY_PREFIX))
                .filter(page -> !activeKeys.contains(page.getStructureKey()))
                .forEach(page -> {
                    confluenceAdapter.deletePage(page.getPageId());
                    documentationStructurePageRepository.delete(page);
                });
    }

    private void deleteSystemStructureTracking(UUID systemId) {
        Set<String> keys = Set.of(systemComponentsPageKey(systemId), systemDeploymentsPageKey(systemId));
        documentationStructurePageRepository.findAll().stream()
                .filter(page -> keys.contains(page.getStructureKey()))
                .forEach(documentationStructurePageRepository::delete);
    }

    private static String groupPageKey(UUID groupId) {
        return GROUP_PAGE_KEY_PREFIX + groupId;
    }

    private static String systemComponentsPageKey(UUID systemId) {
        return SYSTEM_COMPONENTS_PAGE_KEY_PREFIX + systemId;
    }

    private static String systemDeploymentsPageKey(UUID systemId) {
        return SYSTEM_DEPLOYMENTS_PAGE_KEY_PREFIX + systemId;
    }

    private record SystemStructure(String componentsPageId, String deploymentsPageId) {
    }

    private record LegacyPageLocation(String parentPageId, String title) {
    }

    private record DocumentationStructure(String stagesPageId,
                                          Map<UUID, SystemStructure> systems,
                                          List<System> orderedSystems) {
    }

    private GeneratedDeploymentPageDto generateUndeploymentLetter(String parentPageId, Deployment deployment) {
        DeploymentLetterPageDto undeploymentLetterPageDto = generatorService.createUndeploymentLetterPageDto(deployment);
        return generateUndeploymentLetter(parentPageId, undeploymentLetterPageDto);
    }
}
