package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mirror of the Agent's ActionFeedbackWorkflowResponse. Per-node intermediate
 * results (normalizationResult/graphUpdateResult) and the observability
 * payload are kept as opaque JsonNode; the main backend only consumes the
 * typed proposal/graphPreview fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionFeedbackWorkflowResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String workflowVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
    public String runId;
    public ActionFeedbackWorkflowStatus status;
    public JsonNode normalizationResult;
    public JsonNode graphUpdateResult;
    public GraphUpdateProposal proposal;
    public PersonalCognitionGraph graphPreview;
    public String nextNodeId;
    public AgentError error;
    public JsonNode observation;
}
