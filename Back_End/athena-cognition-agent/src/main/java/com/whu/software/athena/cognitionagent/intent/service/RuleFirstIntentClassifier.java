package com.whu.software.athena.cognitionagent.intent.service;

import com.whu.software.athena.cognitionagent.intent.context.IntentContext;
import com.whu.software.athena.cognitionagent.intent.contract.AmbiguityCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.DecisionSource;
import com.whu.software.athena.cognitionagent.intent.contract.EvidenceClass;
import com.whu.software.athena.cognitionagent.intent.contract.FactEligibility;
import com.whu.software.athena.cognitionagent.intent.contract.NextRoute;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;

/**
 * Classifies the explicit user choice. No model call is needed for this path.
 */
public class RuleFirstIntentClassifier {

    public IntentClassificationDecision classify(IntentContext context) {
        if (context.intent() == null) {
            return new IntentClassificationDecision(
                    null,
                    EvidenceClass.UNKNOWN,
                    FactEligibility.NOT_BODY_FACT,
                    DecisionSource.RULE,
                    AmbiguityCode.INSUFFICIENT_TEXT,
                    NextRoute.NEEDS_CLARIFICATION
            );
        }

        return switch (context.intent()) {
            case RELATED -> classifyRelated(context.relationType());
            case QUESTION -> new IntentClassificationDecision(
                    ClueIntent.QUESTION,
                    EvidenceClass.USER_QUESTION,
                    FactEligibility.NOT_BODY_FACT,
                    DecisionSource.USER_DECLARED,
                    AmbiguityCode.NONE,
                    NextRoute.QUESTION_INBOX
            );
            case KNOWLEDGE_ONLY -> new IntentClassificationDecision(
                    ClueIntent.KNOWLEDGE_ONLY,
                    EvidenceClass.ARTICLE_KNOWLEDGE,
                    FactEligibility.NOT_BODY_FACT,
                    DecisionSource.USER_DECLARED,
                    AmbiguityCode.NONE,
                    NextRoute.KNOWLEDGE_INBOX
            );
        };
    }

    private IntentClassificationDecision classifyRelated(RelationType relationType) {
        EvidenceClass evidenceClass = relationType == RelationType.OBSERVE
                ? EvidenceClass.USER_DECLARED_RELEVANCE
                : EvidenceClass.USER_PERSONAL_CLAIM;
        return new IntentClassificationDecision(
                ClueIntent.RELATED,
                evidenceClass,
                FactEligibility.CANDIDATE_ONLY,
                DecisionSource.USER_DECLARED,
                AmbiguityCode.NONE,
                NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE
        );
    }
}
