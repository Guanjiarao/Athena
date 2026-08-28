package com.whu.software.athena.cognitionagent.scope.contract;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.ArrayList;
import java.util.List;

public class GraphUpdateScopeRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.SCOPE_NODE_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType;
    public String contextSnapshotId;
    public PersonalCognitionGraph graph;
    public List<CanonicalEvidence> evidence = new ArrayList<>();
    public GraphUpdateRoute targetRoute;
    public String targetTopicId;
    public String proposedTopicTitle;
}
