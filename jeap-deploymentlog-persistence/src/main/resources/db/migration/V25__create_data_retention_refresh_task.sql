-- The refresh payload is intentionally stored as opaque JSON: a task is written and processed atomically,
-- and none of its affected page identifiers are queried independently. Keeping it in one row provides
-- durable retry semantics without introducing normalized tables for short-lived internal work items.
CREATE TABLE data_retention_refresh_task
(
    id         UUID      NOT NULL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    payload    TEXT      NOT NULL
);

CREATE INDEX data_retention_refresh_task_created_at
    ON data_retention_refresh_task (created_at);
