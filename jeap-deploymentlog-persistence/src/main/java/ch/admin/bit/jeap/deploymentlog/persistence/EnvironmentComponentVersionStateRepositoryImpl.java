package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class EnvironmentComponentVersionStateRepositoryImpl implements EnvironmentComponentVersionStateRepository {

    private final JpaEnvironmentComponentVersionStateRepository jpaEnvironmentComponentVersionStateRepository;

    @Override
    public List<Component> findComponentsBySystem(System system) {
        return jpaEnvironmentComponentVersionStateRepository.findComponentsBySystemId(system.getId());
    }

    @Override
    public Optional<EnvironmentComponentVersionState> findByEnvironmentAndComponent(Environment environment, Component component) {
        return jpaEnvironmentComponentVersionStateRepository.findByEnvironmentAndComponent(environment, component);
    }

    @Override
    public Optional<EnvironmentComponentVersionState> findLastByEnvironmentAndComponentAndDeploymentTypeCode(Environment environment, Component component) {
        return jpaEnvironmentComponentVersionStateRepository.findTopByEnvironmentAndComponentAndDeployment_DeploymentTypesContainingOrderByDeployment_StartedAtDesc(environment, component, DeploymentType.CODE);
    }

    @Override
    public List<EnvironmentComponentVersionState> findByComponentIn(Set<Component> components) {
        return jpaEnvironmentComponentVersionStateRepository.findByComponentIn(components);
    }

    @Override
    public EnvironmentComponentVersionState save(EnvironmentComponentVersionState environmentComponentVersionState) {
        return jpaEnvironmentComponentVersionStateRepository.save(environmentComponentVersionState);
    }

    @Override
    public void deleteByComponentEqualsAndEnvironmentEquals(Component component, Environment environment) {
        jpaEnvironmentComponentVersionStateRepository.deleteByComponentEqualsAndEnvironmentEquals(component, environment);
    }

    @Override
    public List<ComponentVersionSummary> getDeployedComponentsOnEnvironment(Environment environment) {
        return jpaEnvironmentComponentVersionStateRepository.getDeployedComponentsOnEnvironment(environment.getId());
    }
}
