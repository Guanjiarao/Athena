package com.whu.software.athena.cognitionagent.patch.contract;

import com.whu.software.athena.cognitionagent.action.contract.NextActionPlan;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;

import java.util.ArrayList;
import java.util.List;

public class GraphPatchAssemblyRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.PATCH_ASSEMBLY_NODE_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType;
    public String contextSnapshotId;
    public PersonalCognitionGraph graph;
    public List<CanonicalEvidence> evidence = new ArrayList<>();
    public GraphUpdateScope scope;
    public GraphSemanticUpdateDraft semanticDraft;
    public NextActionPlan actionPlan;
    public String proposalCreatedAt;
}
