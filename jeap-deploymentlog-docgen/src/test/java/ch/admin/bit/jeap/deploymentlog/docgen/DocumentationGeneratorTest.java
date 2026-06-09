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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    private DocumentationGenerator documentationGenerator;

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
        doReturn(systemList).when(systemRepositoryMock).findAll();

        // when
        documentationGenerator.generateAllPages();

        // then
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(rootPageId), eq(systemName), anyString());
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

        // when
        documentationGenerator.generateAllPagesForSystem(systemName, null);

        // then
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(rootPageId), eq(systemName), anyString());
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

        when(confluenceAdapterMock.addOrUpdatePageUnderAncestor(anyString(), anyString(), anyString())).thenReturn(UUID.randomUUID().toString());

        doReturn(systemPageDto).when(generatorServiceMock).createSystemPageDto(system);

        // when
        documentationGenerator.migrateSystem(system);

        // then
        verify(confluenceAdapterMock, times(1)).addOrUpdatePageUnderAncestor(eq(rootPageId), eq(systemName), anyString());
        verify(confluenceAdapterMock, times(2)).movePage(anyString(), anyString());
    }

    @Test
    void mergeSystems() {
        // given
        String systemName = "SYSTEM A";
        String oldSystemName = "SYSTEM OLD";
        System system = new System(systemName);
        System oldSystem = new System(oldSystemName);

        SystemPage systemPageMock = mock(SystemPage.class);
        UUID systemPageId = UUID.randomUUID();
        when(systemPageMock.getSystemPageId()).thenReturn(systemPageId.toString());
        when(systemPageRepositoryMock.findSystemPageBySystemId(system.getId())).thenReturn(Optional.of(systemPageMock));

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

        when(confluenceAdapterMock.addOrUpdatePageUnderAncestor(anyString(), anyString(), anyString())).thenReturn(UUID.randomUUID().toString());

        // when
        documentationGenerator.mergeSystems(system, oldSystem);

        // then
        verify(confluenceAdapterMock, times(1)).addOrUpdatePageUnderAncestor(eq(systemPageId.toString()), eq("Deployment History null (SYSTEM A)"), anyString());
        verify(confluenceAdapterMock, times(2)).movePage(anyString(), anyString());
        verify(generatorServiceMock, never()).createSystemPageDto(any(System.class));
    }

    @Test
    void generateJiraLinksForSystem() {
        // given
        String systemName = "SYSTEM";
        System system = new System(systemName);
        ZonedDateTime from = ZonedDateTime.now().minusDays(10);
        ZonedDateTime to = ZonedDateTime.now().minusDays(1);

        when(systemRepositoryMock.findByNameIgnoreCase(systemName)).thenReturn(Optional.of(system));

        UUID deployment1Id = UUID.randomUUID();
        UUID deployment2Id = UUID.randomUUID();
        UUID deployment3Id = UUID.randomUUID();

        Deployment deployment1Mock = mock(Deployment.class);
        Deployment deployment2Mock = mock(Deployment.class);
        Deployment deployment3Mock = mock(Deployment.class);

        DeploymentPage deploymentPage1Mock = mock(DeploymentPage.class);
        String pageId = UUID.randomUUID().toString();
        when(deploymentPage1Mock.getPageId()).thenReturn(pageId);

        when(deployment1Mock.getId()).thenReturn(deployment1Id);
        when(deployment1Mock.getChangelog()).thenReturn(Changelog.builder().jiraIssueKeys(Set.of("JIRA-1234", "JIRA-2345")).build());

        when(deployment3Mock.getChangelog()).thenReturn(Changelog.builder().jiraIssueKeys(Set.of()).build());

        List<Deployment> deployments = List.of(deployment1Mock, deployment2Mock, deployment3Mock);
        when(deploymentRepositoryMock.findAllDeploymentsForSystemStartedBetween(system, from, to)).thenReturn(deployments);
        when(deploymentPageRepositoryMock.findDeploymentPageByDeploymentId(deployment1Id)).thenReturn(Optional.of(deploymentPage1Mock));

        // when
        documentationGenerator.generateJiraLinksForSystem(systemName, from, to);

        // then
        verify(jiraAdapterMock).updateJiraIssuesWithConfluenceLink(Set.of("JIRA-1234", "JIRA-2345"), pageId);
        verify(deploymentPageRepositoryMock, never()).findDeploymentPageByDeploymentId(deployment2Id);
        verify(deploymentPageRepositoryMock, never()).findDeploymentPageByDeploymentId(deployment3Id);

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
                deploymentListPageRepositoryMock);

        String systemName = "SYSTEM A";
        System system = new System(systemName);
        doReturn(SystemPageDto.builder().name(systemName).build()).when(generatorServiceMock).createSystemPageDto(system);
        doReturn(List.of(system)).when(systemRepositoryMock).findAll();

        // when
        generator.generateAllPages();

        // then - the top-level system page is created under exactly the configured root page id
        verify(confluenceAdapterMock).addOrUpdatePageUnderAncestor(eq(configuredRootPageId), eq(systemName), anyString());
    }

    @BeforeEach
    void setUp() {
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
                deploymentListPageRepositoryMock);
    }
}

