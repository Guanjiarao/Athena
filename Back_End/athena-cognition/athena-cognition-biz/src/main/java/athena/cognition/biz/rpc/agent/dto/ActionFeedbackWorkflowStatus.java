package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum ActionFeedbackWorkflowStatus {
    PROPOSAL_READY,
    NO_CHANGE,
    STALE,
    BLOCKED,
    REJECTED,
    FAILED
}
