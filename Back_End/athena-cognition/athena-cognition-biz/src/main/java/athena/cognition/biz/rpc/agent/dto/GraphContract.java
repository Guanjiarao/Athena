package athena.cognition.biz.rpc.agent.dto;

/**
 * Mirror of the Agent's GraphContract version constants. Only the version
 * constants used by the mirrored request/response DTOs are kept here; node
 * id/version constants stay Agent-internal.
 */
public final class GraphContract {

    public static final String CONTRACT_VERSION = "cognition-agent-v1";
    public static final String GRAPH_SCHEMA_VERSION = "personal-cognition-graph-v1";
    public static final String PROPOSAL_SCHEMA_VERSION = "graph-update-proposal-v1";
    public static final String WORKFLOW_VERSION = "cognition-graph-workflow-v1";
    public static final String FEEDBACK_WORKFLOW_VERSION = "action-feedback-workflow-v1";
    /** Node 1 (intent classification) version, Agent-side AgentContract.NODE_VERSION. */
    public static final String INTENT_NODE_VERSION = "intent-evidence-v1";

    private GraphContract() {
    }
}
