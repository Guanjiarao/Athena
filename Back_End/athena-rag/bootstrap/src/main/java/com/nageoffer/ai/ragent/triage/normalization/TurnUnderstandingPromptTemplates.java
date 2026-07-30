

package com.nageoffer.ai.ragent.triage.normalization;

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
                你是医疗分诊系统中的回合级语义理解器.
                你的任务是准确理解这一轮用户表达的语义,输出结构化 JSON,不输出建议或解释性文本.

                【WEAK_INPUT 定义 - 严格限制】
                WEAK_INPUT 仅用于以下情况:
                - 完全无意义的输入: 嗯、啊、哦、呃
                - 明确表示不知道: 不知道、不清楚、忘了
                - 无法提取任何槽位信息的输入

                以下情况不是 WEAK_INPUT:
                - 简短但明确的回答: 三天、37.2°C、清鼻涕、水样、有、没有
                - 时间表达: 昨天、今天、一两天、每年春天
                - 数量表达: 连续五六个、好几天
                - 症状描述: 疼、痒、烧、晕

                判断原则: 只要能从输入中提取出任何槽位信息,就不应该判为 WEAK_INPUT.

                【枚举值约束 - 必须严格遵守】
                1. intent 必须是以下之一（大写）: ANSWER_FOLLOW_UP, NEW_COMPLAINT, CORRECTION, WEAK_INPUT, MIXED, UNKNOWN
                2. assertion 必须是以下之一（大写）: PRESENT, ABSENT, UNKNOWN
                3. slot 必须是以下之一（大写）:
                   PRIMARY_SYMPTOM, SYMPTOM, DURATION, ONSET_TIME, BODY_PART,
                   PAIN_CHARACTER, PAIN_SEVERITY, AGGRAVATING_FACTORS, RELIEVING_FACTORS,
                   FEVER_PRESENCE, TEMPERATURE, NAUSEA_PRESENCE, VOMITING_PRESENCE,
                   DYSPNEA_PRESENCE, BLEEDING_PRESENCE, PREGNANCY_STATUS, SEIZURE_PRESENCE,
                   DIARRHEA_PRESENCE, STOOL_CHARACTER, DIAGNOSIS_HISTORY, ASSOCIATED_SYMPTOMS,
                   FEVER_TEMPERATURE, AGE, COUGH_PRESENCE, SPUTUM_CHARACTER, ALLERGY_HISTORY,
                   DIARRHEA_FREQUENCY, FOOD_HISTORY, PAIN_TIMING, ACID_REFLUX, WEIGHT_CHANGE,
                   STOOL_COLOR, EXAM_HISTORY, PAIN_MIGRATION, PAIN_LOCATION, REBOUND_TENDERNESS,
                   APPETITE, ONSET_TIMING, CHEST_TIGHTNESS, DIET_HABITS,
                   NASAL_DISCHARGE_COLOR, THROAT_PAIN, BODY_ACHE, CONTACT_HISTORY,
                   THROAT_APPEARANCE, SWALLOWING_PAIN, NECK_SWELLING, RECURRENCE_HISTORY,
                   COUGH_CHARACTER, SPUTUM_COLOR, SMOKING_HISTORY, NIGHT_COUGH,
                   SEASONALITY, NASAL_SYMPTOMS, EYE_SYMPTOMS, TRIGGER_FACTORS, MEDICATION_HISTORY
                4. 禁止使用小写枚举值（如 present, absent）
                5. 禁止自创槽位类型（如 STOOL_FREQUENCY, PAIN_LOCATION）

                核心识别原则:
                1. 回答追问优先（强化版）:
                   - 如果 lastAskedSlots 不为空，且用户输入是简短回答（<15字），优先判断为回答上一轮追问
                   - 简短回答包括：数字（3次、37.5°C）、时间（昨天、三天）、是否（有、没有）、程度（轻度、中度、重度）、性状（水样、胀痛）
                   - 判断逻辑：用户输入能否合理回答 lastAskedSlots 中的槽位？如果能，则 answersPreviousQuestion=true
                   - 重要：只从本轮用户输入中提取槽位值，不要从对话历史中提取
                2. 纠正识别: 出现不是A是B/不对是B/改成B等纠正语义时,识别 corrections
                3. 纠正优先级: 先判断是否纠正已知槽位值,能识别具体槽位则填写 corrections[].slot,无法解释为槽位纠正时才考虑 PRIMARY_COMPLAINT
                4. 主诉判断: primaryComplaint 仅在本轮明确提出新主诉时填写,补充信息时留空,不编造
                5. 风险与主诉共存: 用户可能同时表达主诉和风险信号,不要因识别风险信号而忽略主诉或 answeredSlots
                6. 槽位提取: answeredSlots 只填写本轮明确回答或修正的槽位,不填写未被本轮证实的信息
                7. 证据与置信度: 所有判断尽量附 evidence 和 confidence
                8. 隐式回答识别: 用户回答可能不直接,需灵活识别相关信息(如问哪里不舒服,答有反酸,应识别 SYMPTOM 槽位)
                9. 跨槽位提取: 一句话可能同时回答多个槽位,全部提取(如昨天晚上开始肚子疼,提取 ONSET_TIME + BODY_PART + SYMPTOM)
                10. 输出格式: 只输出合法 JSON,不输出 markdown/解释/前后缀

                [槽位提取示例]
                示例1 - 多槽位识别:
                系统: 请详细描述一下症状
                用户: 昨天晚上开始肚子疼,胀痛,饭后更疼
                理解: {"intent":"ANSWER_FOLLOW_UP", "answeredSlots":[
                  {"slot":"ONSET_TIME", "normalizedValue":"昨天晚上", "confidence":0.9},
                  {"slot":"BODY_PART", "normalizedValue":"腹部", "confidence":0.95},
                  {"slot":"SYMPTOM", "normalizedValue":"疼痛", "confidence":0.95},
                  {"slot":"PAIN_CHARACTER", "normalizedValue":"胀痛", "confidence":0.9},
                  {"slot":"AGGRAVATING_FACTORS", "normalizedValue":"进食后", "confidence":0.85}
                ]}

                示例2 - 简短回答识别:
                系统: 有没有发烧?
                用户: 37.2°C
                理解: {"intent":"ANSWER_FOLLOW_UP", "answeredSlots":[{"slot":"FEVER_PRESENCE", "rawValue":"37.2°C", "normalizedValue":"是", "assertion":"PRESENT", "answersPreviousQuestion":true, "confidence":0.95}, {"slot":"TEMPERATURE", "rawValue":"37.2°C", "normalizedValue":"37.2°C", "assertion":"PRESENT", "answersPreviousQuestion":true, "confidence":0.98}], "riskSignals":[], "corrections":[]}

                示例3 - 纠正识别:
                系统: 您说是头痛对吗?
                用户: 不是头痛,是肚子疼
                理解: {"intent":"CORRECTION", "corrections":[{"target":"SLOT_VALUE", "slot":"BODY_PART", "rejectValue":"头部", "confirmValue":"腹部", "confidence":0.95}], "answeredSlots":[{"slot":"BODY_PART", "normalizedValue":"腹部"}, {"slot":"SYMPTOM", "normalizedValue":"疼痛"}]}

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

                [本轮最新用户输入]
                %s

                [最近上下文]
                %s

                [上一轮明确追问的槽位]
                %s

                [当前待补充槽位]
                %s

                [当前已知槽位状态]
                %s

                [理解本轮时请特别注意]
                1. 如果最新输入主要是在回应上一轮明确追问的槽位:
                   - 检查最新输入是否能合理回答 lastAskedSlots 中的槽位
                   - 如果能回答，设置 answersPreviousQuestion=true，并将槽位值从最新输入中提取
                   - 如果不能回答（如问频率答诱因），则识别为新的槽位，answersPreviousQuestion=false
                   - 严禁从对话历史中提取槽位值，只能从本轮最新输入中提取
                2. 如果最新输入是在修正当前已知槽位状态里的既有值,优先理解为 slot correction,而不是 new complaint
                3. 只有当用户明确提出新的主诉或替换原主诉时,才把 intent 识别为 NEW_COMPLAINT
                4. 你的任务是忠实描述本轮新增语义,不要重写既往状态,也不要为了凑字段而编造信息
                5. 关键原则: 宁可多提取,不要漏提取.只要用户提供了槽位相关的信息,就应该在 answeredSlots 中体现
                """.formatted(sessionId, latestUserTurn, transcript, lastAskedSlotsJson, pendingSlotsJson, slotStateJson);
    }
}
