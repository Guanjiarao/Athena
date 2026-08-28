package com.whu.software.athena.cognitionagent.guard.contract;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;

import java.util.ArrayList;
import java.util.List;

public class GraphPatchGuardRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.PATCH_GUARD_NODE_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType;
    public String contextSnapshotId;
    public PersonalCognitionGraph graph;
    public List<CanonicalEvidence> evidence = new ArrayList<>();
    public GraphUpdateScope scope;
    public GraphUpdateProposal proposal;
}
