

package com.nageoffer.ai.ragent.triage.battle.pureprompt;

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
 * battle 基线一：纯提示词。
 *
 * <p>目标是尽量不借用现有多 Agent、槽位规则、内置模板，只通过一次 LLM 调用直接产出追问或报告。</p>
 */
@Service
@RequiredArgsConstructor
public class PurePromptBattleService {

    private static final String PROMPT = """
            你是一个在线预分诊助手。请根据用户本轮输入和历史文本，直接判断下一步应该追问还是生成就医分诊报告。
            如果信息不足，输出 1 到 3 个问题，每个问题带可选选项；如果信息已足够，输出报告和建议科室。
            注意：这不是诊断，只能做就医分诊建议；如果出现胸痛、呼吸困难、意识障碍、大出血、严重过敏、卒中表现等急危重信号，应优先给出紧急就医提醒。
            只返回 JSON，不要返回 Markdown。
            JSON 字段：
            action: ASK_CLARIFICATION 或 GENERATE_REPORT 或 WARN
            message: 给用户看的简短中文话术
            riskLevel: 0未知/1低/2中/3高/4紧急
            questions: 数组；每项包含 slot, question, inputType(SINGLE_CHOICE/MULTI_CHOICE/TEXT), required, multiple, options；options 每项包含 label,value
            extractedSymptoms: 已识别症状数组
            missingFields: 缺失信息数组
            evidence: 判断依据数组
            recommendedDepartment: 建议科室
            departmentReason: 科室理由
            report: 适当情况下生成的中文分诊报告
            """;

    private final TriageModelGateway triageModelGateway;
    private final BattleJsonSupport battleJsonSupport;

    public TriageAnalyzeResponse analyze(TriageAnalyzeRequest request) {
        long startNanos = System.nanoTime();
        validateRequest(request);
        String sessionId = StrUtil.blankToDefault(request.getSessionId(), IdUtil.getSnowflakeNextIdStr());
        String userPrompt = buildUserPrompt(request);
        String raw = triageModelGateway.chatWithTextModel(
                List.of(ChatMessage.system(PROMPT), ChatMessage.user(userPrompt)),
                0.2,
                0.8,
                1600
        );
        BattleTriageResult result = battleJsonSupport.parseResult(raw);
        return BattleTriageResponseMapper.toResponse(sessionId, "pure-prompt", result, elapsedMillis(startNanos));
    }

    private String buildUserPrompt(TriageAnalyzeRequest request) {
        return "用户输入：" + request.getUserInput().trim() + "\n"
                + "sessionId：" + StrUtil.blankToDefault(request.getSessionId(), "new-session") + "\n"
                + "请直接返回符合约定的 JSON。";
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
