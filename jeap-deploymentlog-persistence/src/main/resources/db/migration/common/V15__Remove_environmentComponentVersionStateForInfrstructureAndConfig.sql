DELETE FROM environment_component_version_state ecvs
WHERE ecvs.deployment_id IN (
    SELECT d.id
    FROM deployment d
             JOIN deployment_types dt ON d.id = dt.deployment_id
    WHERE dt.type IN ('INFRASTRUCTURE', 'CONFIG')
);
