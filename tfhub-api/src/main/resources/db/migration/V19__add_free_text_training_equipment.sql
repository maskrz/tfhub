ALTER TABLE training_sessions
    ADD COLUMN equipment TEXT,
    ADD CONSTRAINT ck_training_sessions_equipment_type
        CHECK (training_type = 'FREE_TEXT' OR equipment IS NULL);

ALTER TABLE training_import_drafts
    ADD COLUMN equipment TEXT;
