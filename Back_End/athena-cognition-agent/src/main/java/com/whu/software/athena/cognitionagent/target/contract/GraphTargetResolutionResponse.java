package com.whu.software.athena.cognitionagent.target.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.DecisionSource;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

public class GraphTargetResolutionResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.TARGET_NODE_VERSION;
    public String nodeId = GraphContract.TARGET_NODE_ID;
    public String runId;
    public TargetResolutionStatus status;
    public GraphUpdateRoute route;
    public String targetTopicId;
    public String suggestedTopicTitle;
    public GraphMatchStrength matchStrength;
    public DecisionSource decisionSource;
    public String rationale;
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public WorkflowRunObservation observation;
    public AgentError error;
}
