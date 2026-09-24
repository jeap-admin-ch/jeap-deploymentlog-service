package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentDto;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // Isolate async tasks
class DeploymentControllerIT extends IntegrationTestBase {

    @Test
    @SneakyThrows
    void searchDeployments_filtersPagesAndDoesNotExposeInternalIds() {
        DeploymentCreateDto dto = createDeploymentDto();
        dto.setStartedAt(ZonedDateTime.parse("2007-12-03T10:15:30+01:00"));
        postDeployment(dto, "read-api-deployment");
        awaitUntilAsyncTasksCompleted();

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());
        mockMvc.perform(get("/api/deployment-records")
                        .header("Authorization", basicAuthHeader)
                        .param("from", "2007-12-03T10:15:30+01:00")
                        .param("to", "2007-12-03T10:15:31+01:00")
                        .param("environment", "DEV")
                        .param("system", "TestSystem")
                        .param("component", "test")
                        .param("version", "1.2.3-4")
                        .param("jiraProject", "proj")
                        .param("jiraIssue", "proj-123")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "externalId,asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].externalId").value("read-api-deployment"))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.content[0].componentVersion.id").doesNotExist())
                .andExpect(jsonPath("$.content[0].changelog.jiraIssueKeys[0]").value("PROJ-123"));
    }

    @Test
    @SneakyThrows
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

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/deployment/{externalId}", externalId)
                        .header("Authorization", basicAuthHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        DeploymentDto response = objectMapper.readValue(responseBody, DeploymentDto.class);

        assertThat(response.getState()).isEqualTo(DeploymentState.STARTED);
        assertThat(response.getProperties()).containsEntry("key", "value");
    }

    @Test
    @Transactional
    @SneakyThrows
    void updateDeploymentState() {
        String externalId = "external-id-2";
        DeploymentState state = DeploymentState.SUCCESS;
        putDeploymentState(externalId, state, Map.of("key", "value"));

        awaitUntilAsyncTasksCompleted();

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/deployment/{externalId}", externalId)
                        .header("Authorization", basicAuthHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        DeploymentDto response = objectMapper.readValue(responseBody, DeploymentDto.class);

        assertThat(response.getState()).isEqualTo(DeploymentState.SUCCESS);
        assertThat(response.getProperties()).containsEntry("key", "value");
    }

    @Test
    @Transactional
    @SneakyThrows
    void updateDeploymentStateToCancelled() {
        String externalId = "external-id-cancelled";
        putDeploymentState(externalId, DeploymentState.CANCELLED, Map.of());

        awaitUntilAsyncTasksCompleted();

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());
        String responseBody = mockMvc.perform(get("/api/deployment/{externalId}", externalId)
                        .header("Authorization", basicAuthHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        DeploymentDto response = objectMapper.readValue(responseBody, DeploymentDto.class);
        assertThat(response.getState()).isEqualTo(DeploymentState.CANCELLED);
    }

    @Test
    @SneakyThrows
    void getDeployment() {
        String externalId = "external-id-3";
        postDeployment(createDeploymentDto(), externalId);

        awaitUntilAsyncTasksCompleted();

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("read:secret").getBytes());

        String responseBody = mockMvc.perform(get("/api/deployment/{externalId}", externalId)
                        .header("Authorization", basicAuthHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        DeploymentDto response = objectMapper.readValue(responseBody, DeploymentDto.class);

        assertEquals("1.2.3-4", response.getComponentVersion().getVersionName());
        assertEquals("DEV", response.getEnvironment().getName());
    }
}
