package com.whu.software.athena.cognitionagent.intent.context;

import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.ClueStatus;
import com.whu.software.athena.cognitionagent.intent.contract.ClueType;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.HelpRequestType;
import com.whu.software.athena.cognitionagent.intent.contract.QuestionType;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;

/**
 * The allow-listed view of a clue used by the first node.
 * It deliberately contains no identity token, database handle, or user history.
 */
public record IntentContext(
        String id,
        ClueType type,
        ClueIntent intent,
        RelationType relationType,
        HelpRequestType helpRequestType,
        String articleId,
        String articleTitle,
        Integer articleType,
        String selectedText,
        QuestionType questionType,
        String questionText,
        String occurredAt,
        CycleRelation cycleRelation,
        Integer severity,
        Boolean resolved,
        String source,
        ClueStatus status,
        String suggestedTopicId,
        String suggestedTopicTitle,
        String originalLabel,
        String createdAt,
        String updatedAt
) {
}
