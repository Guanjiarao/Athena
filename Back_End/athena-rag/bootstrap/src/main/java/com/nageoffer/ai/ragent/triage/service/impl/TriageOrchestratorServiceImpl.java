

package com.nageoffer.ai.ragent.triage.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.triage.config.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageReportData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageWarningData;
import com.nageoffer.ai.ragent.triage.engine.DepartmentRecommender;
import com.nageoffer.ai.ragent.triage.engine.TriageStateMachine;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.repository.TriageRepository;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import com.nageoffer.ai.ragent.triage.service.TriageSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageOrchestratorServiceImpl implements TriageOrchestratorService {

    private final TriageStateMachine triageStateMachine;
    private final TriageSessionManager triageSessionManager;
    private final TriageRepository triageRepository;
    private final TriageModelGateway triageModelGateway;
    private final TriageSessionProperties triageSessionProperties;
    private final TriageConversationMemoryHelper triageConversationMemoryHelper;

    @Override
    public TriageAnalyzeResponse analyze(TriageAnalyzeRequest request) {
        validateRequest(request);
        TriageContext context = loadOrCreateContext(request);
        try {
            triageStateMachine.execute(context);
            return toResponse(context);
        } finally {
            triageSessionManager.saveContext(context);
            persistTerminalContext(context);
        }
    }

    private TriageContext loadOrCreateContext(TriageAnalyzeRequest request) {
        String sessionId = StrUtil.blankToDefault(request.getSessionId(), IdUtil.getSnowflakeNextIdStr());
        TriageContext context = triageSessionManager.getContext(sessionId);
        String latestUserInput = request.getUserInput().trim();
        if (context == null) {
            context = TriageContext.builder().sessionId(sessionId).build();
            context.ensureCollections();
            context.appendState("Session initialized.");
        } else {
            context.ensureCollections();
            context.appendState("Session restored from session manager.");
        }
        context.resetTurnState();
        context.setTotalTurnCount(context.getTotalTurnCount() + 1);
        context.appendState("Turn counter incremented: totalTurnCount=" + context.getTotalTurnCount());
        context.setLatestUserTurn(latestUserInput);
        context.appendConversation(latestUserInput);
        compressConversationMemoryIfNeeded(context);
        context.setUserInput(context.buildConversationTranscript(true));
        return context;
    }

    private void compressConversationMemoryIfNeeded(TriageContext context) {
        int contextWindowMaxChars = safePositive(triageSessionProperties.getContextWindowMaxChars(), 2400);
        int targetRecentWindowChars = safePositive(triageSessionProperties.getTargetRecentWindowChars(), 1200);
        int summaryMaxChars = safePositive(triageSessionProperties.getSummaryMaxChars(), 400);
        int beforeTotalChars = context.totalTranscriptChars(true);
        int beforeRecentChars = context.recentConversationChars();
        int beforeSummaryChars = StrUtil.length(context.getConversationSummary());
        context.appendState("Memory window check: totalChars=" + beforeTotalChars
                + ", recentChars=" + beforeRecentChars
                + ", summaryChars=" + beforeSummaryChars
                + ", maxChars=" + contextWindowMaxChars
                + ", targetRecentChars=" + targetRecentWindowChars);
        if (beforeTotalChars <= contextWindowMaxChars) {
            context.appendState("Memory window within budget, skip compression.");
            return;
        }
        List<String> evictedTurns = context.evictOldestTurnsByCharBudget(targetRecentWindowChars);
        if (evictedTurns.isEmpty()) {
            context.appendState("Memory window exceeded but no turns could be evicted.");
            return;
        }
        String summary = triageConversationMemoryHelper.summarizeConversation(
                context,
                evictedTurns,
                summaryMaxChars,
                triageModelGateway
        );
        if (StrUtil.isNotBlank(summary)) {
            context.setConversationSummary(summary);
            context.appendState("Memory compressed: evictedTurns=" + evictedTurns.size()
                    + ", recentChars=" + context.recentConversationChars()
                    + ", summaryChars=" + StrUtil.length(summary)
                    + ", totalChars=" + context.totalTranscriptChars(true));
            return;
        }
        context.appendState("Memory compression produced empty summary, keeping previous summary.");
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private void validateRequest(TriageAnalyzeRequest request) {
        if (request == null) {
            throw new ClientException("Triage request must not be null.");
        }
        if (StrUtil.isBlank(request.getUserInput())) {
            throw new ClientException("userInput must not be blank.");
        }
    }

    private void persistTerminalContext(TriageContext context) {
        if (context == null || context.getCurrentState() == null || !context.getCurrentState().isTerminal()) {
            return;
        }
        triageRepository.save(context);
    }

    private TriageAnalyzeResponse toResponse(TriageContext context) {
        TriageAction action = context.getNextAction();
        if (action == null) {
            context.setNextAction(TriageAction.ASK_CLARIFICATION);
            if (StrUtil.isBlank(context.getFinalReply())) {
                context.setFinalReply("为了继续判断，请再补充一些不适细节。");
            }
            return toClarificationResponse(context);
        }
        return switch (action) {
            case ASK_CLARIFICATION -> toClarificationResponse(context);
            case TRIGGER_WARNING -> toWarningResponse(context);
            case GENERATE_REPORT -> toReportResponse(context);
        };
    }

    private TriageAnalyzeResponse toClarificationResponse(TriageContext context) {
        log.info("[Orchestrator] 构建澄清响应，generatedOptions 数量: {}",
                context.getGeneratedOptions() == null ? "null" : context.getGeneratedOptions().size());

        TriageClarificationData data = TriageClarificationData.builder()
                .sessionId(context.getSessionId())
                .extractedSymptoms(context.getExtractedSymptoms())
                .missingFields(context.getMissingFields())
                .pendingSlots(context.getPendingSlots())
                .questionPlan(context.getQuestionPlan())
                .followUpQuestion(context.getFinalReply())
                .options(context.getGeneratedOptions())  // 添加选项
                .build();

        log.info("[Orchestrator] 构建的 data.options 数量: {}",
                data.getOptions() == null ? "null" : data.getOptions().size());

        // 记录系统回复到历史（用于检测连续通用问题）
        if (context.getSystemReplyHistory() == null) {
            context.setSystemReplyHistory(new ArrayList<>());
        }
        context.getSystemReplyHistory().add(context.getFinalReply());
        log.info("[Orchestrator] 记录系统回复到历史: {}, 当前历史数量: {}",
                context.getFinalReply(), context.getSystemReplyHistory().size());

        return TriageAnalyzeResponse.builder()
                .action(TriageAction.ASK_CLARIFICATION.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(0)
                .build();
    }

    private TriageAnalyzeResponse toWarningResponse(TriageContext context) {
        // 使用科室推荐引擎获取推荐科室（高风险默认推荐急诊科）
        DepartmentRecommender recommender = new DepartmentRecommender();
        DepartmentRecommender.DepartmentRecommendation recommendation = recommender.recommend(context);

        // 如果风险等级 >= 3，强制推荐急诊科
        if (context.getRiskAssessment() != null && context.getRiskAssessment().getLevel() >= 3) {
            recommendation = new DepartmentRecommender.DepartmentRecommendation(
                "急诊科",
                "存在高风险症状，建议立即前往急诊科就诊"
            );
        }

        TriageWarningData data = TriageWarningData.builder()
                .sessionId(context.getSessionId())
                .riskAssessment(context.getRiskAssessment())
                .extractedSymptoms(context.getExtractedSymptoms())
                .warningText(context.getFinalReply())
                .emergencyGuidance("如症状持续加重，或出现呼吸困难、意识变化、明显出血等情况，请立即前往急诊。")
                .recommendedDepartment(recommendation.getDepartment())
                .departmentReason(recommendation.getReason())
                .build();
        return TriageAnalyzeResponse.builder()
                .action(TriageAction.TRIGGER_WARNING.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(context.getRiskAssessment() == null ? 0 : context.getRiskAssessment().getLevel())
                .build();
    }

    private TriageAnalyzeResponse toReportResponse(TriageContext context) {
        // 使用科室推荐引擎获取推荐科室
        DepartmentRecommender recommender = new DepartmentRecommender();
        DepartmentRecommender.DepartmentRecommendation recommendation = recommender.recommend(context);

        TriageReportData data = TriageReportData.builder()
                .sessionId(context.getSessionId())
                .report(context.getFinalReply())
                .riskAssessment(context.getRiskAssessment())
                .extractedSymptoms(context.getExtractedSymptoms())
                .recommendedDepartment(recommendation.getDepartment())
                .departmentReason(recommendation.getReason())
                .build();
        return TriageAnalyzeResponse.builder()
                .action(TriageAction.GENERATE_REPORT.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(context.getRiskAssessment() == null ? 0 : context.getRiskAssessment().getLevel())
                .build();
    }
}
