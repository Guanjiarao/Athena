package com.whu.software.athena.cognitionagent.intent.contract;

/**
 * Flat business payload copied from the existing Android Clue model.
 * Server-owned fields are included because the Agent receives a saved clue.
 */
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
