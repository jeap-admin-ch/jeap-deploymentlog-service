package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DeploymentDocControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jeap.deploymentlog.documentation.root-url}")
    private String documentationRootUrl;

    @MockitoBean
    private DeploymentRepository deploymentRepository;

    @MockitoBean
    private DeploymentPageRepository deploymentPageRepository;

    private static final String EXTERNAL_ID = "external-id-3";
    private static final UUID DEPLOYMENT_UUID = UUID.randomUUID();
    private static final String PAGE_ID = "myPageId";

    @Test
    @SneakyThrows
    void getDeployment_deploymentPageFound_returnRedirect() {
        final Deployment mockDeployment = mock(Deployment.class);
        when(deploymentRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(mockDeployment));
        when(mockDeployment.getId()).thenReturn(DEPLOYMENT_UUID);
        final DeploymentPage mockDeploymentPage = mock(DeploymentPage.class);
        when(mockDeploymentPage.getPageId()).thenReturn(PAGE_ID);
        when(deploymentPageRepository.findDeploymentPageByDeploymentId(DEPLOYMENT_UUID)).thenReturn(Optional.of(mockDeploymentPage));

        mockMvc.perform(get("/api/deployment-doc/{externalId}", EXTERNAL_ID))
                .andExpect(status().isFound()) // 302 Found
                .andExpect(redirectedUrl(documentationRootUrl + PAGE_ID));
    }

    @Test
    @SneakyThrows
    void getDeployment_deploymentPageNotFound_returnNotFound() {
        final Deployment mockDeployment = mock(Deployment.class);
        when(deploymentRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(mockDeployment));
        when(mockDeployment.getId()).thenReturn(DEPLOYMENT_UUID);
        mockMvc.perform(get("/api/deployment-doc/{externalId}", EXTERNAL_ID))
                .andExpect(status().isNotFound()); // 404 Not Found
    }

    @Test
    @SneakyThrows
    void getDeployment_deploymentNotFound_returnNotFound() {
        mockMvc.perform(get("/api/deployment-doc/{externalId}", EXTERNAL_ID))
                .andExpect(status().isNotFound()); // 404 Not Found
    }
}
