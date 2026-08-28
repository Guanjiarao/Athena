package com.whu.software.athena.cognitionagent.scope.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

public class GraphUpdateScopeResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.SCOPE_NODE_VERSION;
    public String nodeId = GraphContract.SCOPE_NODE_ID;
    public String runId;
    public GraphUpdateScopeStatus status;
    public GraphUpdateScope scope;
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public WorkflowRunObservation observation;
    public AgentError error;
}
