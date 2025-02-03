package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.persistence.JpaEnvironmentComponentVersionStateRepository;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ComponentVersionSummaryDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30s")
class EnvironmentControllerIT extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JpaEnvironmentComponentVersionStateRepository componentVersionStateRepository;

    @Test
    void getEnvironmentComponents() {
        componentVersionStateRepository.deleteAll();

        String externalIdFirst = "external-id-env-controller";
        final DeploymentCreateDto deploymentDtoFirst = createDeploymentDto("myTestSystem", "test", "1.2.3");
        postDeployment(deploymentDtoFirst, externalIdFirst);
        putDeploymentState(externalIdFirst, DeploymentState.SUCCESS);

        webTestClient.get()
                .uri("/api/environment/dev/components")
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(ComponentVersionSummaryDto[].class)
                .value(response -> assertEquals("test", response[0].componentName()));
    }
}
