package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the Agent's CanonicalEvidence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
