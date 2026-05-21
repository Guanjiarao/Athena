

package com.nageoffer.ai.ragent.triage.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.engine.TriageStateMachine;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.response.TriageResponseAgent;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import com.nageoffer.ai.ragent.triage.session.TriageConversationMemoryHelper;
import com.nageoffer.ai.ragent.triage.session.TriageRepository;
import com.nageoffer.ai.ragent.triage.session.TriageSessionManager;
import com.nageoffer.ai.ragent.triage.session.TriageSessionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final TriageResponseAgent triageResponseAgent;

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
        return triageResponseAgent.toResponse(context);
    }
}
