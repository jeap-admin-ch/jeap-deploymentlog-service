package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class ComponentRepositoryImpl implements ComponentRepository {

    private final JpaComponentRepository jpaComponentRepository;

    @Override
    public Component save(Component component) {
        return jpaComponentRepository.save(component);
    }

    @Override
    public void lockById(UUID componentId) {
        jpaComponentRepository.lockById(componentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Component not found while acquiring documentation lock: " + componentId));
    }

    @Override
    public java.util.Optional<Component> findById(UUID componentId) {
        return jpaComponentRepository.findById(componentId);
    }
}
