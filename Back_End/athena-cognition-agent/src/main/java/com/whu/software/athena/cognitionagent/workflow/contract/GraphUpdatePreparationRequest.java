package com.whu.software.athena.cognitionagent.workflow.contract;

import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCandidate;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.ArrayList;
import java.util.List;

public class GraphUpdatePreparationRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String workflowVersion = GraphContract.WORKFLOW_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType;
    public String contextSnapshotId;
    public PersonalCognitionGraph graph;
    public List<EvidenceCandidate> candidates = new ArrayList<>();
    public List<CanonicalEvidence> existingEvidence = new ArrayList<>();
    public String userSelectedTopicId;
    public String suggestedTopicTitle;
    /** Server-supplied timestamp. Null is allowed in the local no-backend prototype. */
    public String requestedAt;
}
