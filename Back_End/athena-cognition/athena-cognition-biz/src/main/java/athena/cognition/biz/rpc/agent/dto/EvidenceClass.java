package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum EvidenceClass {
    ARTICLE_KNOWLEDGE,
    USER_QUESTION,
    USER_PERSONAL_CLAIM,
    USER_DECLARED_RELEVANCE,
    UNKNOWN
}
