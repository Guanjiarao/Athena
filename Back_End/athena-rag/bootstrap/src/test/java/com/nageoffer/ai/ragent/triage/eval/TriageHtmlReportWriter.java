

package com.nageoffer.ai.ragent.triage.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分诊评测 HTML 报告生成器
 */
@Slf4j
@Component
public class TriageHtmlReportWriter {

    /**
     * 生成并写入 HTML 报告
     */
    public void writeHtmlReport(TriageEvalReport report, Path reportDir, String timestamp) {
        try {
            String html = generateHtml(report);
            String fileName = String.format("triage_eval_report_%s.html", timestamp);
            Path htmlFile = reportDir.resolve(fileName);
            Files.writeString(htmlFile, html);
            log.info("HTML 报告已生成: {}", htmlFile.toAbsolutePath());
        } catch (IOException ex) {
            log.error("生成 HTML 报告失败", ex);
        }
    }

    /**
     * 生成完整的 HTML 页面
     */
    private String generateHtml(TriageEvalReport report) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>分诊系统评测报告</title>\n");
        html.append(generateStyles());
        html.append("</head>\n");
        html.append("<body>\n");

        // 页面标题
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>分诊系统评测报告</h1>\n");
        html.append("        <p class=\"subtitle\">").append(report.getSuiteName()).append("</p>\n");
        html.append("        <p class=\"timestamp\">生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
        html.append("    </div>\n");

        // 总体统计
        html.append(generateSummary(report));

        // 各维度平均分
        html.append(generateDimensionScores(report));

        // 详细结果
        html.append(generateDetailedResults(report));

        html.append(generateScripts());
        html.append("</body>\n");
        html.append("</html>");

        return html.toString();
    }

