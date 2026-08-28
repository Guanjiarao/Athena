package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the Agent's CluePayload: flat business payload copied from the
 * Android Clue model. Field names are the JSON names (Jackson default naming).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CluePayload {

    public String id;
    public ClueType type;
    public ClueIntent intent;
    public RelationType relationType;
    public HelpRequestType helpRequestType;
    public String articleId;
    public String articleTitle;
    public Integer articleType;
    public String selectedText;
    public QuestionType questionType;
    public String questionText;
    public String occurredAt;
    public CycleRelation cycleRelation;
    public Integer severity;
    public Boolean resolved;
    public String source;
    public ClueStatus status;
    public String suggestedTopicId;
    public String suggestedTopicTitle;
    public String originalLabel;
    public String createdAt;
    public String updatedAt;
}
