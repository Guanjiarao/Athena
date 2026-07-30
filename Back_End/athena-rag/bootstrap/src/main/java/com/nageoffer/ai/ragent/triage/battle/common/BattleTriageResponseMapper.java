

package com.nageoffer.ai.ragent.triage.battle.common;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 battle 基线结果适配为现有前端可识别的统一响应协议。
 */
public final class BattleTriageResponseMapper {

    private BattleTriageResponseMapper() {
    }

    public static TriageAnalyzeResponse toResponse(String sessionId, BattleTriageResult result) {
        return toResponse(sessionId, "battle", result);
    }

    public static TriageAnalyzeResponse toResponse(String sessionId, String baseline, BattleTriageResult result) {
        return toResponse(sessionId, baseline, result, null);
    }

    public static TriageAnalyzeResponse toResponse(String sessionId, String baseline, BattleTriageResult result, Long elapsedMillis) {
        if (result == null) {
            return TriageAnalyzeResponse.builder()
                    .action("ASK_CLARIFICATION")
                    .message("分诊基线未返回有效结果，请重新描述主要症状。")
                    .riskLevel(0)
                    .data(buildFallbackData(sessionId, baseline, elapsedMillis))
                    .build();
        }
        String action = normalizeAction(result.getAction());
        return TriageAnalyzeResponse.builder()
                .action(action)
                .message(result.getMessage())
                .riskLevel(result.getRiskLevel() == null ? 0 : result.getRiskLevel())
                .data(buildData(sessionId, baseline, result, action, elapsedMillis))
                .build();
    }

    private static String normalizeAction(String action) {
        if ("GENERATE_REPORT".equalsIgnoreCase(action)) {
            return "GENERATE_REPORT";
        }
        if ("WARN".equalsIgnoreCase(action)) {
            return "WARN";
        }
        return "ASK_CLARIFICATION";
    }

    private static Map<String, Object> buildFallbackData(String sessionId, String baseline, Long elapsedMillis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("baseline", baseline);
        putTiming(data, elapsedMillis);
        return data;
    }

    private static Map<String, Object> buildData(String sessionId, String baseline, BattleTriageResult result, String action, Long elapsedMillis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("baseline", baseline);
        putTiming(data, elapsedMillis);
        data.put("extractedSymptoms", result.getExtractedSymptoms());
        data.put("missingFields", result.getMissingFields());
        data.put("evidence", result.getEvidence());
        if ("ASK_CLARIFICATION".equals(action)) {
            data.put("questions", result.getQuestions());
            data.put("followUpQuestion", result.getMessage());
            return data;
        }
        data.put("report", result.getReport());
        data.put("recommendedDepartment", result.getRecommendedDepartment());
        data.put("departmentReason", result.getDepartmentReason());
        return data;
    }

    private static void putTiming(Map<String, Object> data, Long elapsedMillis) {
        if (elapsedMillis == null) {
            return;
        }
        data.put("elapsedMillis", elapsedMillis);
        data.put("elapsedSeconds", elapsedMillis / 1000.0D);
    }
}
