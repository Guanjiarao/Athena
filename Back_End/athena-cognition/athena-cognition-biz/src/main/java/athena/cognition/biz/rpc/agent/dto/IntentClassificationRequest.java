package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the Agent's IntentClassificationRequest (POST
 * /internal/v1/cognition/nodes/intent-classification).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentClassificationRequest {

    public String contractVersion;
    public String nodeVersion;
    public String runId;
    public String idempotencyKey;
    public TriggerType triggerType;
    public String contextSnapshotId;
    public CluePayload clue;
}
