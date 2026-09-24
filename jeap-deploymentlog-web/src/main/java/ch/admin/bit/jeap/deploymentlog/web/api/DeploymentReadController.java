package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentSearchCriteria;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentNotFoundException;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentPageDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentReadDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/deployment-records")
@RequiredArgsConstructor
@Slf4j
public class DeploymentReadController {

    private static final String EXTERNAL_ID = "externalId";
    private static final String STARTED_AT = "startedAt";
    private static final String ENVIRONMENT = "environment";
    private static final String SYSTEM = "system";
    private static final String COMPONENT = "component";
    private static final String VERSION = "version";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String JIRA_PROJECT = "jiraProject";
    private static final String JIRA_ISSUE = "jiraIssue";
    private static final Pattern JIRA_PROJECT_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern JIRA_ISSUE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*-[1-9][0-9]*");
    private static final Set<String> FILTER_NAMES = Set.of(
            FROM, TO, ENVIRONMENT, SYSTEM, COMPONENT, VERSION, JIRA_PROJECT, JIRA_ISSUE);
    private static final Map<String, String> SORT_PROPERTIES = Map.ofEntries(
            Map.entry(EXTERNAL_ID, EXTERNAL_ID),
            Map.entry(STARTED_AT, STARTED_AT),
            Map.entry("endedAt", "endedAt"),
            Map.entry("state", "state"),
            Map.entry("sequence", "sequence"),
            Map.entry(ENVIRONMENT, "environment.name"),
            Map.entry(SYSTEM, "componentVersion.component.system.name"),
            Map.entry(COMPONENT, "componentVersion.component.name"),
            Map.entry(VERSION, "componentVersion.versionName"),
            Map.entry("committedAt", "componentVersion.committedAt"));

    private final DeploymentService deploymentService;

    @GetMapping("/{externalId}")
    @Operation(summary = "Get a deployment by its external deployment ID")
    @ApiResponse(responseCode = "200", description = "Deployment found")
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Missing deploymentlog-read or deploymentlog-write role", content = @Content)
    @ApiResponse(responseCode = "404", description = "External deployment ID not found", content = @Content)
    @TransactionalReadReplica
    public DeploymentReadDto getDeployment(
            @Parameter(description = "External deployment ID", required = true)
            @PathVariable String externalId) throws DeploymentNotFoundException {
        log.debug("Retrieve the deployment with externalId '{}' through the read API", externalId);
        return DeploymentReadDto.of(deploymentService.getDeployment(externalId));
    }

