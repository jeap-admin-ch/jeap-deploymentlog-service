package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentCheckService;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentController;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.*;
import ch.admin.bit.jeap.deploymentlog.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.security.resource.properties.ResourceServerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DeploymentController.class, WebSecurityConfig.class})
@Import(ResourceServerProperties.class)
@AutoConfigureMockMvc
class DeploymentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private DeploymentService deploymentService;
    @MockBean
    private DeploymentCheckService deploymentCheckService;
    @MockBean
    private DocgenAsyncService docgenAsyncService;

    @Test
    void putNewDeployment_whenNotExists_thenReturnsCreated() throws Exception {
        String externalId = "123";
        ComponentVersionCreateDto componentVersion = new ComponentVersionCreateDto();
        componentVersion.setComponentName("test");
        componentVersion.setVersionName("1.2.3-4");
        componentVersion.setPublishedVersion(false);
        componentVersion.setVersionControlUrl("test");
        componentVersion.setSystemName("test");
        DeploymentCreateDto deploymentCreateDto = new DeploymentCreateDto();
        deploymentCreateDto.setEnvironmentName("test");
        deploymentCreateDto.setTarget(new DeploymentTarget("cf","http://localhost/cf","details"));
        deploymentCreateDto.setComponentVersion(componentVersion);
        deploymentCreateDto.setStartedBy("user");
        deploymentCreateDto.setLinks(Collections.emptySet());
        deploymentCreateDto.setProperties(Map.of("key", "value"));
        deploymentCreateDto.setReferenceIdentifiers(Set.of("https://foo"));
        ChangelogDto changelogDto = new ChangelogDto();
        changelogDto.setComment("comment");
        changelogDto.setComparedToVersion("1.1.0");
        changelogDto.setJiraIssueKeys(Set.of("PROJ-123"));
        deploymentCreateDto.setChangelog(changelogDto);
        deploymentCreateDto.setRemedyChangeId("REMEDY_123");

        mockMvc.perform(
                        put("/api/deployment/{externalId}", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verify(deploymentService, times(1)).findByExternalId(externalId);

        verify(deploymentService, times(1)).createDeployment(
                externalId,
                deploymentCreateDto.getComponentVersion().getVersionName(),
                deploymentCreateDto.getComponentVersion().getTaggedAt(),
                deploymentCreateDto.getComponentVersion().getVersionControlUrl(),
                deploymentCreateDto.getComponentVersion().getCommitRef(),
                deploymentCreateDto.getComponentVersion().getCommitedAt(),
                deploymentCreateDto.getComponentVersion().isPublishedVersion(),
                deploymentCreateDto.getComponentVersion().getSystemName(),
                deploymentCreateDto.getComponentVersion().getComponentName(),
                deploymentCreateDto.getEnvironmentName(),
                new DeploymentTarget(deploymentCreateDto.getTarget().getType(),
                     deploymentCreateDto.getTarget().getUrl(),
                     deploymentCreateDto.getTarget().getDetails()),
                deploymentCreateDto.getStartedAt(),
                deploymentCreateDto.getStartedBy(),
                deploymentCreateDto.getDeploymentUnit(),
                Collections.emptySet(),
                Map.of("key", "value"),
                Set.of("https://foo"),
                changelogDto.getComment(),
                changelogDto.getComparedToVersion(),
                changelogDto.getJiraIssueKeys(),
                deploymentCreateDto.getRemedyChangeId());

    }

    @Test
    void getDeployment_whenExists_thenReturnsDeployment() throws Exception {

        final String externalId = "123";
        final ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName("test")
                .versionControlUrl("test")
                .committedAt(ZonedDateTime.now())
                .commitRef("test")
                .component(new Component("test", new System("test")))
                .deploymentUnit(DeploymentUnit.builder().artifactRepositoryUrl("test").type(DeploymentUnitType.DOCKER_IMAGE).coordinates("test").build())
                .build();
        final Deployment deployment = Deployment.builder()
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(new Environment("test"))
                .target(new DeploymentTarget("cf", "http://localhost/cf", "details"))
                .componentVersion(componentVersion)
                .externalId(externalId)
                .sequence(DeploymentSequence.NEW)
                .properties(Map.of("key", "value"))
                .build();
        when(deploymentService.getDeployment(externalId)).thenReturn(deployment);

        mockMvc.perform(
                        get("/api/deployment/{externalId}", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(httpBasic("read", "secret")))
                .andDo(result -> java.lang.System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId", is(deployment.getExternalId())))
                .andExpect(jsonPath("$.environment.name", is(deployment.getEnvironment().getName())))
                .andExpect(jsonPath("$.componentVersion.component.name", is(deployment.getComponentVersion().getComponent().getName())))
                .andExpect(jsonPath("$.properties.key", is("value")));

    }

    @Test
    void updateDeployment_whenExists_thenOk() throws Exception {
        final String externalId = "123";
        DeploymentUpdateStateDto deploymentUpdateStateDto = new DeploymentUpdateStateDto();
        deploymentUpdateStateDto.setState(DeploymentState.SUCCESS);
        deploymentUpdateStateDto.setTimestamp(ZonedDateTime.now());

        mockMvc.perform(
                        put("/api/deployment/{externalId}/state", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentUpdateStateDto))
                                .with(httpBasic("write", "secret")))
                .andDo(result -> java.lang.System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isOk());

        verify(deploymentService, times(1)).updateState(
                anyString(),
                any(DeploymentState.class),
                eq(null),
                any(ZonedDateTime.class),
                eq(Map.of()));
    }

    @Test
    void updateDeployment_whenExistsWithMessage_thenOk() throws Exception {
        final String externalId = "123";
        DeploymentUpdateStateDto deploymentUpdateStateDto = new DeploymentUpdateStateDto();
        deploymentUpdateStateDto.setState(DeploymentState.FAILURE);
        deploymentUpdateStateDto.setTimestamp(ZonedDateTime.now());
        deploymentUpdateStateDto.setMessage("very bad");
        deploymentUpdateStateDto.setProperties(Map.of("key", "value"));

        mockMvc.perform(
                        put("/api/deployment/{externalId}/state", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentUpdateStateDto))
                                .with(httpBasic("write", "secret")))
                .andDo(result -> java.lang.System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isOk());

        verify(deploymentService, times(1)).updateState(
                anyString(),
                any(DeploymentState.class),
                anyString(),
                any(ZonedDateTime.class),
                eq(Map.of("key", "value")));
    }

    @Test
    void putNewDeployment_noWriteRole_thenReturnsForbidden() throws Exception {
        String externalId = "123";
        ComponentVersionCreateDto componentVersion = new ComponentVersionCreateDto();
        componentVersion.setComponentName("test");
        componentVersion.setVersionName("1.2.3-4");
        componentVersion.setPublishedVersion(false);
        componentVersion.setVersionControlUrl("test");
        componentVersion.setSystemName("test");
        DeploymentCreateDto deploymentCreateDto = new DeploymentCreateDto();
        deploymentCreateDto.setEnvironmentName("test");
        deploymentCreateDto.setComponentVersion(componentVersion);
        deploymentCreateDto.setStartedBy("user");
        deploymentCreateDto.setLinks(Collections.emptySet());

        mockMvc.perform(
                        put("/api/deployment/{externalId}", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());

        verify(deploymentService, never()).findByExternalId(externalId);

        verify(deploymentService, never()).createDeployment(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void issuesReadyForDeploy_whenAllWithLabel_thenReturnsOk() throws Exception {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");

        String externalId = "123";
        DeploymentCreateDto deploymentCreateDto = generateDeploymentCreateDto(issues);

        DeploymentCheckResultDto resultDto = DeploymentCheckResultDto.builder().result(DeploymentCheckResult.OK).build();
        when(deploymentCheckService.issuesReadyForDeploy(issues)).thenReturn(resultDto);

        //when
        mockMvc.perform(
                        put("/api/deployment/{externalId}?readyForDeployCheck=true", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkResult.result", is(DeploymentCheckResult.OK.name())));

        //then
        verify(deploymentCheckService, times(1)).issuesReadyForDeploy(issues);
    }

    @Test
    void issuesReadyForDeploy_withoutLabel_thenReturnsNok() throws Exception {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");

        String externalId = "123";
        DeploymentCreateDto deploymentCreateDto = generateDeploymentCreateDto(issues);

        DeploymentCheckResultDto resultDto = DeploymentCheckResultDto.builder()
                .result(DeploymentCheckResult.NOK)
                .message("myMessage")
                .build();
        when(deploymentCheckService.issuesReadyForDeploy(issues)).thenReturn(resultDto);

        //when
        mockMvc.perform(
                        put("/api/deployment/{externalId}?readyForDeployCheck=true", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkResult.result", is(DeploymentCheckResult.NOK.name())))
                .andExpect(jsonPath("$.checkResult.message", is("myMessage")));

        //then
        verify(deploymentCheckService, times(1)).issuesReadyForDeploy(issues);
    }

    @Test
    void issuesReadyForDeploy_checkResultIsFailed_thenThrowsException() throws Exception {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");

        String externalId = "123";
        DeploymentCreateDto deploymentCreateDto = generateDeploymentCreateDto(issues);

        when(deploymentCheckService.issuesReadyForDeploy(issues))
                .thenThrow(new RestClientResponseException("myMessage", 500, "status", null, null, null));

        //when
        mockMvc.perform(
                        put("/api/deployment/{externalId}?readyForDeployCheck=true", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isInternalServerError());

        //then
        verify(deploymentCheckService, times(1)).issuesReadyForDeploy(issues);
    }


    private DeploymentCreateDto generateDeploymentCreateDto(Set<String> issues){
        ComponentVersionCreateDto componentVersion = new ComponentVersionCreateDto();
        componentVersion.setComponentName("test");
        componentVersion.setVersionName("1.2.3-4");
        componentVersion.setPublishedVersion(false);
        componentVersion.setVersionControlUrl("test");
        componentVersion.setSystemName("test");
        DeploymentCreateDto deploymentCreateDto = new DeploymentCreateDto();
        deploymentCreateDto.setEnvironmentName("test");
        deploymentCreateDto.setTarget(new DeploymentTarget("cf","http://localhost/cf","details"));
        deploymentCreateDto.setComponentVersion(componentVersion);
        deploymentCreateDto.setStartedBy("user");
        deploymentCreateDto.setLinks(Collections.emptySet());
        ChangelogDto changelogDto = new ChangelogDto();
        changelogDto.setComment("comment");
        changelogDto.setComparedToVersion("1.1.0");
        changelogDto.setJiraIssueKeys(issues);
        deploymentCreateDto.setChangelog(changelogDto);
        deploymentCreateDto.setRemedyChangeId("REMEDY_123");

        return deploymentCreateDto;
    }

}
