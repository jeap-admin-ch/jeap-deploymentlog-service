package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@Getter
@Entity
public class Changelog {

    @Id
    private UUID id;

    private String comment;

    private String comparedToVersion;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "changelog_jira_issue", joinColumns = @JoinColumn(name = "changelog"))
    @Column(name = "issue_key")
    private Set<String> jiraIssueKeys;

    @SuppressWarnings("java:S107")
    @Builder
    private Changelog(String comment, String comparedToVersion, @NonNull Set<String> jiraIssueKeys) {
        this.id = UUID.randomUUID();
        this.comment = comment == null ? null : StringUtils.abbreviate(comment, 250);
        this.comparedToVersion = comparedToVersion;
        this.jiraIssueKeys = new HashSet<>(jiraIssueKeys);
    }
}
