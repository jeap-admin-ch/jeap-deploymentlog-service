package ch.admin.bit.jeap.deploymentlog.docgen.model;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class DeploymentLetterPageDto {

    String deploymentId;
    String externalId;
    String environmentName;
    String targetType;
    String targetUrl;
    String targetDetails;
    String startedAt;
    String endedAt;
    String duration;
    String startedBy;
    String state;
    String stateMessage;
    String componentName;
    String version;
    String versionControlUrl;
    String deploymentUnitType;
    String deploymentUnitCoordinates;
    String deploymentUnitRepositoryUrl;
    List<LinkDto> links;
    ZonedDateTime deploymentStateTimestamp;
    String changeComment;
    String changeComparedToVersion;
    Set<String> changeJiraIssueKeys;
    String sequence;
    String remedyChangeId;
    String remedyChangeLink;
    Set<String> buildJobLinks;
    Map<String, String> properties;

    public String getPageTitle() {
        String pageTitle = startedAt + " " + componentName + " (" + environmentName + ")";
        if (StringUtils.hasText(getSequence()) && getSequence().equals(DeploymentSequence.UNDEPLOYED.getLabel())) {
            pageTitle += DocumentationGenerator.UNDEPLOY_PAGE_SUFFIX;
        }
        return pageTitle;
    }
}
