package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
public class SystemRepositoryImpl implements SystemRepository {

    private final JpaSystemRepository jpaSystemRepository;

    @Override
    public System getById(UUID systemId) {
        return jpaSystemRepository.findById(systemId).orElseThrow();
    }

    @Override
    public Optional<System> findByNameIgnoreCase(String name) {
        return jpaSystemRepository.findByNameIgnoreCase(name);
    }

    @Override
    public System save(System system) {
        return jpaSystemRepository.save(system);
    }

    public List<System> findAll() {
        return StreamSupport
                .stream(jpaSystemRepository.findAll().spliterator(), false)
                .collect(toList());
    }

    @Override
    public List<UUID> getAllSystemIds() {
        return jpaSystemRepository.getAllSystemIds();
    }

    @Override
    public void delete(System system){
        jpaSystemRepository.delete(system);
    }
}
