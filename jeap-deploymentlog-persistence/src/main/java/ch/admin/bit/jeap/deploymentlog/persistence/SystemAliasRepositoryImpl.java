package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemAlias;
import ch.admin.bit.jeap.deploymentlog.domain.SystemAliasRepository;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class SystemAliasRepositoryImpl implements SystemAliasRepository {

    private final JpaSystemAliasRepository jpaSystemAliasRepository;

    @Override
    public SystemAlias save(SystemAlias systemAlias) {
        return jpaSystemAliasRepository.save(systemAlias);
    }

    @Override
    public Optional<SystemAlias> findByName(String name){
        return jpaSystemAliasRepository.findByName(name.toLowerCase());
    }
}
