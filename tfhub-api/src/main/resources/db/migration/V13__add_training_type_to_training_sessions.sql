ALTER TABLE training_sessions
    ADD COLUMN training_type VARCHAR(20) NOT NULL DEFAULT 'TEMPO';

UPDATE training_sessions
SET training_type = 'TEMPO';

ALTER TABLE training_sessions
    ADD CONSTRAINT ck_training_sessions_training_type
        CHECK (training_type IN ('TEMPO', 'FREE_TEXT'));
