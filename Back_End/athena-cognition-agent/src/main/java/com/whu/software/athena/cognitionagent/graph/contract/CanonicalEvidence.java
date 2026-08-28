package com.whu.software.athena.cognitionagent.graph.contract;

import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;

public class CanonicalEvidence {

    public String evidenceId;
    public EvidenceSourceType sourceType;
    public String sourceId;
    public EvidenceFactLevel factLevel;
    public String summary;
    public String contentFingerprint;
    public String occurredAt;
    public CycleRelation cycleRelation;
    public Integer severity;
    public Boolean resolved;
    public String relatedActionId;
    public GraphActionFeedbackResult feedbackResult;
}
