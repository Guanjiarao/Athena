package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mirror of the Agent's GraphUpdatePreparationResponse. Per-node intermediate
 * results (evidenceResult ... patchGuardResult) and the observability payload
 * are kept as opaque JsonNode; the main backend only consumes the typed
 * proposal/graphPreview fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphUpdatePreparationResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String workflowVersion = GraphContract.WORKFLOW_VERSION;
    public String runId;
    public GraphPreparationStatus status;
    public JsonNode evidenceResult;
    public JsonNode targetResult;
    public JsonNode scopeResult;
    public JsonNode semanticResult;
    public JsonNode actionResult;
    public JsonNode patchAssemblyResult;
    public JsonNode patchGuardResult;
    public GraphUpdateProposal proposal;
    public PersonalCognitionGraph graphPreview;
    public String nextNodeId;
    public AgentError error;
    public JsonNode observation;
}
