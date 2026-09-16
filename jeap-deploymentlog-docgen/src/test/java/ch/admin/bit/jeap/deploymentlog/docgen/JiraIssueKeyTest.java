package ch.admin.bit.jeap.deploymentlog.docgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class JiraIssueKeyTest {

    @Test
    void warnsOnlyOnceForRepeatedInvalidKey(CapturedOutput output) {
        String invalidKey = "REPEATED-00042";

        assertThat(JiraIssueKey.parse(invalidKey)).isEmpty();
        assertThat(JiraIssueKey.parse(invalidKey)).isEmpty();

        assertThat(output.getAll())
                .containsOnlyOnce("Skipping syntactically invalid Jira issue key: " + invalidKey);
    }

    @Test
    void treatsEquivalentInvalidKeysAsTheSameWarning(CapturedOutput output) {
        assertThat(JiraIssueKey.parse(" normalized-00042 ")).isEmpty();
        assertThat(JiraIssueKey.parse("NORMALIZED-00042")).isEmpty();

        assertThat(output.getAll())
                .containsOnlyOnce("Skipping syntactically invalid Jira issue key:  normalized-00042 ");
    }
}
