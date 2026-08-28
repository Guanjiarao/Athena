package com.whu.software.athena.cognitionagent.patch.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

public class GraphPatchAssemblyResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.PATCH_ASSEMBLY_NODE_VERSION;
    public String nodeId = GraphContract.PATCH_ASSEMBLY_NODE_ID;
    public String runId;
    public PatchAssemblyStatus status;
    public GraphUpdateProposal proposal;
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public WorkflowRunObservation observation;
    public AgentError error;
}
