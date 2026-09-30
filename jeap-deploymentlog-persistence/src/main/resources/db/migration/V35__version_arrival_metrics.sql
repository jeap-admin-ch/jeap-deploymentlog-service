-- Preserve distinct-version identities in the existing durable metric events.
-- No foreign key: these metric records intentionally survive deployment retention.
ALTER TABLE deployment_metric_event ADD COLUMN staging_version VARCHAR(255);
CREATE INDEX deployment_metric_event_staging_idx
    ON deployment_metric_event (system_name, component_name, environment_name, staging_version);
