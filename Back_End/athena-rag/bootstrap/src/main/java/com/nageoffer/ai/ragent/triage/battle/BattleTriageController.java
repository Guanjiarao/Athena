

package com.nageoffer.ai.ragent.triage.battle;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.triage.battle.pureprompt.PurePromptBattleService;
import com.nageoffer.ai.ragent.triage.battle.pureskill.PureSkillBattleService;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预分诊 battle 对比入口。
 *
 * <p>这些接口用于快速对比不同基线链路，不经过正式多 Agent 编排链路。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "分诊 Battle 对比接口")
public class BattleTriageController {

    private final PurePromptBattleService purePromptBattleService;
    private final PureSkillBattleService pureSkillBattleService;
    private final BattleRunService battleRunService;

    /**
     * 一键运行三路 battle：official / pure-prompt / pure-skill 同时跑默认 10 个用例。
     */
    @PostMapping({"/rag/triage/battle/run", "/triage/battle/run"})
    @Operation(summary = "预分诊 battle：三路批量运行")
    public BattleRunResponse runBattle(@RequestBody(required = false) BattleRunRequest request) {
        return battleRunService.run(request);
    }

    /**
     * 统一 battle 入口：通过 mode 选择基线。
     */
    @PostMapping({"/rag/triage/battle/analyze", "/triage/battle/analyze"})
    @Operation(summary = "预分诊 battle：统一对比入口")
    public TriageAnalyzeResponse analyze(@RequestParam(defaultValue = "pure-skill") String mode,
                                          @RequestBody TriageAnalyzeRequest request) {
        String normalizedMode = normalizeMode(mode);
        if ("pure-prompt".equals(normalizedMode)) {
            return analyzeWithPurePrompt(request);
        }
        if ("pure-skill".equals(normalizedMode)) {
            return analyzeWithPureSkill(request);
        }
        return fallback("Unsupported battle mode: " + mode + ". 可选值：pure-prompt, pure-skill。");
    }

    /**
     * 纯提示词基线：一次 LLM 调用直接完成预分诊。
     */
    @PostMapping({"/rag/triage/battle/pure-prompt/analyze", "/triage/battle/pure-prompt/analyze"})
    @Operation(summary = "预分诊 battle：纯提示词基线")
    public TriageAnalyzeResponse analyzeWithPurePrompt(@RequestBody TriageAnalyzeRequest request) {
        try {
            return purePromptBattleService.analyze(request);
        } catch (ClientException ex) {
            log.warn("纯提示词 battle 请求参数错误: {}", ex.getErrorMessage());
            return fallback(ex.getErrorMessage());
        } catch (Exception ex) {
            log.error("纯提示词 battle 接口异常", ex);
            return fallback("纯提示词分诊基线暂时不可用，请稍后重试。可先补充症状、持续时间、严重程度和危险信号。");
        }
    }

    /**
     * 纯 skill 基线：一次 LLM 调用，但使用完整技能说明约束输出。
     */
    @PostMapping({"/rag/triage/battle/pure-skill/analyze", "/triage/battle/pure-skill/analyze"})
    @Operation(summary = "预分诊 battle：纯 skill 基线")
    public TriageAnalyzeResponse analyzeWithPureSkill(@RequestBody TriageAnalyzeRequest request) {
        try {
            return pureSkillBattleService.analyze(request);
        } catch (ClientException ex) {
            log.warn("纯 skill battle 请求参数错误: {}", ex.getErrorMessage());
            return fallback(ex.getErrorMessage());
        } catch (Exception ex) {
            log.error("纯 skill battle 接口异常", ex);
            return fallback("纯 skill 分诊基线暂时不可用，请稍后重试。可先补充症状、持续时间、严重程度和危险信号。");
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "pure-skill";
        }
        return mode.trim().toLowerCase().replace('_', '-');
    }

    private TriageAnalyzeResponse fallback(String message) {
        return TriageAnalyzeResponse.builder()
                .action("ASK_CLARIFICATION")
                .data(null)
                .message(message)
                .riskLevel(0)
                .build();
    }
}
