

package com.nageoffer.ai.ragent.triage.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分诊评测用例
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageEvalCase {

    /**
     * 用例编号（如：001）
     */
    private String caseId;

    /**
     * 疾病名称（如：急性肠胃炎）
     */
    private String diseaseName;

    /**
     * 风险等级标识（如：轻症 🟢）
     */
    private String riskLabel;

    /**
     * 系统分类（如：消化系统）
     */
    private String systemCategory;

    /**
     * 用户初始输入
     */
    private String userInput;

    /**
     * 标准对话流程
     */
    private List<DialogueTurn> standardDialogue;

    /**
     * 评分标准
     */
    private TriageEvalCriteria criteria;

    /**
     * 对话轮次
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DialogueTurn {
        /**
         * 角色：system 或 user
         */
        private String role;

        /**
         * 内容
         */
        private String content;
    }
}
