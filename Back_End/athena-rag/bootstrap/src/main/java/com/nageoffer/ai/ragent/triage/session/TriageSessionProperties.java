

package com.nageoffer.ai.ragent.triage.session;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * triage 会话配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "triage.session")
public class TriageSessionProperties {

    /**
     * Redis Key 前缀。
     */
    private String keyPrefix = "triage:session:";

    /**
     * 会话存活时间（分钟）。
     */
    private Long ttlMinutes = 120L;

    /**
     * 触发压缩的上下文最大字符数。
     */
    private Integer contextWindowMaxChars = 2400;

    /**
     * 压缩后最近原始对话目标字符数。
     */
    private Integer targetRecentWindowChars = 1200;

    /**
     * 摘要最大字符数。
     */
    private Integer summaryMaxChars = 400;

    /**
     * 目标澄清问题轮次，用于前端进度条和默认问诊节奏。
     */
    private Integer targetClarificationTurns = 7;

    /**
     * 最大总对话轮次。
     */
    private Integer maxTotalTurns = 9;

    /**
     * 最少必须问的问题数（红旗情况除外）。
     */
    private Integer minRequiredTurns = 5;
}
