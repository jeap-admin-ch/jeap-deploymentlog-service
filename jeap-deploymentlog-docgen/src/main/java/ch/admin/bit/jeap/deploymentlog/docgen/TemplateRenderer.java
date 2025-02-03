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

    private final ITemplateEngine templateEngine;

    String renderSystemPage(SystemPageDto systemPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("system", systemPageDto);
        return templateEngine.process("system", context).trim();
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
        context.setVariable("deploymentLetter", deploymentLetterPageDto);
        return templateEngine.process("deploymentLetter", context).trim();
    }

    String renderUndeploymentLetterPage(DeploymentLetterPageDto deploymentLetterPageDto) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable("deploymentLetter", deploymentLetterPageDto);
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
