

package com.nageoffer.ai.ragent.triage.engine;

import com.nageoffer.ai.ragent.triage.session.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TurnLimitHelper {

    private final TriageSessionProperties properties;

    /**
     * 检查是否满足最少轮次要求
     */
    public boolean hasMetMinimumTurns(TriageContext context) {
        Integer totalTurns = context.getTotalTurnCount();
        Integer minTurns = properties.getMinRequiredTurns();

        if (totalTurns == null || minTurns == null) {
            return true; // 如果配置缺失，默认认为已满足
        }

        return totalTurns >= minTurns;
    }

    public boolean shouldForceReport(TriageContext context) {
        Integer totalTurns = context.getTotalTurnCount();
        Integer maxTurns = properties.getMaxTotalTurns();

        if (totalTurns == null || maxTurns == null) {
            return false;
        }

        boolean shouldForce = totalTurns >= maxTurns;
        if (shouldForce) {
            context.appendState("Turn limit reached: totalTurns=" + totalTurns + ", maxTurns=" + maxTurns + ", forcing report generation.");
        }
        return shouldForce;
    }
}