    /**
     * 生成 CSS 样式
     */
    private String generateStyles() {
        return """
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }

                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
                    background: #f5f7fa;
                    color: #333;
                    line-height: 1.6;
                    padding: 20px;
                }

                .header {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: white;
                    padding: 40px;
                    border-radius: 12px;
                    margin-bottom: 30px;
                    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                }

                .header h1 {
                    font-size: 32px;
                    margin-bottom: 10px;
                }

                .subtitle {
                    font-size: 18px;
                    opacity: 0.9;
                }

                .timestamp {
                    font-size: 14px;
                    opacity: 0.8;
                    margin-top: 10px;
                }

                .summary {
                    background: white;
                    padding: 30px;
                    border-radius: 12px;
                    margin-bottom: 30px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }

                .summary h2 {
                    font-size: 24px;
                    margin-bottom: 20px;
                    color: #667eea;
                }

                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 20px;
                    margin-top: 20px;
                }

                .stat-card {
                    background: #f8f9fa;
                    padding: 20px;
                    border-radius: 8px;
                    text-align: center;
                }

                .stat-label {
                    font-size: 14px;
                    color: #666;
                    margin-bottom: 8px;
                }

                .stat-value {
                    font-size: 28px;
                    font-weight: bold;
                    color: #333;
                }

                .stat-value.pass {
                    color: #10b981;
                }

                .stat-value.fail {
                    color: #ef4444;
                }

                .dimensions {
                    background: white;
                    padding: 30px;
                    border-radius: 12px;
                    margin-bottom: 30px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }

                .dimensions h2 {
                    font-size: 24px;
                    margin-bottom: 20px;
                    color: #667eea;
                }

                .dimension-item {
                    margin-bottom: 20px;
                }

                .dimension-header {
                    display: flex;
                    justify-content: space-between;
                    margin-bottom: 8px;
                }

                .dimension-name {
                    font-weight: 500;
                    color: #333;
                }

                .dimension-score {
                    font-weight: bold;
                    color: #667eea;
                }

                .progress-bar {
                    height: 24px;
                    background: #e5e7eb;
                    border-radius: 12px;
                    overflow: hidden;
                }

                .progress-fill {
                    height: 100%;
                    background: linear-gradient(90deg, #10b981 0%, #059669 100%);
                    transition: width 0.3s ease;
                    display: flex;
                    align-items: center;
                    justify-content: flex-end;
                    padding-right: 10px;
                    color: white;
                    font-size: 12px;
                    font-weight: bold;
                }

                .progress-fill.high {
                    background: linear-gradient(90deg, #10b981 0%, #059669 100%);
                }

                .progress-fill.medium {
                    background: linear-gradient(90deg, #f59e0b 0%, #d97706 100%);
                }

                .progress-fill.low {
                    background: linear-gradient(90deg, #ef4444 0%, #dc2626 100%);
                }

                .results {
                    background: white;
                    padding: 30px;
                    border-radius: 12px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }

                .results h2 {
                    font-size: 24px;
                    margin-bottom: 20px;
                    color: #667eea;
                }

                .result-item {
                    border: 1px solid #e5e7eb;
                    border-radius: 8px;
                    margin-bottom: 20px;
                    overflow: hidden;
                }

                .result-header {
                    background: #f8f9fa;
                    padding: 15px 20px;
                    cursor: pointer;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    transition: background 0.2s;
                }

                .result-header:hover {
                    background: #e5e7eb;
                }

                .result-title {
                    font-weight: 500;
                    font-size: 16px;
                }

                .result-status {
                    display: inline-block;
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 12px;
                    font-weight: bold;
                    margin-left: 10px;
                }

                .result-status.pass {
                    background: #d1fae5;
                    color: #065f46;
                }

                .result-status.fail {
                    background: #fee2e2;
                    color: #991b1b;
                }

                .result-status.error {
                    background: #fef3c7;
                    color: #92400e;
                }

                .result-score {
                    font-size: 18px;
                    font-weight: bold;
                    color: #667eea;
                }

                .result-body {
                    padding: 20px;
                    display: none;
                }

                .result-body.expanded {
                    display: block;
                }

                .score-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 20px;
                }

                .score-table th,
                .score-table td {
                    padding: 12px;
                    text-align: left;
                    border-bottom: 1px solid #e5e7eb;
                }

                .score-table th {
                    background: #f8f9fa;
                    font-weight: 600;
                    color: #666;
                }

                .score-table td:last-child {
                    text-align: right;
                    font-weight: bold;
                }

                .conversation {
                    background: #f8f9fa;
                    padding: 20px;
                    border-radius: 8px;
                    margin-top: 20px;
                }

                .conversation h3 {
                    font-size: 16px;
                    margin-bottom: 15px;
                    color: #667eea;
                }

                .turn {
                    margin-bottom: 15px;
                    display: flex;
                    gap: 10px;
                }

                .turn.user {
                    justify-content: flex-end;
                }

                .turn.system {
                    justify-content: flex-start;
                }

                .message-bubble {
                    max-width: 70%;
                    padding: 12px 16px;
                    border-radius: 12px;
                    position: relative;
                }

                .turn.user .message-bubble {
                    background: #667eea;
                    color: white;
                    border-bottom-right-radius: 4px;
                }

                .turn.system .message-bubble {
                    background: white;
                    color: #333;
                    border: 1px solid #e5e7eb;
                    border-bottom-left-radius: 4px;
                }

                .message-label {
                    font-size: 12px;
                    font-weight: bold;
                    margin-bottom: 4px;
                    opacity: 0.8;
                }

                .message-text {
                    font-size: 14px;
                    line-height: 1.5;
                }

                .action-tag {
                    display: inline-block;
                    padding: 2px 8px;
                    border-radius: 4px;
                    font-size: 11px;
                    font-weight: bold;
                    margin-top: 6px;
                    background: rgba(255,255,255,0.2);
                }

                .turn.system .action-tag {
                    background: #e5e7eb;
                    color: #666;
                }

                .error-message {
                    background: #fee2e2;
                    color: #991b1b;
                    padding: 12px;
                    border-radius: 8px;
                    margin-top: 15px;
                    font-size: 14px;
                }

                .toggle-icon {
                    transition: transform 0.3s;
                }

                .toggle-icon.expanded {
                    transform: rotate(180deg);
                }
            </style>
        """;
    }

