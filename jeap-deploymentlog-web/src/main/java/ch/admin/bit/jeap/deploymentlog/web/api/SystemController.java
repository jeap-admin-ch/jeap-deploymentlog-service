package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.exception.*;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.value;


@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
public class SystemController {

    private final EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;
    private final SystemService systemService;
    private final DeploymentService deploymentService;
    private final DocgenAsyncService docgenAsyncService;

    @GetMapping("/{systemName}")
    @Operation(summary = "Get all the latest deployments for the system")
    @TransactionalReadReplica
    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    public EnvironmentComponentVersionStateDto getSystem(@PathVariable String systemName) throws SystemNotFoundException {
        log.debug("Get all the latest deployments for the system with name '{}'", systemName);
        final System system = systemService.retrieveSystemByName(systemName);

        final List<EnvironmentComponentVersionState> environmentComponentVersionStates = environmentComponentVersionStateRepository.findByComponentIn(system.getComponents());

        final Map<String, List<EnvironmentComponentVersionState>> deploymentsGroupedByComponent = environmentComponentVersionStates.stream().collect(Collectors.groupingBy(e -> e.getComponent().getName()));

        final List<ComponentSnapshotDto> components = new ArrayList<>();

        deploymentsGroupedByComponent.forEach((componentName, deployments) -> components.add(
                ComponentSnapshotDto.builder()
                        .name(componentName)
                        .deployments(DeploymentSnapshotDto.of(deployments))
                        .build()));

        return EnvironmentComponentVersionStateDto.builder()
                .systemName(systemName)
                .components(components)
                .build();
    }

    @PutMapping("/{id}/undeploy")
    @Operation(summary = "Delete the component of the system")
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Transactional
    public void deleteComponent(@PathVariable(name = "id") String externalId, @RequestBody UndeploymentCreateDto undeploymentCreateDto) throws SystemNotFoundException, EnvironmentNotFoundException, ComponentNotFoundException {
        String componentName = undeploymentCreateDto.getComponentName();
        String systemName = undeploymentCreateDto.getSystemName();
        String environmentName = undeploymentCreateDto.getEnvironmentName();

        final Component component = systemService.retrieveComponentByName(systemName, componentName);
        final Environment environment = systemService.retrieveEnvironmentByName(environmentName);
        Deployment previousDeployment = deploymentService.getLastDeploymentForComponent(component, environment);

        log.debug("Delete the component '{}' from system '{}' on environment '{}'", componentName, systemName, environmentName);

        systemService.deleteComponent(systemName, componentName, environmentName);
        UUID undeploymentId = deploymentService.createUndeployment(previousDeployment, externalId, systemName, componentName, environmentName, undeploymentCreateDto.getStartedAt(), undeploymentCreateDto.getStartedBy(), undeploymentCreateDto.getRemedyChangeId());
        triggerDocgenForUndeployment(systemName, undeploymentId);
    }

