package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum GraphPreparationStatus {
    SEMANTIC_DRAFT_READY,
    PROPOSAL_READY,
    NO_CHANGE,
    NEEDS_CONFIRMATION,
    STALE,
    BLOCKED,
    REJECTED,
    FAILED
}
