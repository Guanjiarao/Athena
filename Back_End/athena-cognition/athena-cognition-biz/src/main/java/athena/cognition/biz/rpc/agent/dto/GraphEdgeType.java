package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum GraphEdgeType {
    ABOUT,
    GROUNDS,
    SUPPORTS,
    CHALLENGES,
    NEXT_STEP_FOR,
    FEEDBACK_FOR
}
