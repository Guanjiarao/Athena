package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum AgentErrorCode {
    INVALID_REQUEST,
    MISSING_REQUIRED_FIELD,
    INVALID_ENUM,
    TEXT_TOO_LONG,
    UNSUPPORTED_SOURCE_TYPE,
    UNSUPPORTED_VERSION,
    MODEL_TIMEOUT,
    MODEL_UNAVAILABLE,
    MODEL_OUTPUT_INVALID,
    POLICY_BLOCKED,
    IDEMPOTENCY_CONFLICT,
    GRAPH_VERSION_CONFLICT,
    GRAPH_INTEGRITY_VIOLATION,
    INTERNAL_ERROR
}
