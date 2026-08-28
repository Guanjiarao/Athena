package com.whu.software.athena.cognitionagent.feedbackgraph.contract;

import com.whu.software.athena.cognitionagent.feedback.contract.NormalizedActionFeedback;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

public class FeedbackGraphUpdateRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.FEEDBACK_GRAPH_NODE_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType;
    public String contextSnapshotId;
    public PersonalCognitionGraph graph;
    public NormalizedActionFeedback normalizedFeedback;
}
