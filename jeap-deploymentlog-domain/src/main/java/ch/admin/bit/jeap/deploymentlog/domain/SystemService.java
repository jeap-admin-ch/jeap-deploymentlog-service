package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static java.util.Collections.singleton;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Slf4j
public class SystemService {

    private final SystemRepository systemRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final ComponentRepository componentRepository;
    private final EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;
    private final SystemAliasRepository systemAliasRepository;

    @Transactional
    public void deleteComponent(String systemName, String componentName, String environmentName) throws SystemNotFoundException, EnvironmentNotFoundException, ComponentNotFoundException {
        final System system = retrieveSystemByName(systemName);
        final Component component = retrieveComponentByName(system, componentName);
        final Environment environment = retrieveEnvironmentByName(environmentName);

        environmentComponentVersionStateRepository.deleteByComponentEqualsAndEnvironmentEquals(component, environment);
        boolean componentNotDeployedAnywhere = environmentComponentVersionStateRepository.findByComponentIn(singleton(component)).isEmpty();
        if (componentNotDeployedAnywhere) {
            component.inactive();
        }
    }

    @TransactionalReadReplica
    public System retrieveSystemByName(String name) throws SystemNotFoundException {
        return retrieveSystemByNameOrAlias(name).orElseThrow(() -> new SystemNotFoundException(name));
    }

    @Transactional
    public Component retrieveComponentByName(String systemName, String componentName) throws SystemNotFoundException, ComponentNotFoundException {
        final System system = retrieveSystemByName(systemName);
        return retrieveComponentByName(system, componentName);
    }

    private Component retrieveComponentByName(System system, String componentName) throws ComponentNotFoundException {
        return system.getComponents().stream()
                .filter(c -> c.getName().equals(componentName))
                .findFirst()
                .orElseThrow(() -> new ComponentNotFoundException(system.getName(), componentName));
    }

    public Environment retrieveEnvironmentByName(String name) throws EnvironmentNotFoundException {
        return environmentRepository.findByName(name).orElseThrow(() -> new EnvironmentNotFoundException(name));
    }

