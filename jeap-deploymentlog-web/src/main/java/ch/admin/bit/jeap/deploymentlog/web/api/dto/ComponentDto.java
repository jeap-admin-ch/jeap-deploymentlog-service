package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ComponentDto {

    UUID id;

    String name;

    boolean active;

    SystemDto system;

    static ComponentDto of(Component component) {
        return ComponentDto.builder()
                .id(component.getId())
                .name(component.getName())
                .active(component.isActive())
                .system(SystemDto.of(component.getSystem()))
                .build();
    }

}
