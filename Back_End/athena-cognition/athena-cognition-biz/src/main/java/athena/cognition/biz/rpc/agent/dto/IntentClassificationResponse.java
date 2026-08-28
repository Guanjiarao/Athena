package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the Agent's IntentClassificationResponse. Intermediate node
 * outputs (policyResult/schemaResult/modelSuggestion) and the observability
 * payload are kept as opaque JsonNode; the main backend only consumes the
 * typed decision fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentClassificationResponse {

    public String contractVersion;
    public String nodeVersion;
    public String runId;
    public String clueId;
    public String nodeId;
    public IntentClassificationStatus status;
    public ClueIntent intent;
    public EvidenceClass evidenceClass;
    public FactEligibility factEligibility;
    public DecisionSource decisionSource;
    public AmbiguityCode ambiguityCode;
    public NextRoute nextRoute;
    public List<String> evidenceIds = new ArrayList<>();
    public JsonNode policyResult;
    public JsonNode schemaResult;
    public JsonNode modelSuggestion;
    public boolean modelConflict;
    public JsonNode observation;
    public AgentError error;
}