    @GetMapping(value = "/{systemName}/component/{componentName}/currentVersion/{environment}", produces = "text/plain")
    @Operation(summary = "Get current version of component of system on environment")
    @ApiResponses(
            @ApiResponse(responseCode = "404", description = "System, component or environment not found, or no version deployed on env at the moment")
    )
    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    public String getCurrentComponentVersionOnEnvironment(@PathVariable String systemName,
                                                          @PathVariable String componentName,
                                                          @PathVariable String environment) throws SystemNotFoundException, EnvironmentNotFoundException, ComponentNotFoundException {
        log.debug("Get the version of component '{}' of system '{}' on environment '{}'", componentName, systemName, environment);
        return systemService.getCurrentVersionOfComponent(systemName, componentName, environment)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/{systemName}/component/{componentName}/previousVersion/{environment}", produces = "text/plain")
    @Operation(summary = "Get previous version of component of system on environment that is different to the version param")
    @ApiResponses(
            @ApiResponse(responseCode = "404", description = "System, component or environment not found, or no version deployed on env at the moment")
    )
    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    public String getPreviousComponentVersionOnEnvironment(@PathVariable String systemName,
                                                           @PathVariable String componentName,
                                                           @PathVariable String environment,
                                                           @RequestParam String version) throws SystemNotFoundException, EnvironmentNotFoundException, ComponentNotFoundException {
        log.debug("Get the previous version of '{}' of component '{}' of system '{}' on environment '{}'", version, componentName, systemName, environment);
        return systemService.getPreviousVersionOfComponent(systemName, componentName, environment, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/{systemName}/component/{componentName}/previousDeployment/{environment}")
    @Operation(summary = "Get previous deployment of component of system on environment that is different to the version param")
    @ApiResponses(
            @ApiResponse(responseCode = "404", description = "System, component or environment not found, or no version deployed on env at the moment")
    )
    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    @TransactionalReadReplica
    public DeploymentDto getPreviousComponentDeploymentOnEnvironment(@PathVariable String systemName,
                                       @PathVariable String componentName,
                                       @PathVariable String environment,
                                       @RequestParam String version) throws SystemNotFoundException, EnvironmentNotFoundException, ComponentNotFoundException {
        log.debug("Get the previous deployment of '{}' of component '{}' of system '{}' on environment '{}'", version, componentName, systemName, environment);
        Deployment deployment = systemService.getPreviousDeploymentOfComponent(systemName, componentName, environment, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return DeploymentDto.of(deployment);
    }

    @PostMapping("/{systemName}/alias/{aliasName}")
    @Operation(summary = "Create a new alias for a system")
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Transactional
    public ResponseEntity<Void> createAlias(@PathVariable(name = "systemName") String systemName, @PathVariable(name = "aliasName") String aliasName) throws SystemNotFoundException, AliasNameAlreadyDefinedException, SystemNameAlreadyDefinedException {
        log.info("Create new alias '{}' for system '{}'", aliasName, systemName);
        systemService.createAlias(systemName, aliasName);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{oldSystemName}/migrate-to/{newSystemName}")
    @Operation(summary = "Update the name of the system and create a new alias with the old name")
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Transactional
    public ResponseEntity<Void> migrateFromAlias(@PathVariable(name = "oldSystemName") String oldSystemName, @PathVariable(name = "newSystemName") String newSystemName) throws SystemNotFoundException, AliasNameAlreadyDefinedException, SystemNameAlreadyDefinedException {
        log.info("Updating system '{}' with new name '{}'", oldSystemName, newSystemName);
        System system = systemService.updateSystemName(oldSystemName, newSystemName);

        docgenAsyncService.triggerMigrationForSystem(system);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{systemName}/merge-from/{oldSystemName}")
    @Operation(summary = "Merge the second system into the first system and create a new alias with the second system name")
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Transactional
    public ResponseEntity<Void> mergeSystem(@PathVariable(name = "systemName") String systemName, @PathVariable(name = "oldSystemName") String oldSystemName) throws SystemNotFoundException {
        log.info("Merging system '{}' into '{}'", oldSystemName, systemName);
        System system = systemService.retrieveSystemByName(systemName);
        System oldSystem = systemService.retrieveSystemByName(oldSystemName);

        if (system.getId().equals(oldSystem.getId())) {
            log.warn("Cannot merge system into itself");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Update Confluence documentation
        docgenAsyncService.triggerMergeSystems(system, oldSystem);
        // Update db entries
        systemService.mergeSystems(system, oldSystem);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    private void triggerDocgenForUndeployment(String systemName, UUID deploymentId) {
        try {
            docgenAsyncService.triggerDocgenForUndeployment(systemName, deploymentId);
        } catch (Exception ex) {
            log.error("Failed to trigger docgen for undeployment {} - will re-attempt generation in scheduled task",
                    value("deploymentId", deploymentId), ex);
        }
    }

}
