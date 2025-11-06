ALTER TABLE changelog_jira_issue
    ALTER COLUMN issue_key SET NOT NULL;

ALTER TABLE changelog_jira_issue
    ALTER COLUMN changelog SET NOT NULL;

ALTER TABLE changelog_jira_issue
    ADD CONSTRAINT pk_changelog_jira_issue PRIMARY KEY (changelog, issue_key);

ALTER TABLE deployment_links
    ALTER COLUMN label SET NOT NULL;

ALTER TABLE deployment_links
    ALTER COLUMN url SET NOT NULL;

ALTER TABLE deployment_links
    ADD CONSTRAINT pk_deployment_links PRIMARY KEY (deployment_id, label);