    @TransactionalReadReplica
    public Optional<String> getCurrentVersionOfComponent(String systemName, String componentName, String environmentName) throws SystemNotFoundException, ComponentNotFoundException, EnvironmentNotFoundException {
        Component component = retrieveComponentByName(retrieveSystemByName(systemName), componentName);
        Environment environment = retrieveEnvironmentByName(environmentName);

        return environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environment, component)
                .map(envState -> envState.getComponentVersion().getVersionName());
    }

    @TransactionalReadReplica
    public Optional<Deployment> getCurrentDeploymentOfComponent(String systemName, String componentName, String environmentName) throws SystemNotFoundException, ComponentNotFoundException, EnvironmentNotFoundException {
        Component component = retrieveComponentByName(retrieveSystemByName(systemName), componentName);
        Environment environment = retrieveEnvironmentByName(environmentName);

        return environmentComponentVersionStateRepository.findByEnvironmentAndComponent(environment, component)
                .map(EnvironmentComponentVersionState::getDeployment);
    }

    /**
     * Retrieves the previous version of a component deployed in a specific environment,
     * excluding the current version.
     *
     * @param systemName      The name of the system.
     * @param componentName   The name of the component.
     * @param environmentName The name of the environment.
     * @param version         The current version of the component.
     * @return An Optional containing the previous version of the component, or empty if not found.
     * @throws SystemNotFoundException       If the system with the given name is not found.
     * @throws ComponentNotFoundException    If the component with the given name is not found.
     * @throws EnvironmentNotFoundException  If the environment with the given name is not found.
     */
    @TransactionalReadReplica
    public Optional<String> getPreviousVersionOfComponent(String systemName, String componentName, String environmentName, String version) throws SystemNotFoundException, ComponentNotFoundException, EnvironmentNotFoundException {

        // Get the current version of the component in the specified environment
        final Optional<String> currentVersionOfComponent = getCurrentVersionOfComponent(systemName, componentName, environmentName);

        // If there's no current version or it's different from the provided version,
        // return the current version
        if (currentVersionOfComponent.isEmpty() || !currentVersionOfComponent.get().equals(version)) {
            return currentVersionOfComponent;
        }

        // Retrieve the component and environment objects
        Component component = retrieveComponentByName(retrieveSystemByName(systemName), componentName);
        Environment environment = retrieveEnvironmentByName(environmentName);

        // Get the last successful deployment for the component in the environment,
        // excluding the provided version, and map it to the previous version if found
        final Optional<String> previousVersion = deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(component, environment, version)
                .map(d -> d.getComponentVersion().getVersionName());

        previousVersion.ifPresent(s -> log.info("Found previousVersion '{}'", s));
        return previousVersion;
    }

    /**
     * Retrieves the previous deployment of a component in a specific environment,
     * excluding the current version.
     *
     * @param systemName      The name of the system.
     * @param componentName   The name of the component.
     * @param environmentName The name of the environment.
     * @param version         The version of the component.
     * @return An Optional containing the previous deployment, or empty if not found.
     * @throws SystemNotFoundException       If the system with the given name is not found.
     * @throws ComponentNotFoundException    If the component with the given name is not found.
     * @throws EnvironmentNotFoundException  If the environment with the given name is not found.
     */
    @TransactionalReadReplica
    public Optional<Deployment> getPreviousDeploymentOfComponent(String systemName, String componentName, String environmentName, String version) throws SystemNotFoundException, ComponentNotFoundException, EnvironmentNotFoundException {

        // Get the current version of the component in the specified environment
        final Optional<String> currentVersionOfComponent = getCurrentVersionOfComponent(systemName, componentName, environmentName);

        // If there's no current version or it's different from the provided version,
        // return the current deployment of the component
        if (currentVersionOfComponent.isEmpty() || !currentVersionOfComponent.get().equals(version)) {
            return this.getCurrentDeploymentOfComponent(systemName, componentName, environmentName);
        }

        // Retrieve the component and environment objects
        Component component = retrieveComponentByName(retrieveSystemByName(systemName), componentName);
        Environment environment = retrieveEnvironmentByName(environmentName);

        // Get the last successful deployment for the component in the environment,
        // excluding the provided version
        final Optional<Deployment> previousDeployment = deploymentRepository.getLastSuccessfulDeploymentForComponentDifferentToVersion(component, environment, version);

        previousDeployment.ifPresent(s -> log.info("Found previousDeployment '{}'", s));
        return previousDeployment;
    }

    @Transactional
    public Component retrieveOrCreateComponent(String systemName, String componentName) {
        final System system = retrieveOrCreateSystemByNameOrAlias(systemName);
        final Optional<ch.admin.bit.jeap.deploymentlog.domain.Component> component = system.getComponents().stream().filter(c -> c.getName().equals(componentName)).findFirst();
        return component.orElseGet(() -> componentRepository.save(new ch.admin.bit.jeap.deploymentlog.domain.Component(componentName, system)));
    }

    private Optional<System> retrieveSystemByNameOrAlias(String systemName) {
        Optional<System> system = systemRepository.findByNameIgnoreCase(systemName);
        if (system.isPresent()) {
            log.debug("System found with name {}", system.get().getName());
            return system;
        }

        Optional<SystemAlias> systemAlias = systemAliasRepository.findByName(systemName);
        if (systemAlias.isPresent()) {
            log.debug("System alias found with name {}", systemName);
            return Optional.of(systemAlias.get().getSystem());
        }

        return Optional.empty();
    }

    private System retrieveOrCreateSystemByNameOrAlias(String systemName) {
        Optional<System> system = retrieveSystemByNameOrAlias(systemName);

        if (system.isPresent()) {
            return system.get();
        }

        log.info("System not found with name {}. Creating a new one...", systemName);
        return systemRepository.save(new System(systemName));
    }

    @Transactional
    public void createAlias(String systemName, String alias) throws SystemNotFoundException, AliasNameAlreadyDefinedException, SystemNameAlreadyDefinedException {
        System system = retrieveSystemByName(systemName);

        if (systemRepository.findByNameIgnoreCase(alias).isPresent()) {
            throw SystemNameAlreadyDefinedException.systemNameAlreadyDefined(alias);
        }

        if (systemAliasRepository.findByName(alias).isPresent()) {
            throw AliasNameAlreadyDefinedException.aliasNameAlreadyDefined(alias);
        }

        systemAliasRepository.save(new SystemAlias(alias, system));
    }

    @Transactional
    public System updateSystemName(String oldSystemName, String newSystemName) throws SystemNotFoundException, AliasNameAlreadyDefinedException, SystemNameAlreadyDefinedException {
        System system = retrieveSystemByName(oldSystemName);

        if (systemRepository.findByNameIgnoreCase(newSystemName).isPresent()) {
            throw SystemNameAlreadyDefinedException.systemNameAlreadyDefined(newSystemName);
        }

        if (systemAliasRepository.findByName(newSystemName).isPresent()) {
            throw AliasNameAlreadyDefinedException.aliasNameAlreadyDefined(newSystemName);
        }

        system.updateName(newSystemName);

        //Create alias with the old system name
        systemAliasRepository.save(new SystemAlias(oldSystemName, system));
        return system;
    }

    @Transactional
    public void mergeSystems(System system, System oldSystem) {
        logSystemComponents(oldSystem);
        logSystemComponents(system);

        // Move components from old system to main system
        log.info("Moving {} components from system {} into {}", oldSystem.getComponents().size(), oldSystem.getName(), system.getName());
        for (Component component : oldSystem.getComponents()) {
            log.info("Component {} assigned to system {}", component.getName(), system.getName());
            component.updateSystem(system);
            system.getComponents().add(component);
        }
        oldSystem.getComponents().clear();
        logSystemComponents(oldSystem);
        logSystemComponents(system);

        // Delete the old system
        systemRepository.delete(oldSystem);

        // Create alias with the old system name for the main system
        systemAliasRepository.save(new SystemAlias(oldSystem.getName(), system));
    }

    private void logSystemComponents(System system) {
        log.info("System '{}' contains {} components", system.getName(), system.getComponents().size());
    }

}
