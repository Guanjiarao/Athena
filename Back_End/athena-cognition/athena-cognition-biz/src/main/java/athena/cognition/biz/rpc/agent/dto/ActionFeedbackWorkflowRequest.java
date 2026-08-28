package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the Agent's ActionFeedbackWorkflowRequest (POST
 * /internal/v1/cognition/workflows/action-feedback/prepare).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionFeedbackWorkflowRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String workflowVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType = GraphTriggerType.ACTION_FEEDBACK;
    public String contextSnapshotId;
    public PersonalCognitionGraph graph;
    public List<CanonicalEvidence> existingEvidence = new ArrayList<>();
    public ActionFeedbackSubmission feedback;
}
