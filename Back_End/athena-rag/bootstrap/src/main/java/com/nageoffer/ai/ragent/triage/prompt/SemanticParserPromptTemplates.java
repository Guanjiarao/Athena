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

public final class SemanticParserPromptTemplates {

    private SemanticParserPromptTemplates() {
    }

    public static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "extractedSymptoms": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": {
                      "name": { "type": "string" },
                      "bodyPart": { "type": "string" },
                      "duration": { "type": "string" },
                      "severity": { "type": "string" },
                      "characteristics": {
                        "type": "array",
                        "items": { "type": "string" }
                      },
                      "accompanyingSymptoms": {
                        "type": "array",
                        "items": { "type": "string" }
                      }
                    },
                    "required": ["name"]
                  }
                }
              },
              "required": ["extractedSymptoms"]
            }
            """;

    public static String systemPrompt() {
        return """
                你是医疗分诊系统中的“语义解析 Worker”，只负责信息抽取，不负责诊断。
                请严格从用户原话中提取已经明确表达的信息，不允许臆测，不允许补充未出现的医学事实。

                任务要求：
                1. 识别主诉症状，并规范化为常见临床表达，例如“肚子疼”可归一为“腹痛”。
                2. 抽取症状相关的持续时间、部位、严重程度、症状性质和伴随症状。
                3. 如果信息缺失，对应字段留空或留空数组，不要编造。
                4. 对被明确否定的症状绝对不要抽取，例如“没有发热”“没吐”“不是拉肚子”。
                5. accompanyingSymptoms 中也不要包含被否定的症状。
                6. 只输出符合下列 JSON Schema 的 JSON，不要输出任何解释文字。

                JSON Schema:
                """ + OUTPUT_JSON_SCHEMA;
    }

    public static String userPrompt(String sessionId, String userInput) {
        return """
                会话ID: %s
                原始用户输入:
                %s

                请根据上面的原始输入完成结构化抽取。
                """.formatted(sessionId, userInput);
    }
}
