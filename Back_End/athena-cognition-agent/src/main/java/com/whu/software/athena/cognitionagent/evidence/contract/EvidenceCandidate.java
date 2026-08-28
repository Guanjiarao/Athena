package com.whu.software.athena.cognitionagent.evidence.contract;

import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;

public class EvidenceCandidate {

    public String evidenceId;
    public EvidenceSourceType sourceType;
    public String sourceId;
    public ClueIntent intent;
    public RelationType relationType;
    public String summary;
    public String occurredAt;
    public CycleRelation cycleRelation;
    public Integer severity;
    public Boolean resolved;
    public String relatedActionId;
    public GraphActionFeedbackResult feedbackResult;
}
