package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum NextRoute {
    MATCH_EXISTING_TOPIC_CANDIDATE,
    QUESTION_INBOX,
    KNOWLEDGE_INBOX,
    NEEDS_CLARIFICATION
}
