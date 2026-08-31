package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ComponentPageRepository {

    Optional<ComponentPage> findByComponentId(UUID componentId);

    ComponentPage save(ComponentPage componentPage);

    List<ComponentPage> findByParentPageId(String parentPageId);
}
