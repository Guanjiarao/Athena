CREATE TABLE IF NOT EXISTS t_triage_session_record (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    current_state VARCHAR(64),
    next_action VARCHAR(64),
    risk_level INT,
    risk_score DOUBLE PRECISION,
    final_reply TEXT,
    user_input_snapshot TEXT,
    conversation_history_json TEXT,
    extracted_symptoms_json TEXT,
    missing_fields_json TEXT,
    risk_assessment_json TEXT,
    state_log_json TEXT,
    audit_trail_json TEXT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_triage_session_record_session_id
    ON t_triage_session_record (session_id);

CREATE INDEX IF NOT EXISTS idx_triage_session_record_user_id
    ON t_triage_session_record (user_id);

CREATE INDEX IF NOT EXISTS idx_triage_session_record_state
    ON t_triage_session_record (current_state, next_action);

CREATE INDEX IF NOT EXISTS idx_triage_session_record_create_time
    ON t_triage_session_record (create_time DESC);

CREATE INDEX IF NOT EXISTS idx_triage_session_record_deleted_create_time
    ON t_triage_session_record (deleted, create_time DESC);

CREATE OR REPLACE FUNCTION trg_set_triage_session_record_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS triage_session_record_set_update_time ON t_triage_session_record;

CREATE TRIGGER triage_session_record_set_update_time
BEFORE UPDATE ON t_triage_session_record
FOR EACH ROW
EXECUTE FUNCTION trg_set_triage_session_record_update_time();
