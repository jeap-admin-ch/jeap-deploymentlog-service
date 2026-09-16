ALTER TABLE deployment ADD COLUMN page_generation_suppressed boolean DEFAULT false NOT NULL;
ALTER TABLE deployment ADD COLUMN page_generation_legacy_unclassified boolean DEFAULT false NOT NULL;
ALTER TABLE deployment ADD COLUMN page_generation_request_id UUID;
