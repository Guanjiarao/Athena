

package com.nageoffer.ai.ragent.triage.prompt;

import java.util.List;

public final class SopValidatorPromptTemplates {

    private SopValidatorPromptTemplates() {
    }

    public static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "array",
              "items": { "type": "string" }
            }
            """;

    public static String systemPrompt() {
        return """
                你是医疗分诊系统中的“SOP 校验 Worker”，只负责找出当前病历里缺失的关键信息。
                你必须依据给定的医疗问诊规则，检查“原始用户输入”和“结构化症状”中是否已经出现这些字段。

                严格要求：
                1. 只输出缺失字段名的 JSON 数组，例如 ["腹痛位置","是否伴随发热"]。
                2. 如果信息已经齐全，输出 []。
                3. 不要输出解释，不要输出 Markdown，不要输出对象。
                4. 如果同一字段被不同规则重复命中，只保留一次。

                JSON Schema:
                """ + OUTPUT_JSON_SCHEMA;
    }

    public static String userPrompt(String sessionId,
                                    String userInput,
                                    String symptomsJson,
                                    List<String> matchedChecklist) {
        return """
                会话ID: %s
                原始用户输入:
                %s

                结构化症状:
                %s

                当前命中的问诊 SOP:
                %s

                请仅返回仍然缺失的字段名 JSON 数组。
                """.formatted(sessionId, userInput, symptomsJson, renderChecklist(matchedChecklist));
    }

    private static String renderChecklist(List<String> matchedChecklist) {
        if (matchedChecklist == null || matchedChecklist.isEmpty()) {
            return "- 无";
        }
        return matchedChecklist.stream()
                .map(item -> "- " + item)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("- 无");
    }
}
