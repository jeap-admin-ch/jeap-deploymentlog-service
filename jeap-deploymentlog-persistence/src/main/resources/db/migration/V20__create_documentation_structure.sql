CREATE TABLE documentation_structure_page
(
    structure_key   VARCHAR(100) NOT NULL PRIMARY KEY,
    page_id         VARCHAR(255) NOT NULL,
    parent_page_id  VARCHAR(255) NOT NULL,
    last_updated_at TIMESTAMP    NOT NULL
);

ALTER TABLE system_page ADD COLUMN parent_page_id VARCHAR(255);
ALTER TABLE environment_history_page ADD COLUMN parent_page_id VARCHAR(255);
ALTER TABLE deployment_list_page ADD COLUMN parent_page_id VARCHAR(255);
