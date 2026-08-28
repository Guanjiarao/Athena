package com.whu.software.athena.cognitionagent.evidence.contract;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

import java.util.ArrayList;
import java.util.List;

public class EvidenceCanonicalizationResponse {

    public String contractVersion = GraphContract.CONTRACT_VERSION;
    public String nodeVersion = GraphContract.EVIDENCE_NODE_VERSION;
    public String nodeId = GraphContract.EVIDENCE_NODE_ID;
    public String runId;
    public EvidenceCanonicalizationStatus status;
    public List<CanonicalEvidence> acceptedEvidence = new ArrayList<>();
    public List<EvidenceDecision> decisions = new ArrayList<>();
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public WorkflowRunObservation observation;
    public AgentError error;
}
