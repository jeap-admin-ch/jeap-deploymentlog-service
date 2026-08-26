CREATE TABLE system_group
(
    id              UUID NOT NULL PRIMARY KEY,
    name            TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    CONSTRAINT system_group_normalized_name_uk UNIQUE (normalized_name)
);

ALTER TABLE system ADD COLUMN system_group_id UUID;
ALTER TABLE system ADD CONSTRAINT system_system_group_fk
    FOREIGN KEY (system_group_id) REFERENCES system_group (id) ON DELETE SET NULL;

CREATE INDEX system_system_group_idx ON system (system_group_id);
