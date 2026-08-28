package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum EvidenceFactLevel {
    KNOWLEDGE,
    QUESTION,
    DECLARED_RELEVANCE,
    SELF_REPORTED,
    OBSERVED,
    PROCESS_EVENT
}
