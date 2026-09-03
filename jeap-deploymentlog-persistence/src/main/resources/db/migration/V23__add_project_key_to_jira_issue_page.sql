ALTER TABLE jira_issue_page ADD COLUMN project_key VARCHAR(255);

UPDATE jira_issue_page
SET project_key = CASE
                      WHEN POSITION('-' IN issue_key) > 1
                          THEN UPPER(SUBSTRING(issue_key FROM 1 FOR POSITION('-' IN issue_key) - 1))
                      ELSE UPPER(issue_key)
    END;

ALTER TABLE jira_issue_page ALTER COLUMN project_key SET NOT NULL;
