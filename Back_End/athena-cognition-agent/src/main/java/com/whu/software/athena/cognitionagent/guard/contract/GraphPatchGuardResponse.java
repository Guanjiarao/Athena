package com.whu.software.athena.cognitionagent.guard.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

public class GraphPatchGuardResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.PATCH_GUARD_NODE_VERSION;
    public String nodeId = GraphContract.PATCH_GUARD_NODE_ID;
    public String runId;
    public PatchGuardStatus status;
    public GraphUpdateProposal proposal;
    /** Simulated result for display and audit only; it is never persisted by the Agent. */
    public PersonalCognitionGraph graphPreview;
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public WorkflowRunObservation observation;
    public AgentError error;
}
