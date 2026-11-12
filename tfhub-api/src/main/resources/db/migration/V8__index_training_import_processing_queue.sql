CREATE INDEX idx_training_imports_pending_queue
    ON training_imports (created_at, id)
    WHERE status = 'PENDING';

CREATE INDEX idx_training_import_attempts_processing_started_at
    ON training_import_attempts (started_at)
    WHERE status = 'PROCESSING';
