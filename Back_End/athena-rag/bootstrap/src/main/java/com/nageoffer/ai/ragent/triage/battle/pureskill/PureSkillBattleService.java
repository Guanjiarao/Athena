

package com.nageoffer.ai.ragent.triage.battle.pureskill;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.triage.battle.common.BattleJsonSupport;
import com.nageoffer.ai.ragent.triage.battle.common.BattleTriageResponseMapper;
import com.nageoffer.ai.ragent.triage.battle.common.BattleTriageResult;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * battle 基线二：纯 skill。
 *
 * <p>仍然只调用一次 LLM，但用更完整的“技能说明书”约束风险识别、追问和报告。</p>
 */
@Service
@RequiredArgsConstructor
public class PureSkillBattleService {

    private final TriageModelGateway triageModelGateway;
    private final BattleJsonSupport battleJsonSupport;

    public TriageAnalyzeResponse analyze(TriageAnalyzeRequest request) {
        long startNanos = System.nanoTime();
        validateRequest(request);
        String sessionId = StrUtil.blankToDefault(request.getSessionId(), IdUtil.getSnowflakeNextIdStr());
        String raw = triageModelGateway.chatWithTextModel(
                List.of(
                        ChatMessage.system(PureSkillPromptTemplates.systemPrompt()),
                        ChatMessage.user(buildUserPrompt(request))
                ),
                0.15,
                0.8,
                2200
        );
        BattleTriageResult result = battleJsonSupport.parseResult(raw);
        return BattleTriageResponseMapper.toResponse(sessionId, "pure-skill", result, elapsedMillis(startNanos));
    }

    private String buildUserPrompt(TriageAnalyzeRequest request) {
        return "请对下面用户输入执行预分诊单轮技能。\n"
                + "sessionId：" + StrUtil.blankToDefault(request.getSessionId(), "new-session") + "\n"
                + "用户输入：" + request.getUserInput().trim();
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private void validateRequest(TriageAnalyzeRequest request) {
        if (request == null || StrUtil.isBlank(request.getUserInput())) {
            throw new ClientException("userInput must not be blank.");
        }
    }
}
