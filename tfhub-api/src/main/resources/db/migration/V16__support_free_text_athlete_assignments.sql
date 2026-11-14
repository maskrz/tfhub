ALTER TABLE training_sessions
    ADD COLUMN assignment_type VARCHAR(20) NOT NULL DEFAULT 'GROUP';

ALTER TABLE training_sessions
    ALTER COLUMN training_group_id DROP NOT NULL,
    ADD CONSTRAINT ck_training_sessions_assignment
        CHECK (
            (training_type = 'TEMPO' AND assignment_type = 'GROUP' AND training_group_id IS NOT NULL)
            OR (training_type = 'FREE_TEXT' AND assignment_type = 'GROUP' AND training_group_id IS NOT NULL)
            OR (training_type = 'FREE_TEXT' AND assignment_type = 'ATHLETES' AND training_group_id IS NULL)
        );

ALTER TABLE training_sessions
    ADD CONSTRAINT ck_training_sessions_assignment_type
        CHECK (assignment_type IN ('GROUP', 'ATHLETES'));

CREATE TABLE training_session_athletes (
    training_session_id BIGINT NOT NULL,
    athlete_id BIGINT NOT NULL,
    CONSTRAINT pk_training_session_athletes PRIMARY KEY (training_session_id, athlete_id),
    CONSTRAINT fk_training_session_athletes_training
        FOREIGN KEY (training_session_id) REFERENCES training_sessions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_training_session_athletes_athlete
        FOREIGN KEY (athlete_id) REFERENCES athletes (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_training_session_athletes_athlete
    ON training_session_athletes (athlete_id, training_session_id);
