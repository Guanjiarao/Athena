

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.session.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgressAssembler {

    private final TriageSessionProperties triageSessionProperties;

    public TriageClarificationData.TriageProgress assemble(TriageContext context) {
        int targetSteps = safePositive(triageSessionProperties.getTargetClarificationTurns(), 7);
        int maxSteps = Math.max(safePositive(triageSessionProperties.getMaxTotalTurns(), 9), targetSteps);
        int currentStep = context == null || context.getQuestionPlan() == null || context.getQuestionPlan().getAskCount() == null
                ? 1
                : Math.max(1, context.getQuestionPlan().getAskCount());
        int percent = Math.min(100, (int) Math.round(currentStep * 100.0D / targetSteps));
        boolean riskCheck = hasRiskQuestion(context);
        boolean extended = currentStep > targetSteps;
        String mode;
        String stage;
        String stageLabel;
        String displayText;
        String tip;
        if (riskCheck) {
            mode = "RISK_CHECK";
            stage = "RISK_CHECK";
            stageLabel = "风险确认";
            displayText = "风险确认";
            tip = "我注意到您的描述中可能存在需要优先关注的情况。为了安全起见，需要先确认一个风险相关问题。";
        } else if (extended) {
            mode = "EXTENDED";
            stage = "EXTENDED_CLARIFICATION";
            stageLabel = "补充关键问题";
            displayText = "补充关键问题";
            tip = "基础问诊信息已经基本完成。针对您的情况，还有一个关键问题有助于判断风险，需要再多确认一下。";
        } else {
            mode = "NORMAL";
            stage = "COLLECTING_INFO";
            stageLabel = "问诊中";
            displayText = "问诊进度 " + Math.min(currentStep, targetSteps) + "/" + targetSteps;
            tip = "正在根据您的情况补充必要信息。";
        }
        return TriageClarificationData.TriageProgress.builder()
                .mode(mode)
                .currentStep(currentStep)
                .targetSteps(targetSteps)
                .maxSteps(maxSteps)
                .percent(percent)
                .stage(stage)
                .stageLabel(stageLabel)
                .displayText(displayText)
                .tip(tip)
                .build();
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private boolean hasRiskQuestion(TriageContext context) {
        if (context == null || context.getQuestionPlan() == null || context.getQuestionPlan().getSelectedQuestionGaps() == null) {
            return false;
        }
        return context.getQuestionPlan().getSelectedQuestionGaps().stream()
                .anyMatch(this::isRiskGap);
    }

    private boolean isRiskGap(QuestionGap gap) {
        if (gap == null) {
            return false;
        }
        return gap.getGapType() == QuestionGapType.RISK_REQUIRED || gap.getSource() == QuestionGapSource.RISK_POLICY;
    }
}
