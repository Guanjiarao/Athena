

package com.nageoffer.ai.ragent.triage.battle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行时 battle 用例加载器。
 *
 * <p>用例独立维护在 {@code src/main/resources/triage-battle-cases.json}，不再依赖原预分诊评测集。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BattleCaseLoader {

    private static final String CASE_RESOURCE = "triage-battle-cases.json";

    private final ObjectMapper objectMapper;

    public List<BattleCase> loadAllCases() {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(CASE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("battle 用例资源不存在: " + CASE_RESOURCE);
            }
            List<BattleCase> cases = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
            List<BattleCase> validCases = cases == null ? new ArrayList<>() : cases.stream()
                    .filter(battleCase -> battleCase.getUserInput() != null && !battleCase.getUserInput().isBlank())
                    .toList();
            log.info("成功加载 {} 个 battle 用例", validCases.size());
            return validCases;
        } catch (IOException ex) {
            throw new IllegalStateException("加载 battle 用例资源失败: " + CASE_RESOURCE, ex);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BattleCase {
        private String caseId;
        private String diseaseName;
        private String riskLabel;
        private String systemCategory;
        private String userInput;
        private String standardDialogue;
        private BattleCriteria criteria;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BattleCriteria {
        private String riskLevel;
        private String department;
        private String chiefComplaint;
        private String symptoms;
        private String riskAnalysis;
        private String actionAdvice;
    }
}
