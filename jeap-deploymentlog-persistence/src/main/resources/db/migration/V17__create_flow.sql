CREATE TABLE flow
(
    id                              UUID         NOT NULL PRIMARY KEY,
    type                            VARCHAR(32)  NOT NULL,
    state                           VARCHAR(32)  NOT NULL,
    born_at                         TIMESTAMP    NOT NULL,
    component_version_id            UUID         NOT NULL REFERENCES component_version,
    final_deployment_environment_id UUID         NOT NULL REFERENCES environment,
    aborted_by_id                   UUID         REFERENCES flow
);

ALTER TABLE deployment ADD COLUMN flow_id UUID REFERENCES flow;

CREATE INDEX flow_component_version_state_idx ON flow (component_version_id, state);
CREATE INDEX component_version_business_identity_idx ON component_version (component_id, version_name);
CREATE INDEX flow_final_environment_idx ON flow (final_deployment_environment_id);
CREATE INDEX flow_aborted_by_idx ON flow (aborted_by_id);
CREATE INDEX deployment_flow_idx ON deployment (flow_id);
