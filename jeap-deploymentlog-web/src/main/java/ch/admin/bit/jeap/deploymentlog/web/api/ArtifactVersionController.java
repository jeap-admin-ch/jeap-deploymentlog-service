package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersionService;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ArtifactVersionCreateDto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/artifact-version")
@RequiredArgsConstructor
@Slf4j
public class ArtifactVersionController {

    private final ArtifactVersionService artifactVersionService;

    @PutMapping("/{id}")
    @Operation(summary = "Save new artifact version")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<Void> save(@PathVariable(name = "id") String artifactVersionId, @RequestBody ArtifactVersionCreateDto artifactVersionCreateDto) {
        log.debug("Save new artifactVersion with artifactVersionId '{}', coordinates '{}' and buildJobLink '{}'",
                artifactVersionId,
                artifactVersionCreateDto.getCoordinates(),
                artifactVersionCreateDto.getBuildJobLink());

        UUID artefactVersionUuid = UUID.fromString(artifactVersionId);

        if (artifactVersionService.findById(artefactVersionUuid).isPresent()) {
            log.info("ArtifactVersion with id {} already exists. Returning OK", artifactVersionId);
            return new ResponseEntity<>(HttpStatus.OK);
        }

        artifactVersionService.saveArtifactVersion(artefactVersionUuid, artifactVersionCreateDto.getCoordinates(), artifactVersionCreateDto.getBuildJobLink());
        log.info("ArtifactVersion with id {} saved. Returning CREATED", artifactVersionId);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

}
