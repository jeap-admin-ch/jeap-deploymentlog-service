package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTarget;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnit;
import ch.admin.bit.jeap.deploymentlog.domain.Link;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

@Data
public class DeploymentCreateDto {

    ZonedDateTime startedAt;

    String startedBy;

    String environmentName;

    DeploymentTarget target;

    Set<Link> links;

    ComponentVersionCreateDto componentVersion;

    DeploymentUnit deploymentUnit;

    ChangelogDto changelog;

    String remedyChangeId;

    Map<String, String> properties;

    Set<String> referenceIdentifiers;

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
