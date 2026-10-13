package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.persistence.JpaEnvironmentComponentVersionStateRepository;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ComponentVersionSummaryDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.UndeploymentCreateDto;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EnvironmentControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaEnvironmentComponentVersionStateRepository componentVersionStateRepository;

    @Test
    @SneakyThrows
    void getEnvironmentComponents() {
        componentVersionStateRepository.deleteAll();

        String externalIdFirst = "external-id-env-controller";
        final DeploymentCreateDto deploymentDtoFirst = createDeploymentDto("myTestSystem", "test", "1.2.3");
        postDeployment(deploymentDtoFirst, externalIdFirst);
        putDeploymentState(externalIdFirst, DeploymentState.SUCCESS);

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/environment/dev/components")
                        .header("Authorization", basicAuthHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ComponentVersionSummaryDto[] response = objectMapper.readValue(responseBody, ComponentVersionSummaryDto[].class);

        assertEquals("test", response[0].componentName());
    }

    @Test
    @SneakyThrows
    void getEnvironmentComponents_whenComponentUndeployed_excludesItByDefaultAndIncludesItWhenRequested() {
        componentVersionStateRepository.deleteAll();

        String externalId = "external-id-env-controller-undeploy";
        final DeploymentCreateDto deploymentDto = createDeploymentDto("undeployTestSystem", "undeployedComponent", "1.0.0");
        postDeployment(deploymentDto, externalId);
        putDeploymentState(externalId, DeploymentState.SUCCESS);

        UndeploymentCreateDto undeploymentCreateDto = TestData.getUndeploymentCreateDto();
        undeploymentCreateDto.setSystemName("undeployTestSystem");
        undeploymentCreateDto.setComponentName("undeployedComponent");
        undeploymentCreateDto.setEnvironmentName("dev");

        String basicAuthHeaderWrite = "Basic " + Base64.getEncoder().encodeToString(("write:secret").getBytes());

        mockMvc.perform(put("/api/system/undeploy-id/undeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicAuthHeaderWrite)
                        .content(objectMapper.writeValueAsString(undeploymentCreateDto)))
                .andExpect(status().is2xxSuccessful());

        String basicAuthHeaderRead = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

        String responseBodyWithoutUndeployed = mockMvc.perform(get("/api/environment/dev/components")
                        .header("Authorization", basicAuthHeaderRead)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ComponentVersionSummaryDto[] responseWithoutUndeployed = objectMapper.readValue(responseBodyWithoutUndeployed, ComponentVersionSummaryDto[].class);
        assertThat(Arrays.stream(responseWithoutUndeployed).map(ComponentVersionSummaryDto::componentName))
                .doesNotContain("undeployedComponent");

        String responseBodyWithUndeployed = mockMvc.perform(get("/api/environment/dev/components")
                        .queryParam("includeUndeployed", "true")
                        .header("Authorization", basicAuthHeaderRead)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ComponentVersionSummaryDto[] responseWithUndeployed = objectMapper.readValue(responseBodyWithUndeployed, ComponentVersionSummaryDto[].class);
        ComponentVersionSummaryDto undeployedComponent = Arrays.stream(responseWithUndeployed)
                .filter(dto -> dto.componentName().equals("undeployedComponent"))
                .findFirst()
                .orElseThrow();
        assertThat(undeployedComponent.undeployed()).isTrue();
        assertThat(undeployedComponent.undeployedAt()).isNotNull();
    }
}
