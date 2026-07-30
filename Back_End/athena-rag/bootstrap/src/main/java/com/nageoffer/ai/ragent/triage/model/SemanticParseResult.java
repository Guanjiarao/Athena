

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义解析 Worker 的结构化输出契约。
 *
 * <p>单独定义该实体，是为了让“LLM 输出 JSON”与“全局上下文 TriageContext”
 * 解耦。这样即使未来语义解析结果扩展了字段，也不需要污染整个上下文模型。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticParseResult {

    /**
     * 从用户原始输入中抽取出的结构化症状列表。
     */
    @Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();
}
