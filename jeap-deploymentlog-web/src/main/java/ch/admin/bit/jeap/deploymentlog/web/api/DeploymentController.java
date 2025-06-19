package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidDeploymentStateForUpdateException;
import ch.admin.bit.jeap.deploymentlog.jira.JiraIssuesNotFoundException;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.value;

@RestController
@RequestMapping("/api/deployment")
@RequiredArgsConstructor
@Slf4j
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DocgenAsyncService docgenAsyncService;
    private final DeploymentCheckService deploymentCheckService;

    @PutMapping("/{id}")
    @Operation(summary = "Create a new deployment")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<DeploymentCreateResultDto> createDeployment(
            @PathVariable(name = "id") String externalId,
            @RequestParam(required = false) boolean readyForDeployCheck,
            @RequestBody DeploymentCreateDto deploymentCreateDto) throws JiraIssuesNotFoundException {
        log.debug("Create new deployment with externalId '{}' for the component '{}' of the system '{}' on env '{}'",
                externalId,
                deploymentCreateDto.getComponentVersion().getComponentName(),
                deploymentCreateDto.getComponentVersion().getSystemName(),
                deploymentCreateDto.getEnvironmentName());

        if (deploymentService.findByExternalId(externalId).isPresent()) {
            log.info("Deployment with externalId {} already exists. Returning OK", externalId);
            return new ResponseEntity<>(HttpStatus.OK);
        }

        DeploymentCreateResultDto deploymentCreateResultDto = null;

        String changelogComment = null;
        String changelogComparedToVersion = null;
        Set<String> changelogJiraIssueKeys = null;
        if (deploymentCreateDto.getChangelog() != null) {
            changelogComparedToVersion = deploymentCreateDto.getChangelog().getComparedToVersion();
            changelogComment = deploymentCreateDto.getChangelog().getComment();
            changelogJiraIssueKeys = deploymentCreateDto.getChangelog().getJiraIssueKeys();

            if (readyForDeployCheck) {
                log.info("Check if the issues '{}' are ready to be deployed", changelogJiraIssueKeys);

                deploymentCreateResultDto = DeploymentCreateResultDto.builder()
                        .checkResult(deploymentCheckService.issuesReadyForDeploy(changelogJiraIssueKeys))
                        .build();

                if (deploymentCreateResultDto.getCheckResult().getResult().equals(DeploymentCheckResult.NOK)) {
                    log.info("Result of readyForDeployCheck is {}: returning bad request", deploymentCreateResultDto.getCheckResult().getResult());
                    return ResponseEntity.ok().body(deploymentCreateResultDto);
                }
            }
        }

        UUID deploymentId = deploymentService.createDeployment(externalId,
                deploymentCreateDto.getComponentVersion().getVersionName(),
                deploymentCreateDto.getComponentVersion().getTaggedAt(),
                deploymentCreateDto.getComponentVersion().getVersionControlUrl(),
                deploymentCreateDto.getComponentVersion().getCommitRef(),
                deploymentCreateDto.getComponentVersion().getCommitedAt(),
                deploymentCreateDto.getComponentVersion().isPublishedVersion(),
                deploymentCreateDto.getComponentVersion().getSystemName(),
                deploymentCreateDto.getComponentVersion().getComponentName(),
                deploymentCreateDto.getEnvironmentName(),
                deploymentCreateDto.getTarget(),
                deploymentCreateDto.getStartedAt(),
                deploymentCreateDto.getStartedBy(),
                deploymentCreateDto.getDeploymentUnit(),
                deploymentCreateDto.getLinks(),
                deploymentCreateDto.getProperties(),
                deploymentCreateDto.getReferenceIdentifiers(),
                changelogComment,
                changelogComparedToVersion,
                changelogJiraIssueKeys,
                deploymentCreateDto.getRemedyChangeId(),
                deploymentCreateDto.getDeploymentTypes());

        triggerDocgenForDeployment(deploymentId);

        if (deploymentCreateResultDto == null) {
            return new ResponseEntity<>(HttpStatus.CREATED);
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(deploymentCreateResultDto);
        }
    }

    private void triggerDocgenForDeployment(UUID deploymentId) {
        try {
            docgenAsyncService.triggerDocgenForDeployment(deploymentId);
        } catch (Exception ex) {
            log.error("Failed to trigger docgen for deployment {} - will re-attempt generation in scheduled task",
                    value("deploymentId", deploymentId), ex);
        }
    }

    @PutMapping("/{id}/state")
    @Operation(summary = "Update the state of an existing deployment")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public void updateState(@PathVariable(name = "id") String externalId, @RequestBody DeploymentUpdateStateDto deploymentUpdateStateDto) throws DeploymentNotFoundException, InvalidDeploymentStateForUpdateException {
        log.debug("Update the deployment with externalId '{}' with the new state '{}'", externalId, deploymentUpdateStateDto.getState());
        UUID deploymentId = deploymentService.updateState(
                externalId,
                deploymentUpdateStateDto.getState(),
                deploymentUpdateStateDto.getMessage(),
                deploymentUpdateStateDto.getTimestamp(),
                deploymentUpdateStateDto.getProperties());

        triggerDocgenForDeployment(deploymentId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get deployment information")
    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    @TransactionalReadReplica
    public DeploymentDto getDeployment(@PathVariable(name = "id") String externalId) throws DeploymentNotFoundException {
        log.debug("Retrieve the deployment with externalId '{}'", externalId);
        return DeploymentDto.of(deploymentService.getDeployment(externalId));
    }
}
