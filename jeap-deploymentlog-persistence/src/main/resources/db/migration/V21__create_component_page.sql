CREATE TABLE component_page
(
    component_id    UUID         NOT NULL PRIMARY KEY REFERENCES component,
    page_id         VARCHAR(255) NOT NULL UNIQUE,
    parent_page_id  VARCHAR(255) NOT NULL,
    last_updated_at TIMESTAMP    NOT NULL
);
