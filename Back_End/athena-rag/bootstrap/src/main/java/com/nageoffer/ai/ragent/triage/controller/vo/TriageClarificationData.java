

package com.nageoffer.ai.ragent.triage.controller.vo;

import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 追问动作对应的数据载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "追问动作对应的数据载荷。")
public class TriageClarificationData {

    @Schema(description = "sessionId")
    private String sessionId;

    @Builder.Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    @Builder.Default
    @Schema(description = "当前仍待补齐的槽位")
    private List<SlotCode> pendingSlots = new ArrayList<>();

    @Schema(description = "结构化追问规划")
    private QuestionPlan questionPlan;

    @Schema(description = "followUpQuestion")
    private String followUpQuestion;

    @Schema(description = "问诊进度信息")
    private TriageProgress progress;

    @Builder.Default
    @Schema(description = "结构化问题列表，每个问题绑定自己的槽位和选项。")
    private List<ClarificationQuestion> questions = new ArrayList<>();

    /**
     * 问诊进度信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "问诊进度信息")
    public static class TriageProgress {

        @Schema(description = "进度模式：NORMAL / RISK_CHECK / EXTENDED / READY_TO_REPORT")
        private String mode;

        @Schema(description = "当前步骤")
        private Integer currentStep;

        @Schema(description = "目标步骤数")
        private Integer targetSteps;

        @Schema(description = "最大步骤数")
        private Integer maxSteps;

        @Schema(description = "进度百分比，最大 100")
        private Integer percent;

        @Schema(description = "阶段代码")
        private String stage;

        @Schema(description = "阶段展示名")
        private String stageLabel;

        @Schema(description = "前端可直接展示的进度文本")
        private String displayText;

        @Schema(description = "进度提示文案")
        private String tip;
    }

    /**
     * 结构化追问问题。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "结构化追问问题")
    public static class ClarificationQuestion {

        @Schema(description = "目标槽位")
        private SlotCode slot;

        @Schema(description = "问题文本")
        private String question;

        @Schema(description = "输入类型：SINGLE_CHOICE / MULTI_CHOICE / TEXT")
        private String inputType;

        @Schema(description = "是否必填")
        private Boolean required;

        @Schema(description = "是否多选")
        private Boolean multiple;

        @Builder.Default
        @Schema(description = "该问题对应选项")
        private List<QuestionOption> options = new ArrayList<>();
    }

    /**
     * 问题选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "问题选项")
    public static class QuestionOption {

        @Schema(description = "选项文本，如'轻微'")
        private String label;

        @Schema(description = "选项值，如'mild'")
        private String value;

        @Schema(description = "对应槽位")
        private SlotCode targetSlot;
    }
}
