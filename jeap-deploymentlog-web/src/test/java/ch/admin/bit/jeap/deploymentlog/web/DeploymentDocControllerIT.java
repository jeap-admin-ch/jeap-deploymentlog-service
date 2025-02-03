package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeploymentDocControllerIT {

    @Autowired
    private WebTestClient webTestClient;

    @Value("${jeap.deploymentlog.documentation.root-url}")
    private String documentationRootUrl;

    @MockBean
    private DeploymentRepository deploymentRepository;

    @MockBean
    private DeploymentPageRepository deploymentPageRepository;

    private static final String EXTERNAL_ID = "external-id-3";
    private static final UUID DEPLOYMENT_UUID = UUID.randomUUID();
    private static final String PAGE_ID = "myPageId";

    @Test
    void getDeployment_deploymentPageFound_returnRedirect() {
        final Deployment mockDeployment = mock(Deployment.class);
        when(deploymentRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(mockDeployment));
        when(mockDeployment.getId()).thenReturn(DEPLOYMENT_UUID);
        final DeploymentPage mockDeploymentPage = mock(DeploymentPage.class);
        when(mockDeploymentPage.getPageId()).thenReturn(PAGE_ID);
        when(deploymentPageRepository.findDeploymentPageByDeploymentId(DEPLOYMENT_UUID)).thenReturn(Optional.of(mockDeploymentPage));

        webTestClient.get()
                .uri("/api/deployment-doc/" + EXTERNAL_ID)
                .exchange()
                .expectStatus()
                .isFound()
                .expectHeader()
                .location(documentationRootUrl + PAGE_ID);
    }

    @Test
    void getDeployment_deploymentPageNotFound_returnNotFound() {
        final Deployment mockDeployment = mock(Deployment.class);
        when(deploymentRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(mockDeployment));
        when(mockDeployment.getId()).thenReturn(DEPLOYMENT_UUID);
        webTestClient.get()
                .uri("/api/deployment-doc/" + EXTERNAL_ID)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void getDeployment_deploymentNotFound_returnNotFound() {
        webTestClient.get()
                .uri("/api/deployment-doc/" + EXTERNAL_ID)
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
