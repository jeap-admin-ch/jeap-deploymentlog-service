package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class SystemGroupDto {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Border Control")
    String name;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    List<SystemDto> systems;

    public static SystemGroupDto of(SystemGroup systemGroup) {
        List<SystemDto> systems = systemGroup.getSystems().stream()
                .sorted(Comparator.comparing(ch.admin.bit.jeap.deploymentlog.domain.System::getName,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ch.admin.bit.jeap.deploymentlog.domain.System::getId))
                .map(SystemDto::of)
                .toList();
        return SystemGroupDto.builder()
                .id(systemGroup.getId())
                .name(systemGroup.getName())
                .systems(systems)
                .build();
    }
}
