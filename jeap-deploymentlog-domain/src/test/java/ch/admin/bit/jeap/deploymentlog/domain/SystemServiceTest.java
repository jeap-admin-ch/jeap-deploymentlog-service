package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock
    private SystemRepository systemRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private ComponentRepository componentRepository;
    @Mock
    private SystemAliasRepository systemAliasRepository;
    @Mock
    private EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;
    @InjectMocks
    private SystemService systemService;
    @Captor
    ArgumentCaptor<SystemAlias> systemAliasCaptor;

    @Test
    void deleteComponent_withoutEnvironment_componentDeactivated() {

        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        assertThrows(EnvironmentNotFoundException.class, () -> systemService.deleteComponent("system", "component", null));
    }

    @Test
    void deleteComponent_withEnvironment_componentInEnvDeletedAndComponentIsSetInactive() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {

        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        systemService.deleteComponent("system", "component", "test");

        verify(environmentComponentVersionStateRepository, times(1)).deleteByComponentEqualsAndEnvironmentEquals(component, environment);
        assertThat(component.isActive()).isFalse();
    }

    @Test
    void deleteComponent_withEnvironment_componentInEnvDeleted() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {

        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();

        Environment testEnvironment = new Environment("test");

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        when(environmentRepository.findByName("test")).thenReturn(Optional.of(testEnvironment));

        Deployment deployment = getDeployment(component, new Environment("dev"));
        EnvironmentComponentVersionState environmentComponentVersionState = EnvironmentComponentVersionState.fromDeployment(deployment);
        when(environmentComponentVersionStateRepository.findByComponentIn(singleton(component))).thenReturn(singletonList(environmentComponentVersionState));

        systemService.deleteComponent("system", "component", "test");

        verify(environmentComponentVersionStateRepository, times(1)).deleteByComponentEqualsAndEnvironmentEquals(component, testEnvironment);
        assertThat(component.isActive()).isTrue();
    }

    @Test
    void getPreviousVersionOfComponent_currentVersionIsDifferent_returnCurrentVersion() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {
        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();
        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));
        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        mockCurrentVersion("1.0.0");

        final Optional<String> previousVersionOfComponent = systemService.getPreviousVersionOfComponent("system", "component", "test", "2.0.0");

        verify(deploymentRepository, never()).getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString());
        assertThat(previousVersionOfComponent).isPresent();
        assertThat(previousVersionOfComponent.get()).contains("1.0.0");
    }

    @Test
    void getPreviousVersionOfComponent_noCurrentVersion_returnEmpty() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {
        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();
        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));
        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        final Optional<String> previousVersionOfComponent = systemService.getPreviousVersionOfComponent("system", "component", "test", "2.0.0");

        verify(deploymentRepository, never()).getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString());
        assertThat(previousVersionOfComponent).isEmpty();
    }

    @Test
    void getPreviousVersionOfComponent_currentVersionIsSame_returnPreviousVersion() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {

        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        mockCurrentVersion("1.2.3");

        final Deployment deployment = mockDeployment("1.0.0");
        when(deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString()))
                .thenReturn(Optional.of(deployment));

        final Optional<String> previousVersionOfComponent = systemService.getPreviousVersionOfComponent("system", "component", "test", "1.2.3");

        assertThat(previousVersionOfComponent).isPresent();
        assertThat(previousVersionOfComponent.get()).contains("1.0.0");
    }

    @Test
    void getPreviousVersionOfComponent_allPastVersionsAreSame_returnEmpty() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {

        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        mockCurrentVersion("1.2.3");

        when(deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString()))
                .thenReturn(Optional.empty());

        final Optional<String> previousVersionOfComponent = systemService.getPreviousVersionOfComponent("system", "component", "test", "1.2.3");

        assertThat(previousVersionOfComponent).isEmpty();
    }

    @Test
    void retrieveOrCreateComponent_systemAndComponentExist_returnComponent() {

        System system = getSystem();
        system.getComponents().add(new Component("component", system));

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        systemService.retrieveOrCreateComponent("system", "component");

        verify(systemRepository, never()).save(any(System.class));
        verify(componentRepository, never()).save(any(Component.class));

    }

    @Test
    void retrieveOrCreateComponent_systemAndComponentNotExist_allCreated() {

        when(systemRepository.save(any(System.class))).thenReturn(getSystem());
        when(componentRepository.save(any(Component.class))).thenReturn(new Component("component", getSystem()));

        systemService.retrieveOrCreateComponent("system", "component");

        verify(systemRepository, times(1)).save(any(System.class));
        verify(componentRepository, times(1)).save(any(Component.class));

    }

    @Test
    void retrieveOrCreateComponent_systemWithAliasExists_returnComponent() {
        System system = getSystem();
        system.getComponents().add(new Component("component", system));
        SystemAlias systemAlias = new SystemAlias("systemAlias", system);
        when(systemAliasRepository.findByName("systemAlias")).thenReturn(Optional.of(systemAlias));

        systemService.retrieveOrCreateComponent("systemAlias", "component");

        verify(systemRepository, never()).save(any(System.class));
        verify(componentRepository, never()).save(any(Component.class));
    }

    @Test
    void retrieveOrCreateComponent_systemIgnoreCaseExists_returnComponent() {
        System system = getSystem("TEST-SYSTEM");
        system.getComponents().add(new Component("component", system));
        when(systemRepository.findByNameIgnoreCase("test-system")).thenReturn(Optional.of(system));

        systemService.retrieveOrCreateComponent("test-system", "component");

        verify(systemRepository, never()).save(any(System.class));
        verify(componentRepository, never()).save(any(Component.class));
    }

    @Test
    void retrieveOrCreateComponent_ComponentNotExist_componentCreated() {
        System system = getSystem();
        system.getComponents().add(new Component("component", system));

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        systemService.retrieveOrCreateComponent("system", "componentNew");

        verify(systemRepository, never()).save(any(System.class));
        verify(componentRepository, times(1)).save(any(Component.class));
    }

    @Test
    void getPreviousDeploymentOfComponent_currentVersionIsDifferent_returnCurrentVersion() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {
        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();
        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));
        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        String version = "1.0.0";

        Deployment mockDeployment = mockDeployment(version);
        EnvironmentComponentVersionState mockEnvironmentComponentVersionState = mock(EnvironmentComponentVersionState.class);
        ComponentVersion mockComponentVersion = mock(ComponentVersion.class);
        when(mockEnvironmentComponentVersionState.getDeployment()).thenReturn(mockDeployment);
        when(mockComponentVersion.getVersionName()).thenReturn(version);
        when(mockEnvironmentComponentVersionState.getComponentVersion()).thenReturn(mockComponentVersion);

        when(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(any(), any())).thenReturn(Optional.of(mockEnvironmentComponentVersionState));

        final Optional<Deployment> previousDeploymentOfComponent = systemService.getPreviousDeploymentOfComponent("system", "component", "test", "2.0.0");

        verify(deploymentRepository, never()).getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString());
        assertThat(previousDeploymentOfComponent).isPresent();
        assertThat(previousDeploymentOfComponent.get().getComponentVersion().getVersionName()).contains(version);
    }

    @Test
    void getPreviousDeploymentOfComponent_noCurrentVersion_returnEmpty() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {
        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();
        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));
        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        final Optional<Deployment> previousDeploymentOfComponent = systemService.getPreviousDeploymentOfComponent("system", "component", "test", "2.0.0");

        verify(deploymentRepository, never()).getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString());
        assertThat(previousDeploymentOfComponent).isEmpty();
    }

    @Test
    void getPreviousDeploymentOfComponent_currentVersionIsSame_returnPreviousDeployment() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {

        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();

        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        mockCurrentVersion("1.2.3");

        final Deployment deployment = mockDeployment("1.0.0");
        when(deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString()))
                .thenReturn(Optional.of(deployment));

        final Optional<Deployment> previousVersionOfComponent = systemService.getPreviousDeploymentOfComponent("system", "component", "test", "1.2.3");

        assertThat(previousVersionOfComponent).isPresent();
        assertThat(previousVersionOfComponent.get().getComponentVersion().getVersionName()).contains("1.0.0");
    }

    @Test
    void getPreviousDeploymentOfComponent_withSystemAlias_currentVersionIsSame_returnPreviousDeployment() throws ComponentNotFoundException, SystemNotFoundException, EnvironmentNotFoundException {
        System system = getSystem();
        Component component = new Component("component", system);
        system.getComponents().add(component);
        assertThat(component.isActive()).isTrue();

        when(systemRepository.findByNameIgnoreCase("aliasName")).thenReturn(Optional.empty());
        SystemAlias systemAlias = mock(SystemAlias.class);
        when(systemAlias.getSystem()).thenReturn(system);
        when(systemAliasRepository.findByName("aliasName")).thenReturn(Optional.of(systemAlias));

        Environment environment = new Environment("test");
        when(environmentRepository.findByName(anyString())).thenReturn(Optional.of(environment));

        mockCurrentVersion("1.2.3");

        final Deployment deployment = mockDeployment("1.0.0");
        when(deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(any(), any(), anyString()))
                .thenReturn(Optional.of(deployment));

        final Optional<Deployment> previousVersionOfComponent = systemService.getPreviousDeploymentOfComponent("aliasName", "component", "test", "1.2.3");

        assertThat(previousVersionOfComponent).isPresent();
        assertThat(previousVersionOfComponent.get().getComponentVersion().getVersionName()).contains("1.0.0");
    }

    @Test
    void createAlias_systemNotFound_returnException() {
        assertThrows(SystemNotFoundException.class, () -> systemService.createAlias("system", "alias"));
        verify(systemAliasRepository, never()).save(any());
    }

    @Test
    void createAlias_systemFound_aliasAdded() throws SystemNotFoundException, AliasNameAlreadyDefinedException, SystemNameAlreadyDefinedException {
        System system = getSystem();
        when(systemRepository.findByNameIgnoreCase("system")).thenReturn(Optional.of(system));

        systemService.createAlias("system", "alias");
        verify(systemAliasRepository).save(systemAliasCaptor.capture());
        SystemAlias aliasCaptorValue = systemAliasCaptor.getValue();
        assertThat(aliasCaptorValue.getSystem()).isEqualTo(system);
        assertThat(aliasCaptorValue.getName()).isEqualTo("alias");
    }

    @Test
    void createAlias_aliasAlreadyExists_returnException() {
        System system = getSystem();
        when(systemRepository.findByNameIgnoreCase("system")).thenReturn(Optional.of(system));
        when(systemAliasRepository.findByName(anyString())).thenReturn(Optional.of(new SystemAlias("alias", system)));

        String message = assertThrows(AliasNameAlreadyDefinedException.class, () -> systemService.createAlias("system", "alias")).getMessage();

        verify(systemAliasRepository, never()).save(any());
        assertThat(message).isEqualTo("The alias 'alias' is already defined");
    }

    @Test
    void createAlias_aliasAlreadyExistsAsSystem_returnException() {
        System system = getSystem();
        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        String message = assertThrows(SystemNameAlreadyDefinedException.class, () -> systemService.createAlias("system", "alias")).getMessage();

        verify(systemAliasRepository, never()).save(any());
        assertThat(message).isEqualTo("The system 'alias' is already defined");
    }

    @Test
    void updateSystemName_systemNotFound_returnException() {
        assertThrows(SystemNotFoundException.class, () -> systemService.updateSystemName("oldSystemName", "newSystemName"));
        verify(systemAliasRepository, never()).save(any());
    }

    @Test
    void updateSystemName_systemFound_aliasAdded() throws SystemNotFoundException, AliasNameAlreadyDefinedException, SystemNameAlreadyDefinedException {
        System system = getSystem();
        when(systemRepository.findByNameIgnoreCase("oldSystemName")).thenReturn(Optional.of(system));

        System updatedSystem = systemService.updateSystemName("oldSystemName", "newSystemName");

        assertThat(updatedSystem.getName()).isEqualTo("newSystemName");
        verify(systemAliasRepository).save(systemAliasCaptor.capture());
        SystemAlias aliasCaptorValue = systemAliasCaptor.getValue();
        assertThat(aliasCaptorValue.getSystem()).isEqualTo(system);
        assertThat(aliasCaptorValue.getName()).isEqualTo("oldSystemName".toLowerCase());
    }

    @Test
    void updateSystemName_aliasAlreadyExists_returnException() {
        System system = getSystem();
        when(systemRepository.findByNameIgnoreCase("oldSystemName")).thenReturn(Optional.of(system));
        when(systemAliasRepository.findByName(anyString())).thenReturn(Optional.of(new SystemAlias("newSystemName", system)));

        String message = assertThrows(AliasNameAlreadyDefinedException.class, () -> systemService.updateSystemName("oldSystemName", "newSystemName")).getMessage();

        verify(systemAliasRepository, never()).save(any());
        assertThat(message).isEqualTo("The alias 'newSystemName' is already defined");
    }

    @Test
    void updateSystemName_aliasAlreadyExistsAsSystem_returnException() {
        System system = getSystem();
        when(systemRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(system));

        String message = assertThrows(SystemNameAlreadyDefinedException.class, () -> systemService.updateSystemName("oldSystemName", "newSystemName")).getMessage();

        verify(systemAliasRepository, never()).save(any());
        assertThat(message).isEqualTo("The system 'newSystemName' is already defined");
    }

    @Test
    void mergeSystems_systemFound_aliasAdded() {
        System system1 = getSystem("test1");
        System system2 = getSystem("test2");
        system2.getComponents().add(mock(Component.class));
        system2.getComponents().add(mock(Component.class));
        assertThat(system2.getComponents()).hasSize(2);
        assertThat(system1.getComponents()).isEmpty();

        systemService.mergeSystems(system1, system2);

        verify(systemRepository, times(1)).delete(system2);
        assertThat(system1.getComponents()).hasSize(2);
        assertThat(system2.getComponents()).isEmpty();
        verify(systemAliasRepository).save(systemAliasCaptor.capture());
        SystemAlias aliasCaptorValue = systemAliasCaptor.getValue();
        assertThat(aliasCaptorValue.getSystem()).isEqualTo(system1);
        assertThat(aliasCaptorValue.getName()).isEqualTo(system2.getName().toLowerCase());
    }

    private void mockCurrentVersion(String versionName) {
        EnvironmentComponentVersionState mockEnvironmentComponentVersionState = mock(EnvironmentComponentVersionState.class);
        ComponentVersion mockComponentVersion = mock(ComponentVersion.class);
        when(mockComponentVersion.getVersionName()).thenReturn(versionName);
        when(mockEnvironmentComponentVersionState.getComponentVersion()).thenReturn(mockComponentVersion);
        when(environmentComponentVersionStateRepository.findByEnvironmentAndComponent(any(), any())).thenReturn(Optional.of(mockEnvironmentComponentVersionState));
    }

    private Deployment mockDeployment(String versionName) {
        final Deployment mockDeployment = mock(Deployment.class);
        final ComponentVersion mockComponentVersion = mock(ComponentVersion.class);
        when(mockComponentVersion.getVersionName()).thenReturn(versionName);
        when(mockDeployment.getComponentVersion()).thenReturn(mockComponentVersion);
        return mockDeployment;
    }


    private static System getSystem() {
        return getSystem("test");
    }

    private static System getSystem(String name) {
        return new System(name);
    }

    private static Deployment getDeployment(Component component, Environment devEnvironment) {
        DeploymentUnit deploymentUnit = DeploymentUnit.builder()
                .artifactRepositoryUrl("repoUrl")
                .coordinates("coord")
                .type(DeploymentUnitType.DOCKER_IMAGE)
                .build();
        ComponentVersion componentVersion = ComponentVersion.builder()
                .component(component)
                .versionName("v1")
                .versionControlUrl("dummy")
                .commitRef("commitRef")
                .committedAt(ZonedDateTime.now())
                .deploymentUnit(deploymentUnit)
                .build();
        return Deployment.builder()
                .externalId("someId")
                .environment(devEnvironment)
                .componentVersion(componentVersion)
                .startedAt(ZonedDateTime.now())
                .startedBy("Hans")
                .sequence(DeploymentSequence.FIRST)
                .build();
    }

}
