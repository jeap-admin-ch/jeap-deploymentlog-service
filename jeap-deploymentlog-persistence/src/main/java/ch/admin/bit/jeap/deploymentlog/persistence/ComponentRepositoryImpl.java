package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import lombok.RequiredArgsConstructor;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class ComponentRepositoryImpl implements ComponentRepository {

    private final JpaComponentRepository jpaComponentRepository;

    @Override
    public Component save(Component component) {
        return jpaComponentRepository.save(component);
    }
}
