package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentPageNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidDeploymentStateForUpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private SystemRepository systemRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private ComponentRepository componentRepository;
    @Mock
    private Deployment deploymentMock;
    @Mock
    private ComponentVersion componentVersionMock;
    @Mock
    private Component componentMock;
    @Mock
    private System systemMock;
    @Mock
    private DeploymentPageRepository deploymentPageRepository;
    @Mock
    private EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;
    @Mock
    private SystemService systemService;
    @InjectMocks
    private DeploymentService deploymentService;
    @Captor
    ArgumentCaptor<Deployment> deploymentCaptor;


    @Test
    void createDeployment_envExists_deploymentCreated() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(new Environment("test")));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);

        deploymentService.createDeployment(
                "externalId", "1.2.3-4",
                ZonedDateTime.now(), "test", "test", ZonedDateTime.now(),
                true, "system", "component", "environment",
                new DeploymentTarget("CF", "http://localhost/cf", "details"),
                ZonedDateTime.now(), "user", getDeploymentUnit(), Collections.emptySet(), Map.of(), Set.of(),
                "comment", "1.1.0", Set.of("PROJ-123"), null, null);

        verify(deploymentRepository, times(1)).save(any(Deployment.class));
        verify(systemRepository, never()).save(any(System.class));
        verify(componentRepository, never()).save(any(Component.class));
        verify(environmentRepository, never()).save(any(Environment.class));
    }

    @Test
    void createDeployment_envNotExists_envCreated() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.save(any(Environment.class))).thenReturn(new Environment("test"));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);

        deploymentService.createDeployment("externalId", "1.2.3-4", ZonedDateTime.now(),
                "test", "test", ZonedDateTime.now(), true,
                "system", "component", "environment",
                new DeploymentTarget("CF", "http://localhost/cf", "details"),
                ZonedDateTime.now(),
                "user", getDeploymentUnit(), Collections.emptySet(), Map.of(), Set.of(),
                "comment", "1.1.0", Set.of("PROJ-123"), null, null);

        verify(deploymentRepository, times(1)).save(any(Deployment.class));
        verify(environmentRepository, times(1)).save(any(Environment.class));

    }

    @Test
    void createDeployment_withoutTarget_deploymentCreated() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(new Environment("test")));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);

        deploymentService.createDeployment(
                "externalId", "1.2.3-4",
                ZonedDateTime.now(), "test", "test", ZonedDateTime.now(),
                true, "system", "component", "environment",
                null,
                ZonedDateTime.now(), "user", getDeploymentUnit(), Collections.emptySet(), Map.of(), Set.of(),
                "comment", "1.1.0", Set.of("PROJ-123"), null, null);

        verify(deploymentRepository, times(1)).save(any(Deployment.class));
        verify(systemRepository, never()).save(any(System.class));
        verify(componentRepository, never()).save(any(Component.class));
        verify(environmentRepository, never()).save(any(Environment.class));

    }

    @Test
    void updateState_snapshotExists_updated() throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {

        Deployment deployment = getDeploymentWithTypes(DeploymentType.CODE);
        deployment.success(ZonedDateTime.now(), "great success");

        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(deployment));
        when(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(any(Environment.class), any(Component.class))).thenReturn(
                Optional.of(EnvironmentComponentVersionState.fromDeployment(deployment)));

        deploymentService.updateState("externalId", DeploymentState.SUCCESS, "great success", ZonedDateTime.now(),
                Map.of("test-prop", "test-value"));

        verify(environmentComponentVersionStateRepository, times(1))
                .findByEnvironmentAndComponent(any(Environment.class), any(Component.class));
        verify(environmentComponentVersionStateRepository, never())
                .save(any(EnvironmentComponentVersionState.class));
        assertThat(deployment.getProperties())
                .containsEntry("test-prop", "test-value");
    }

    @Test
    void updateEnvironmentComponentVersionState_whenNotCodeType_thenNoUpdateOrSave() {
        Deployment deployment = mock(Deployment.class);
        when(deployment.getDeploymentTypes()).thenReturn(EnumSet.of(DeploymentType.INFRASTRUCTURE));
        when(deployment.getExternalId()).thenReturn("externalId");

        deploymentService.updateEnvironmentComponentVersionState(deployment);

        verify(environmentComponentVersionStateRepository, never())
                .findByEnvironmentAndComponent(any(), any());
        verify(environmentComponentVersionStateRepository, never())
                .save(any(EnvironmentComponentVersionState.class));
    }

    @Test
    void updateState_snapshotNotExists_created_noDeploymentType() throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {

        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(getDeployment()));

        deploymentService.updateState("externalId", DeploymentState.SUCCESS, "great success", ZonedDateTime.now(), Map.of());

        verify(environmentComponentVersionStateRepository, times(1)).findByEnvironmentAndComponent(any(Environment.class), any(Component.class));
        verify(environmentComponentVersionStateRepository, times(1)).save(any(EnvironmentComponentVersionState.class));

    }

    @Test
    void updateState_snapshotNotExists_created_emptyDeploymentType() throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {

        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(getDeploymentWithTypes()));

        deploymentService.updateState("externalId", DeploymentState.SUCCESS, "great success", ZonedDateTime.now(), Map.of());

        verify(environmentComponentVersionStateRepository, times(1)).findByEnvironmentAndComponent(any(Environment.class), any(Component.class));
        verify(environmentComponentVersionStateRepository, times(1)).save(any(EnvironmentComponentVersionState.class));

    }

    @Test
    void updateState_snapshotNotExists_created_codeDeploymentType() throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {

        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(getDeploymentWithTypes(DeploymentType.CODE)));

        deploymentService.updateState("externalId", DeploymentState.SUCCESS, "great success", ZonedDateTime.now(), Map.of());

        verify(environmentComponentVersionStateRepository, times(1)).findByEnvironmentAndComponent(any(Environment.class), any(Component.class));
        verify(environmentComponentVersionStateRepository, times(1)).save(any(EnvironmentComponentVersionState.class));

    }

    @Test
    void updateState_snapshotNotExists_created_codeAndInfraDeploymentType() throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {

        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(getDeploymentWithTypes(DeploymentType.CODE, DeploymentType.INFRASTRUCTURE)));

        deploymentService.updateState("externalId", DeploymentState.SUCCESS, "great success", ZonedDateTime.now(), Map.of());

        verify(environmentComponentVersionStateRepository, times(1)).findByEnvironmentAndComponent(any(Environment.class), any(Component.class));
        verify(environmentComponentVersionStateRepository, times(1)).save(any(EnvironmentComponentVersionState.class));

    }

    @Test
    void updateState_failure_notCreated() throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {

        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(getDeployment()));

        deploymentService.updateState("externalId", DeploymentState.FAILURE, "badly failed", ZonedDateTime.now(), Map.of());

        verify(environmentComponentVersionStateRepository, never()).findByEnvironmentAndComponent(any(Environment.class), any(Component.class));
        verify(environmentComponentVersionStateRepository, never()).save(any(EnvironmentComponentVersionState.class));

    }

    @Test
    void updateState_snapshotExists_doNotUpdateOnUndeployment() throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {

        final Deployment deployment = getUndeployment();
        deployment.success(ZonedDateTime.now(), "great success");

        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(deployment));

        deploymentService.updateState("externalId", DeploymentState.SUCCESS, "great success", ZonedDateTime.now(), Map.of());

        verify(environmentComponentVersionStateRepository, never()).save(any(EnvironmentComponentVersionState.class));
    }

    @Test
    void getOutdatedNonProductiveDeploymentPages() {
        UUID systemId = UUID.randomUUID();
        when(systemRepository.getAllSystemIds()).thenReturn(List.of(systemId));
        Environment env = new Environment("dev");
        List<Environment> envs = List.of(env);
        when(environmentRepository.findNonProductiveEnvironmentsForSystemId(systemId)).thenReturn(envs);
        DeploymentPage third = createDeploymentPage(ZonedDateTime.now().minusDays(7));
        DeploymentPage fourth = createDeploymentPage(ZonedDateTime.now().minusDays(7));
        List<DeploymentPage> allPages = List.of(
                createDeploymentPage(ZonedDateTime.now().minusDays(1)), // First two pages are kept regardless of timestamp
                createDeploymentPage(ZonedDateTime.now().minusDays(1)),
                third, // Third and fourth page should be removed
                fourth, // (only 2 pages are kept, and date is older than minAge)
                createDeploymentPage(ZonedDateTime.now().minusDays(1)) // Fifth page is kept as well as it is not yet 2 days old
        );
        when(deploymentPageRepository.getSystemDeploymentPagesForEnvironments(systemId, envs)).thenReturn(allPages);
        when(deploymentRepository.findById(any(UUID.class))).thenReturn(Optional.of(deploymentMock));
        when(deploymentMock.getEnvironment()).thenReturn(env);
        when(deploymentMock.getComponentVersion()).thenReturn(componentVersionMock);
        when(componentVersionMock.getComponent()).thenReturn(componentMock);

        Duration minAge = Duration.ofDays(2);
        List<DeploymentPage> pages = deploymentService.getOutdatedNonProductiveDeploymentPages(minAge, 2);

        Set<UUID> removedPageIds = pages.stream().map(DeploymentPage::getId).collect(toSet());
        assertTrue(removedPageIds.contains(third.getId()), "first 7-day old page is removed");
        assertTrue(removedPageIds.contains(fourth.getId()), "second 7-day old page is removed");
        assertEquals(2, pages.size());
    }

    @Test
    void canDeletePageForComponent_whenDeploymentNotFound_thenTrue() {
        DeploymentPage page = createDeploymentPage(ZonedDateTime.now());

        boolean result = deploymentService.canDeleteDeploymentPage(page);

        assertTrue(result);
    }

    @Test
    void canDeletePageForComponent_whenPageIsOlderThanLastSuccess_thenTrue() {
        Environment env = new Environment("dev");
        Deployment lastSuccessfulDeploymentMock = mock(Deployment.class);
        when(lastSuccessfulDeploymentMock.getId()).thenReturn(UUID.randomUUID());
        when(lastSuccessfulDeploymentMock.getStartedAt()).thenReturn(ZonedDateTime.now().minusDays(1));

        DeploymentPage page = createDeploymentPage(ZonedDateTime.now());
        // Last successful deployment is newer than this page --> delete it
        when(deploymentMock.getStartedAt()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(deploymentMock.getComponentVersion()).thenReturn(componentVersionMock);
        when(deploymentMock.getEnvironment()).thenReturn(env);
        when(componentVersionMock.getComponent()).thenReturn(componentMock);
        when(deploymentRepository.findById(page.getDeploymentId()))
                .thenReturn(Optional.of(deploymentMock));
        when(deploymentRepository.getLastSuccessfulDeploymentForComponent(componentMock, env))
                .thenReturn(Optional.of(lastSuccessfulDeploymentMock));

        boolean result = deploymentService.canDeleteDeploymentPage(page);

        assertTrue(result);
    }

    @Test
    void canDeletePageForComponent_whenPageIsForLastSuccess_thenFalse() {
        Environment env = new Environment("dev");

        DeploymentPage page = createDeploymentPage(ZonedDateTime.now());
        when(deploymentMock.getStartedAt()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(deploymentMock.getComponentVersion()).thenReturn(componentVersionMock);
        when(deploymentMock.getEnvironment()).thenReturn(env);
        // This page is for the last successful deployment --> delete it
        when(deploymentMock.getId()).thenReturn(page.getDeploymentId());
        when(componentVersionMock.getComponent()).thenReturn(componentMock);
        when(deploymentRepository.findById(page.getDeploymentId()))
                .thenReturn(Optional.of(deploymentMock));
        when(deploymentRepository.getLastSuccessfulDeploymentForComponent(componentMock, env))
                .thenReturn(Optional.of(deploymentMock));

        boolean result = deploymentService.canDeleteDeploymentPage(page);

        assertFalse(result);
    }

    @Test
    void canDeletePageForComponent_whenPageIsForNewerThanLastSuccess_thenFalse() {
        Environment env = new Environment("dev");
        Deployment lastSuccessfulDeploymentMock = mock(Deployment.class);
        when(lastSuccessfulDeploymentMock.getId()).thenReturn(UUID.randomUUID());
        when(lastSuccessfulDeploymentMock.getStartedAt()).thenReturn(ZonedDateTime.now().minusDays(2));

        DeploymentPage page = createDeploymentPage(ZonedDateTime.now());
        // Last successful deployment is older than this page --> keep it
        when(deploymentMock.getStartedAt()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(deploymentMock.getComponentVersion()).thenReturn(componentVersionMock);
        when(deploymentMock.getEnvironment()).thenReturn(env);
        when(componentVersionMock.getComponent()).thenReturn(componentMock);
        when(deploymentRepository.findById(page.getDeploymentId()))
                .thenReturn(Optional.of(deploymentMock));
        when(deploymentRepository.getLastSuccessfulDeploymentForComponent(componentMock, env))
                .thenReturn(Optional.of(lastSuccessfulDeploymentMock));

        boolean result = deploymentService.canDeleteDeploymentPage(page);

        assertFalse(result);
    }

    @Test
    void canDeletePageForComponent_whenPageIsForNewestDeployment_thenFalse() {
        Environment env = new Environment("dev");

        DeploymentPage page = createDeploymentPage(ZonedDateTime.now());
        when(deploymentMock.getComponentVersion()).thenReturn(componentVersionMock);
        when(deploymentMock.getEnvironment()).thenReturn(env);
        // This page is for the last deployment of the component --> keep it
        when(deploymentMock.getId()).thenReturn(page.getDeploymentId());
        when(componentVersionMock.getComponent()).thenReturn(componentMock);
        when(deploymentRepository.findById(page.getDeploymentId()))
                .thenReturn(Optional.of(deploymentMock));
        when(deploymentRepository.getLastDeploymentForComponent(componentMock, env))
                .thenReturn(Optional.of(deploymentMock));

        boolean result = deploymentService.canDeleteDeploymentPage(page);

        assertFalse(result);
    }

    @Test
    void getSystemAndEnvsForDeploymentIds() {
        UUID deploymentId = UUID.randomUUID();
        UUID systemId = UUID.randomUUID();
        Set<UUID> deploymentIds = Set.of(deploymentId);
        when(deploymentMock.getComponentVersion()).thenReturn(componentVersionMock);
        when(componentVersionMock.getComponent()).thenReturn(componentMock);
        when(componentMock.getSystem()).thenReturn(systemMock);
        when(systemMock.getId()).thenReturn(systemId);
        Environment dev = new Environment("dev");
        when(deploymentMock.getEnvironment()).thenReturn(dev);
        when(deploymentRepository.getById(deploymentId)).thenReturn(deploymentMock);

        Set<SystemEnv> systemAndEnvsForDeploymentIds = deploymentService.getSystemAndEnvsForDeploymentIds(deploymentIds);

        assertEquals(1, systemAndEnvsForDeploymentIds.size());
        assertEquals(systemId, systemAndEnvsForDeploymentIds.iterator().next().getSystemId());
        assertEquals(dev.getId(), systemAndEnvsForDeploymentIds.iterator().next().getEnvId());
    }

    @Test
    void getDeploymentPage_pageFound_pageReturned() throws DeploymentNotFoundException, DeploymentPageNotFoundException {
        final Deployment deployment = getDeployment();
        final DeploymentPage mockDeploymentPage = mock(DeploymentPage.class);
        final String pageId = "myPageId";
        when(mockDeploymentPage.getPageId()).thenReturn(pageId);
        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(deployment));
        when(deploymentPageRepository.findDeploymentPageByDeploymentId(any(UUID.class))).thenReturn(Optional.of(mockDeploymentPage));
        final DeploymentPage deploymentPage = deploymentService.getDeploymentPage("externalId");
        assertEquals(pageId, deploymentPage.getPageId());
    }

    @Test
    void getDeploymentPage_deploymentNotFound_deploymentNotFoundExceptionReturned() {
        assertThatThrownBy(() -> deploymentService.getDeploymentPage("externalId")).isInstanceOf(DeploymentNotFoundException.class);
    }

    @Test
    void getDeploymentPage_pageNotFound_deploymentPageNotFoundExceptionReturned() {
        final Deployment deployment = getDeployment();
        when(deploymentRepository.findByExternalId(anyString())).thenReturn(Optional.of(deployment));
        assertThatThrownBy(() -> deploymentService.getDeploymentPage("externalId")).isInstanceOf(DeploymentPageNotFoundException.class);
    }

    @Test
    void createDeployment_newDeployment_sequenceIsNew() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(new Environment("test")));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);
        EnvironmentComponentVersionState snapshot = mock(EnvironmentComponentVersionState.class);
        ComponentVersion mockComponentVersion = mock(ComponentVersion.class);
        when(mockComponentVersion.getVersionName()).thenReturn("1.0.0");
        when(snapshot.getComponentVersion()).thenReturn(mockComponentVersion);
        when(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(any(Environment.class), any(Component.class))).thenReturn(Optional.of(snapshot));

        deploymentService.createDeployment(
                "externalId", "1.2.3-4",
                ZonedDateTime.now(), "test", "test", ZonedDateTime.now(),
                true, "system", "component", "environment",
                new DeploymentTarget("CF", "http://localhost/cf", "details"),
                ZonedDateTime.now(), "user", getDeploymentUnit(), Collections.emptySet(), Map.of(), Set.of(),
                "comment", "1.1.0", Set.of("PROJ-123"), null, null);

        verify(deploymentRepository).save(deploymentCaptor.capture());
        assertThat(deploymentCaptor.getValue().getSequence()).isEqualTo(DeploymentSequence.NEW);

    }

    @Test
    void createDeployment_withDeploymentTypes_deploymentTypesSaved() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(new Environment("test")));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);
        EnvironmentComponentVersionState snapshot = mock(EnvironmentComponentVersionState.class);
        ComponentVersion mockComponentVersion = mock(ComponentVersion.class);
        when(mockComponentVersion.getVersionName()).thenReturn("1.0.0");
        when(snapshot.getComponentVersion()).thenReturn(mockComponentVersion);
        when(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(any(Environment.class), any(Component.class))).thenReturn(Optional.of(snapshot));

        deploymentService.createDeployment(
                "externalId", "1.2.3-4",
                ZonedDateTime.now(), "test", "test", ZonedDateTime.now(),
                true, "system", "component", "environment",
                new DeploymentTarget("CF", "http://localhost/cf", "details"),
                ZonedDateTime.now(), "user", getDeploymentUnit(), Collections.emptySet(), Map.of(), Set.of(),
                "comment", "1.1.0", Set.of("PROJ-123"), null,
                Set.of(DeploymentType.CODE, DeploymentType.INFRASTRUCTURE));

        verify(deploymentRepository).save(deploymentCaptor.capture());
        assertThat(deploymentCaptor.getValue().getSequence()).isEqualTo(DeploymentSequence.NEW);
        assertThat(deploymentCaptor.getValue().getDeploymentTypes()).containsOnly(DeploymentType.CODE, DeploymentType.INFRASTRUCTURE);

    }

    @Test
    void createDeployment_repeatedDeployment_sequenceIsRepeated() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(new Environment("test")));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);
        EnvironmentComponentVersionState snapshot = mock(EnvironmentComponentVersionState.class);
        ComponentVersion mockComponentVersion = mock(ComponentVersion.class);
        when(mockComponentVersion.getVersionName()).thenReturn("1.0.0");
        when(snapshot.getComponentVersion()).thenReturn(mockComponentVersion);
        when(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(any(Environment.class), any(Component.class))).thenReturn(Optional.of(snapshot));

        deploymentService.createDeployment(
                "externalId", "1.0.0",
                ZonedDateTime.now(), "test", "test", ZonedDateTime.now(),
                true, "system", "component", "environment",
                new DeploymentTarget("CF", "http://localhost/cf", "details"),
                ZonedDateTime.now(), "user", getDeploymentUnit(), Collections.emptySet(), Map.of(), Set.of(),
                "comment", "1.1.0", Set.of("PROJ-123"), null, null);

        verify(deploymentRepository).save(deploymentCaptor.capture());
        assertThat(deploymentCaptor.getValue().getSequence()).isEqualTo(DeploymentSequence.REPEATED);

    }

    @Test
    void createDeployment_firstDeployment_sequenceIsFirst() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(new Environment("test")));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);

        deploymentService.createDeployment(
                "externalId", "1.0.0",
                ZonedDateTime.now(), "test", "test", ZonedDateTime.now(),
                true, "system", "component", "environment",
                new DeploymentTarget("CF", "http://localhost/cf", "details"),
                ZonedDateTime.now(), "user", getDeploymentUnit(), Collections.emptySet(), Map.of(), Set.of(),
                "comment", "1.1.0", Set.of("PROJ-123"), null, null);

        verify(deploymentRepository).save(deploymentCaptor.capture());
        assertThat(deploymentCaptor.getValue().getSequence()).isEqualTo(DeploymentSequence.FIRST);

    }

    @Test
    void createUndeployment_deploymentExists_undeploymentCreated() {
        when(systemService.retrieveOrCreateComponent(anyString(), anyString())).thenReturn(new Component("component", getSystem()));
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(new Environment("test")));
        when(deploymentRepository.save(any(Deployment.class))).thenReturn(deploymentMock);

        deploymentService.createUndeployment(getDeployment(),
                "externalId", "system", "component", "test",
                ZonedDateTime.now(), "user", null);

        verify(deploymentRepository, times(1)).save(any(Deployment.class));
        verify(systemRepository, never()).save(any(System.class));
        verify(componentRepository, never()).save(any(Component.class));
        verify(environmentRepository, never()).save(any(Environment.class));

    }

    private DeploymentPage createDeploymentPage(ZonedDateTime deploymentStateTimestamp) {
        return DeploymentPage.builder()
                .id(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .pageId(UUID.randomUUID().toString())
                .lastUpdatedAt(ZonedDateTime.now())
                .deploymentStateTimestamp(deploymentStateTimestamp)
                .build();
    }

    private Deployment getDeployment() {
        return getDeploymentWithTypes(DeploymentType.CODE);
    }

    private Deployment getDeploymentWithTypes(DeploymentType... deploymentTypes) {
        Environment environment = new Environment("test");
        DeploymentTarget deploymentTarget = new DeploymentTarget("test", "http://localhost/cf", "details");
        Component component = new Component("test", getSystem());
        ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName("test")
                .taggedAt(ZonedDateTime.now())
                .versionControlUrl("test")
                .component(component)
                .deploymentUnit(getDeploymentUnit())
                .publishedVersion(false)
                .committedAt(ZonedDateTime.now())
                .commitRef("test")
                .build();
        return Deployment.builder().externalId("externalId")
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(environment)
                .target(deploymentTarget)
                .componentVersion(componentVersion)
                .links(Collections.emptySet())
                .sequence(DeploymentSequence.NEW)
                .deploymentTypes(Set.of(deploymentTypes))
                .build();
    }

    private Deployment getUndeployment() {
        Environment environment = new Environment("test");
        DeploymentTarget deploymentTarget = new DeploymentTarget("test", "http://localhost/cf", "details");
        Component component = new Component("test", getSystem());
        ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName("test")
                .taggedAt(ZonedDateTime.now())
                .versionControlUrl("test")
                .component(component)
                .deploymentUnit(getDeploymentUnit())
                .publishedVersion(false)
                .committedAt(ZonedDateTime.now())
                .commitRef("test")
                .build();
        return Deployment.builder().externalId("externalId")
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(environment)
                .target(deploymentTarget)
                .componentVersion(componentVersion)
                .links(Collections.emptySet())
                .sequence(DeploymentSequence.UNDEPLOYED)
                .build();
    }

    private static DeploymentUnit getDeploymentUnit() {
        return DeploymentUnit.builder().artifactRepositoryUrl("test").coordinates("test").type(DeploymentUnitType.DOCKER_IMAGE).build();
    }

    private static System getSystem() {
        return new System("test");
    }
}
