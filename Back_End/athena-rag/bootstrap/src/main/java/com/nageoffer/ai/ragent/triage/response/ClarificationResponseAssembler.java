

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.question.QuestionOptionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationResponseAssembler {

    private final ClarificationQuestionAssembler clarificationQuestionAssembler;
    private final ProgressAssembler progressAssembler;
    private final QuestionOptionProvider optionGenerator;

    public TriageAnalyzeResponse assemble(TriageContext context) {
        log.info("[Orchestrator] 构建澄清响应: sessionId={}, nextAction={}, finalReply={}, generatedOptions={}, missingFields={}, pendingSlots={}, questionPlan.nextSlotsToAsk={}",
                context.getSessionId(),
                context.getNextAction(),
                context.getFinalReply(),
                context.getGeneratedOptions() == null ? "null" : context.getGeneratedOptions().size(),
                context.getMissingFields(),
                context.getPendingSlots(),
                context.getQuestionPlan() == null ? "null" : context.getQuestionPlan().getNextSlotsToAsk());

        List<TriageClarificationData.ClarificationQuestion> questions = clarificationQuestionAssembler.assemble(context, optionGenerator);
        TriageClarificationData.TriageProgress progress = progressAssembler.assemble(context);

        TriageClarificationData data = TriageClarificationData.builder()
                .sessionId(context.getSessionId())
                .extractedSymptoms(context.getExtractedSymptoms())
                .missingFields(context.getMissingFields())
                .pendingSlots(context.getPendingSlots())
                .questionPlan(null)
                .followUpQuestion(context.getFinalReply())
                .progress(progress)
                .questions(questions)
                .build();

        log.info("[Orchestrator] 构建的 data: followUpQuestion={}, progressMode={}, progressText={}, questionsCount={}, questionPlan.nextSlotsToAsk={}",
                data.getFollowUpQuestion(),
                progress == null ? "null" : progress.getMode(),
                progress == null ? "null" : progress.getDisplayText(),
                data.getQuestions() == null ? "null" : data.getQuestions().size(),
                data.getQuestionPlan() == null ? "null" : data.getQuestionPlan().getNextSlotsToAsk());

        if (context.getSystemReplyHistory() == null) {
            context.setSystemReplyHistory(new ArrayList<>());
            log.info("[Orchestrator] systemReplyHistory 初始化完成");
        }
        context.getSystemReplyHistory().add(context.getFinalReply());
        log.info("[Orchestrator] 记录系统回复到历史: {}, 当前历史数量: {}, history={}",
                context.getFinalReply(), context.getSystemReplyHistory().size(), context.getSystemReplyHistory());

        return TriageAnalyzeResponse.builder()
                .action(TriageAction.ASK_CLARIFICATION.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(0)
                .build();
    }
}
