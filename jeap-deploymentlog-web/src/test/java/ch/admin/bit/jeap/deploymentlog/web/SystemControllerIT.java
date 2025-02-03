package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentSnapshotDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.EnvironmentComponentVersionStateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.UndeploymentCreateDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.org.springframework.security=debug")
@AutoConfigureWebTestClient(timeout = "PT30s")
class SystemControllerIT extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getCurrentComponentVersionOnEnvironment() {
        String externalId = "external-id-10";
        postDeployment(createDeploymentDto(), externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        webTestClient.get()
                .uri("/api/system/TestSystem/component/test/currentVersion/DEV")
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(String.class)
                .value(version -> assertEquals("1.2.3-4", version));
    }

    @Test
    void deleteComponentOnEnvironment() throws JsonProcessingException {
        String externalId = "external-id-10";
        postDeployment(createDeploymentDto(), externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        UndeploymentCreateDto undeploymentCreateDto = TestData.getUndeploymentCreateDto();
        undeploymentCreateDto.setSystemName("TestSystem");
        undeploymentCreateDto.setEnvironmentName("DEV");
        undeploymentCreateDto.setComponentName("test");

        webTestClient.put()
                .uri("/api/system/deployment-id/undeploy")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(undeploymentCreateDto))
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful();

        webTestClient.get()
                .uri("/api/system/TestSystem/component/test/currentVersion/DEV")
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is4xxClientError()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"status\":404"));
    }

    @Test
    void getSystem() {
        String externalId = "external-id-20";
        postDeployment(createDeploymentDto(), externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        webTestClient.get()
                .uri("/api/system/TestSystem")
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(EnvironmentComponentVersionStateDto.class)
                .value(dto -> {
                    assertEquals("TestSystem", dto.getSystemName());
                    assertEquals("test", dto.getComponents().get(0).getName());
                    DeploymentSnapshotDto deploymentSnapshotDto = dto.getComponents().get(0).getDeployments().get(0);
                    assertEquals("DEV", deploymentSnapshotDto.getEnv());
                    assertEquals("1.2.3-4", deploymentSnapshotDto.getVersion());
                });
    }

    @Test
    void getPreviousVersionOfComponent() {
        String externalId = "external-id-20-test1";
        final DeploymentCreateDto deploymentDto = createDeploymentDto("myTestSystem", "test1", "5.0.0");
        postDeployment(deploymentDto, externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        webTestClient.get()
                .uri("/api/system/myTestSystem/component/test1/previousVersion/DEV?version=6.0.0")
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(String.class)
                .value(response -> assertEquals(deploymentDto.getComponentVersion().getVersionName(), response));
    }

    @Test
    void getPreviousVersionOfComponent_notFound() {
        String externalId = "external-id-20-test3";
        final DeploymentCreateDto deploymentDto = createDeploymentDto("myTestSystem", "test3", "1.0.0");
        postDeployment(deploymentDto, externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        webTestClient.get()
                .uri("/api/system/myTestSystem/component/test3/previousVersion/DEV?version=" + deploymentDto.getComponentVersion().getVersionName())
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void getPreviousVersionOfComponent_foundPrevious() {
        String externalIdFirst = "external-id-20-test2";
        final DeploymentCreateDto deploymentDtoFirst = createDeploymentDto("myTestSystem", "test2", "1.2.3");
        postDeployment(deploymentDtoFirst, externalIdFirst);
        putDeploymentState(externalIdFirst, DeploymentState.SUCCESS);

        String externalIdSecond = "external-id-30-test2";
        final DeploymentCreateDto deploymentDtoSecond = createDeploymentDto("myTestSystem", "test2", "1.2.2");
        postDeployment(deploymentDtoSecond, externalIdSecond);
        putDeploymentState(externalIdSecond, DeploymentState.SUCCESS);


        webTestClient.get()
                .uri("/api/system/myTestSystem/component/test2/previousVersion/DEV?version=" + deploymentDtoFirst.getComponentVersion().getVersionName())
                .headers(headers -> headers.setBasicAuth("read", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(String.class)
                .value(response -> assertEquals(deploymentDtoSecond.getComponentVersion().getVersionName(), response));
    }

}
