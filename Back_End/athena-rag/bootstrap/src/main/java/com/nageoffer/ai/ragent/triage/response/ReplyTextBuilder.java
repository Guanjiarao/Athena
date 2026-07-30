

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.engine.TriageReplyBuilder;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.question.QuestionOptionProvider;
import com.nageoffer.ai.ragent.triage.question.QuestionPlanningSupport;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;

/**
 * Response-package facade for reply text generation.
 *
 * <p>Keeps existing engine implementation in place while moving response ownership into
 * {@code triage.response} without changing business behavior.</p>
 */
public final class ReplyTextBuilder {

    private ReplyTextBuilder() {
    }

    public static String buildClarificationReply(TriageContext context,
                                                 QuestionOptionProvider optionGenerator,
                                                 QuestionPlanningSupport questionPlanSupport,
                                                 TriageModelGateway triageModelGateway) {
        return TriageReplyBuilder.buildClarificationReply(context, optionGenerator, questionPlanSupport, triageModelGateway);
    }

    public static String buildWarningReply(TriageContext context) {
        return TriageReplyBuilder.buildWarningReply(context);
    }

    public static String generatePreTriageReport(TriageContext context, TriageModelGateway triageModelGateway) {
        return TriageReplyBuilder.generatePreTriageReport(context, triageModelGateway);
    }
}
