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

package com.nageoffer.ai.ragent.triage.worker;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险分级 Worker。
 *
 * <p>它只在“信息基本齐备”时使用，用于给出 1 到 4 级的风险等级。
 * 若模型返回不稳定，系统会自动切换到规则化保守兜底，优先保障医疗安全。</p>
 */
@Component
public class RiskStratifierWorker extends AbstractStructuredTriageWorker {

    private static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "level": {
                  "type": "integer",
                  "minimum": 1,
                  "maximum": 4
                },
                "score": {
                  "type": "number",
                  "minimum": 0,
                  "maximum": 100
                },
                "evidence": {
                  "type": "string"
                },
                "rationale": {
                  "type": "string"
                }
              },
              "required": ["level", "score", "evidence", "rationale"]
            }
            """;

    public RiskStratifierWorker(LLMService llmService, ObjectMapper objectMapper) {
        super(llmService, objectMapper);
    }

    /**
     * 执行风险分层。
     *
     * <p>这里对外只暴露一个统一入口。即使模型失败，也会产出一个可解释的风险结果，
     * 避免编排器在医疗红线场景里“拿不到结论”。</p>
     */
    public TriageContext execute(TriageContext context) {
        if (context == null) {
            context = new TriageContext();
        }
        context.ensureCollections();

        if (context.hasMissingFields()) {
            return context;
        }

        RiskLevel riskLevel;
        try {
            String rawResponse = invokeLlm(
                    buildSystemPrompt(),
                    buildUserPrompt(context),
                    0.1D,
                    0.2D
            );
            riskLevel = readObjectSafely(
                    rawResponse,
                    RiskLevel.class,
                    heuristicRiskFallback(context),
                    "风险分层"
            );
        } catch (Exception ex) {
            riskLevel = heuristicRiskFallback(context);
        }

        context.setRiskAssessment(riskLevel.normalize());
        return context;
    }

    private String buildSystemPrompt() {
        return """
                你是医疗分诊系统中的“风险分级 Worker”，只负责做风险分层，不负责给出诊断。
                请基于用户描述和结构化症状，输出当前病历的风险等级。

                分级要求：
                1. level 取值必须为 1-4，数字越大风险越高。
                2. score 取值范围必须为 0-100。
                3. evidence 必须写明判级依据，强调触发高风险的关键信号。
                4. rationale 必须解释判级逻辑，但不得写成诊断结论。
                5. 只输出合法 JSON，不允许附加说明文字。

                JSON Schema:
                """ + OUTPUT_JSON_SCHEMA;
    }

    private String buildUserPrompt(TriageContext context) {
        return """
                会话ID: %s
                原始用户输入:
                %s

                结构化症状:
                %s

                请输出风险分级 JSON。
                """.formatted(
                StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"),
                StrUtil.blankToDefault(context.getUserInput(), ""),
                toJsonSafely(context.getExtractedSymptoms())
        );
    }

    private RiskLevel heuristicRiskFallback(TriageContext context) {
        String combinedText = buildCombinedText(context);
        List<String> evidence = new ArrayList<>();

        if (containsAny(combinedText, List.of("呼吸困难", "喘不过气", "意识不清", "昏迷", "晕厥", "抽搐", "大出血"))) {
            evidence.add("存在危及生命的红旗信号，如呼吸困难、意识障碍或大量出血。");
            return RiskLevel.builder()
                    .level(4)
                    .score(95D)
                    .evidence(String.join("；", evidence))
                    .rationale("出现急危重红旗症状时，系统应直接归入最高风险等级。")
                    .build();
        }

        boolean pregnancyBleeding = containsAny(combinedText, List.of("怀孕", "妊娠")) && containsAny(combinedText, List.of("出血", "见红"));
        boolean severeAbdominalRisk = containsAny(combinedText, List.of("腹痛", "肚子疼", "肚子痛"))
                && containsAny(combinedText, List.of("发热", "发烧", "呕吐", "剧烈", "难忍"));
        boolean chestPainRisk = containsAny(combinedText, List.of("胸痛", "胸口痛", "心口痛"));

        if (pregnancyBleeding) {
            evidence.add("妊娠相关出血属于高危场景。");
        }
        if (severeAbdominalRisk) {
            evidence.add("腹痛合并发热、呕吐或剧烈疼痛，需警惕急腹症风险。");
        }
        if (chestPainRisk) {
            evidence.add("胸痛属于需要优先排查的高危主诉。");
        }
        if (!evidence.isEmpty()) {
            return RiskLevel.builder()
                    .level(3)
                    .score(82D)
                    .evidence(String.join("；", evidence))
                    .rationale("存在明显高风险组合症状，按保守策略应引导尽快线下就医。")
                    .build();
        }

        boolean moderateRisk = containsAny(combinedText, List.of("发热", "发烧", "呕吐", "腹泻", "头晕"))
                || hasModerateSymptomLoad(context.getExtractedSymptoms());
        if (moderateRisk) {
            return RiskLevel.builder()
                    .level(2)
                    .score(55D)
                    .evidence("存在需要持续观察的中等风险症状，但暂未命中明确急危重红旗信号。")
                    .rationale("症状已经具有一定复杂度，应继续观察并结合病情变化决定是否就医。")
                    .build();
        }

        return RiskLevel.builder()
                .level(1)
                .score(20D)
                .evidence("当前描述未命中高风险红旗信号，且症状负荷较低。")
                .rationale("在信息齐备前提下，暂可归入低风险观察级。")
                .build();
    }

    private String buildCombinedText(TriageContext context) {
        StringBuilder builder = new StringBuilder(StrUtil.blankToDefault(context.getUserInput(), ""));
        if (context.getExtractedSymptoms() != null) {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                if (symptom == null) {
                    continue;
                }
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getName(), ""));
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getBodyPart(), ""));
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getDuration(), ""));
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getSeverity(), ""));
                if (symptom.getCharacteristics() != null) {
                    builder.append(" ").append(String.join(" ", symptom.getCharacteristics()));
                }
                if (symptom.getAccompanyingSymptoms() != null) {
                    builder.append(" ").append(String.join(" ", symptom.getAccompanyingSymptoms()));
                }
            }
        }
        return builder.toString();
    }

    private boolean hasModerateSymptomLoad(List<Symptom> symptoms) {
        if (symptoms == null || symptoms.isEmpty()) {
            return false;
        }
        if (symptoms.size() >= 3) {
            return true;
        }
        return symptoms.stream()
                .anyMatch(each -> each != null && StrUtil.containsAnyIgnoreCase(
                        StrUtil.blankToDefault(each.getSeverity(), ""),
                        "中度", "重度"
                ));
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (StrUtil.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (StrUtil.isNotBlank(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
