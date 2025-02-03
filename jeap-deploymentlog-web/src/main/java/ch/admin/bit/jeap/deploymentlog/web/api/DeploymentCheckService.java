package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.jira.JiraIssuesNotFoundException;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResult;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeploymentCheckService {

    private final JiraWebClient jiraWebClient;

    public static final String R4DEPLOY_LABEL = "R4DEPLOY";

    public DeploymentCheckResultDto issuesReadyForDeploy(Set<String> issues) throws JiraIssuesNotFoundException {
        try {
            return checkIssues(issues, List.of());
        } catch (JiraIssuesNotFoundException issuesNotFoundException) {
            return ignoreUnknownJiraIssues(issues, issuesNotFoundException.getIssues());
        }
    }

    private DeploymentCheckResultDto ignoreUnknownJiraIssues(Set<String> issues, List<String> unknownIssues) throws JiraIssuesNotFoundException {
        unknownIssues.forEach(issues::remove);
        return checkIssues(issues, unknownIssues);
    }


    private DeploymentCheckResultDto checkIssues(Set<String> issues, List<String> unknownIssues) throws JiraIssuesNotFoundException {
        log.info("Check if the issues '{}' are ready to be deployed", issues);
        final Map<String, List<String>> foundIssues = jiraWebClient.searchIssuesLabels(issues);

        Set<String> issuesWithoutReadyForDeploy = new HashSet<>();

        for (Map.Entry<String, List<String>> entry : foundIssues.entrySet()) {
            if (!entry.getValue().contains(R4DEPLOY_LABEL)) {
                issuesWithoutReadyForDeploy.add(entry.getKey());
            }
        }

        return generateResultDto(issuesWithoutReadyForDeploy.stream().sorted().toList(), unknownIssues);
    }

    private DeploymentCheckResultDto generateResultDto(List<String> issuesWithoutReadyForDeploy, List<String> unknownIssues){
        if (issuesWithoutReadyForDeploy.isEmpty()){
            if (unknownIssues.isEmpty()) {
                return DeploymentCheckResultDto.builder().result(DeploymentCheckResult.OK).build();
            } else {
                return DeploymentCheckResultDto.builder().result(DeploymentCheckResult.WARNING).message("Issues not found: " + unknownIssues).build();
            }
        } else {
            return DeploymentCheckResultDto.builder().result(DeploymentCheckResult.NOK)
                    .message("Issues without label: " + issuesWithoutReadyForDeploy + ". Issues not found: " + unknownIssues).build();
        }
    }

}
