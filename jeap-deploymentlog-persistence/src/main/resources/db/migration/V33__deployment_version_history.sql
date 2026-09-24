-- Version flows are a read-side grouping, not persisted aggregates.
ALTER TABLE deployment ADD COLUMN staging_type VARCHAR(32);
CREATE TABLE deployment_final_environments (
    deployment_id UUID NOT NULL REFERENCES deployment(id),
    environment_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (deployment_id, environment_name)
);

-- Existing deployments are not reclassified.
-- Keep flow and deployment.flow_id for old instances during the first rollout.
-- Remove them in a later release once all instances use deployment version history.
