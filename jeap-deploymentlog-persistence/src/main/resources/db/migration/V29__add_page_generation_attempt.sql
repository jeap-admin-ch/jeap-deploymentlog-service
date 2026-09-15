ALTER TABLE deployment ADD COLUMN page_generation_attempted_at timestamp;

CREATE INDEX deployment_page_generation_attempt_idx
    ON deployment (page_generation_attempted_at, started_at);
