package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
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
    void renderComponentPageWithoutFlows() {
        String content = templateRenderer.renderComponentPage(ComponentPageDto.builder()
                .componentName("component")
                .flowMaxShow(50)
                .flows(List.of())
                .build());

        assertThat(content)
                .startsWith("<ac:structured-macro ac:name=\"expand\">")
                .contains("<ac:parameter ac:name=\"title\">Version Flows Diagram</ac:parameter>")
                .contains("Version Flows")
                .contains("noch keine Version Flows bekannt")
                .doesNotContain("<table class=\"wrapped\" style=\"table-layout");
    }

    @Test
    void renderComponentPageEscapesDynamicContentAndKeepsAttemptsAndLinksReadable() {
        ComponentFlowDto flow = ComponentFlowDto.builder()
                .flowId("flow-id")
                .version("a-very-long-version<&>")
                .versionControlUrl("https://git.example/version?a=1&b=2")
                .bornAt("2026-08-01 10:00:00")
                .duration(null)
                .type("AD_HOC")
                .state("ABORTED")
                .targetStage("PROD<&>")
                .deployments(List.of(
                        ComponentFlowDeploymentDto.builder().startedAt("2026-08-01 10:00:00")
                                .startedAtInstant(Instant.parse("2026-08-01T08:00:00Z"))
                                .stage("DEV").state("FAILURE").build(),
                        ComponentFlowDeploymentDto.builder().startedAt("2026-08-01 10:05:00")
                                .startedAtInstant(Instant.parse("2026-08-01T08:05:00Z"))
                                .stage("DEV").state("SUCCESS")
                                .pageUrl("https://confluence.example/pages/viewpage.action?pageId=page-123").build(),
                        ComponentFlowDeploymentDto.builder().startedAt("2026-08-01 10:10:00")
                                .startedAtInstant(Instant.parse("2026-08-01T08:10:00Z"))
                                .stage("REF").state("STARTED").build(),
                        ComponentFlowDeploymentDto.builder().startedAt("2026-08-01 10:15:00")
                                .startedAtInstant(Instant.parse("2026-08-01T08:15:00Z"))
                                .stage("REF").state("CANCELLED").build()))
                .jiraIssues(List.of(JiraIssueDto.builder().key("JEAP-1")
                        .url("https://jira.example/browse/JEAP-1").build()))
                .build();

        String content = templateRenderer.renderComponentPage(ComponentPageDto.builder()
                .componentName("component")
                .flowMaxShow(50)
                .flows(List.of(flow))
                .build());

        assertThat(content)
                .startsWith("<ac:structured-macro ac:name=\"expand\">")
                .contains("<svg", "<polyline", "<title>a-very-long-version&lt;&amp;&gt; · DEV · FAILURE")
                .contains("a-very-long-version&lt;&amp;&gt;")
                .contains("PROD&lt;&amp;&gt;")
                .contains("ABORTED", "AD_HOC", "page-123", "JEAP-1")
                .contains("<ac:emoticon ac:name=\"cross\"/>", "<ac:emoticon ac:name=\"tick\"/>",
                        "<ac:emoticon ac:name=\"minus\"/>", "<ac:emoticon ac:name=\"question\"/>")
                .contains("background-color: #ffebe6", "width: 31%")
                .contains("href=\"https://confluence.example/pages/viewpage.action?pageId=page-123\"")
                .doesNotContain("Bewertung", "ri:content-id", "<strong>ABORTED</strong>", "—",
                        ">STARTED<", ">CANCELLED<")
                .doesNotContain("a-very-long-version<&>");
    }

    @Test
    void renderJiraProjectPageEscapesContentAndShowsStatusWithoutJiraApiData() {
        String content = templateRenderer.renderJiraProjectPage(JiraProjectPageDto.builder()
                .projectKey("JEAP")
                .confluenceSpaceKey("JMEAWS<&>")
                .activityPeriod(java.time.Duration.ofDays(30))
                .issues(List.of(
                        JiraProjectIssueDto.builder()
                                .issueKey("JEAP-1<&>")
                                .jiraIssueUrl("https://jira.example/browse/JEAP-1")
                                .latestDeploymentAt("2026-09-01 10:00:00")
                                .highestSuccessfulStage("REF<&>")
                                .failedHigherStage("PROD<&>")
                                .build(),
                        JiraProjectIssueDto.builder()
                                .issueKey("JEAP-2")
                                .jiraIssueUrl("https://jira.example/browse/JEAP-2")
                                .deploymentLogIssuePageUrl("https://confluence.example/pages/issue-2")
                                .latestDeploymentAt("2026-09-01 09:00:00")
                                .highestSuccessfulStage("DEV")
                                .build()))
                .build());

        assertThat(content)
                .contains("Deploymentrelevante Jira Issues", "30 Tage", "JEAP-1&lt;&amp;&gt;", "REF&lt;&amp;&gt;")
                .contains("<ac:emoticon ac:name=\"cross\"/>", "Fehler auf", ">-</span>")
                .contains("JEAP-2 - JEAP - JMEAWS&lt;&amp;&gt; - Confluence")
                .contains("href=\"https://confluence.example/pages/issue-2\"")
                .containsSubsequence("<th>Jira Issue</th>", "<th>Deployment Status</th>",
                        "<th>Confluence Issue Page</th>")
                .doesNotContain("Letztes Deployment", "2026-09-01 10:00:00", "2026-09-01 09:00:00",
                        "PT720H", "JEAP-1<&>", "REF<&>", "PROD<&>");
    }

    @Test
    void renderJiraIssuePageShowsDeploymentDataStatusTextAndSafeLinks() {
        String content = templateRenderer.renderJiraIssuePage(JiraIssuePageDto.builder()
                .issueKey("JEAP-1<&>")
                .jiraIssueUrl("https://jira.example/browse/JEAP-1")
                .deployments(List.of(
                        JiraIssueDeploymentDto.builder()
                                .startedAt("2026-07-06 14:45:16").stage("PROD").system("WVS")
                                .component("service<&>").version("5.0.2-long<&>")
                                .deploymentTypes("CODE, CONFIG").state("SUCCESS")
                                .deploymentPageUrl("https://confluence.example/pages/viewpage.action?pageId=42")
                                .startedBy("John <Doe>").build(),
                        JiraIssueDeploymentDto.builder()
                                .startedAt("2026-07-06 14:32:22").stage("REF").system("WVS")
                                .component("service").version("5.0.2").deploymentTypes("INFRASTRUCTURE")
                                .state("FAILURE").startedBy("Jane Doe").build()))
                .build());

        assertThat(content)
                .contains("Datum / Zeit", "Umgebung", "System", "Komponente", "Version", "Typ", "Status",
                        "Deployment", "Gestartet durch", "CODE, CONFIG", "INFRASTRUCTURE")
                .contains("<ac:emoticon ac:name=\"tick\"/>", "Erfolgreich",
                        "<ac:emoticon ac:name=\"cross\"/>", "Fehlgeschlagen")
                .contains("href=\"https://confluence.example/pages/viewpage.action?pageId=42\"")
                .contains("service&lt;&amp;&gt;", "5.0.2-long&lt;&amp;&gt;", "John &lt;Doe&gt;")
                .doesNotContain("service<&>", "John <Doe>");
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
    void renderCancelledDeploymentHistoryPage() {
        DeploymentDto deploymentDto = DeploymentDto.builder()
                .state("CANCELLED")
                .build();
        DeploymentHistoryPageDto pageDto = DeploymentHistoryPageDto.builder()
                .deployments(List.of(deploymentDto))
                .build();

        String content = templateRenderer.renderDeploymentHistoryPage(pageDto);

        assertThat(content)
                .contains("<ac:emoticon ac:name=\"minus\"/>", "Vorzeitig beendet")
                .doesNotContain("Abgebrochen");
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
                .deploymentTypes("CODE, CONFIG")
                .build();

        String content = templateRenderer.renderDeploymentLetterPage(deploymentLetterPageDto);
        assertNotNull(content);
        assertThat(content)
                .doesNotContain("Kein Changelog vorhanden")
                .contains("Änderungen zu Version 1.2.3")
                .contains("Keine Jira Referenzen wurden im Commit-Log gefunden")
                .contains("CODE, CONFIG");
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
                .contains("https://my-build-job-link.com")
                .contains("AWS Task Definition")
                .contains("arn:foo:bar")
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
                .deploymentTypes("CODE, CONFIG")
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
                .contains(dto.getDeploymentHistoryOverviewMinStartedAt())
                .contains("CODE, CONFIG");
    }

    @Test
    void renderDeploymentHistoryOverviewRootPage() {
        String content = templateRenderer.renderDeploymentHistoryOverviewRootPage();
        assertNotNull(content);
    }

    @BeforeEach
    void setUp() {
        DocumentationGeneratorConfig generatorConfig = new DocumentationGeneratorConfig();
        templateRenderer = new TemplateRenderer(generatorConfig.templateEngine(applicationContext),
                new VersionFlowDiagramRenderer());
    }
}
