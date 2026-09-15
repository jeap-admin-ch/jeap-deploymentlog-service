ALTER TABLE deployment ADD COLUMN page_generation_suppressed boolean DEFAULT false NOT NULL;
ALTER TABLE deployment ADD COLUMN page_generation_legacy_unclassified boolean DEFAULT false NOT NULL;
ALTER TABLE deployment ADD COLUMN page_generation_request_id UUID;

-- Missing legacy tracking cannot distinguish a failed generation from a housekeeping deletion.
-- Classify these rows using the configured housekeeping policy before admitting them to repair.
UPDATE deployment SET page_generation_legacy_unclassified = true
WHERE NOT EXISTS (SELECT 1 FROM deployment_page p WHERE p.deployment_id = deployment.id);
