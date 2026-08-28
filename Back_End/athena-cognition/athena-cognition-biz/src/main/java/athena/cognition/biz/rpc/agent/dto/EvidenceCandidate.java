package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the Agent's EvidenceCandidate.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
