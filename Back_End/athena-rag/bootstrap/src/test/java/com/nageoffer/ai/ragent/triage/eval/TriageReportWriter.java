

package com.nageoffer.ai.ragent.triage.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 分诊评测报告生成器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriageReportWriter {

    private final ObjectMapper objectMapper;
    private final TriageHtmlReportWriter htmlReportWriter;

    /**
     * 生成并写入评测报告
     */
    public void writeReport(TriageEvalReport report) {
        try {
            Path reportDir = resolveReportDir();
            Files.createDirectories(reportDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("triage_eval_report_%s.json", timestamp);
            Path reportFile = reportDir.resolve(fileName);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), report);
            log.info("评测报告已生成: {}", reportFile.toAbsolutePath());

            // 同时生成可读的文本报告
            writeTextReport(report, reportDir, timestamp);

            // 生成 HTML 报告
            htmlReportWriter.writeHtmlReport(report, reportDir, timestamp);

        } catch (IOException ex) {
            log.error("生成评测报告失败", ex);
        }
    }

    /**
     * 生成文本格式报告
     */
    private void writeTextReport(TriageEvalReport report, Path reportDir, String timestamp) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("=".repeat(80)).append("\n");
        sb.append("分诊系统评测报告\n");
        sb.append("=".repeat(80)).append("\n\n");

        sb.append("测试套件: ").append(report.getSuiteName()).append("\n");
        sb.append("生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // 计算加权总分、过程分、结果分的平均值
        double averageWeightedScore = report.getResults().stream()
                .mapToDouble(r -> r.getWeightedScore() != null ? r.getWeightedScore() : 0.0)
                .average()
                .orElse(0.0);

        double averageProcessScore = report.getResults().stream()
                .mapToDouble(r -> r.getProcessScore() != null ? r.getProcessScore() : 0)
                .average()
                .orElse(0.0);

        double averageOutcomeScore = report.getResults().stream()
                .mapToDouble(r -> r.getOutcomeScore() != null ? r.getOutcomeScore() : 0)
                .average()
                .orElse(0.0);

        sb.append("【总体统计】\n");
        sb.append(String.format("  总用例数: %d\n", report.getTotal()));
        sb.append(String.format("  通过数: %d (%.1f%%)\n", report.getPassed(), report.getPassed() * 100.0 / report.getTotal()));
        sb.append(String.format("  失败数: %d (%.1f%%)\n", report.getFailed(), report.getFailed() * 100.0 / report.getTotal()));
        sb.append(String.format("  错误数: %d (%.1f%%)\n", report.getErrors(), report.getErrors() * 100.0 / report.getTotal()));
        sb.append(String.format("  平均加权总分: %.1f / 100\n", averageWeightedScore));
        sb.append(String.format("  平均过程分: %.1f / 100\n", averageProcessScore));
        sb.append(String.format("  平均结果分: %.1f / 100\n\n", averageOutcomeScore));

        // 计算过程评分各维度平均分
        if (report.getAverageScores() != null) {
            double avgMemoryConsistency = report.getResults().stream()
                    .mapToInt(r -> r.getScores() != null && r.getScores().getMemoryConsistencyScore() != null
                            ? r.getScores().getMemoryConsistencyScore() : 0)
                    .average()
                    .orElse(0.0);

            double avgInformationCompleteness = report.getResults().stream()
                    .mapToInt(r -> r.getScores() != null && r.getScores().getInformationCompletenessScore() != null
                            ? r.getScores().getInformationCompletenessScore() : 0)
                    .average()
                    .orElse(0.0);

            double avgConversationTurns = report.getResults().stream()
                    .mapToInt(r -> r.getScores() != null && r.getScores().getConversationTurnsScore() != null
                            ? r.getScores().getConversationTurnsScore() : 0)
                    .average()
                    .orElse(0.0);

            double avgLogicCoherence = report.getResults().stream()
                    .mapToInt(r -> r.getScores() != null && r.getScores().getLogicCoherenceScore() != null
                            ? r.getScores().getLogicCoherenceScore() : 0)
                    .average()
                    .orElse(0.0);

            double avgOptionQuality = report.getResults().stream()
                    .mapToInt(r -> r.getScores() != null && r.getScores().getOptionQualityScore() != null
                            ? r.getScores().getOptionQualityScore() : 0)
                    .average()
                    .orElse(0.0);

            sb.append("【过程评分各维度平均分】\n");
            sb.append(String.format("  幻觉/记忆一致性: %.1f / 40\n", avgMemoryConsistency));
            sb.append(String.format("  信息完整度: %.1f / 20\n", avgInformationCompleteness));
            sb.append(String.format("  对话轮次合理性: %.1f / 15\n", avgConversationTurns));
            sb.append(String.format("  逻辑连贯性: %.1f / 15\n", avgLogicCoherence));
            sb.append(String.format("  选项推送率: %.1f / 10\n\n", avgOptionQuality));

            sb.append("【结果评分各维度平均分】\n");
            sb.append(String.format("  风险等级: %.1f / 20\n", report.getAverageScores().getRiskLevelScore() / (double) report.getTotal()));
            sb.append(String.format("  建议科室: %.1f / 15\n", report.getAverageScores().getDepartmentScore() / (double) report.getTotal()));
            sb.append(String.format("  主诉提炼: %.1f / 15\n", report.getAverageScores().getChiefComplaintScore() / (double) report.getTotal()));
            sb.append(String.format("  症状提取: %.1f / 20\n", report.getAverageScores().getSymptomsScore() / (double) report.getTotal()));
            sb.append(String.format("  风险分析: %.1f / 15\n", report.getAverageScores().getRiskAnalysisScore() / (double) report.getTotal()));
            sb.append(String.format("  行动建议: %.1f / 15\n\n", report.getAverageScores().getActionAdviceScore() / (double) report.getTotal()));
        }

        sb.append("【详细结果】\n");
        sb.append("-".repeat(80)).append("\n");

        for (TriageEvalResult result : report.getResults()) {
            sb.append(String.format("用例%s: %s (%s)\n", result.getCaseId(), result.getDiseaseName(), result.getStatus()));
            sb.append(String.format("  用户输入: %s\n", result.getUserInput()));
            Integer actualTurns = result.getActualTurns();
            sb.append(String.format("  实际对话轮次: %d\n", actualTurns != null ? actualTurns : 0));
            if (result.getIsRedFlag() != null && result.getIsRedFlag()) {
                sb.append("  红旗标记: 是（豁免轮次要求）\n");
            }

            // 显示加权总分
            double weightedScore = result.getWeightedScore() != null ? result.getWeightedScore() : 0.0;
            sb.append(String.format("  加权总分: %.1f / 100\n", weightedScore));

            // 显示过程分
            Integer processScore = result.getProcessScore() != null ? result.getProcessScore() : 0;
            sb.append(String.format("  过程分: %d / 100\n", processScore));

            if (result.getScores() != null) {
                TriageEvalScore scores = result.getScores();
                sb.append(String.format("    幻觉/记忆: %d / 40\n",
                        scores.getMemoryConsistencyScore() != null ? scores.getMemoryConsistencyScore() : 0));
                sb.append(String.format("    信息完整: %d / 20\n",
                        scores.getInformationCompletenessScore() != null ? scores.getInformationCompletenessScore() : 0));
                sb.append(String.format("    轮次合理: %d / 15\n",
                        scores.getConversationTurnsScore() != null ? scores.getConversationTurnsScore() : 0));
                sb.append(String.format("    逻辑连贯: %d / 15\n",
                        scores.getLogicCoherenceScore() != null ? scores.getLogicCoherenceScore() : 0));
                sb.append(String.format("    选项推送: %d / 10\n",
                        scores.getOptionQualityScore() != null ? scores.getOptionQualityScore() : 0));
            }

            // 显示结果分
            Integer outcomeScore = result.getOutcomeScore() != null ? result.getOutcomeScore() : 0;
            sb.append(String.format("  结果分: %d / 100\n", outcomeScore));

            if (result.getScores() != null) {
                TriageEvalScore scores = result.getScores();
                sb.append(String.format("    风险等级: %d / 20\n",
                        scores.getRiskLevelScore() != null ? scores.getRiskLevelScore() : 0));
                sb.append(String.format("    建议科室: %d / 15\n",
                        scores.getDepartmentScore() != null ? scores.getDepartmentScore() : 0));
                sb.append(String.format("    主诉提炼: %d / 15\n",
                        scores.getChiefComplaintScore() != null ? scores.getChiefComplaintScore() : 0));
                sb.append(String.format("    症状提取: %d / 20\n",
                        scores.getSymptomsScore() != null ? scores.getSymptomsScore() : 0));
                sb.append(String.format("    风险分析: %d / 15\n",
                        scores.getRiskAnalysisScore() != null ? scores.getRiskAnalysisScore() : 0));
                sb.append(String.format("    行动建议: %d / 15\n",
                        scores.getActionAdviceScore() != null ? scores.getActionAdviceScore() : 0));
            }

            if (result.getErrorMessage() != null) {
                sb.append(String.format("  错误信息: %s\n", result.getErrorMessage()));
            }

            sb.append("-".repeat(80)).append("\n");
        }

        String textFileName = String.format("triage_eval_report_%s.txt", timestamp);
        Path textFile = reportDir.resolve(textFileName);
        Files.writeString(textFile, sb.toString());
        log.info("文本报告已生成: {}", textFile.toAbsolutePath());
    }

    /**
     * 解析报告目录
     */
    private Path resolveReportDir() {
        String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory != null && !multiModuleProjectDirectory.isBlank()) {
            return Paths.get(multiModuleProjectDirectory).resolve("eval-reports");
        }
        return Paths.get("").toAbsolutePath().normalize().getParent().resolve("eval-reports");
    }
}
