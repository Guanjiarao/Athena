

package com.nageoffer.ai.ragent.triage.battle.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * battle 约定：多轮对话中每次追问都直接回答第一个选项。
 */
@Component
@RequiredArgsConstructor
public class FirstOptionAnswerStrategy {

    private final ObjectMapper objectMapper;

    public String answer(TriageAnalyzeResponse response) {
        JsonNode root = objectMapper.valueToTree(response.getData());
        String optionLabel = firstOptionLabel(root);
        if (optionLabel != null && !optionLabel.isBlank()) {
            return optionLabel;
        }
        String question = firstQuestionText(root);
        if (question != null && !question.isBlank()) {
            return "不清楚";
        }
        return "不清楚";
    }

    private String firstOptionLabel(JsonNode root) {
        JsonNode questions = root.path("questions");
        if (questions.isArray()) {
            for (JsonNode question : questions) {
                String label = firstOptionLabelFromOptions(question.path("options"));
                if (label != null) {
                    return label;
                }
            }
        }
        JsonNode questionPlanOptions = root.path("questionPlan").path("options");
        String label = firstOptionLabelFromOptions(questionPlanOptions);
        if (label != null) {
            return label;
        }
        return firstOptionLabelFromOptions(root.path("options"));
    }

    private String firstOptionLabelFromOptions(JsonNode options) {
        if (!options.isArray() || options.isEmpty()) {
            return null;
        }
        JsonNode first = options.get(0);
        String label = first.path("label").asText(null);
        if (label != null && !label.isBlank()) {
            return label;
        }
        String value = first.path("value").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String firstQuestionText(JsonNode root) {
        JsonNode questions = root.path("questions");
        if (questions.isArray() && !questions.isEmpty()) {
            String question = questions.get(0).path("question").asText(null);
            if (question != null && !question.isBlank()) {
                return question;
            }
        }
        String followUpQuestion = root.path("followUpQuestion").asText(null);
        if (followUpQuestion != null && !followUpQuestion.isBlank()) {
            return followUpQuestion;
        }
        return root.path("questionPlan").path("question").asText(null);
    }
}
