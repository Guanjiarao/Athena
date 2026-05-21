

package com.nageoffer.ai.ragent.rag.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG V3 评测用例
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagV3EvalCase {

    /**
     * 用例唯一标识
     */
    private String id;

    /**
     * 用例分类
     */
    private String category;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 会话 ID（可选）
     */
    private String conversationId;

    /**
     * 是否开启深度思考（可选）
     */
    @JsonProperty("deep_thinking")
    private Boolean deepThinking;

    /**
     * 多轮上下文（当前第一版 runner 暂不支持执行，仅用于标记）
     */
    private List<HistoryMessage> history;

    /**
     * 预期检查项
     */
    @JsonProperty("expected_checks")
    private Map<String, Boolean> expectedChecks;

    /**
     * 备注
     */
    private String notes;

    public boolean hasHistory() {
        return history != null && !history.isEmpty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryMessage {
        private String role;
        private String content;
    }
}
