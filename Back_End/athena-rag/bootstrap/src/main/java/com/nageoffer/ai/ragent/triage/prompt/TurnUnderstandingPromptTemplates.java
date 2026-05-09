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

public final class TurnUnderstandingPromptTemplates {

    private TurnUnderstandingPromptTemplates() {
    }

    public static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "intent": {
                  "type": "string",
                  "description": "Must be one of ANSWER_FOLLOW_UP, NEW_COMPLAINT, CORRECTION, WEAK_INPUT, MIXED, UNKNOWN"
                },
                "primaryComplaint": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string" },
                    "confidence": { "type": "number" },
                    "evidence": { "type": "string" }
                  }
                },
                "answeredSlots": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "slot": { "type": "string" },
                      "rawValue": { "type": "string" },
                      "normalizedValue": { "type": "string" },
                      "assertion": { "type": "string" },
                      "confidence": { "type": "number" },
                      "evidence": { "type": "string" },
                      "answersPreviousQuestion": { "type": "boolean" }
                    }
                  }
                },
                "riskSignals": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "type": { "type": "string" },
                      "assertion": { "type": "string" },
                      "severityHint": { "type": "string" },
                      "confidence": { "type": "number" },
                      "evidence": { "type": "string" }
                    }
                  }
                },
                "corrections": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "target": {
                        "type": "string",
                        "description": "Must be one of PRIMARY_COMPLAINT, SLOT_VALUE, UNKNOWN"
                      },
                      "slot": {
                        "type": "string",
                        "description": "If target is SLOT_VALUE and the corrected slot is identifiable, fill it with a SlotCode such as BODY_PART, DURATION, FEVER_PRESENCE"
                      },
                      "rejectValue": { "type": "string" },
                      "confirmValue": { "type": "string" },
                      "confidence": { "type": "number" },
                      "evidence": { "type": "string" }
                    }
                  }
                },
                "notes": { "type": "array", "items": { "type": "string" } },
                "confidence": { "type": "number" }
              },
              "required": ["intent", "answeredSlots", "riskSignals", "corrections"]
            }
            """;

    public static String systemPrompt() {
        return """
                你是医疗分诊系统中的回合级语义理解器。
                你的任务不是做最终分诊动作，而是尽可能完整、稳定地理解“这一轮用户到底表达了什么”。

                你需要输出一个结构化 JSON，描述这一轮的语义结果，而不是输出建议或解释性文本。

                识别原则：
                1. intent 必须使用运行时枚举口径：ANSWER_FOLLOW_UP、NEW_COMPLAINT、CORRECTION、WEAK_INPUT、MIXED、UNKNOWN。
                2. 如果这一轮主要是在回答上一轮追问、补充槽位、确认 yes/no、补充时长/部位，请优先判为 ANSWER_FOLLOW_UP，而不是 NEW_COMPLAINT。
                3. 如果这一轮出现“不是A，是B”“不对，是B”“改成B”“更正”这类纠正语义，请识别 corrections。
                4. correction 必须优先按 state-first 理解：
                   - 先判断是不是在纠正某个已知槽位值；
                   - 如果能识别被纠正的具体槽位，请填写 corrections[].slot；
                   - 只有无法解释为槽位纠正时，才考虑 PRIMARY_COMPLAINT。
                5. 如果这一轮只是 follow-up answer 或 slot correction，而不是明确提出新的主诉，不要因为用户没重复主诉文本就臆造“新主诉”。
                6. primaryComplaint 表示当前这一轮可以确认的主诉表达；如果这一轮只是补充信息而没有新的主诉文本，可以留空，不要编造。
                7. complaint 与 risk signal 可以同时存在：例如用户既在表达胸部不适，也在表达呼吸困难。不要因为识别到风险信号，就忽略主诉或 answeredSlots。
                8. answeredSlots 只填写这一轮被用户明确回答或修正到的槽位；不要填写未被本轮证实的信息。
                9. 所有判断必须尽量附 evidence，并尽量给 confidence。
                10. 不要做最终 warning / riskDecision 决策，不要编造不存在的信息。
                11. 只输出合法 JSON，不要输出 markdown、解释、前后缀。

                JSON Schema:
                """ + OUTPUT_JSON_SCHEMA;
    }

    public static String userPrompt(String sessionId,
                                    String latestUserTurn,
                                    String transcript,
                                    String lastAskedSlotsJson,
                                    String pendingSlotsJson,
                                    String slotStateJson) {
        return """
                会话ID: %s

                【本轮最新用户输入】
                %s

                【最近上下文】
                %s

                【上一轮明确追问的槽位】
                %s

                【当前待补充槽位】
                %s

                【当前已知槽位状态】
                %s

                【理解本轮时请特别注意】
                1. 如果最新输入主要是在回应“上一轮明确追问的槽位”或“当前待补充槽位”，优先理解为 follow-up answer。
                2. 如果最新输入是在修正“当前已知槽位状态”里的既有值，优先理解为 slot correction，而不是 new complaint。
                3. 只有当用户明确提出新的主诉或替换原主诉时，才把 intent 识别为 NEW_COMPLAINT，或把 correction 识别为 PRIMARY_COMPLAINT。
                4. 如果用户既表达主诉，又表达风险线索或槽位回答，可以同时输出；不要因为识别到风险线索就忽略 complaint / answeredSlots / corrections。
                5. 你的任务是忠实描述本轮新增语义，不要重写既往状态，也不要为了凑字段而编造信息。
                """.formatted(sessionId, latestUserTurn, transcript, lastAskedSlotsJson, pendingSlotsJson, slotStateJson);
    }
}
