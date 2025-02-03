package ch.admin.bit.jeap.deploymentlog.jira;

import lombok.Getter;

import java.util.List;

@Getter
public class JiraIssuesNotFoundException extends RuntimeException {

    private final List<String> issues;

    public JiraIssuesNotFoundException(List<String> issues) {
        super("One or more issues not found in jira");
        this.issues = issues;
    }
}
