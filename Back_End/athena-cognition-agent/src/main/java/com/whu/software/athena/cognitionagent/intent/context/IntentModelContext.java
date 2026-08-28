package com.whu.software.athena.cognitionagent.intent.context;

import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.HelpRequestType;
import com.whu.software.athena.cognitionagent.intent.contract.QuestionType;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;

/** The only business fields that a model provider is allowed to receive. */
public record IntentModelContext(
        ClueIntent explicitIntent,
        RelationType relationType,
        HelpRequestType helpRequestType,
        String articleTitle,
        String selectedText,
        QuestionType questionType,
        String questionText,
        CycleRelation cycleRelation
) {
}
