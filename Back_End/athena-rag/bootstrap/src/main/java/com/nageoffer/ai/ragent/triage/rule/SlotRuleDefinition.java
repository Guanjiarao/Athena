

package com.nageoffer.ai.ragent.triage.rule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可缓存/可持久化的症状信号追问槽位规则。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlotRuleDefinition {

    private String signal;

    private SlotCode slot;

    private QuestionGapType gapType;

    private QuestionGapSource source;

    private Integer priority;

    private String reason;

    private Double confidence;

    /**
     * 该槽位对应的可选项，用于 Redis/DB 规则命中后无需再硬编码生成。
     */
    private List<TriageClarificationData.QuestionOption> options;
}
