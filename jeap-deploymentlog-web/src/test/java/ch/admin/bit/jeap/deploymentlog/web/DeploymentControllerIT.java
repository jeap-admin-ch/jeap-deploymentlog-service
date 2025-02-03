package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // Isolate async tasks
class DeploymentControllerIT extends IntegrationTestBase {

    @Test
    void putSingleDeployment() {
        DeploymentCreateDto dto = createDeploymentDto();
        dto.setProperties(Map.of("key", "value"));
        String externalId = "external-id-1";
        postDeployment(dto, externalId);

        awaitUntilAsyncTasksCompleted();

        assertTrue(confluenceAdapterMock.getModifiedPages().contains("2007-12-03 10:15:30 test (DEV)"));
        assertTrue(confluenceAdapterMock.getModifiedPages().contains("2007-Deployments DEV (TestSystem)"));
        assertTrue(confluenceAdapterMock.getModifiedPages().contains("Deployment History DEV (TestSystem)"));
        assertTrue(confluenceAdapterMock.getModifiedPages().contains("TestSystem"));
        webTestClient.get()
                .uri("/api/deployment/" + externalId)
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(DeploymentDto.class)
                .value(response -> {
                    assertThat(response.getState())
                            .isEqualTo(DeploymentState.STARTED);
                    assertThat(response.getProperties())
                            .containsEntry("key", "value");
                });
    }

    @Test
    @Transactional
    void updateDeploymentState() {
        String externalId = "external-id-2";
        DeploymentState state = DeploymentState.SUCCESS;
        putDeploymentState(externalId, state, Map.of("key", "value"));

        awaitUntilAsyncTasksCompleted();
        webTestClient.get()
                .uri("/api/deployment/" + externalId)
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(DeploymentDto.class)
                .value(response -> {
                    assertThat(response.getState())
                            .isEqualTo(DeploymentState.SUCCESS);
                    assertThat(response.getProperties())
                            .containsEntry("key", "value");
                });
    }

    @Test
    void getDeployment() throws InterruptedException {
        String externalId = "external-id-3";
        postDeployment(createDeploymentDto(), externalId);

        awaitUntilAsyncTasksCompleted();

        webTestClient.get()
                .uri("/api/deployment/" + externalId)
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(DeploymentDto.class)
                .value(response -> {
                    assertEquals("1.2.3-4", response.getComponentVersion().getVersionName());
                    assertEquals("DEV", response.getEnvironment().getName());
                });
    }
}
