

package com.nageoffer.ai.ragent.triage.risk;

import com.nageoffer.ai.ragent.triage.model.RiskDecision;
import com.nageoffer.ai.ragent.triage.model.RiskGap;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险 Agent 输出。风险 Agent 负责安全判断、风险追问建议和红旗中断建议。
 */
@Data
@Builder
public class RiskAgentResult {

    private RiskLevel riskLevel;

    private RiskDecision riskDecision;

    private Boolean interrupt;

    private String warningReason;

    @Builder.Default
    private List<RiskGap> riskGaps = new ArrayList<>();
}
