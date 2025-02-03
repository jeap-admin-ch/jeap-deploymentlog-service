package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.DeploymentLetterPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.GeneratedDeploymentPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.LinkDto;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class JiraAdapterTest {

    @Mock
    JiraWebClient jiraWebClient;

    @InjectMocks
    JiraAdapter jiraAdapter;

    @Test
    @SneakyThrows
    void getGenerateDeploymentLetter_jiraFailed_exceptionIgnored() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId(UUID.randomUUID().toString())
                .startedAt("01.01.2022 - 12:00:00")
                .componentName("Microservice A")
                .environmentName("PROD")
                .startedBy("John Doe")
                .state("SUCCESS")
                .version("1.0.0")
                .links(List.of(LinkDto.builder()
                        .linkLabel("theLabel")
                        .linkUrl("linkURL")
                        .build()))
                .changeComparedToVersion("1.2.3")
                .changeJiraIssueKeys(Set.of("JEAP-1234"))
                .sequence("NEW")
                .build();

        GeneratedDeploymentPageDto generatedDeploymentPageDto = GeneratedDeploymentPageDto.builder()
                .deploymentLetterPageDto(deploymentLetterPageDto)
                .pageId("pageId")
                .build();

        doThrow(mock(RestClientException.class)).when(jiraWebClient).updateIssueWithConfluenceLink(anyString(), anyString());

        assertDoesNotThrow(() -> jiraAdapter.updateJiraIssuesWithConfluenceLink(generatedDeploymentPageDto));

    }

}
