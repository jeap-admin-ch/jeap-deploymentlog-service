CREATE TABLE jira_project_page
(
    project_key    VARCHAR(255) NOT NULL PRIMARY KEY,
    page_id        VARCHAR(255) NOT NULL UNIQUE,
    parent_page_id VARCHAR(255) NOT NULL,
    last_updated_at TIMESTAMP   NOT NULL
);

CREATE TABLE jira_issue_page
(
    issue_key      VARCHAR(255) NOT NULL PRIMARY KEY,
    page_id        VARCHAR(255) NOT NULL UNIQUE,
    parent_page_id VARCHAR(255) NOT NULL,
    last_updated_at TIMESTAMP   NOT NULL
);
