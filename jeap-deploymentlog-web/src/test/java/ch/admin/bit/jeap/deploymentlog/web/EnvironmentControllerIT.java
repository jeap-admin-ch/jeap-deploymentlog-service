package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.persistence.JpaEnvironmentComponentVersionStateRepository;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ComponentVersionSummaryDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
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
}
