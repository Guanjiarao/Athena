package com.whu.software.athena.cognitionagent.feedbackgraph.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardResponse;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

public class FeedbackGraphUpdateResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.FEEDBACK_GRAPH_NODE_VERSION;
    public String nodeId = GraphContract.FEEDBACK_GRAPH_NODE_ID;
    public String runId;
    public FeedbackGraphUpdateStatus status;
    public GraphUpdateProposal proposal;
    public PersonalCognitionGraph graphPreview;
    public GraphPatchGuardResponse guardResult;
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public WorkflowRunObservation observation;
    public AgentError error;
}
