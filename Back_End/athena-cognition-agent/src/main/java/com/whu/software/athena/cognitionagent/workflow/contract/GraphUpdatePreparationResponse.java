package com.whu.software.athena.cognitionagent.workflow.contract;

import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningResponse;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationResponse;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardResponse;
import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyResponse;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeResponse;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateResponse;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionResponse;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;

public class GraphUpdatePreparationResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String workflowVersion = GraphContract.WORKFLOW_VERSION;
    public String runId;
    public GraphPreparationStatus status;
    public EvidenceCanonicalizationResponse evidenceResult;
    public GraphTargetResolutionResponse targetResult;
    public GraphUpdateScopeResponse scopeResult;
    public GraphSemanticUpdateResponse semanticResult;
    public NextActionPlanningResponse actionResult;
    public GraphPatchAssemblyResponse patchAssemblyResult;
    public GraphPatchGuardResponse patchGuardResult;
    public GraphUpdateProposal proposal;
    public PersonalCognitionGraph graphPreview;
    public String nextNodeId;
    public AgentError error;
    public WorkflowRunObservation observation;
}
