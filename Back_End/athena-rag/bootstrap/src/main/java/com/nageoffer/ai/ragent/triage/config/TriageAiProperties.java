

package com.nageoffer.ai.ragent.triage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * triage 模块专属 AI 配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "triage.ai")
public class TriageAiProperties {

    /**
     * triage 文本任务默认模型。
     */
    private String textModel;

    /**
     * triage 报告生成默认模型。
     */
    private String reportModel;

    /**
     * triage 视觉分析默认模型。
     */
    private String visionModel = "qwen-vl-max";

    /**
     * triage turn understanding 模型。
     */
    private String turnUnderstandingModel;

    /**
     * triage 事实抽取模型。
     */
    private String factExtractorModel;

    /**
     * triage 语义解析模型。
     */
    private String semanticParserModel;

    /**
     * triage SOP 校验模型。
     */
    private String sopValidatorModel;

    /**
     * triage 风险分层模型。
     */
    private String riskStratifierModel;

    /**
     * triage 会话窗口使用的文本温度。
     */
    private Double textTemperature = 0.2D;

    /**
     * triage 报告生成温度。
     */
    private Double reportTemperature = 0.2D;
}
