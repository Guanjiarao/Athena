

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.engine.TriageReplyBuilder;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.question.QuestionOptionProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClarificationQuestionAssembler {

    public List<TriageClarificationData.ClarificationQuestion> assemble(TriageContext context, QuestionOptionProvider optionGenerator) {
        return TriageReplyBuilder.buildClarificationQuestions(context, optionGenerator);
    }
}
