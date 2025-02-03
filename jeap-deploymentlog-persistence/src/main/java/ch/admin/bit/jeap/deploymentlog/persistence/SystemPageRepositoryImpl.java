package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemPage;
import ch.admin.bit.jeap.deploymentlog.domain.SystemPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemPageRepositoryImpl implements SystemPageRepository {

    private final JpaSystemPageRepository jpaSystemPageRepository;

    @Override
    public SystemPage save(SystemPage systemPage) {
        return jpaSystemPageRepository.save(systemPage);
    }

    @Override
    public Optional<SystemPage> findSystemPageBySystemId(UUID systemId) {
        return jpaSystemPageRepository.findSystemPageBySystemId(systemId);
    }

    @Override
    public void deleteSystemPage(SystemPage systemPage) {
        jpaSystemPageRepository.delete(systemPage);
    }
}
