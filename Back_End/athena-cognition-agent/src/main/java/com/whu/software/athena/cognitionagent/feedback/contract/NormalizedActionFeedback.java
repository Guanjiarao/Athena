package com.whu.software.athena.cognitionagent.feedback.contract;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;

public class NormalizedActionFeedback {

    public String feedbackId;
    public String actionId;
    public String topicId;
    public GraphActionFeedbackResult result;
    public GraphActionStatus resultingActionStatus;
    public long baseGraphVersion;
    public CanonicalEvidence evidence;
}
