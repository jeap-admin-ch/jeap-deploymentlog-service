package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroupService;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.SystemGroupDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.SystemGroupNameDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/system-groups")
@RequiredArgsConstructor
@Tag(name = "System groups", description = "Administrative API for grouping systems")
@ApiResponse(responseCode = "401", description = "Authentication required")
@ApiResponse(responseCode = "403", description = "Required DeploymentLog role missing")
public class SystemGroupController {

    private final SystemGroupService systemGroupService;

    @GetMapping
    @TransactionalReadReplica
    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    @Operation(summary = "List all system groups")
    @ApiResponse(responseCode = "200", description = "System groups sorted deterministically by name")
    public List<SystemGroupDto> findAll() {
        return systemGroupService.findAll().stream().map(SystemGroupDto::of).toList();
    }

    @GetMapping("/{groupId}")
    @TransactionalReadReplica
    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    @Operation(summary = "Get a system group by id")
    @ApiResponse(responseCode = "200", description = "System group found")
    @ApiResponse(responseCode = "404", description = "System group not found")
    public SystemGroupDto get(@PathVariable UUID groupId) {
        return SystemGroupDto.of(systemGroupService.get(groupId));
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Operation(summary = "Create a system group")
    @ApiResponse(responseCode = "201", description = "System group created")
    @ApiResponse(responseCode = "400", description = "Invalid group name")
    @ApiResponse(responseCode = "409", description = "Group name already exists")
    public ResponseEntity<SystemGroupDto> create(@Valid @RequestBody SystemGroupNameDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SystemGroupDto.of(systemGroupService.create(request.getName())));
    }

    @PutMapping("/{groupId}")
    @Transactional
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Operation(summary = "Rename a system group")
    @ApiResponse(responseCode = "200", description = "System group renamed")
    @ApiResponse(responseCode = "400", description = "Invalid group name")
    @ApiResponse(responseCode = "404", description = "System group not found")
    @ApiResponse(responseCode = "409", description = "Group name already exists")
    public SystemGroupDto rename(@PathVariable UUID groupId, @Valid @RequestBody SystemGroupNameDto request) {
        return SystemGroupDto.of(systemGroupService.rename(groupId, request.getName()));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Operation(summary = "Delete a system group without deleting its systems")
    @ApiResponse(responseCode = "204", description = "System group deleted")
    @ApiResponse(responseCode = "404", description = "System group not found")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId) {
        systemGroupService.delete(groupId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{groupId}/systems/{systemId}")
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Operation(summary = "Assign a system to a system group, replacing a previous assignment")
    @ApiResponse(responseCode = "204", description = "System assigned")
    @ApiResponse(responseCode = "404", description = "System group or system not found")
    public ResponseEntity<Void> assignSystem(@PathVariable UUID groupId, @PathVariable UUID systemId) {
        systemGroupService.assignSystem(groupId, systemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/systems/{systemId}")
    @PreAuthorize("hasRole('deploymentlog-write')")
    @Operation(summary = "Remove a system from a system group")
    @ApiResponse(responseCode = "204", description = "Assignment removed or did not exist")
    @ApiResponse(responseCode = "404", description = "System group or system not found")
    public ResponseEntity<Void> removeSystem(@PathVariable UUID groupId, @PathVariable UUID systemId) {
        systemGroupService.removeSystem(groupId, systemId);
        return ResponseEntity.noContent().build();
    }
}
