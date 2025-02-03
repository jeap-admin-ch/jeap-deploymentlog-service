package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EnvironmentRepositoryImpl implements EnvironmentRepository {

    private final JpaEnvironmentRepository jpaEnvironmentRepository;

    @Override
    public Environment getById(UUID envId) {
        return jpaEnvironmentRepository.findById(envId).orElseThrow();
    }

    @Override
    public Optional<Environment> findByName(String name) {
        return jpaEnvironmentRepository.findByName(name);
    }

    @Override
    public Environment save(Environment environment) {
        return jpaEnvironmentRepository.save(environment);
    }

    @Override
    public List<Environment> findEnvironmentsForSystem(System system) {
        return jpaEnvironmentRepository.findEnvironmentsForSystem(system.getId());
    }

    @Override
    public List<Environment> findNonProductiveEnvironmentsForSystemId(UUID systemId) {
        return jpaEnvironmentRepository.findEnvironmentsForSystem(systemId)
                .stream().filter(env -> !env.isProductive())
                .toList();
    }

    @Override
    public Iterable<Environment> findAll() {
        return jpaEnvironmentRepository.findAll();
    }
}
