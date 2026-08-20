-- Existing NULL values must be resolved deliberately before this migration is applied.
-- No inferred commit timestamp is generated because committed_at defines the functional age of a version.
ALTER TABLE component_version ALTER COLUMN committed_at SET NOT NULL;
