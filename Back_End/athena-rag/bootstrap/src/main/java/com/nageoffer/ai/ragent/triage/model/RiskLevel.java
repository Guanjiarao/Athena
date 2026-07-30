

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险分层结果实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskLevel {

    private Integer level;

    private Double score;

    private String evidence;

    private String rationale;

    private Boolean shouldInterrupt;

    private Boolean needsMoreInfo;

    @Builder.Default
    private List<SlotCode> missingCriticalSlots = new ArrayList<>();

    @Builder.Default
    private List<String> riskHints = new ArrayList<>();

    public static RiskLevel conservativeFallback(String reason) {
        return RiskLevel.builder()
                .level(3)
                .score(85D)
                .evidence("结构化风险输出解析失败，系统按保守策略降级为高风险。")
                .rationale(reason)
                .shouldInterrupt(Boolean.TRUE)
                .needsMoreInfo(Boolean.FALSE)
                .build();
    }

    public RiskLevel normalize() {
        if (level == null) {
            level = 2;
        }
        if (level < 1) {
            level = 1;
        }
        if (level > 4) {
            level = 4;
        }
        if (score == null) {
            score = switch (level) {
                case 1 -> 20D;
                case 2 -> 45D;
                case 3 -> 75D;
                default -> 92D;
            };
        }
        if (evidence == null || evidence.isBlank()) {
            evidence = "模型未提供明确判级依据，已使用系统默认说明。";
        }
        if (rationale == null || rationale.isBlank()) {
            rationale = "风险等级由症状严重度、伴随症状和急危重红旗信号共同决定。";
        }
        if (shouldInterrupt == null) {
            shouldInterrupt = level >= 3;
        }
        if (needsMoreInfo == null) {
            needsMoreInfo = Boolean.FALSE;
        }
        if (missingCriticalSlots == null) {
            missingCriticalSlots = new ArrayList<>();
        }
        if (riskHints == null) {
            riskHints = new ArrayList<>();
        }
        return this;
    }
}