    @GetMapping
    @Operation(summary = "Search deployment records", description = "Returns persisted deployment records. All supplied business " +
            "filters are combined with AND. 'from' is inclusive and 'to' is exclusive for startedAt. Jira filters " +
            "only query persisted DeploymentLog data and never call Jira.")
    @ApiResponse(responseCode = "200", description = "Page of matching deployment records")
    @ApiResponse(responseCode = "400", description = "Invalid or repeated filter, paging, or sort value")
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Missing deploymentlog-read or deploymentlog-write role", content = @Content)
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page number",
            schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "Number of deployments per page",
            schema = @Schema(type = "integer", defaultValue = "20", minimum = "1"))
    @Parameter(name = "sort", in = ParameterIn.QUERY, description = "Sort property and direction. Supported: " +
            "externalId, startedAt, endedAt, state, sequence, environment, system, component, version, " +
            "committedAt. May be repeated for multi-column sorting. externalId is automatically appended " +
            "as a stable final tie-breaker when omitted.",
            schema = @Schema(type = "string", example = "startedAt,desc"))
    @TransactionalReadReplica
    public DeploymentPageDto searchDeployments(
            @Parameter(description = "Inclusive lower bound for startedAt (ISO-8601 timestamp with offset)",
                    example = "2026-09-01T00:00:00+02:00")
            @RequestParam(required = false) String from,
            @Parameter(description = "Exclusive upper bound for startedAt (ISO-8601 timestamp with offset)",
                    example = "2026-10-01T00:00:00+02:00")
            @RequestParam(required = false) String to,
            @Parameter(description = "Exact environment name")
            @RequestParam(required = false) String environment,
            @Parameter(description = "Exact system name")
            @RequestParam(required = false) String system,
            @Parameter(description = "Exact component name")
            @RequestParam(required = false) String component,
            @Parameter(description = "Exact component version name")
            @RequestParam(required = false) String version,
            @Parameter(description = "Normalized Jira project key; matches at least one stored issue")
            @RequestParam(required = false) String jiraProject,
            @Parameter(description = "Normalized exact stored Jira issue key")
            @RequestParam(required = false) String jiraIssue,
            @Parameter(hidden = true) @PageableDefault(size = 20, sort = STARTED_AT, direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {
        rejectRepeatedFilters(request);
        ZonedDateTime parsedFrom = parseDate(FROM, from);
        ZonedDateTime parsedTo = parseDate(TO, to);
        if (parsedFrom != null && parsedTo != null && !parsedFrom.isBefore(parsedTo)) {
            throw new InvalidDeploymentFilterException("Filter 'from' must be before filter 'to'");
        }
        String normalizedProject = normalizeJiraFilter(JIRA_PROJECT, jiraProject, JIRA_PROJECT_PATTERN);
        String normalizedIssue = normalizeJiraFilter(JIRA_ISSUE, jiraIssue, JIRA_ISSUE_PATTERN);
        DeploymentSearchCriteria criteria = new DeploymentSearchCriteria(
                parsedFrom, parsedTo,
                nonBlank(ENVIRONMENT, environment), nonBlank(SYSTEM, system),
                nonBlank(COMPONENT, component), nonBlank(VERSION, version),
                normalizedProject, normalizedIssue);
        Pageable translatedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                translateSort(pageable.getSort()));
        return DeploymentPageDto.of(deploymentService.searchDeployments(criteria, translatedPageable)
                .map(DeploymentReadDto::of));
    }

    private static void rejectRepeatedFilters(HttpServletRequest request) {
        FILTER_NAMES.forEach(filter -> {
            String[] values = request.getParameterValues(filter);
            if (values != null && values.length > 1) {
                throw new InvalidDeploymentFilterException("Filter '%s' must not be repeated".formatted(filter));
            }
        });
    }

    private static ZonedDateTime parseDate(String name, String value) {
        if (value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(nonBlank(name, value));
        } catch (DateTimeParseException ex) {
            throw new InvalidDeploymentFilterException(
                    "Filter '%s' must be an ISO-8601 timestamp with an offset".formatted(name));
        }
    }

    private static String normalizeJiraFilter(String name, String value, Pattern pattern) {
        String normalized = nonBlank(name, value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!pattern.matcher(normalized).matches()) {
            throw new InvalidDeploymentFilterException("Filter '%s' is not a valid Jira key".formatted(name));
        }
        return normalized;
    }

    private static String nonBlank(String name, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidDeploymentFilterException("Filter '%s' must not be blank".formatted(name));
        }
        return trimmed;
    }

    private static Sort translateSort(Sort requestedSort) {
        ArrayList<Sort.Order> translatedOrders = new ArrayList<>(requestedSort.stream().map(order -> {
            String property = SORT_PROPERTIES.get(order.getProperty());
            if (property == null) {
                throw new InvalidDeploymentFilterException(
                        "Unsupported deployment sort property '%s'".formatted(order.getProperty()));
            }
            return new Sort.Order(order.getDirection(), property, order.getNullHandling());
        }).toList());
        if (requestedSort.getOrderFor(EXTERNAL_ID) == null) {
            translatedOrders.add(Sort.Order.asc(EXTERNAL_ID));
        }
        return Sort.by(translatedOrders);
    }
}
