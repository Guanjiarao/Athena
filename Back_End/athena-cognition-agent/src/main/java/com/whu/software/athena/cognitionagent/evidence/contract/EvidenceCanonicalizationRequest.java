package com.whu.software.athena.cognitionagent.evidence.contract;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;

import java.util.ArrayList;
import java.util.List;

public class EvidenceCanonicalizationRequest {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.EVIDENCE_NODE_VERSION;
    public String runId;
    public String idempotencyKey;
    public GraphTriggerType triggerType;
    public String contextSnapshotId;
    public List<EvidenceCandidate> candidates = new ArrayList<>();
    public List<CanonicalEvidence> existingEvidence = new ArrayList<>();
}
