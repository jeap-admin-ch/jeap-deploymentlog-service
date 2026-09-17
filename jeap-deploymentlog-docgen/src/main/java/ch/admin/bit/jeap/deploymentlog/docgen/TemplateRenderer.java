package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

@Component
@RequiredArgsConstructor
class TemplateRenderer {

    public static final String DEPLOYMENT_LETTER = "deploymentLetter";
    private final ITemplateEngine templateEngine;
    private final VersionFlowDiagramRenderer versionFlowDiagramRenderer;

    String renderSystemPage(SystemPageDto systemPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("system", systemPageDto);
        return templateEngine.process("system", context).trim();
    }

    String renderComponentPage(ComponentPageDto componentPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("component", componentPageDto);
        context.setVariable("versionFlowDiagram", versionFlowDiagramRenderer.render(componentPageDto));
        return templateEngine.process("component", context).trim();
    }

    String renderJiraProjectPage(JiraProjectPageDto jiraProjectPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("project", jiraProjectPageDto);
        return templateEngine.process("jiraProject", context).trim();
    }

    String renderJiraIssuePage(JiraIssuePageDto jiraIssuePageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("issue", jiraIssuePageDto);
        return templateEngine.process("jiraIssue", context).trim();
    }

    String renderDeploymentHistoryPage(DeploymentHistoryPageDto deploymentHistoryPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("deploymentHistory", deploymentHistoryPageDto);
        return templateEngine.process("deploymentHistory", context).trim();
    }

    String renderDeploymentListPage() {
        Context context = new Context(Locale.GERMAN);
        return templateEngine.process("deploymentList", context).trim();
    }

    String renderDeploymentLetterPage(DeploymentLetterPageDto deploymentLetterPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable(DEPLOYMENT_LETTER, deploymentLetterPageDto);
        return templateEngine.process(DEPLOYMENT_LETTER, context).trim();
    }

    String renderUndeploymentLetterPage(DeploymentLetterPageDto deploymentLetterPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable(DEPLOYMENT_LETTER, deploymentLetterPageDto);
        return templateEngine.process("undeploymentLetter", context).trim();
    }

    String renderDeploymentHistoryOverviewPage(DeploymentHistoryOverviewPageDto deploymentHistoryOverviewPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("deploymentHistoryOverview", deploymentHistoryOverviewPageDto);
        return templateEngine.process("deploymentHistoryOverview", context).trim();
    }

    String renderDeploymentHistoryOverviewRootPage() {
        return templateEngine.process("deploymentHistoryOverviewRoot", new Context(Locale.GERMAN)).trim();
    }
}
