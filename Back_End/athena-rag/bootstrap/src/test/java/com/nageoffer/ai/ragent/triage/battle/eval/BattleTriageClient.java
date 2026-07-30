

package com.nageoffer.ai.ragent.triage.battle.eval;

import com.nageoffer.ai.ragent.triage.battle.pureprompt.PurePromptBattleService;
import com.nageoffer.ai.ragent.triage.battle.pureskill.PureSkillBattleService;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 三版本评测统一调用入口。
 */
@Component
@RequiredArgsConstructor
public class BattleTriageClient {

    private final TriageOrchestratorService triageOrchestratorService;
    private final PurePromptBattleService purePromptBattleService;
    private final PureSkillBattleService pureSkillBattleService;

    public TriageAnalyzeResponse analyze(BattleVariant variant, TriageAnalyzeRequest request) {
        return switch (variant) {
            case MULTI_AGENT -> triageOrchestratorService.analyze(request);
            case PURE_PROMPT -> purePromptBattleService.analyze(request);
            case PURE_SKILL -> pureSkillBattleService.analyze(request);
        };
    }
}
