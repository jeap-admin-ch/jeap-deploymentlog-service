-- Release 2: all running instances must already support deployment version history (V33).
-- Remove the reference before the table; retained deployment data is not reclassified or deleted.
ALTER TABLE deployment DROP COLUMN flow_id;
DROP TABLE flow;
