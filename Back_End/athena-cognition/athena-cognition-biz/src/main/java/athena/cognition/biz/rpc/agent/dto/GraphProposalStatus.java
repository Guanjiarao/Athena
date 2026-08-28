package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum GraphProposalStatus {
    DRAFT,
    READY_FOR_CONFIRMATION,
    ACCEPTED,
    KEPT_AS_KNOWLEDGE,
    REJECTED,
    STALE,
    BLOCKED
}
