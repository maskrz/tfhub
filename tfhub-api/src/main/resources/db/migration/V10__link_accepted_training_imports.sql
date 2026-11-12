ALTER TABLE training_imports
    ADD COLUMN accepted_training_session_id BIGINT;

ALTER TABLE training_imports
    ADD CONSTRAINT uq_training_imports_accepted_training_session
        UNIQUE (accepted_training_session_id),
    ADD CONSTRAINT fk_training_imports_accepted_training_session
        FOREIGN KEY (accepted_training_session_id) REFERENCES training_sessions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT ck_training_imports_accepted_training_session
        CHECK ((status = 'ACCEPTED') = (accepted_training_session_id IS NOT NULL));
