package com.whu.software.athena.cognitionagent.feedbackworkflow.contract;

import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackSubmission;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.ArrayList;
import java.util.List;

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
