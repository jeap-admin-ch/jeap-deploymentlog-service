package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.jira.JiraUnavailableException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidFlowStageRequestException;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentCheckService;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentController;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentReadController;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.*;
import ch.admin.bit.jeap.deploymentlog.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.security.resource.properties.ResourceServerProperties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DeploymentController.class, DeploymentReadController.class})
@Import({WebSecurityConfig.class, ResourceServerProperties.class})
@AutoConfigureMockMvc
class DeploymentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    @MockitoBean
    private DeploymentService deploymentService;
    @MockitoBean
    private DeploymentCheckService deploymentCheckService;
    @MockitoBean
    private DocgenAsyncService docgenAsyncService;
    @MockitoBean
    private FlowStageResolver flowStageResolver;
    @MockitoBean
    private FlowStageProperties flowStageProperties;

    @BeforeEach
    void configureFinalEnvironment() {
        when(flowStageProperties.isEnabled()).thenReturn(true);
        when(flowStageResolver.resolveEffectiveFinalDeploymentEnvironment(any()))
                .thenReturn(new Environment("PROD"));
    }

    @Test
    void putNewDeployment_whenNotExists_thenReturnsCreated() throws Exception {
        String externalId = "123";
        ComponentVersionCreateDto componentVersion = new ComponentVersionCreateDto();
        componentVersion.setComponentName("test");
        componentVersion.setVersionName("1.2.3-4");
        componentVersion.setPublishedVersion(false);
        componentVersion.setVersionControlUrl("test");
        componentVersion.setSystemName("test");
        componentVersion.setCommittedAt(ZonedDateTime.now());
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
        deploymentCreateDto.setDeploymentTypes(Set.of(DeploymentType.CODE, DeploymentType.INFRASTRUCTURE));

        mockMvc.perform(
                        put("/api/deployment/{externalId}", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verify(deploymentService, times(1)).findByExternalId(externalId);

        verify(deploymentService, times(1)).createDeploymentWithFinalEnvironments(
                externalId,
                deploymentCreateDto.getComponentVersion().getVersionName(),
                deploymentCreateDto.getComponentVersion().getTaggedAt(),
                deploymentCreateDto.getComponentVersion().getVersionControlUrl(),
                deploymentCreateDto.getComponentVersion().getCommitRef(),
                deploymentCreateDto.getComponentVersion().getCommittedAt().withZoneSameInstant(ZoneOffset.UTC),
                deploymentCreateDto.getComponentVersion().isPublishedVersion(),
                deploymentCreateDto.getComponentVersion().getSystemName(),
                deploymentCreateDto.getComponentVersion().getComponentName(),
                deploymentCreateDto.getEnvironmentName(),
                deploymentCreateDto.getFinalDeploymentEnvironments(),
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
                deploymentCreateDto.getRemedyChangeId(),
                Set.of(DeploymentType.CODE, DeploymentType.INFRASTRUCTURE));

    }

    @Test
    void putNewDeployment_resolvesRequestedFinalDeploymentEnvironments() throws Exception {
        DeploymentCreateDto deploymentCreateDto = getDeploymentCreateDto();
        deploymentCreateDto.setDeploymentTypes(Set.of(DeploymentType.CODE));
        deploymentCreateDto.setFinalDeploymentEnvironments(List.of("ABN", "PROD"));

        mockMvc.perform(put("/api/deployment/{externalId}", "target-stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deploymentCreateDto))
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verify(flowStageResolver).resolveEffectiveFinalDeploymentEnvironment(List.of("ABN", "PROD"));
    }

    @Test
    void putNewDeployment_withoutDeploymentTypes_doesNotResolveFlowStage() throws Exception {
        DeploymentCreateDto deploymentCreateDto = getDeploymentCreateDto();
        deploymentCreateDto.setDeploymentTypes(null);

        mockMvc.perform(put("/api/deployment/{externalId}", "no-deployment-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deploymentCreateDto))
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verifyNoInteractions(flowStageResolver);
    }

    @Test
    void putNewDeployment_withConfigType_doesNotResolveFlowStage() throws Exception {
        DeploymentCreateDto deploymentCreateDto = getDeploymentCreateDto();
        deploymentCreateDto.setDeploymentTypes(Set.of(DeploymentType.CONFIG));

        mockMvc.perform(put("/api/deployment/{externalId}", "config-deployment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deploymentCreateDto))
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verifyNoInteractions(flowStageResolver);
    }

    @Test
    void putNewCodeDeployment_whenFlowProcessingDisabled_doesNotResolveFlowStage() throws Exception {
        when(flowStageProperties.isEnabled()).thenReturn(false);
        DeploymentCreateDto deploymentCreateDto = getDeploymentCreateDto();
        deploymentCreateDto.setDeploymentTypes(Set.of(DeploymentType.CODE));

        mockMvc.perform(put("/api/deployment/{externalId}", "flow-disabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deploymentCreateDto))
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verifyNoInteractions(flowStageResolver);
    }

    @Test
    void putNewDeployment_withoutCommittedAt_returnsBadRequest() throws Exception {
        DeploymentCreateDto deploymentCreateDto = getDeploymentCreateDto();
        deploymentCreateDto.getComponentVersion().setCommittedAt(null);

        mockMvc.perform(put("/api/deployment/{externalId}", "missing-commit-time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deploymentCreateDto))
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(flowStageResolver);
        verify(deploymentService, never()).createDeploymentWithFinalEnvironments(any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void putNewDeployment_withInvalidFinalEnvironment_returnsBadRequest() throws Exception {
        DeploymentCreateDto deploymentCreateDto = getDeploymentCreateDto();
        deploymentCreateDto.setDeploymentTypes(Set.of(DeploymentType.CODE));
        deploymentCreateDto.setFinalDeploymentEnvironments(List.of("UNKNOWN"));
        when(flowStageResolver.resolveEffectiveFinalDeploymentEnvironment(List.of("UNKNOWN")))
                .thenThrow(new InvalidFlowStageRequestException("Unknown final deployment environment(s): UNKNOWN"));

        mockMvc.perform(put("/api/deployment/{externalId}", "unknown-target-stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deploymentCreateDto))
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unknown final deployment environment(s): UNKNOWN"));

        verify(deploymentService, never()).createDeploymentWithFinalEnvironments(any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
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
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.environment.name", is(deployment.getEnvironment().getName())))
                .andExpect(jsonPath("$.environment.id").exists())
                .andExpect(jsonPath("$.componentVersion.component.name", is(deployment.getComponentVersion().getComponent().getName())))
                .andExpect(jsonPath("$.componentVersion.id").exists())
                .andExpect(jsonPath("$.componentVersion.component.id").exists())
                .andExpect(jsonPath("$.properties.key", is("value")));

    }

    @Test
    void getDeploymentThroughReadApi_doesNotExposeInternalIds() throws Exception {
        String externalId = "read-123";
        ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName("test")
                .versionControlUrl("test")
                .committedAt(ZonedDateTime.now())
                .commitRef("test")
                .component(new Component("test", new System("test")))
                .deploymentUnit(DeploymentUnit.builder().artifactRepositoryUrl("test")
                        .type(DeploymentUnitType.DOCKER_IMAGE).coordinates("test").build())
                .build();
        Deployment deployment = Deployment.builder()
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(new Environment("test"))
                .componentVersion(componentVersion)
                .externalId(externalId)
                .sequence(DeploymentSequence.NEW)
                .properties(Map.of())
                .build();
        when(deploymentService.getDeployment(externalId)).thenReturn(deployment);

        mockMvc.perform(get("/api/deployment-records/{externalId}", externalId)
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId", is(externalId)))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.environment.id").doesNotExist())
                .andExpect(jsonPath("$.componentVersion.id").doesNotExist())
                .andExpect(jsonPath("$.componentVersion.component.id").doesNotExist());
    }

    @Test
    void searchDeployments_rejectsInvalidAndRepeatedFilters() throws Exception {
        mockMvc.perform(get("/api/deployment-records")
                        .param("jiraIssue", "invalid")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_DEPLOYMENT_FILTER")));

        mockMvc.perform(get("/api/deployment-records")
                        .param("system", "one", "two")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_DEPLOYMENT_FILTER")));

        verify(deploymentService, never()).searchDeployments(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,''", "page,' '", "page,1.5", "page,2147483648",
            "size,0", "size,-1", "size,abc", "size,''", "size,' '", "size,1.5", "size,2147483648"})
    void searchDeployments_rejectsInvalidPaging(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/deployment-records")
                        .param(parameter, value)
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_DEPLOYMENT_FILTER")));

        verify(deploymentService, never()).searchDeployments(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size"})
    void searchDeployments_rejectsRepeatedPaging(String parameter) throws Exception {
        mockMvc.perform(get("/api/deployment-records")
                        .param(parameter, "1", "2")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_DEPLOYMENT_FILTER")));

        verify(deploymentService, never()).searchDeployments(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"0,1", "2,50"})
    void searchDeployments_preservesValidPaging(int page, int size) throws Exception {
        when(deploymentService.searchDeployments(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/deployment-records")
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size))
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk());

        verify(deploymentService).searchDeployments(any(), argThat(pageable ->
                pageable.getPageNumber() == page && pageable.getPageSize() == size));
    }

    @Test
    void searchDeployments_appendsExternalIdAsStableSortTieBreaker() throws Exception {
        when(deploymentService.searchDeployments(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/deployment-records")
                        .param("sort", "startedAt,desc")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk());

        verify(deploymentService).searchDeployments(any(), argThat(pageable -> {
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort().toList())
                    .extracting(order -> order.getProperty() + ":" + order.getDirection())
                    .containsExactly("startedAt:DESC", "externalId:ASC");
            return true;
        }));
    }

    @Test
    void searchDeployments_doesNotDuplicateRequestedExternalIdSort() throws Exception {
        when(deploymentService.searchDeployments(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/deployment-records")
                        .param("sort", "startedAt,desc", "externalId,desc")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk());

        verify(deploymentService).searchDeployments(any(), argThat(pageable -> {
            assertThat(pageable.getSort().toList())
                    .extracting(order -> order.getProperty() + ":" + order.getDirection())
                    .containsExactly("startedAt:DESC", "externalId:DESC");
            return true;
        }));
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
    void updateDeployment_whenDocgenQueueIsFull_thenDeploymentRequestStillSucceeds() throws Exception {
        String externalId = "123";
        UUID deploymentId = UUID.randomUUID();
        DeploymentUpdateStateDto deploymentUpdateStateDto = new DeploymentUpdateStateDto();
        deploymentUpdateStateDto.setState(DeploymentState.SUCCESS);
        deploymentUpdateStateDto.setTimestamp(ZonedDateTime.now());
        when(deploymentService.updateState(anyString(), any(), any(), any(), any())).thenReturn(deploymentId);
        doThrow(new TaskRejectedException("queue full"))
                .when(docgenAsyncService).triggerDocgenForDeployment(deploymentId);

        mockMvc.perform(put("/api/deployment/{externalId}/state", externalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deploymentUpdateStateDto))
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isOk());

        verify(docgenAsyncService).triggerDocgenForDeployment(deploymentId);
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
        mockMvc.perform(
                        put("/api/deployment/{externalId}", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(getDeploymentCreateDto()))
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());

        verify(deploymentService, never()).findByExternalId(externalId);

        verify(deploymentService, never()).createDeploymentWithFinalEnvironments(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), any());
    }

    private static DeploymentCreateDto getDeploymentCreateDto() {
        ComponentVersionCreateDto componentVersion = new ComponentVersionCreateDto();
        componentVersion.setComponentName("test");
        componentVersion.setVersionName("1.2.3-4");
        componentVersion.setPublishedVersion(false);
        componentVersion.setVersionControlUrl("test");
        componentVersion.setSystemName("test");
        componentVersion.setCommittedAt(ZonedDateTime.now());
        DeploymentCreateDto deploymentCreateDto = new DeploymentCreateDto();
        deploymentCreateDto.setEnvironmentName("test");
        deploymentCreateDto.setComponentVersion(componentVersion);
        deploymentCreateDto.setStartedBy("user");
        deploymentCreateDto.setLinks(Collections.emptySet());
        return deploymentCreateDto;
    }

    @Test
    void checkIssuesReadyForDeploy_whenAllWithLabel_thenReturnsOk() throws Exception {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");

        String externalId = "123";
        DeploymentCreateDto deploymentCreateDto = generateDeploymentCreateDto(issues);

        DeploymentCheckResultDto resultDto = DeploymentCheckResultDto.builder().result(DeploymentCheckResult.OK).build();
        when(deploymentCheckService.checkIssuesReadyForDeploy(issues)).thenReturn(resultDto);

        //when
        mockMvc.perform(
                        put("/api/deployment/{externalId}?readyForDeployCheck=true", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkResult.result", is(DeploymentCheckResult.OK.name())));

        //then
        verify(deploymentCheckService, times(1)).checkIssuesReadyForDeploy(issues);
    }

    @Test
    void checkIssuesReadyForDeploy_withoutLabel_thenReturnsNok() throws Exception {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");

        String externalId = "123";
        DeploymentCreateDto deploymentCreateDto = generateDeploymentCreateDto(issues);

        DeploymentCheckResultDto resultDto = DeploymentCheckResultDto.builder()
                .result(DeploymentCheckResult.NOK)
                .message("myMessage")
                .issuesWithoutLabel(java.util.List.of("JEAP-1234"))
                .issuesNotFound(java.util.List.of("JEAP-2345"))
                .projectsNotVisible(java.util.List.of("UTF"))
                .build();
        when(deploymentCheckService.checkIssuesReadyForDeploy(issues)).thenReturn(resultDto);

        //when
        mockMvc.perform(
                        put("/api/deployment/{externalId}?readyForDeployCheck=true", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkResult.result", is(DeploymentCheckResult.NOK.name())))
                .andExpect(jsonPath("$.checkResult.message", is("myMessage")))
                .andExpect(jsonPath("$.checkResult.issuesWithoutLabel[0]", is("JEAP-1234")))
                .andExpect(jsonPath("$.checkResult.issuesNotFound[0]", is("JEAP-2345")))
                .andExpect(jsonPath("$.checkResult.projectsNotVisible[0]", is("UTF")));

        //then: a NOK check result blocks the deployment - no deployment record is created
        verify(deploymentCheckService, times(1)).checkIssuesReadyForDeploy(issues);
        verify(deploymentService, never()).createDeploymentWithFinalEnvironments(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void checkIssuesReadyForDeploy_warning_thenCreatesDeploymentAndReturnsCreated() throws Exception {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "UTF-8");

        String externalId = "123";
        DeploymentCreateDto deploymentCreateDto = generateDeploymentCreateDto(issues);

        DeploymentCheckResultDto resultDto = DeploymentCheckResultDto.builder()
                .result(DeploymentCheckResult.WARNING)
                .message("Issues in jira projects not visible to the deployment log service: [UTF-8]")
                .issuesNotFound(java.util.List.of("UTF-8"))
                .projectsNotVisible(java.util.List.of("UTF"))
                .build();
        when(deploymentCheckService.checkIssuesReadyForDeploy(issues)).thenReturn(resultDto);

        //when
        mockMvc.perform(
                        put("/api/deployment/{externalId}?readyForDeployCheck=true", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkResult.result", is(DeploymentCheckResult.WARNING.name())))
                .andExpect(jsonPath("$.checkResult.issuesNotFound[0]", is("UTF-8")))
                .andExpect(jsonPath("$.checkResult.projectsNotVisible[0]", is("UTF")));

        //then: a WARNING check result does not block the deployment - the deployment record is created
        verify(deploymentCheckService, times(1)).checkIssuesReadyForDeploy(issues);
        verify(deploymentService, times(1)).createDeploymentWithFinalEnvironments(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void checkIssuesReadyForDeploy_jiraUnavailable_thenReturnsServiceUnavailable() throws Exception {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");

        String externalId = "123";
        DeploymentCreateDto deploymentCreateDto = generateDeploymentCreateDto(issues);

        when(deploymentCheckService.checkIssuesReadyForDeploy(issues))
                .thenThrow(JiraUnavailableException.jiraNotAvailable(new ResourceAccessException("connection refused")));

        //when
        mockMvc.perform(
                        put("/api/deployment/{externalId}?readyForDeployCheck=true", externalId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deploymentCreateDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isServiceUnavailable());

        //then
        verify(deploymentCheckService, times(1)).checkIssuesReadyForDeploy(issues);
        verify(deploymentService, never()).createDeploymentWithFinalEnvironments(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), any());
    }

    private DeploymentCreateDto generateDeploymentCreateDto(Set<String> issues){
        ComponentVersionCreateDto componentVersion = new ComponentVersionCreateDto();
        componentVersion.setComponentName("test");
        componentVersion.setVersionName("1.2.3-4");
        componentVersion.setPublishedVersion(false);
        componentVersion.setVersionControlUrl("test");
        componentVersion.setSystemName("test");
        componentVersion.setCommittedAt(ZonedDateTime.now());
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
