package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeploymentRepositoryImpl implements DeploymentRepository {

    private final JpaDeploymentRepository jpaDeploymentRepository;

    @Override
    public Deployment save(Deployment deployment) {
        return jpaDeploymentRepository.save(deployment);
    }

    @Override
    public Deployment getById(UUID id) {
        return jpaDeploymentRepository.findById(id).orElseThrow();
    }

    @Override
    public Optional<Deployment> findById(UUID id) {
        return jpaDeploymentRepository.findById(id);
    }

    @Override
    public Optional<Deployment> findByExternalId(String externalId) {
        return jpaDeploymentRepository.findByExternalId(externalId);
    }

    @Override
    public List<Deployment> findAllDeploymentForSystemAndEnv(System system, Environment environment) {
        return jpaDeploymentRepository.findAllDeploymentForSystemAndEnv(environment.getId(), system.getId());
    }

    @Override
    public List<Deployment> findAllDeploymentsForSystemStartedBetween(System system, ZonedDateTime from, ZonedDateTime to) {
        return jpaDeploymentRepository.findAllDeploymentsForSystemStartedBetween(system.getId(), from, to);
    }

    @Override
    public List<Integer> findAllDeploymentsYearsForSystemAndEnv(System system, Environment environment) {
        return jpaDeploymentRepository.findAllDeploymentForSystemAndEnv(environment.getId(), system.getId())
                .stream()
                .map(deployment -> deployment.getStartedAt().getYear())
                .distinct()
                .toList();
    }

    @Override
    public List<Deployment> findDeploymentForSystemAndEnvLimited(System system, Environment environment, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<Deployment> deploymentPage = jpaDeploymentRepository.findDeploymentForSystemAndEnvLimited(
                environment.getId(),
                system.getId(),
                pageable);
        return deploymentPage.stream().toList();
    }

    @Override
    public List<Deployment> findDeploymentForEnvLimited(Environment environment, ZonedDateTime minStartedAt, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<Deployment> deploymentPage = jpaDeploymentRepository.findDeploymentForEnvLimited(
                environment.getId(),
                minStartedAt,
                pageable);
        return deploymentPage.stream().toList();
    }

    @Override
    public List<UUID> getDeploymentIdsWithMissingOrOutdatedGeneratedPages(int limit, ZonedDateTime from, ZonedDateTime to) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("lastModified").descending());
        return jpaDeploymentRepository.getDeploymentIdsMissingOrOutdatedGeneratedPages(from, to, pageable);
    }

    @Override
    public long countDeploymentsWithMissingOrOutdatedGeneratedPages(ZonedDateTime from) {
        return jpaDeploymentRepository.countDeploymentsWithMissingOrOutdatedGeneratedPages(from);
    }

    @Override
    public Optional<Deployment> getLastDeploymentForComponent(ch.admin.bit.jeap.deploymentlog.domain.Component component,
                                                              Environment env) {
        List<Deployment> results = jpaDeploymentRepository.getLastDeploymentsForComponent(component, env, PageRequest.of(0, 1));
        return results.stream().findFirst();
    }

    @Override
    public Optional<Deployment> getLastSuccessfulDeploymentForComponent(ch.admin.bit.jeap.deploymentlog.domain.Component component,
                                                                        Environment env) {
        List<Deployment> results = jpaDeploymentRepository.getLastSuccessfulDeploymentsForComponent(component, env, PageRequest.of(0, 1));
        return results.stream().findFirst();
    }

    @Override
    public Optional<Deployment> getLastSuccessfulDeploymentForComponentDifferentToVersion(ch.admin.bit.jeap.deploymentlog.domain.Component component, Environment env, String version) {
        List<Deployment> results =  jpaDeploymentRepository.getSuccessfulDeploymentsForComponentDifferentToVersion(component, env, version, PageRequest.of(0, 1));
        return results.stream().findFirst();
    }

    @Override
    public String getSystemNameForDeployment(UUID deploymentId) {
        return jpaDeploymentRepository.getSystemNameForDeployment(deploymentId);
    }
}
