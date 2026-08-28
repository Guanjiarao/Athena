package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the athena-cognition-agent contract enum (com.whu.software.athena.cognitionagent).
 * Values must stay aligned with the Agent contract.
 */
public enum GraphOperationType {
    ADD_NODE,
    UPDATE_NODE,
    ADD_EDGE,
    SUPERSEDE_NODE,
    DEACTIVATE_EDGE,
    NO_OP
}
