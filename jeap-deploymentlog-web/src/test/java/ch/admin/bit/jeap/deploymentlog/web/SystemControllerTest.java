package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.exception.AliasNameAlreadyDefinedException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.EnvironmentNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemNotFoundException;
import ch.admin.bit.jeap.deploymentlog.web.api.SystemController;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.UndeploymentCreateDto;
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

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SystemController.class, WebSecurityConfig.class})
@Import(ResourceServerProperties.class)
@AutoConfigureMockMvc
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepository;
    @MockBean
    private SystemService systemService;
    @MockBean
    private DeploymentService deploymentService;
    @MockBean
    private DocgenAsyncService docgenAsyncService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getSystem_whenExists_thenReturnsSystem() throws Exception {

        final String name = "test";
        final System system = new System(name);
        system.getComponents().add(new Component("test", system));

        when(systemService.retrieveSystemByName(anyString())).thenReturn(system);

        mockMvc.perform(
                        get("/api/system/{name}", name)
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(httpBasic("read", "secret")))
                .andDo(result -> java.lang.System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isOk());

        verify(environmentComponentVersionStateRepository, times(1)).findByComponentIn(system.getComponents());
    }

    @Test
    void deleteComponent_noEnvironment_thenErrorIsThrown() throws Exception {
        final String systemName = "testSystem";
        final String componentName = "testComponent";
        final System system = new System(systemName);
        Component component = new Component(componentName, system);
        system.getComponents().add(component);

        UndeploymentCreateDto undeploymentCreateDto = TestData.getUndeploymentCreateDto();
        undeploymentCreateDto.setEnvironmentName(null);

        when(systemService.retrieveComponentByName(systemName, componentName)).thenReturn(component);
        when(systemService.retrieveEnvironmentByName(any())).thenThrow(new EnvironmentNotFoundException(null));
        mockMvc.perform(
                        put("/api/system/deployment-id/undeploy")
                                .content(objectMapper.writeValueAsString(undeploymentCreateDto))
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteComponentWithParams_whenExists_thenComponentDeleted() throws Exception {
        final String systemName = "testSystem";
        final String componentName = "testComponent";
        final System system = new System(systemName);
        Component component = new Component(componentName, system);
        system.getComponents().add(component);
        Environment refEnvironment = new Environment("ref");

        when(systemService.retrieveComponentByName(systemName, componentName)).thenReturn(component);
        when(systemService.retrieveEnvironmentByName(any())).thenReturn(refEnvironment);
        when(deploymentService.getLastDeploymentForComponent(any(Component.class), any(Environment.class))).thenReturn(TestData.getDeployment());
        doNothing().when(systemService).deleteComponent(anyString(), anyString(), anyString());
        mockMvc.perform(
                        put("/api/system/deployment-id/undeploy")
                                .content(objectMapper.writeValueAsString(TestData.getUndeploymentCreateDto()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isOk());
        verify(systemService, times(1)).deleteComponent(systemName, componentName, "REF");
        verify(deploymentService).createUndeployment(any(), eq("deployment-id"), eq(systemName), eq(componentName), eq("REF"), any(), anyString(), anyString());
    }

    @Test
    void deleteComponent_whenNoWriteRole_thenForbidden() throws Exception {
        mockMvc.perform(
                        put("/api/system/deployment-id/undeploy")
                                .content(objectMapper.writeValueAsString(TestData.getUndeploymentCreateDto()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCurrentComponentVersionOnEnvironment() throws Exception {
        String systemName = "test";
        String componentName = "test";
        String environmentName = "PROD";

        String previousVersion = "0.9.1";
        when(systemService.getCurrentVersionOfComponent(systemName, componentName, environmentName))
                .thenReturn(Optional.of(previousVersion));

        mockMvc.perform(get("/api/system/{name}/component/{componentName}/currentVersion/{environmentName}",
                        systemName, componentName, environmentName)
                        .with(httpBasic("read", "secret")))
                .andExpect(content().string(previousVersion))
                .andExpect(status().isOk());

    }

    @Test
    void getCurrentComponentVersionOnEnvironment_notFound() throws Exception {
        String systemName = "test";
        String componentName = "test";
        String environmentName = "PROD";

        when(systemService.getCurrentVersionOfComponent(systemName, componentName, environmentName))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/system/{name}/component/{componentName}/currentVersion/{environmentName}",
                        systemName, componentName, environmentName)
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isNotFound());

    }

    @Test
    void getPreviousComponentVersionOnEnvironment() throws Exception {
        String systemName = "test";
        String componentName = "test";
        String environmentName = "PROD";
        String version = "1.0.0";
        String previousVersion = "0.9.0";

        when(systemService.getPreviousVersionOfComponent(systemName, componentName, environmentName, version))
                .thenReturn(Optional.of(previousVersion));

        mockMvc.perform(get("/api/system/{name}/component/{componentName}/previousVersion/{environmentName}?version={version}",
                        systemName, componentName, environmentName, version)
                        .with(httpBasic("read", "secret")))
                .andExpect(content().string(previousVersion))
                .andExpect(status().isOk());

    }

    @Test
    void getPreviousComponentVersionOnEnvironment_notFound() throws Exception {
        String systemName = "test";
        String componentName = "test";
        String environmentName = "PROD";
        String version = "1.0.0";

        when(systemService.getPreviousVersionOfComponent(systemName, componentName, environmentName, version))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/system/{name}/component/{componentName}/previousVersion/{environmentName}?version={version}",
                        systemName, componentName, environmentName, version)
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isNotFound());

    }

    @Test
    void getPreviousDeploymentOfComponent() throws Exception {
        String systemName = "test";
        String componentName = "test";
        String environmentName = "PROD";
        String version = "1.0.0";
        String previousVersion = "0.9.0";

        Deployment mockDeployment = mockDeployment(previousVersion);

        when(systemService.getPreviousDeploymentOfComponent(systemName, componentName, environmentName, version))
                .thenReturn(Optional.of(mockDeployment));

        mockMvc.perform(get("/api/system/{name}/component/{componentName}/previousDeployment/{environmentName}?version={version}",
                        systemName, componentName, environmentName, version)
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"versionName\":\"" + previousVersion)));

    }

    @Test
    void getPreviousDeploymentOfComponent_notFound() throws Exception {
        String systemName = "test";
        String componentName = "test";
        String environmentName = "PROD";
        String version = "1.0.0";

        when(systemService.getPreviousDeploymentOfComponent(systemName, componentName, environmentName, version))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/system/{name}/component/{componentName}/previousVersion/{environmentName}?version={version}",
                        systemName, componentName, environmentName, version)
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isNotFound());

    }

    @Test
    void createAlias_whenNoWriteRole_thenForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/system/system-id/alias/my-alias")
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAlias_systemExists_thenCreated() throws Exception {
        mockMvc.perform(
                        post("/api/system/system-id/alias/my-alias")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());
    }

    @Test
    void createAlias_systemNotExists_thenNoFound() throws Exception {
        doThrow(new SystemNotFoundException("system-id"))
                .when(systemService).createAlias("system-id", "my-alias");

        mockMvc.perform(
                        post("/api/system/system-id/alias/my-alias")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAlias_aliasExists_thenBadRequest() throws Exception {
        doThrow(AliasNameAlreadyDefinedException.aliasNameAlreadyDefined("my-alias"))
                .when(systemService).createAlias("system-id", "my-alias");

        mockMvc.perform(
                        post("/api/system/system-id/alias/my-alias")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void migrateTo_whenNoWriteRole_thenForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/system/system-old/migrate-to/system-new")
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }

    @Test
    void migrateTo_systemExists_thenCreated() throws Exception {
        mockMvc.perform(
                        post("/api/system/system-old/migrate-to/system-new")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());
    }

    @Test
    void migrateTo_systemNotExists_thenNoFound() throws Exception {
        doThrow(new SystemNotFoundException("system-old"))
                .when(systemService).updateSystemName("system-old", "system-new");

        mockMvc.perform(
                        post("/api/system/system-old/migrate-to/system-new")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound());
    }

    @Test
    void migrateTo_aliasExists_thenBadRequest() throws Exception {
        doThrow(AliasNameAlreadyDefinedException.aliasNameAlreadyDefined("system-new"))
                .when(systemService).updateSystemName("system-old", "system-new");

        mockMvc.perform(
                        post("/api/system/system-old/migrate-to/system-new")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mergeFrom_whenNoWriteRole_thenForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/system/system/merge-from/system-old")
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mergeFrom_systemExists_thenCreated() throws Exception {
        System systemMock = mock(System.class);
        when(systemService.retrieveSystemByName("system")).thenReturn(systemMock);
        when(systemMock.getId()).thenReturn(UUID.randomUUID());
        System systemOldMock = mock(System.class);
        when(systemService.retrieveSystemByName("system-old")).thenReturn(systemOldMock);
        when(systemMock.getId()).thenReturn(UUID.randomUUID());
        mockMvc.perform(
                        post("/api/system/system/merge-from/system-old")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isOk());
    }

    @Test
    void mergeFrom_systemIdentical_thenBadRequest() throws Exception {
        System systemMock = mock(System.class);
        when(systemService.retrieveSystemByName("system")).thenReturn(systemMock);
        when(systemService.retrieveSystemByName("system-old")).thenReturn(systemMock);
        when(systemMock.getId()).thenReturn(UUID.randomUUID());

        mockMvc.perform(
                        post("/api/system/system/merge-from/system-old")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mergeFrom_systemNotExists_thenNoFound() throws Exception {
        doThrow(new SystemNotFoundException("system")).when(systemService).retrieveSystemByName("system");

        mockMvc.perform(
                        post("/api/system/system/merge-from/system-old")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound());
    }

    @Test
    void mergeFrom_oldSystemNotExists_thenNoFound() throws Exception {
        doThrow(new SystemNotFoundException("system")).when(systemService).retrieveSystemByName("system-old");

        mockMvc.perform(
                        post("/api/system/system/merge-from/system-old")
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound());
    }

    private Deployment mockDeployment(String versionName) {
        final Deployment mockDeployment = mock(Deployment.class);
        final ComponentVersion mockComponentVersion = mock(ComponentVersion.class);
        final Environment devEnvironment = new Environment("DEV");
        final Component component = new Component("componentName", new System("systemName"));
        when(mockDeployment.getEnvironment()).thenReturn(devEnvironment);
        when(mockComponentVersion.getComponent()).thenReturn(component);
        when(mockComponentVersion.getVersionName()).thenReturn(versionName);
        when(mockDeployment.getComponentVersion()).thenReturn(mockComponentVersion);
        when(mockDeployment.getComponentVersion().getVersionName()).thenReturn(versionName);
        return mockDeployment;
    }
}
