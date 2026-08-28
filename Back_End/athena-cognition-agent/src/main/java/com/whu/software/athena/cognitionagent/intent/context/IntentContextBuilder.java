package com.whu.software.athena.cognitionagent.intent.context;

import com.whu.software.athena.cognitionagent.intent.contract.CluePayload;

/** Builds the first node's explicit allow-list from the saved clue payload. */
public class IntentContextBuilder {

    public IntentContext build(CluePayload clue) {
        if (clue == null) {
            throw new IllegalArgumentException("clue must not be null");
        }
        return new IntentContext(
                clue.id,
                clue.type,
                clue.intent,
                clue.relationType,
                clue.helpRequestType,
                clue.articleId,
                clue.articleTitle,
                clue.articleType,
                clue.selectedText,
                clue.questionType,
                clue.questionText,
                clue.occurredAt,
                clue.cycleRelation,
                clue.severity,
                clue.resolved,
                clue.source,
                clue.status,
                clue.suggestedTopicId,
                clue.suggestedTopicTitle,
                clue.originalLabel,
                clue.createdAt,
                clue.updatedAt
        );
    }
}
