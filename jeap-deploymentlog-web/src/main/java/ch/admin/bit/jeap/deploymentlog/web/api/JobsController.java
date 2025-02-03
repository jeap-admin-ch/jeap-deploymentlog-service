package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.docgen.service.SchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobsController {

    private final DocumentationGenerator documentationGenerator;
    private final SchedulingService schedulingService;
    private final DocgenAsyncService docgenAsyncService;

    @PostMapping("/docgen")
    @Operation(summary = "Regenerate all Confluence Pages. For Testing/Developing only!")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<Void> generateDocumentation() {
        documentationGenerator.generateAllPages();
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/docgen/system/{systemName}")
    @Operation(summary = "Regenerate all confluence pages for a system")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<Void> generateDocumentationForSystem(@PathVariable("systemName") String systemName, @RequestParam(name = "year", required = false) Integer year) {
        docgenAsyncService.triggerDocgenForSystem(systemName, year);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/docgen/deployment/{deploymentId}")
    @Operation(summary = "(Re)generate a page for a single deployment")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<Void> generateDeploymentPage(@PathVariable UUID deploymentId) {
        docgenAsyncService.triggerDocgenForDeployment(deploymentId);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/outdatedPageHousekeeping")
    @Operation(summary = "Clean up outdated pages")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<Void> outdatedPageHousekeeping() {
        schedulingService.outdatedPageHousekeeping();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/docgen/system/{systemName}/repairJiraLinks")
    @Operation(summary = "Regenerate for a system all jira links of deployments started between the given dates",
    parameters = {
        @Parameter(name = "from", description = "Start date (incl) (format: yyyy-MM-dd)", required = true, example = "2025-01-01"),
        @Parameter(name = "to", description = "End date (excl) (format: yyyy-MM-dd)", required = true, example = "2025-01-20")
    })
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<Void> generateJiraLinksForSystem(@PathVariable("systemName") String systemName, @RequestParam("from") String from, @RequestParam("to") String to) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        docgenAsyncService.triggerGenerateJiraLinksForSystem(
                systemName,
                LocalDate.parse(from, dateTimeFormatter).atStartOfDay(ZoneId.systemDefault()),
                LocalDate.parse(to, dateTimeFormatter).atStartOfDay(ZoneId.systemDefault()));
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
