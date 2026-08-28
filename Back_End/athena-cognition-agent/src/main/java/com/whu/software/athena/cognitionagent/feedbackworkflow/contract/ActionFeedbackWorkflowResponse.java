package com.whu.software.athena.cognitionagent.feedbackworkflow.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationResponse;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateResponse;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;

public class ActionFeedbackWorkflowResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String workflowVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
    public String runId;
    public ActionFeedbackWorkflowStatus status;
    public ActionFeedbackNormalizationResponse normalizationResult;
    public FeedbackGraphUpdateResponse graphUpdateResult;
    public GraphUpdateProposal proposal;
    public PersonalCognitionGraph graphPreview;
    public String nextNodeId;
    public AgentError error;
    public WorkflowRunObservation observation;
}
