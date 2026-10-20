-- One row per deployment and referenced Jira issue. Rows are only inserted and deleted, never updated, so concurrent
-- deployments of the same issue cannot conflict and a refresh deletes exactly the requests it has processed.
CREATE TABLE jira_change_view_request
(
    id           UUID         NOT NULL PRIMARY KEY,
    issue_key    VARCHAR(255) NOT NULL,
    project_key  VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP    NOT NULL
);

CREATE INDEX jira_change_view_request_project_key
    ON jira_change_view_request (project_key);

CREATE INDEX jira_change_view_request_requested_at
    ON jira_change_view_request (requested_at);
