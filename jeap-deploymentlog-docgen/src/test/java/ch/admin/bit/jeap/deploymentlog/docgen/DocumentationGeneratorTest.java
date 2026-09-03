package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.SystemPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.service.GeneratorService;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "SpringJavaInjectionPointsAutowiringInspection"})
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
class DocumentationGeneratorTest {

    private static final String ROOT_PAGE_ID = "configuredRootPageId";
    @Autowired
    ApplicationContext applicationContext;

    @Mock
    ConfluenceAdapter confluenceAdapterMock;

    @Mock
    JiraAdapter jiraAdapterMock;

    @Mock
    SystemRepository systemRepositoryMock;

    @Mock
    EnvironmentRepository environmentRepositoryMock;

    @Mock
    DeploymentRepository deploymentRepositoryMock;

    @Mock
    DeploymentPageRepository deploymentPageRepositoryMock;

    @Mock
    SystemPageRepository systemPageRepositoryMock;

    @Mock
    GeneratorService generatorServiceMock;

    @Mock
    EnvironmentHistoryPageRepository environmentHistoryPageRepositoryMock;

    @Mock
    DeploymentListPageRepository deploymentListPageRepositoryMock;

    @Mock
    DocumentationStructurePageRepository documentationStructurePageRepositoryMock;

    @Mock
    DocumentationStructureLock documentationStructureLockMock;

    @Mock
    ComponentPageGenerator componentPageGeneratorMock;

    @Mock
    JiraProjectPageGenerator jiraProjectPageGeneratorMock;

    @Mock
    JiraIssuePageRepository jiraIssuePageRepositoryMock;

    private DocumentationGenerator documentationGenerator;
    private final Map<String, DocumentationStructurePage> structurePages = new HashMap<>();

