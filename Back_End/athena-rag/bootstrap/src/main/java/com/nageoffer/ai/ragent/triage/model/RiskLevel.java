/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险分层结果实体。
 *
 * <p>这里的 level 是供编排器决策使用的硬信号；
 * score 用于补充表达风险强弱；
 * evidence 和 rationale 用于解释“为什么是这个等级”，便于前端展示和后续留痕。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskLevel {

    /**
     * 风险等级，强约束为 1 到 4 级。
     */
    private Integer level;

    /**
     * 风险评分，约定使用 0 到 100 的闭区间，数值越大越危险。
     */
    private Double score;

    /**
     * 判级依据，建议由多个关键风险点拼成一句可展示文本。
     */
    private String evidence;

    /**
     * 风险解释，用于说明分级背后的原则，而不是给出医学诊断。
     */
    private String rationale;

    /**
     * 当 LLM 结构化输出失败时，按“宁可保守，不可放过”的原则走安全兜底。
     */
    public static RiskLevel conservativeFallback(String reason) {
        return RiskLevel.builder()
                .level(3)
                .score(85D)
                .evidence("结构化风险输出解析失败，系统按保守策略降级为高风险。")
                .rationale(reason)
                .build();
    }

    /**
     * 统一收敛边界值，避免后续编排器收到越界数据。
     */
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
        return this;
    }
}
