package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapterMock;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DeploymentAsyncExecutorConfiguration;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ChangelogDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ComponentVersionCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentUpdateStateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    @Qualifier(DeploymentAsyncExecutorConfiguration.ASYNC_THREADPOOL_TASK_EXECUTOR)
    protected ThreadPoolTaskExecutor asyncDocgenExecutor;
    @Autowired
    protected DeploymentRepository deploymentRepository;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    protected ConfluenceAdapterMock confluenceAdapterMock;
    @Autowired
    protected ObjectMapper objectMapper;

    void awaitUntilAsyncTasksCompleted() {
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> asyncDocgenExecutor.getThreadPoolExecutor().getCompletedTaskCount() > 0 &&
                        asyncDocgenExecutor.getThreadPoolExecutor().getActiveCount() == 0);
    }

    protected static DeploymentCreateDto createDeploymentDto() {
        return createDeploymentDto("TestSystem", "test", "1.2.3-4", DeploymentType.CODE);
    }

    protected static DeploymentCreateDto createConfigDeploymentDto() {
        return createDeploymentDto("TestSystem", "test", "2b04ffa", DeploymentType.CONFIG);
    }

    protected static DeploymentCreateDto createDeploymentDto(String systemName, String componentName, String versionName) {
        return createDeploymentDto(systemName, componentName, versionName, DeploymentType.CODE);
    }

    protected static DeploymentCreateDto createDeploymentDto(String systemName, String componentName, String versionName, DeploymentType deploymentType) {
        ComponentVersionCreateDto componentVersion = new ComponentVersionCreateDto();
        componentVersion.setComponentName(componentName);
        componentVersion.setVersionName(versionName);
        componentVersion.setPublishedVersion(false);
        componentVersion.setVersionControlUrl("test");
        componentVersion.setSystemName(systemName);
        componentVersion.setCommitRef("foobar");
        componentVersion.setCommitedAt(ZonedDateTime.now());

        DeploymentCreateDto deploymentCreateDto = new DeploymentCreateDto();
        deploymentCreateDto.setEnvironmentName("DEV");
        deploymentCreateDto.setTarget(new DeploymentTarget("cf","http://localhost/cf","details"));
        deploymentCreateDto.setComponentVersion(componentVersion);
        deploymentCreateDto.setStartedBy("user");
        LocalDateTime startedAt = LocalDateTime.parse("2007-12-03T10:15:30");
        deploymentCreateDto.setStartedAt(ZonedDateTime.of(startedAt, ZoneId.systemDefault()));
        deploymentCreateDto.setLinks(Collections.emptySet());
        deploymentCreateDto.setDeploymentUnit(DeploymentUnit.builder()
                .artifactRepositoryUrl("http://url")
                .coordinates("org:artifact:1.0.0")
                .type(DeploymentUnitType.MAVEN_JAR)
                .build());
        deploymentCreateDto.setDeploymentTypes(Set.of(deploymentType));

        ChangelogDto changelogDto = new ChangelogDto();
        changelogDto.setComment("comment");
        changelogDto.setComparedToVersion("1.1.0");
        changelogDto.setJiraIssueKeys(Set.of("PROJ-123"));
        deploymentCreateDto.setChangelog(changelogDto);

        return deploymentCreateDto;
    }

    protected void postDeployment(DeploymentCreateDto dto, String externalId) {
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("write:secret").getBytes());

        try {
            mockMvc.perform(put("/api/deployment/{externalId}", externalId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", basicAuthHeader)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().is2xxSuccessful());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void putDeploymentState(String externalId, DeploymentState state) {
        putDeploymentState(externalId, state, Map.of());
    }

    protected void putDeploymentState(String externalId, DeploymentState state, Map<String, String> properties) {
        DeploymentCreateDto dto = createDeploymentDto();
        postDeployment(dto, externalId);
        DeploymentUpdateStateDto stateDto = new DeploymentUpdateStateDto();
        stateDto.setState(state);
        stateDto.setMessage("message");
        stateDto.setProperties(properties);
        LocalDateTime startedAt = LocalDateTime.parse("2007-12-03T10:15:30");
        stateDto.setTimestamp(ZonedDateTime.of(startedAt, ZoneId.systemDefault()));

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("write:secret").getBytes());
        try {
            mockMvc.perform(put("/api/deployment/{externalId}/state", externalId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", basicAuthHeader)
                            .content(objectMapper.writeValueAsString(stateDto)))
                    .andExpect(status().is2xxSuccessful());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
