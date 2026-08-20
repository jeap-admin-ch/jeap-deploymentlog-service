package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTarget;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnit;
import ch.admin.bit.jeap.deploymentlog.domain.Link;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Data
public class DeploymentCreateDto {

    ZonedDateTime startedAt;

    String startedBy;

    String environmentName;

    DeploymentTarget target;

    Set<Link> links;

    @Valid
    @NotNull
    ComponentVersionCreateDto componentVersion;

    @ArraySchema(schema = @Schema(example = "PROD"), arraySchema = @Schema(
            description = "Optional final environments of the current automated staging. The environment with the " +
                    "highest configured stagingOrder is used. If omitted or empty, the configured default final " +
                    "environment (or productive=true fallback) is used."))
    List<String> finalDeploymentEnvironments;

    DeploymentUnit deploymentUnit;

    ChangelogDto changelog;

    String remedyChangeId;

    Map<String, String> properties;

    Set<String> referenceIdentifiers;

    Set<DeploymentType> deploymentTypes;

    public String getEnvironmentName() {
        return environmentName.toUpperCase();
    }

    public Map<String, String> getProperties() {
        return properties == null ? Map.of() : properties;
    }

    public Set<String> getReferenceIdentifiers() {
        return referenceIdentifiers == null ? Set.of() : referenceIdentifiers;
    }
}
