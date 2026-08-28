package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum GraphActionType {
    RECORD_BODY,
    RECORD_MOOD,
    RECORD_SLEEP,
    READ_CONTENT,
    ANSWER_QUESTION,
    CONFIRM_STATUS
}
