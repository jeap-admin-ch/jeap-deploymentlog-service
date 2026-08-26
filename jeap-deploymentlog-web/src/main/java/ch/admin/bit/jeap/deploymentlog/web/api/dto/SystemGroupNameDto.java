package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemGroupNameDto {

    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Border Control")
    private String name;
}
