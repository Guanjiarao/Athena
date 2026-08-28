package com.whu.software.athena.cognitionagent.feedback.contract;

import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;

public class ActionFeedbackSubmission {

    public String feedbackId;
    public String actionId;
    public GraphActionFeedbackResult result;
    public String note;
    public String occurredAt;
}
