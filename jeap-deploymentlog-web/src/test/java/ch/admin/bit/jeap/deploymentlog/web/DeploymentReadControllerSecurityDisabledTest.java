package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersion;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnit;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnitType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentReadController;
import ch.admin.bit.jeap.deploymentlog.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.security.resource.properties.ResourceServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeploymentReadController.class)
@Import({WebSecurityConfig.class, ResourceServerProperties.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = "jeap.deploymentlog.read-api.security-enabled=false")
class DeploymentReadControllerSecurityDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentService deploymentService;

    @Test
    void readEndpointsAreAccessibleWithoutAuthenticationWhenSecurityIsDisabled() throws Exception {
        when(deploymentService.searchDeployments(any(), any())).thenReturn(Page.empty());
        when(deploymentService.getDeployment("external-id")).thenReturn(deployment("external-id"));

        mockMvc.perform(get("/api/deployment-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/deployment-records/external-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("external-id"));
    }

    private static Deployment deployment(String externalId) {
        ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName("1.0.0")
                .versionControlUrl("https://example.test/repository")
                .commitRef("abc123")
                .committedAt(ZonedDateTime.now())
                .component(new Component("component", new System("system")))
                .deploymentUnit(DeploymentUnit.builder()
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("registry/image:1.0.0")
                        .artifactRepositoryUrl("https://example.test/registry")
                        .build())
                .build();
        return Deployment.builder()
                .externalId(externalId)
                .startedAt(ZonedDateTime.now())
                .startedBy("test")
                .environment(new Environment("DEV"))
                .componentVersion(componentVersion)
                .sequence(DeploymentSequence.NEW)
                .properties(Map.of())
                .build();
    }
}
