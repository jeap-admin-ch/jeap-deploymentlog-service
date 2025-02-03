package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentComponentVersionStateRepository;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemNotFoundException;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ComponentVersionSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/environment")
@RequiredArgsConstructor
@Slf4j
public class EnvironmentController {

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;

    @GetMapping("/{environmentName}/components")
    @Operation(summary = "Get deployed components for environment")

    @PreAuthorize("hasAnyRole('deploymentlog-read','deploymentlog-write')")
    public List<ComponentVersionSummaryDto> getEnvironmentComponents(@PathVariable String environmentName) throws SystemNotFoundException {
        var environment = environmentRepository.findByName(environmentName.toUpperCase()).orElseThrow();
        return environmentComponentVersionStateRepository.getDeployedComponentsOnEnvironment(environment).stream()
                .map(ComponentVersionSummaryDto::from)
                .toList();
    }
}
