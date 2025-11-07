package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentSnapshotDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.EnvironmentComponentVersionStateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.UndeploymentCreateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.org.springframework.security=debug")
@AutoConfigureMockMvc
class SystemControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @SneakyThrows
    void getCurrentComponentVersionOnEnvironment() {
        String externalId = "external-id-10";
        postDeployment(createDeploymentDto(), externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/system/TestSystem/component/test/currentVersion/DEV")
                        .header("Authorization", basicAuthHeader))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals("1.2.3-4", responseBody);
    }

    @Test
    @SneakyThrows
    void getCurrentComponentVersionOnEnvironment_withMultipleDeployments_returnsLatestCodeVersion() {
        String externalCodeId = "external-id-10";
        postDeployment(createDeploymentDto(), externalCodeId);
        putDeploymentState(externalCodeId, DeploymentState.SUCCESS);

        String externalConfigId = "external-id-20";
        postDeployment(createConfigDeploymentDto(), externalConfigId);
        putDeploymentState(externalConfigId, DeploymentState.SUCCESS);

        String basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/system/TestSystem/component/test/currentVersion/DEV")
                        .header("Authorization", basicAuthHeader))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals("1.2.3-4", responseBody);
    }

    @Test
    @SneakyThrows
    void deleteComponentOnEnvironment() {
        String externalId = "external-id-10";
        postDeployment(createDeploymentDto(), externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        UndeploymentCreateDto undeploymentCreateDto = TestData.getUndeploymentCreateDto();
        undeploymentCreateDto.setSystemName("TestSystem");
        undeploymentCreateDto.setEnvironmentName("DEV");
        undeploymentCreateDto.setComponentName("test");

        String basicAuthHeaderWrite = "Basic " + Base64.getEncoder().encodeToString(("write:secret").getBytes());

        mockMvc.perform(put("/api/system/deployment-id/undeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicAuthHeaderWrite)
                        .content(objectMapper.writeValueAsString(undeploymentCreateDto)))
                .andExpect(status().is2xxSuccessful());

        String basicAuthHeaderRead = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

       mockMvc.perform(get("/api/system/TestSystem/component/test/currentVersion/DEV")
                        .header("Authorization", basicAuthHeaderRead))
                .andExpect(status().is(404))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @SneakyThrows
    void getSystem() {
        String externalId = "external-id-20";
        postDeployment(createDeploymentDto(), externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        String basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/system/TestSystem")
                        .header("Authorization", basicAuthHeader))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        EnvironmentComponentVersionStateDto dto = objectMapper.readValue(
                responseBody, EnvironmentComponentVersionStateDto.class);

        assertEquals("TestSystem", dto.getSystemName());
        assertEquals("test", dto.getComponents().getFirst().getName());

        DeploymentSnapshotDto deploymentSnapshotDto = dto.getComponents().getFirst().getDeployments().getFirst();
        assertEquals("DEV", deploymentSnapshotDto.getEnv());
        assertEquals("1.2.3-4", deploymentSnapshotDto.getVersion());
    }

    @Test
    @SneakyThrows
    void getPreviousVersionOfComponent() {
        String externalId = "external-id-20-test1";
        final DeploymentCreateDto deploymentDto = createDeploymentDto("myTestSystem", "test1", "5.0.0");
        postDeployment(deploymentDto, externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        String basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/system/myTestSystem/component/test1/previousVersion/DEV")
                        .header("Authorization", basicAuthHeader)
                        .param("version", "6.0.0"))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(deploymentDto.getComponentVersion().getVersionName(), responseBody);
    }

    @Test
    @SneakyThrows
    void getPreviousVersionOfComponent_notFound() {
        String externalId = "external-id-20-test3";
        final DeploymentCreateDto deploymentDto = createDeploymentDto("myTestSystem", "test3", "1.0.0");
        postDeployment(deploymentDto, externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

        mockMvc.perform(get("/api/system/myTestSystem/component/test3/previousVersion/DEV")
                        .header("Authorization", basicAuthHeader)
                        .param("version", deploymentDto.getComponentVersion().getVersionName()))
                .andExpect(status().isNotFound());
    }

    @Test
    @SneakyThrows
    void getPreviousVersionOfComponent_foundPrevious() {
        String externalIdFirst = "external-id-20-test2";
        final DeploymentCreateDto deploymentDtoFirst = createDeploymentDto("myTestSystem", "test2", "1.2.3");
        postDeployment(deploymentDtoFirst, externalIdFirst);
        putDeploymentState(externalIdFirst, DeploymentState.SUCCESS);

        String externalIdSecond = "external-id-30-test2";
        final DeploymentCreateDto deploymentDtoSecond = createDeploymentDto("myTestSystem", "test2", "1.2.2");
        postDeployment(deploymentDtoSecond, externalIdSecond);
        putDeploymentState(externalIdSecond, DeploymentState.SUCCESS);


        String basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/system/myTestSystem/component/test2/previousVersion/DEV")
                        .header("Authorization", basicAuthHeader)
                        .param("version", deploymentDtoFirst.getComponentVersion().getVersionName()))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(deploymentDtoSecond.getComponentVersion().getVersionName(), responseBody);
    }

}
