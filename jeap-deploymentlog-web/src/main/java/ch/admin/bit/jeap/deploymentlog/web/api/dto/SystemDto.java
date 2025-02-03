package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class SystemDto {

    UUID id;

    String name;

    static SystemDto of(System system) {
        return SystemDto.builder()
                .id(system.getId())
                .name(system.getName())
                .build();
    }

}