    /**
     * 生成总体统计部分
     */
    private String generateSummary(TriageEvalReport report) {
        double passRate = report.getTotal() > 0 ? (report.getPassed() * 100.0 / report.getTotal()) : 0;

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

        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"summary\">\n");
        html.append("        <h2>总体统计</h2>\n");
        html.append("        <div class=\"stats-grid\">\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">总用例数</div>\n");
        html.append("                <div class=\"stat-value\">").append(report.getTotal()).append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">通过数</div>\n");
        html.append("                <div class=\"stat-value pass\">").append(report.getPassed()).append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">失败数</div>\n");
        html.append("                <div class=\"stat-value fail\">").append(report.getFailed()).append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">错误数</div>\n");
        html.append("                <div class=\"stat-value fail\">").append(report.getErrors()).append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">通过率</div>\n");
        html.append("                <div class=\"stat-value\">").append(String.format("%.1f%%", passRate)).append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">平均加权总分</div>\n");
        html.append("                <div class=\"stat-value\">").append(String.format("%.1f / 100", averageWeightedScore)).append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">平均过程分</div>\n");
        html.append("                <div class=\"stat-value\">").append(String.format("%.1f / 100", averageProcessScore)).append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-label\">平均结果分</div>\n");
        html.append("                <div class=\"stat-value\">").append(String.format("%.1f / 100", averageOutcomeScore)).append("</div>\n");
        html.append("            </div>\n");

        html.append("        </div>\n");
        html.append("    </div>\n");

        return html.toString();
    }

    /**
     * 生成各维度平均分部分
     */
    private String generateDimensionScores(TriageEvalReport report) {
        if (report.getTotal() == 0) {
            return "";
        }

        StringBuilder html = new StringBuilder();

        // 过程评分各维度
        html.append("    <div class=\"dimensions\">\n");
        html.append("        <h2>过程评分各维度平均分</h2>\n");

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

        addDimensionBar(html, "幻觉/记忆一致性", avgMemoryConsistency, 40);
        addDimensionBar(html, "信息完整度", avgInformationCompleteness, 20);
        addDimensionBar(html, "对话轮次合理性", avgConversationTurns, 15);
        addDimensionBar(html, "逻辑连贯性", avgLogicCoherence, 15);
        addDimensionBar(html, "选项推送率", avgOptionQuality, 10);

        html.append("    </div>\n");

        // 结果评分各维度
        if (report.getAverageScores() != null) {
            TriageEvalScore avgScores = report.getAverageScores();
            int total = report.getTotal();

            html.append("    <div class=\"dimensions\">\n");
            html.append("        <h2>结果评分各维度平均分</h2>\n");

            addDimensionBar(html, "风险等级", avgScores.getRiskLevelScore() / (double) total, 20);
            addDimensionBar(html, "建议科室", avgScores.getDepartmentScore() / (double) total, 15);
            addDimensionBar(html, "主诉提炼", avgScores.getChiefComplaintScore() / (double) total, 15);
            addDimensionBar(html, "症状提取", avgScores.getSymptomsScore() / (double) total, 20);
            addDimensionBar(html, "风险分析", avgScores.getRiskAnalysisScore() / (double) total, 15);
            addDimensionBar(html, "行动建议", avgScores.getActionAdviceScore() / (double) total, 15);

            html.append("    </div>\n");
        }

        return html.toString();
    }

    /**
     * 添加维度进度条
     */
    private void addDimensionBar(StringBuilder html, String name, double score, int maxScore) {
        double percentage = (score / maxScore) * 100;
        String level = percentage >= 80 ? "high" : (percentage >= 60 ? "medium" : "low");

        html.append("        <div class=\"dimension-item\">\n");
        html.append("            <div class=\"dimension-header\">\n");
        html.append("                <span class=\"dimension-name\">").append(name).append("</span>\n");
        html.append("                <span class=\"dimension-score\">").append(String.format("%.2f / %d", score, maxScore)).append("</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"progress-bar\">\n");
        html.append("                <div class=\"progress-fill ").append(level).append("\" style=\"width: ").append(String.format("%.1f%%", percentage)).append("\">\n");
        html.append("                    ").append(String.format("%.1f%%", percentage)).append("\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
    }

    /**
     * 生成详细结果部分
     */
    private String generateDetailedResults(TriageEvalReport report) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"results\">\n");
        html.append("        <h2>详细结果</h2>\n");

        for (TriageEvalResult result : report.getResults()) {
            html.append(generateResultItem(result));
        }

        html.append("    </div>\n");

        return html.toString();
    }

    /**
     * 生成单个结果项
     */
    private String generateResultItem(TriageEvalResult result) {
        StringBuilder html = new StringBuilder();

        // 计算加权总分
        double weightedScore = result.getWeightedScore() != null ? result.getWeightedScore() : 0.0;

        html.append("        <div class=\"result-item\">\n");
        html.append("            <div class=\"result-header\" onclick=\"toggleResult(this)\">\n");
        html.append("                <div>\n");
        html.append("                    <span class=\"result-title\">用例").append(result.getCaseId()).append(": ").append(escapeHtml(result.getDiseaseName())).append("</span>\n");
        html.append("                    <span class=\"result-status ").append(result.getStatus()).append("\">").append(result.getStatus().toUpperCase()).append("</span>\n");
        html.append("                </div>\n");
        html.append("                <div>\n");
        html.append("                    <span class=\"result-score\">").append(String.format("%.1f / 100", weightedScore)).append("</span>\n");
        html.append("                    <span class=\"toggle-icon\">▼</span>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"result-body\">\n");

        // 用户输入
        html.append("                <p><strong>用户输入:</strong> ").append(escapeHtml(result.getUserInput())).append("</p>\n");
        Integer actualTurns = result.getActualTurns();
        html.append("                <p><strong>实际对话轮次:</strong> ").append(actualTurns != null ? actualTurns : 0).append(" 轮</p>\n");
        if (result.getIsRedFlag() != null && result.getIsRedFlag()) {
            html.append("                <p><strong>红旗标记:</strong> <span style=\"color: #ef4444;\">是（豁免轮次要求）</span></p>\n");
        }

        // 加权总分
        html.append("                <p><strong>加权总分:</strong> ").append(String.format("%.1f / 100", weightedScore)).append("</p>\n");

        // 过程分表格
        Integer processScore = result.getProcessScore() != null ? result.getProcessScore() : 0;
        html.append("                <h4 style=\"margin-top: 20px; color: #667eea;\">过程分: ").append(processScore).append(" / 100</h4>\n");
        if (result.getScores() != null) {
            html.append("                <table class=\"score-table\">\n");
            html.append("                    <thead>\n");
            html.append("                        <tr>\n");
            html.append("                            <th>维度</th>\n");
            html.append("                            <th>得分</th>\n");
            html.append("                        </tr>\n");
            html.append("                    </thead>\n");
            html.append("                    <tbody>\n");

            TriageEvalScore scores = result.getScores();
            addScoreRow(html, "幻觉/记忆一致性", scores.getMemoryConsistencyScore() != null ? scores.getMemoryConsistencyScore() : 0, 40);
            addScoreRow(html, "信息完整度", scores.getInformationCompletenessScore() != null ? scores.getInformationCompletenessScore() : 0, 20);
            addScoreRow(html, "对话轮次合理性", scores.getConversationTurnsScore() != null ? scores.getConversationTurnsScore() : 0, 15);
            addScoreRow(html, "逻辑连贯性", scores.getLogicCoherenceScore() != null ? scores.getLogicCoherenceScore() : 0, 15);
            addScoreRow(html, "选项推送率", scores.getOptionQualityScore() != null ? scores.getOptionQualityScore() : 0, 10);

            html.append("                    </tbody>\n");
            html.append("                </table>\n");
        }

        // 结果分表格
        Integer outcomeScore = result.getOutcomeScore() != null ? result.getOutcomeScore() : 0;
        html.append("                <h4 style=\"margin-top: 20px; color: #667eea;\">结果分: ").append(outcomeScore).append(" / 100</h4>\n");
        if (result.getScores() != null) {
            html.append("                <table class=\"score-table\">\n");
            html.append("                    <thead>\n");
            html.append("                        <tr>\n");
            html.append("                            <th>维度</th>\n");
            html.append("                            <th>得分</th>\n");
            html.append("                        </tr>\n");
            html.append("                    </thead>\n");
            html.append("                    <tbody>\n");

            TriageEvalScore scores = result.getScores();
            addScoreRow(html, "风险等级", scores.getRiskLevelScore() != null ? scores.getRiskLevelScore() : 0, 20);
            addScoreRow(html, "建议科室", scores.getDepartmentScore() != null ? scores.getDepartmentScore() : 0, 15);
            addScoreRow(html, "主诉提炼", scores.getChiefComplaintScore() != null ? scores.getChiefComplaintScore() : 0, 15);
            addScoreRow(html, "症状提取", scores.getSymptomsScore() != null ? scores.getSymptomsScore() : 0, 20);
            addScoreRow(html, "风险分析", scores.getRiskAnalysisScore() != null ? scores.getRiskAnalysisScore() : 0, 15);
            addScoreRow(html, "行动建议", scores.getActionAdviceScore() != null ? scores.getActionAdviceScore() : 0, 15);

            html.append("                    </tbody>\n");
            html.append("                </table>\n");
        }

        // 对话记录
        if (result.getActualResponse() != null && !result.getActualResponse().isEmpty()) {
            html.append(generateConversation(result.getActualResponse()));
        }

        // 错误信息
        if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
            html.append("                <div class=\"error-message\">\n");
            html.append("                    <strong>错误信息:</strong> ").append(escapeHtml(result.getErrorMessage())).append("\n");
            html.append("                </div>\n");
        }

        html.append("            </div>\n");
        html.append("        </div>\n");

        return html.toString();
    }

    /**
     * 添加得分行
     */
    private void addScoreRow(StringBuilder html, String dimension, int score, int maxScore) {
        html.append("                        <tr>\n");
        html.append("                            <td>").append(dimension).append("</td>\n");
        html.append("                            <td>").append(score).append(" / ").append(maxScore).append("</td>\n");
        html.append("                        </tr>\n");
    }

    /**
     * 生成对话记录
     */
    private String generateConversation(String actualResponse) {
        StringBuilder html = new StringBuilder();
        html.append("                <div class=\"conversation\">\n");
        html.append("                    <h3>对话记录</h3>\n");

        List<ConversationTurn> turns = parseConversation(actualResponse);
        for (ConversationTurn turn : turns) {
            html.append("                    <div class=\"turn ").append(turn.speaker).append("\">\n");
            html.append("                        <div class=\"message-bubble\">\n");
            html.append("                            <div class=\"message-label\">").append(turn.speaker.equals("user") ? "用户" : "系统").append("</div>\n");
            html.append("                            <div class=\"message-text\">").append(escapeHtml(turn.message)).append("</div>\n");
            if (turn.action != null && !turn.action.isEmpty()) {
                html.append("                            <div class=\"action-tag\">").append(turn.action).append("</div>\n");
            }
            html.append("                        </div>\n");
            html.append("                    </div>\n");
        }

        html.append("                </div>\n");

        return html.toString();
    }

    /**
     * 解析对话记录
     */
    private List<ConversationTurn> parseConversation(String actualResponse) {
        List<ConversationTurn> turns = new ArrayList<>();

        // 按轮次分割
        String[] rounds = actualResponse.split("【第\\d+轮】");

        for (String round : rounds) {
            if (round.trim().isEmpty()) {
                continue;
            }

            // 提取用户输入
            Pattern userPattern = Pattern.compile("用户:\\s*(.+?)(?=\\n系统:|$)", Pattern.DOTALL);
            Matcher userMatcher = userPattern.matcher(round);

            // 提取系统回复
            Pattern systemPattern = Pattern.compile("系统:\\s*(.+?)(?=\\nAction:|$)", Pattern.DOTALL);
            Matcher systemMatcher = systemPattern.matcher(round);

            // 提取 Action
            Pattern actionPattern = Pattern.compile("Action:\\s*(.+?)(?=\\n|$)");
            Matcher actionMatcher = actionPattern.matcher(round);

            if (userMatcher.find()) {
                String userMessage = userMatcher.group(1).trim();
                turns.add(new ConversationTurn("user", userMessage, null));
            }

            if (systemMatcher.find()) {
                String systemMessage = systemMatcher.group(1).trim();
                String action = actionMatcher.find() ? actionMatcher.group(1).trim() : null;
                turns.add(new ConversationTurn("system", systemMessage, action));
            }
        }

        return turns;
    }

    /**
     * 生成 JavaScript 脚本
     */
    private String generateScripts() {
        return """
            <script>
                function toggleResult(header) {
                    const body = header.nextElementSibling;
                    const icon = header.querySelector('.toggle-icon');

                    body.classList.toggle('expanded');
                    icon.classList.toggle('expanded');
                }
            </script>
        """;
    }

    /**
     * HTML 转义
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 对话轮次
     */
    private static class ConversationTurn {
        String speaker;  // "user" or "system"
        String message;
        String action;

        ConversationTurn(String speaker, String message, String action) {
            this.speaker = speaker;
            this.message = message;
            this.action = action;
        }
    }
}
