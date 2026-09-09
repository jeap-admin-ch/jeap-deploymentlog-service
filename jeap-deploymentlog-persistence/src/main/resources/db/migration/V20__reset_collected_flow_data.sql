-- Discard flow data collected before deployments below the configured start stage were excluded.
-- Deployment records remain unchanged, and future eligible deployments create new flows.
UPDATE deployment
SET flow_id = NULL
WHERE flow_id IS NOT NULL;

UPDATE flow
SET aborted_by_id = NULL
WHERE aborted_by_id IS NOT NULL;

DELETE FROM flow;
