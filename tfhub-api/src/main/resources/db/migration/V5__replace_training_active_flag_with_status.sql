DROP INDEX idx_training_sessions_group_active_date;

ALTER TABLE training_sessions
    ADD COLUMN status VARCHAR(20);

UPDATE training_sessions
SET status = 'PLANNED';

ALTER TABLE training_sessions
    ALTER COLUMN status SET DEFAULT 'PLANNED',
    ALTER COLUMN status SET NOT NULL,
    DROP COLUMN active,
    ADD CONSTRAINT ck_training_sessions_status
        CHECK (status IN ('PLANNED', 'COMPLETED'));

CREATE INDEX idx_training_sessions_group_status_date
    ON training_sessions (training_group_id, status, training_date DESC, id);
