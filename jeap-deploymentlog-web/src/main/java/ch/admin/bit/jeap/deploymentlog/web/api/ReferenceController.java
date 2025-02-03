package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.domain.ReferenceService;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ReferenceDto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
@Slf4j
public class ReferenceController {

    private final ReferenceService referenceService;

    @PutMapping("/{id}")
    @Operation(summary = "Save new reference")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<Void> save(@PathVariable(name = "id") UUID referenceId,
                                     @RequestBody ReferenceDto referenceDto) {
        log.debug("Save new reference {}", referenceDto);

        if (!referenceId.equals(referenceDto.getId())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (referenceService.referenceExistsById(referenceId)) {
            log.info("Reference with id {} already exists", referenceId);
            return new ResponseEntity<>(HttpStatus.OK);
        }

        referenceService.saveReference(
                referenceId, referenceDto.getType(), referenceDto.getReferenceIdentifier(),
                referenceDto.getUri());

        log.info("Reference with id {} saved", referenceId);

        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
