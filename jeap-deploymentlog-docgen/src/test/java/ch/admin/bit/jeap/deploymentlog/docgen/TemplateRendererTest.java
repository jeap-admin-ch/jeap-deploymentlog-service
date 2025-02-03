package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
@ExtendWith(SpringExtension.class)
class TemplateRendererTest {

    @Autowired
    ApplicationContext applicationContext;

    private TemplateRenderer templateRenderer;

    @Test
    void renderSystemPage() {
        SystemPageDto systemPageDto = SystemPageDto.builder().name("SYSTEM A").build();
        String content = templateRenderer.renderSystemPage(systemPageDto);
        assertNotNull(content);
    }

    @Test
    void renderDeploymentHistoryPage() {

        DeploymentDto deploymentDto = DeploymentDto.builder()
                .version("0.0.1")
                .versionControlUrl("https://somewere.com")
                .startedBy("John Doe")
                .deploymentLetterLink("01.01.2022 - 12:00:00 Deployment Letter 123")
                .state("STARTED")
                .deploymentId("123")
                .startedAt("01.01.2022 - 12:00:00")
                .build();

        DeploymentHistoryPageDto deploymentHistoryPageDto = DeploymentHistoryPageDto.builder()
                .deploymentHistoryMaxShow(1)
                .environmentName("ENV")
                .systemName("SYSTEM A")
                .deployments(List.of(deploymentDto))
                .build();

        String content = templateRenderer.renderDeploymentHistoryPage(deploymentHistoryPageDto);
        assertNotNull(content);
    }

    @Test
    void renderDeploymentListPage() {
        String content = templateRenderer.renderDeploymentListPage();
        assertNotNull(content);
    }

    @Test
    void renderDeploymentLetterPage_withEmptyChangeLog() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId("123")
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
                .changeJiraIssueKeys(Set.of())
                .sequence("NEW")
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content)
                .doesNotContain("Kein Changelog vorhanden")
                .contains("Änderungen zu Version 1.2.3")
                .contains("Keine Jira Referenzen wurden im Commit-Log gefunden");
    }

    @Test
    void renderDeploymentLetterPage_withChangeLog() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId("123")
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
                .changeComment("")
                .sequence("NEW")
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content)
                .doesNotContain("Kein Changelog vorhanden")
                .contains("Änderungen zu Version 1.2.3")
                .doesNotContain("Keine Jira Referenzen wurden im Commit-Log gefunden");
    }

    @Test
    void renderDeploymentLetterPage_withoutChangeLog() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId("123")
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
                .changeComparedToVersion("")
                .changeJiraIssueKeys(Set.of())
                .sequence("NEW")
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content)
                .contains("Kein Changelog vorhanden")
                .doesNotContain("Änderungen zu Version")
                .doesNotContain("Keine Jira Referenzen wurden im Commit-Log gefunden");
    }

    @Test
    void renderDeploymentLetterPage_withRemedyChange() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId("123")
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
                .changeComparedToVersion("")
                .changeJiraIssueKeys(Set.of())
                .sequence("NEW")
                .remedyChangeId("MyRemedyChangeId")
                .remedyChangeLink("https://remedy-test.com")
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content)
                .contains("Remedy")
                .contains("MyRemedyChangeId")
                .contains("https://remedy-test.com");
    }

    @Test
    void renderDeploymentLetterPage_withoutRemedyChange() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId("123")
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
                .changeComparedToVersion("")
                .changeJiraIssueKeys(Set.of())
                .sequence("NEW")
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content).doesNotContain("Remedy");
    }

    @Test
    void renderDeploymentLetterPage_withBuildJobLinks() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId("123")
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
                .changeComparedToVersion("")
                .changeJiraIssueKeys(Set.of())
                .sequence("NEW")
                .buildJobLinks(Set.of("https://my-build-job-link.com"))
                .properties(Map.of(
                        "AWS Task Definition", "arn:foo:bar",
                        "Some linked resource", "https://foo/bar"))
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content)
                .contains("Build Job")
                .contains("https://my-build-job-link.com");
        assertThat(content)
                .contains("AWS Task Definition")
                .contains("arn:foo:bar");
        assertThat(content)
                .contains("Some linked resource")
                .contains("""
                        <a href="https://foo/bar" target="_blank">https://foo/bar</a>""");
    }

    @Test
    void renderDeploymentLetterPage_withTarget() {
        DeploymentLetterPageDto deploymentLetterPageDto = DeploymentLetterPageDto.builder()
                .deploymentId("123")
                .startedAt("01.01.2022 - 12:00:00")
                .componentName("Microservice A")
                .environmentName("PROD")
                .targetType("CF")
                .targetUrl("http://localhost/cf")
                .targetDetails("details")
                .startedBy("John Doe")
                .state("SUCCESS")
                .version("1.0.0")
                .links(List.of(LinkDto.builder()
                        .linkLabel("theLabel")
                        .linkUrl("linkURL")
                        .build()))
                .changeComparedToVersion("1.2.3")
                .changeJiraIssueKeys(Set.of("JEAP-1234"))
                .changeComment("")
                .sequence("NEW")
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content)
                .contains("CF")
                .contains("http://localhost/cf")
                .contains("details");
    }

    @Test
    void renderDeploymentHistoryOverviewPage() {
        DeploymentDto deploymentDto = DeploymentDto.builder()
                .version("0.0.1")
                .versionControlUrl("https://somewere.com")
                .startedBy("John Doe")
                .deploymentLetterLink("01.01.2022 - 12:00:00 Deployment Letter 123")
                .state("STARTED")
                .deploymentId("123")
                .startedAt("01.01.2022 - 12:00:00")
                .build();

        DeploymentHistoryOverviewPageDto dto = DeploymentHistoryOverviewPageDto.builder()
                .deploymentHistoryMaxShow(1)
                .deploymentHistoryOverviewMinStartedAt("22.08.2023")
                .environmentName("ENV")
                .deployments(List.of(deploymentDto))
                .build();

        String content = templateRenderer.renderDeploymentHistoryOverviewPage(dto);
        assertThat(content)
                .isNotNull()
                .contains(dto.getDeploymentHistoryOverviewMinStartedAt());
    }

    @Test
    void renderDeploymentHistoryOverviewRootPage() {
        String content = templateRenderer.renderDeploymentHistoryOverviewRootPage();
        assertNotNull(content);
    }

    @BeforeEach
    void setUp() {
        DocumentationGeneratorConfig generatorConfig = new DocumentationGeneratorConfig();
        templateRenderer = new TemplateRenderer(generatorConfig.templateEngine(applicationContext));
    }
}
