package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the Agent's GraphUpdatePreparationRequest (POST
 * /internal/v1/cognition/workflows/graph-update/prepare).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphUpdatePreparationRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String workflowVersion = GraphContract.WORKFLOW_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType;
    public String contextSnapshotId;
    public PersonalCognitionGraph graph;
    public List<EvidenceCandidate> candidates = new ArrayList<>();
    public List<CanonicalEvidence> existingEvidence = new ArrayList<>();
    public String userSelectedTopicId;
    public String suggestedTopicTitle;
    /** Server-supplied timestamp. Null is allowed by the Agent contract. */
    public String requestedAt;
}
