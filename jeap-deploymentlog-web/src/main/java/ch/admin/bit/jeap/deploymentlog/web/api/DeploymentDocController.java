package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.DeploymentPageNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/deployment-doc")
@RequiredArgsConstructor
@Slf4j
public class DeploymentDocController {

    private final DeploymentService deploymentService;

    @Value("${jeap.deploymentlog.documentation.root-url}")
    private String documentationRootUrl;

    @ApiResponses(value = {@ApiResponse(responseCode = "302", description = "Redirects to documentation")})
    @Operation(summary = "Redirect to the deployment page")
    @GetMapping("/{id}")
    public void redirectToDeploymentPage(@PathVariable(name = "id") String externalId, HttpServletResponse httpServletResponse) throws DeploymentNotFoundException, DeploymentPageNotFoundException {
        log.debug("Retrieve the deployment page for the deployment with externalId '{}'", externalId);
        final DeploymentPage deploymentPage = deploymentService.getDeploymentPage(externalId);
        log.info("Redirect for deployment with externalId {} to deployment page {}", externalId, deploymentPage.getPageId());
        httpServletResponse.setHeader(HttpHeaders.LOCATION, documentationRootUrl + deploymentPage.getPageId());
        httpServletResponse.setStatus(HttpStatus.FOUND.value());
    }
}
