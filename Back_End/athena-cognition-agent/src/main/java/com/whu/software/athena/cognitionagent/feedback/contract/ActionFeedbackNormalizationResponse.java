package com.whu.software.athena.cognitionagent.feedback.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

public class ActionFeedbackNormalizationResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.FEEDBACK_NORMALIZATION_NODE_VERSION;
    public String nodeId = GraphContract.FEEDBACK_NORMALIZATION_NODE_ID;
    public String runId;
    public FeedbackNormalizationStatus status;
    public NormalizedActionFeedback normalizedFeedback;
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public WorkflowRunObservation observation;
    public AgentError error;
}
