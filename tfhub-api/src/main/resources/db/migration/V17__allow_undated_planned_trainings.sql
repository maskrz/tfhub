ALTER TABLE training_sessions
    ALTER COLUMN training_date DROP NOT NULL,
    ADD CONSTRAINT ck_training_sessions_completed_date
        CHECK (status <> 'COMPLETED' OR training_date IS NOT NULL);

ALTER TABLE training_import_drafts
    ALTER COLUMN training_date DROP NOT NULL;
