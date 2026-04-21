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
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_triage_session_record_session_id
    ON t_triage_session_record(session_id);
