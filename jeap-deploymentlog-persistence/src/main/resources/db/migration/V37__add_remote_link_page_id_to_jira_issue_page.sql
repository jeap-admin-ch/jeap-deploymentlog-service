-- The Confluence page the stable Jira remote link of the issue was last written for. NULL until the link is written,
-- so every existing issue gets its link written once more after this migration.
ALTER TABLE jira_issue_page ADD COLUMN remote_link_page_id VARCHAR(255);
