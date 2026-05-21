

package com.nageoffer.ai.ragent.triage.rule;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * triage 槽位规则配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "triage.slot-rule")
public class TriageSlotRuleProperties {

    /**
     * Redis Key 前缀，最终 key 为 prefix + signal。
     */
    private String keyPrefix = "triage:slot-rule:signal:";

    /**
     * 单个 signal 规则缓存 TTL（分钟）。
     */
    private Long ttlMinutes = 1440L;

    /**
     * LLM 学习/DB 配置规则的最低置信度。
     */
    private Double minConfidence = 0.6D;
}
