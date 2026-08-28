package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the Agent's ActionFeedbackSubmission.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionFeedbackSubmission {

    public String feedbackId;
    public String actionId;
    public GraphActionFeedbackResult result;
    public String note;
    public String occurredAt;
}
