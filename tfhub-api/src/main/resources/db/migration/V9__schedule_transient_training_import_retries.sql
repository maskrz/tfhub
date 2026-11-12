ALTER TABLE training_imports
    ADD COLUMN automatic_retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_training_imports_automatic_retry_count
        CHECK (automatic_retry_count >= 0),
    ADD CONSTRAINT ck_training_imports_next_retry_status
        CHECK (next_retry_at IS NULL OR status = 'FAILED');

CREATE INDEX idx_training_imports_due_retry
    ON training_imports (next_retry_at, created_at, id)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
