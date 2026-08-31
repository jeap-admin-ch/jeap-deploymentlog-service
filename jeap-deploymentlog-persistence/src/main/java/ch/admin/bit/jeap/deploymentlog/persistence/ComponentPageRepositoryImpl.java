package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ComponentPageRepositoryImpl implements ComponentPageRepository {

    private final JpaComponentPageRepository repository;

    @Override
    public Optional<ComponentPage> findByComponentId(UUID componentId) {
        return repository.findById(componentId);
    }

    @Override
    public ComponentPage save(ComponentPage componentPage) {
        return repository.save(componentPage);
    }

    @Override
    public List<ComponentPage> findByParentPageId(String parentPageId) {
        return repository.findByParentPageId(parentPageId);
    }
}
