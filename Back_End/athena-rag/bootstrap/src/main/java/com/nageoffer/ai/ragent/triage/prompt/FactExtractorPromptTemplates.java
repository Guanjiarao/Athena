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

package com.nageoffer.ai.ragent.triage.prompt;

public final class FactExtractorPromptTemplates {

    private FactExtractorPromptTemplates() {
    }

    public static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "facts": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "slot": { "type": "string" },
                      "canonicalValue": { "type": "string" },
                      "polarity": { "type": "string" },
                      "confidence": { "type": "number" },
                      "evidence": { "type": "string" }
                    },
                    "required": ["slot", "canonicalValue"]
                  }
                }
              },
              "required": ["facts"]
            }
            """;

    public static String systemPrompt() {
        return """
                你是医疗分诊系统中的事实抽取器，只负责从本轮最新用户输入中抽取结构化事实。
                规则：
                1. 主要分析最新用户输入，可参考待补充槽位和上一轮追问。
                2. 否定信息必须保留，例如“没发热”应输出 FEVER_PRESENCE=NO。
                3. 不要重新总结整段病情，不要编造。
                4. 只输出符合 schema 的 JSON。

                JSON Schema:
                """ + OUTPUT_JSON_SCHEMA;
    }

    public static String userPrompt(String sessionId,
                                    String latestUserTurn,
                                    String lastAskedSlotsJson,
                                    String pendingSlotsJson,
                                    String conversationSummary) {
        return """
                会话ID: %s
                最新用户输入:
                %s

                上一轮问到的槽位:
                %s

                当前待补充槽位:
                %s

                历史摘要:
                %s
                """.formatted(sessionId, latestUserTurn, lastAskedSlotsJson, pendingSlotsJson, conversationSummary);
    }
}