    @Test
    void generate() {
        // given
        String rootPageId = ROOT_PAGE_ID;
        String systemName = "SYSTEM A";
        System system = new System(systemName);
        List<System> systemList = List.of(system);
        SystemPageDto systemPageDto = SystemPageDto.builder()
                .name(systemName)
                .build();

        doReturn(systemPageDto).when(generatorServiceMock).createSystemPageDto(system);
        doReturn(systemList).when(systemRepositoryMock).findAllWithSystemGroup();

        // when
        documentationGenerator.generateAllPages();

        // then
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(rootPageId + "/Systems"), eq(systemName), any());
    }

    @Test
    void generateAllPagesForSystem() {
        // given
        String rootPageId = ROOT_PAGE_ID;
        String systemName = "SYSTEM A";
        System system = new System(systemName);
        SystemPageDto systemPageDto = SystemPageDto.builder()
                .name(systemName)
                .build();

        doReturn(systemPageDto).when(generatorServiceMock).createSystemPageDto(system);
        doReturn(Optional.of(system)).when(systemRepositoryMock).findByNameIgnoreCase(systemName);
        doReturn(List.of(system)).when(systemRepositoryMock).findAllWithSystemGroup();

        // when
        documentationGenerator.generateAllPagesForSystem(systemName, null);

        // then
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(rootPageId + "/Systems"), eq(systemName), any());
    }

    @Test
    void generateDeploymentPagesUpdatesOnlyAffectedComponentPage() {
        System system = new System("SYSTEM A");
        ch.admin.bit.jeap.deploymentlog.domain.Component component =
                new ch.admin.bit.jeap.deploymentlog.domain.Component("component-a", system);
        system.getComponents().add(component);
        ComponentVersion componentVersion = mock(ComponentVersion.class);
        Environment environment = new Environment("DEV");
        Deployment deployment = mock(Deployment.class);
        UUID deploymentId = UUID.randomUUID();
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-08-31T10:00:00+02:00");
        when(deploymentRepositoryMock.getById(deploymentId)).thenReturn(deployment);
        when(deployment.getEnvironment()).thenReturn(environment);
        when(deployment.getComponentVersion()).thenReturn(componentVersion);
        when(componentVersion.getComponent()).thenReturn(component);
        when(deployment.getStartedAt()).thenReturn(startedAt);
        when(deployment.getSequence()).thenReturn(DeploymentSequence.NEW);
        when(systemRepositoryMock.findAllWithSystemGroup()).thenReturn(List.of(system));
        when(generatorServiceMock.createSystemPageDto(system))
                .thenReturn(SystemPageDto.builder().name(system.getName()).build());
        when(generatorServiceMock.createDeploymentLetterPageDto(deployment)).thenReturn(
                ch.admin.bit.jeap.deploymentlog.docgen.model.DeploymentLetterPageDto.builder()
                        .deploymentId(deploymentId.toString())
                        .startedAt("2026-08-31 10:00:00")
                        .componentName(component.getName())
                        .environmentName(environment.getName())
                        .state("STARTED")
                        .sequence(DeploymentSequence.NEW.getLabel())
                        .deploymentStateTimestamp(startedAt)
                        .changeJiraIssueKeys(Set.of())
                        .build());

        documentationGenerator.generateDeploymentPages(deploymentId);

        verify(componentPageGeneratorMock).generatePage(
                ROOT_PAGE_ID + "/Systems/SYSTEM A/Components (SYSTEM A)", component);
        verify(componentPageGeneratorMock, never()).generatePages(anyString(), any());
    }


    @Test
    void migrateSystem() {
        // given
        String rootPageId = ROOT_PAGE_ID;
        String systemName = "SYSTEM A";
        System system = new System(systemName);
        SystemPageDto systemPageDto = SystemPageDto.builder()
                .name(systemName)
                .build();

        Environment environment = mock(Environment.class);
        UUID environmentId = UUID.randomUUID();
        when(environment.getId()).thenReturn(environmentId);
        when(environment.getName()).thenReturn("REF");
        when(generatorServiceMock.getEnvironmentsForSystem(system)).thenReturn(List.of(environment));
        EnvironmentHistoryPage trackedHistoryPage = EnvironmentHistoryPage.builder()
                .id(UUID.randomUUID())
                .systemId(system.getId())
                .environmentId(environmentId)
                .pageId("existing-stage-page")
                .parentPageId("existing-system-page")
                .lastUpdatedAt(ZonedDateTime.now())
                .build();
        when(environmentHistoryPageRepositoryMock.findEnvironmentHistoryPageBySystemIdAndEnvironmentId(
                system.getId(), environmentId)).thenReturn(Optional.of(trackedHistoryPage));

        doReturn(systemPageDto).when(generatorServiceMock).createSystemPageDto(system);
        doReturn(List.of(system)).when(systemRepositoryMock).findAllWithSystemGroup();

        // when
        documentationGenerator.migrateSystem(system);

        // then
        verify(confluenceAdapterMock, times(1)).addOrUpdatePageUnderAncestor(eq(rootPageId + "/Systems"), eq(systemName), any());
        verify(confluenceAdapterMock).updatePageById(eq("existing-stage-page"),
                eq(rootPageId + "/Systems/" + systemName + "/Deployments (" + systemName + ")"),
                eq("Deployment History REF (SYSTEM A)"), any(), eq(true));
    }

    @Test
    void mergeSystems() {
        // given
        String systemName = "SYSTEM A";
        String oldSystemName = "SYSTEM OLD";
        System system = new System(systemName);
        System oldSystem = new System(oldSystemName);

        UUID deployment1Id = UUID.randomUUID();
        UUID deployment2Id = UUID.randomUUID();

        when(deploymentPageRepositoryMock.getDeploymentPagesForSystem(any(UUID.class))).thenReturn(List.of(
                new DeploymentPageQueryResult(deployment1Id, UUID.randomUUID().toString()),
                new DeploymentPageQueryResult(deployment2Id, UUID.randomUUID().toString())
        ));

        Environment environment = mock(Environment.class);
        Deployment deployment1Mock = mock(Deployment.class);
        Deployment deployment2Mock = mock(Deployment.class);
        when(deployment1Mock.getEnvironment()).thenReturn(environment);
        when(deployment2Mock.getEnvironment()).thenReturn(environment);
        when(deployment1Mock.getStartedAt()).thenReturn(ZonedDateTime.now());
        when(deployment2Mock.getStartedAt()).thenReturn(ZonedDateTime.now());
        when(deploymentRepositoryMock.getById(deployment1Id)).thenReturn(deployment1Mock);
        when(deploymentRepositoryMock.getById(deployment2Id)).thenReturn(deployment2Mock);
        doReturn(List.of(system, oldSystem)).when(systemRepositoryMock).findAllWithSystemGroup();
        doReturn(SystemPageDto.builder().name(systemName).build())
                .when(generatorServiceMock).createSystemPageDto(system);
        doReturn(SystemPageDto.builder().name(oldSystemName).build())
                .when(generatorServiceMock).createSystemPageDto(oldSystem);

        // when
        documentationGenerator.mergeSystems(system, oldSystem);

        // then
        verify(confluenceAdapterMock, times(1)).addOrUpdatePageUnderAncestor(
                eq(ROOT_PAGE_ID + "/Systems/" + systemName + "/Deployments (" + systemName + ")"),
                eq("Deployment History null (SYSTEM A)"), any());
        verify(confluenceAdapterMock, times(2)).movePage(anyString(), anyString());
        verify(componentPageGeneratorMock).moveTrackedPages(
                ROOT_PAGE_ID + "/Systems/" + oldSystemName + "/Components (" + oldSystemName + ")",
                ROOT_PAGE_ID + "/Systems/" + systemName + "/Components (" + systemName + ")");
    }

    @Test
    void generateJiraLinksForSystem() {
        // given
        String systemName = "SYSTEM";
        System system = new System(systemName);
        ZonedDateTime from = ZonedDateTime.now().minusDays(10);
        ZonedDateTime to = ZonedDateTime.now().minusDays(1);

        when(systemRepositoryMock.findByNameIgnoreCase(systemName)).thenReturn(Optional.of(system));

        Deployment deployment1Mock = mock(Deployment.class);
        Deployment deployment2Mock = mock(Deployment.class);
        Deployment deployment3Mock = mock(Deployment.class);

        when(deployment1Mock.getChangelog()).thenReturn(Changelog.builder().jiraIssueKeys(Set.of("JIRA-1234", "JIRA-2345")).build());

        when(deployment3Mock.getChangelog()).thenReturn(Changelog.builder().jiraIssueKeys(Set.of()).build());

        List<Deployment> deployments = List.of(deployment1Mock, deployment2Mock, deployment3Mock);
        when(deploymentRepositoryMock.findAllDeploymentsForSystemStartedBetween(system, from, to)).thenReturn(deployments);
        when(jiraIssuePageRepositoryMock.findByIssueKey("JIRA-1234")).thenReturn(Optional.of(
                JiraIssuePage.create("JIRA-1234", "JIRA", "issue-page-1", "project-page")));
        when(jiraIssuePageRepositoryMock.findByIssueKey("JIRA-2345")).thenReturn(Optional.of(
                JiraIssuePage.create("JIRA-2345", "JIRA", "issue-page-2", "project-page")));

        // when
        documentationGenerator.generateJiraLinksForSystem(systemName, from, to);

        // then
        verify(jiraAdapterMock).updateIssuePageRemoteLink("JIRA-1234", "issue-page-1");
        verify(jiraAdapterMock).updateIssuePageRemoteLink("JIRA-2345", "issue-page-2");

    }

    @Test
    void generateAllPages_usesConfiguredRootPageIdAsAncestor() {
        String configuredRootPageId = "a-distinct-configured-root-page-id";
        DocumentationGeneratorConfig generatorConfig = new DocumentationGeneratorConfig();
        DocumentationGeneratorConfluenceProperties props = new DocumentationGeneratorConfluenceProperties();
        props.setRootPageId(configuredRootPageId);
        DocumentationGenerator generator = new DocumentationGenerator(
                confluenceAdapterMock,
                jiraAdapterMock,
                new TemplateRenderer(generatorConfig.templateEngine(applicationContext)),
                props,
                systemRepositoryMock,
                environmentRepositoryMock,
                generatorServiceMock,
                deploymentRepositoryMock,
                deploymentPageRepositoryMock,
                systemPageRepositoryMock,
                environmentHistoryPageRepositoryMock,
                deploymentListPageRepositoryMock,
                documentationStructurePageRepositoryMock,
                documentationStructureLockMock,
                componentPageGeneratorMock,
                jiraProjectPageGeneratorMock,
                jiraIssuePageRepositoryMock);

        String systemName = "SYSTEM A";
        System system = new System(systemName);
        doReturn(SystemPageDto.builder().name(systemName).build()).when(generatorServiceMock).createSystemPageDto(system);
        doReturn(List.of(system)).when(systemRepositoryMock).findAllWithSystemGroup();

        // when
        generator.generateAllPages();

        // then - the top-level system page is created under exactly the configured root page id
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(configuredRootPageId + "/Systems"), eq(systemName), any());
    }

    @Test
    void generateAllPages_createsTopLevelPagesDirectlyBelowConfiguredRootWithoutAnotherDeploymentsPage() {
        documentationGenerator.generateAllPages();

        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(ROOT_PAGE_ID), eq("Changes"), any());
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(ROOT_PAGE_ID), eq("Systems"), any());
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(ROOT_PAGE_ID), eq("Stages"), any());
        verify(confluenceAdapterMock, never()).addOrUpdatePageUnderAncestor(anyString(), eq("Deployments"), any());
    }

    @Test
    void generateAllPages_createsSystemSpecificContainerTitles() {
        System system = new System("SYSTEM A");
        when(systemRepositoryMock.findAllWithSystemGroup()).thenReturn(List.of(system));
        when(generatorServiceMock.createSystemPageDto(system))
                .thenReturn(SystemPageDto.builder().name(system.getName()).build());

        documentationGenerator.generateAllPages();

        String systemPageId = ROOT_PAGE_ID + "/Systems/" + system.getName();
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(
                eq(systemPageId), eq("Components (SYSTEM A)"), any());
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(
                eq(systemPageId), eq("Deployments (SYSTEM A)"), any());
    }

    @Test
    void generateAllPagesGeneratesComponentPagesBelowCurrentSystemContainer() {
        System system = new System("SYSTEM A");
        ch.admin.bit.jeap.deploymentlog.domain.Component component =
                new ch.admin.bit.jeap.deploymentlog.domain.Component("component-a", system);
        system.getComponents().add(component);
        when(systemRepositoryMock.findAllWithSystemGroup()).thenReturn(List.of(system));
        when(generatorServiceMock.createSystemPageDto(system))
                .thenReturn(SystemPageDto.builder().name(system.getName()).build());

        documentationGenerator.generateAllPages();

        verify(componentPageGeneratorMock).generatePages(
                ROOT_PAGE_ID + "/Systems/SYSTEM A/Components (SYSTEM A)", system.getComponents());
    }

    @Test
    void generateAllPages_adoptsPreviousSystemContainerTitlesAndRenamesThem() {
        System system = new System("SYSTEM A");
        when(systemRepositoryMock.findAllWithSystemGroup()).thenReturn(List.of(system));
        when(generatorServiceMock.createSystemPageDto(system))
                .thenReturn(SystemPageDto.builder().name(system.getName()).build());
        String systemPageId = ROOT_PAGE_ID + "/Systems/" + system.getName();
        lenient().doReturn(Optional.of("legacy-components-page")).when(confluenceAdapterMock)
                .findPageByTitle(systemPageId, "Components");
        lenient().doReturn(Optional.of("legacy-deployments-page")).when(confluenceAdapterMock)
                .findPageByTitle(systemPageId, "Deployments SYSTEM A");

        documentationGenerator.generateAllPages();

        verify(confluenceAdapterMock).updatePageById(eq("legacy-components-page"), eq(systemPageId),
                eq("Components (SYSTEM A)"), any(), eq(false));
        verify(confluenceAdapterMock).updatePageById(eq("legacy-deployments-page"), eq(systemPageId),
                eq("Deployments (SYSTEM A)"), any(), eq(false));
    }

    @Test
    void generateAllPages_placesGroupedAndUngroupedSystemsAtTheirDeterministicTargets() {
        System groupedSystem = new System("B GROUPED");
        System ungroupedSystem = new System("A UNGROUPED");
        SystemGroup group = new SystemGroup("Example Group");
        SystemGroupRepository groupRepository = mock(SystemGroupRepository.class);
        when(groupRepository.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(systemRepositoryMock.findByNameIgnoreCase(groupedSystem.getName())).thenReturn(Optional.of(groupedSystem));
        new SystemGroupService(groupRepository, systemRepositoryMock).assignSystem(group.getId(), groupedSystem.getName());
        when(systemRepositoryMock.findAllWithSystemGroup()).thenReturn(List.of(groupedSystem, ungroupedSystem));
        when(generatorServiceMock.createSystemPageDto(groupedSystem))
                .thenReturn(SystemPageDto.builder().name(groupedSystem.getName()).build());
        when(generatorServiceMock.createSystemPageDto(ungroupedSystem))
                .thenReturn(SystemPageDto.builder().name(ungroupedSystem.getName()).build());

        documentationGenerator.generateAllPages();

        String systemsPageId = ROOT_PAGE_ID + "/Systems";
        String groupPageId = systemsPageId + "/" + group.getName();
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(systemsPageId), eq(group.getName()), any());
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(
                eq(groupPageId), eq(groupedSystem.getName()), any());
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(
                eq(systemsPageId), eq(ungroupedSystem.getName()), any());
    }

    @Test
    void generateAllPages_movesTrackedSystemPageToGroupAndKeepsItsPageId() {
        System system = new System("SYSTEM A");
        SystemGroup group = new SystemGroup("Group A");
        SystemGroupRepository groupRepository = mock(SystemGroupRepository.class);
        when(groupRepository.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(systemRepositoryMock.findByNameIgnoreCase(system.getName())).thenReturn(Optional.of(system));
        new SystemGroupService(groupRepository, systemRepositoryMock).assignSystem(group.getId(), system.getName());
        when(systemRepositoryMock.findAllWithSystemGroup()).thenReturn(List.of(system));
        when(generatorServiceMock.createSystemPageDto(system))
                .thenReturn(SystemPageDto.builder().name(system.getName()).build());
        SystemPage trackedSystemPage = SystemPage.builder()
                .id(UUID.randomUUID())
                .systemId(system.getId())
                .systemPageId("existing-system-page")
                .parentPageId(ROOT_PAGE_ID)
                .lastUpdatedAt(ZonedDateTime.now())
                .build();
        when(systemPageRepositoryMock.findSystemPageBySystemId(system.getId()))
                .thenReturn(Optional.of(trackedSystemPage));

        documentationGenerator.generateAllPages();

        String groupPageId = ROOT_PAGE_ID + "/Systems/" + group.getName();
        verify(confluenceAdapterMock).updatePageById(eq("existing-system-page"), eq(groupPageId),
                eq(system.getName()), any(), eq(true));
        verify(generatorServiceMock).persistSystemPage(system, "existing-system-page", groupPageId);
    }

    @Test
    void generateAllPages_removesTrackedEmptyGroupPage() {
        DocumentationStructurePage obsoleteGroupPage = DocumentationStructurePage.create(
                "SYSTEM_GROUP:" + UUID.randomUUID(), "obsolete-group-page", ROOT_PAGE_ID + "/Systems");
        structurePages.put(obsoleteGroupPage.getStructureKey(), obsoleteGroupPage);

        documentationGenerator.generateAllPages();

        verify(confluenceAdapterMock).deletePage("obsolete-group-page");
        verify(documentationStructurePageRepositoryMock).delete(obsoleteGroupPage);
    }

    @Test
    void generateAllPages_movesGlobalStagePagesAndRemovesDeploymentHistoryIntermediatePage() {
        Environment environment = mock(Environment.class);
        UUID environmentId = UUID.randomUUID();
        when(environment.getId()).thenReturn(environmentId);
        when(environment.getName()).thenReturn("DEV");
        when(environmentRepositoryMock.findAll()).thenReturn(List.of(environment));
        String stagesPageId = ROOT_PAGE_ID + "/Stages";
        lenient().doReturn(Optional.of("legacy-history-page")).when(confluenceAdapterMock)
                .findPageByTitle(stagesPageId, "Deployment History");
        lenient().doReturn(Optional.of("legacy-stage-page")).when(confluenceAdapterMock)
                .findPageByTitle("legacy-history-page", "Deployment History Overview DEV");

        documentationGenerator.generateAllPages();

        verify(confluenceAdapterMock).updatePageById(eq("legacy-stage-page"), eq(stagesPageId),
                eq("Deployment History Overview DEV"), any(), eq(true));
        verify(confluenceAdapterMock).deletePage("legacy-history-page");
        org.junit.jupiter.api.Assertions.assertFalse(
                structurePages.containsKey("TOP:STAGES:DEPLOYMENT_HISTORY"));
        DocumentationStructurePage trackedStagePage = structurePages.get("GLOBAL_STAGE:" + environmentId);
        org.junit.jupiter.api.Assertions.assertEquals("legacy-stage-page", trackedStagePage.getPageId());
        org.junit.jupiter.api.Assertions.assertEquals(stagesPageId, trackedStagePage.getParentPageId());
    }

    @BeforeEach
    void setUp() {
        structurePages.clear();
        // Render the page content just like the real adapter does, which invokes the supplier at least once
        lenient().when(confluenceAdapterMock.addOrUpdatePageUnderAncestor(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(2, Supplier.class).get();
                    return invocation.getArgument(0, String.class) + "/" + invocation.getArgument(1, String.class);
                });
        lenient().when(confluenceAdapterMock.updatePageById(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(3, Supplier.class).get();
                    return true;
                });
        lenient().when(documentationStructurePageRepositoryMock.findByStructureKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(structurePages.get(invocation.getArgument(0))));
        lenient().when(documentationStructurePageRepositoryMock.findAll())
                .thenAnswer(invocation -> List.copyOf(structurePages.values()));
        lenient().when(documentationStructurePageRepositoryMock.save(any(DocumentationStructurePage.class)))
                .thenAnswer(invocation -> {
                    DocumentationStructurePage page = invocation.getArgument(0);
                    structurePages.put(page.getStructureKey(), page);
                    return page;
                });
        lenient().doAnswer(invocation -> {
            DocumentationStructurePage page = invocation.getArgument(0);
            structurePages.remove(page.getStructureKey());
            return null;
        }).when(documentationStructurePageRepositoryMock).delete(any(DocumentationStructurePage.class));
        lenient().when(documentationStructureLockMock.runLocked(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());

        DocumentationGeneratorConfig generatorConfig = new DocumentationGeneratorConfig();
        TemplateRenderer templateRenderer = new TemplateRenderer(generatorConfig.templateEngine(applicationContext));
        DocumentationGeneratorConfluenceProperties props = new DocumentationGeneratorConfluenceProperties();
        props.setRootPageId(ROOT_PAGE_ID);
        documentationGenerator = new DocumentationGenerator(
                confluenceAdapterMock,
                jiraAdapterMock,
                templateRenderer,
                props,
                systemRepositoryMock,
                environmentRepositoryMock,
                generatorServiceMock,
                deploymentRepositoryMock,
                deploymentPageRepositoryMock,
                systemPageRepositoryMock,
                environmentHistoryPageRepositoryMock,
                deploymentListPageRepositoryMock,
                documentationStructurePageRepositoryMock,
                documentationStructureLockMock,
                componentPageGeneratorMock,
                jiraProjectPageGeneratorMock,
                jiraIssuePageRepositoryMock);
    }
}
