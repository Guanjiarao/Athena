package com.whu.software.athena.cognitionagent.intent.provider;

import com.whu.software.athena.cognitionagent.intent.context.IntentModelContext;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.HelpRequestType;
import com.whu.software.athena.cognitionagent.intent.contract.QuestionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockIntentModelProviderTest {

    @Test
    void returnsStableStructuredSuggestionWithoutProbability() {
        IntentModelSuggestion suggestion = new MockIntentModelProvider()
                .suggest(context(ClueIntent.QUESTION));

        assertEquals("mock", suggestion.provider());
        assertEquals("mock-intent-v1", suggestion.modelName());
        assertEquals("intent-evidence-prompt-v1", suggestion.promptVersion());
        assertEquals(ClueIntent.QUESTION, suggestion.suggestedIntent());
    }

    @Test
    void canSimulateConflictingModelOutputForLaterPolicyTests() {
        IntentModelSuggestion suggestion = new MockIntentModelProvider(ClueIntent.RELATED)
                .suggest(context(ClueIntent.QUESTION));

        assertEquals(ClueIntent.RELATED, suggestion.suggestedIntent());
    }

    @Test
    void rejectsMissingAllowListedContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new MockIntentModelProvider().suggest(null));
    }

    private IntentModelContext context(ClueIntent intent) {
        return new IntentModelContext(
                intent,
                null,
                HelpRequestType.KNOWLEDGE,
                "Cycle changes",
                "Selected article text",
                QuestionType.IS_COMMON,
                "Is this common?",
                CycleRelation.NO_RELATION);
    }
}
