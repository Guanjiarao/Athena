

package com.nageoffer.ai.ragent.triage.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分诊评测用例加载器
 */
@Slf4j
@Component
public class TriageCaseLoader {

    private static final Pattern CASE_PATTERN = Pattern.compile("## 用例(\\d+)：(.+?)（(.+?)）");
    private static final Pattern SYSTEM_PATTERN = Pattern.compile("\\*\\*系统分类\\*\\*：(.+)");
    private static final Pattern INPUT_PATTERN = Pattern.compile("\\*\\*输入\\*\\*：\\s*```\\s*([^`]+)```", Pattern.DOTALL);
    private static final Pattern DIALOGUE_PATTERN = Pattern.compile("\\*\\*标准对话\\*\\*：\\s*```\\s*([^`]+)```", Pattern.DOTALL);
    private static final Pattern CRITERIA_ROW_PATTERN = Pattern.compile("\\| (.+?) \\| (.+?) \\| \\d+ \\|");

    /**
     * 加载所有测试用例
     */
    public List<TriageEvalCase> loadAllCases() {
        Path root = resolveProjectRoot();
        Path filePath = root.resolve("bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/预分诊测试用例/预分诊评测集_大模型打分版本.md");

        try {
            String content = Files.readString(filePath);
            return parseCases(content);
        } catch (IOException ex) {
            throw new IllegalStateException("加载分诊评测集失败: " + filePath, ex);
        }
    }

    /**
     * 解析测试用例
     */
    private List<TriageEvalCase> parseCases(String content) {
        List<TriageEvalCase> cases = new ArrayList<>();
        String[] sections = content.split("---");

        for (String section : sections) {
            if (section.trim().isEmpty() || !section.contains("## 用例")) {
                continue;
            }

            try {
                TriageEvalCase evalCase = parseCase(section);
                if (evalCase != null) {
                    cases.add(evalCase);
                }
            } catch (Exception ex) {
                log.warn("解析用例失败，跳过该用例", ex);
            }
        }

        log.info("成功加载 {} 个分诊测试用例", cases.size());
        return cases;
    }

    /**
     * 解析单个用例
     */
    private TriageEvalCase parseCase(String section) {
        Matcher caseMatcher = CASE_PATTERN.matcher(section);
        if (!caseMatcher.find()) {
            return null;
        }

        String caseId = caseMatcher.group(1);
        String diseaseName = caseMatcher.group(2);
        String riskLabel = caseMatcher.group(3);

        Matcher systemMatcher = SYSTEM_PATTERN.matcher(section);
        String systemCategory = systemMatcher.find() ? systemMatcher.group(1).trim() : "";

        Matcher inputMatcher = INPUT_PATTERN.matcher(section);
        String userInput = inputMatcher.find() ? inputMatcher.group(1).trim() : "";

        List<TriageEvalCase.DialogueTurn> dialogue = parseDialogue(section);
        TriageEvalCriteria criteria = parseCriteria(section);

        return TriageEvalCase.builder()
                .caseId(caseId)
                .diseaseName(diseaseName)
                .riskLabel(riskLabel)
                .systemCategory(systemCategory)
                .userInput(userInput)
                .standardDialogue(dialogue)
                .criteria(criteria)
                .build();
    }

    /**
     * 解析标准对话
     */
    private List<TriageEvalCase.DialogueTurn> parseDialogue(String section) {
        List<TriageEvalCase.DialogueTurn> turns = new ArrayList<>();
        Matcher dialogueMatcher = DIALOGUE_PATTERN.matcher(section);

        if (!dialogueMatcher.find()) {
            return turns;
        }

        String dialogueContent = dialogueMatcher.group(1);
        String[] lines = dialogueContent.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("系统：")) {
                turns.add(TriageEvalCase.DialogueTurn.builder()
                        .role("system")
                        .content(line.substring(3).trim())
                        .build());
            } else if (line.startsWith("用户：")) {
                turns.add(TriageEvalCase.DialogueTurn.builder()
                        .role("user")
                        .content(line.substring(3).trim())
                        .build());
            }
        }

        return turns;
    }

    /**
     * 解析评分标准
     */
    private TriageEvalCriteria parseCriteria(String section) {
        TriageEvalCriteria.TriageEvalCriteriaBuilder builder = TriageEvalCriteria.builder();

        Matcher matcher = CRITERIA_ROW_PATTERN.matcher(section);
        while (matcher.find()) {
            String dimension = matcher.group(1).trim();
            String answer = matcher.group(2).trim();

            switch (dimension) {
                case "风险等级" -> builder.riskLevel(answer);
                case "建议科室" -> builder.department(answer);
                case "主诉提炼" -> builder.chiefComplaint(answer);
                case "症状提取" -> builder.symptoms(answer);
                case "风险分析" -> builder.riskAnalysis(answer);
                case "行动建议" -> builder.actionAdvice(answer);
            }
        }

        return builder.build();
    }

    /**
     * 解析项目根目录
     */
    private Path resolveProjectRoot() {
        String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory != null && !multiModuleProjectDirectory.isBlank()) {
            return Paths.get(multiModuleProjectDirectory);
        }
        return Paths.get("").toAbsolutePath().normalize().getParent();
    }
}
