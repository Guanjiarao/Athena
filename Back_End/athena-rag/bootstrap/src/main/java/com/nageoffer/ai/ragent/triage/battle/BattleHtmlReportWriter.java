

package com.nageoffer.ai.ragent.triage.battle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * battle HTML 报告生成器。
 */
@Slf4j
@Component
public class BattleHtmlReportWriter {

    public String write(BattleRunResponse response) {
        Path reportDir = resolveProjectRoot().resolve("bootstrap/target/triage-battle-reports");
        try {
            Files.createDirectories(reportDir);
            String fileName = "battle-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".html";
            Path reportPath = reportDir.resolve(fileName);
            Files.writeString(reportPath, render(response), StandardCharsets.UTF_8);
            return reportPath.toAbsolutePath().toString();
        } catch (IOException ex) {
            log.warn("生成 battle HTML 报告失败", ex);
            return null;
        }
    }

    private String render(BattleRunResponse response) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <title>预分诊 Battle 报告</title>
                  <style>
                    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',sans-serif;margin:0;background:#f6f7fb;color:#1f2937;}
                    header{background:linear-gradient(135deg,#1f2937,#4f46e5);color:white;padding:28px 36px;}
                    h1{margin:0 0 8px;font-size:28px}.meta{opacity:.86}.wrap{padding:24px 36px}.card{background:white;border-radius:14px;box-shadow:0 8px 24px rgba(15,23,42,.08);padding:20px;margin-bottom:20px;}
                    table{width:100%;border-collapse:collapse;font-size:14px}th,td{border-bottom:1px solid #e5e7eb;padding:10px;vertical-align:top;text-align:left}th{background:#f9fafb;color:#374151}.num{text-align:right;font-variant-numeric:tabular-nums}.pill{display:inline-block;border-radius:999px;padding:3px 9px;background:#eef2ff;color:#3730a3;font-weight:600}.err{color:#b91c1c}.reason{max-width:420px}.log{white-space:pre-wrap;background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:10px;max-height:220px;overflow:auto}.case-title{font-size:18px;font-weight:700;margin-bottom:6px}.muted{color:#6b7280}.score-high{color:#047857;font-weight:700}.score-mid{color:#b45309;font-weight:700}.score-low{color:#b91c1c;font-weight:700}
                  </style>
                </head>
                <body>
                """);
        html.append("<header><h1>预分诊 Battle 报告</h1><div class=\"meta\">用例数：")
                .append(response.getCaseCount())
                .append("，Baseline 数：")
                .append(response.getBaselineCount())
                .append("，总耗时：")
                .append(response.getElapsedMillis())
                .append(" ms</div></header><div class=\"wrap\">");
        renderSummary(html, response);
        renderCases(html, response);
        html.append("</div></body></html>");
        return html.toString();
    }

    private void renderSummary(StringBuilder html, BattleRunResponse response) {
        html.append("<section class=\"card\"><h2>汇总排名</h2><table><thead><tr><th>Baseline</th><th class=\"num\">成功</th><th class=\"num\">失败</th><th class=\"num\">结果均分</th><th class=\"num\">过程均分</th><th class=\"num\">加权均分</th><th class=\"num\">平均耗时(ms)</th></tr></thead><tbody>");
        response.getSummaries().stream()
                .sorted(Comparator.comparing(BattleRunResponse.BaselineSummary::getAverageWeightedScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(summary -> html.append("<tr><td><span class=\"pill\">").append(escape(summary.getBaseline())).append("</span></td>")
                        .append("<td class=\"num\">").append(summary.getSuccessCount()).append("</td>")
                        .append("<td class=\"num\">").append(summary.getErrorCount()).append("</td>")
                        .append("<td class=\"num\">").append(fmt(summary.getAverageOutcomeScore())).append("</td>")
                        .append("<td class=\"num\">").append(fmt(summary.getAverageProcessScore())).append("</td>")
                        .append("<td class=\"num ").append(scoreClass(summary.getAverageWeightedScore())).append("\">").append(fmt(summary.getAverageWeightedScore())).append("</td>")
                        .append("<td class=\"num\">").append(fmt(summary.getAverageElapsedMillis())).append("</td></tr>"));
        html.append("</tbody></table></section>");
    }

    private void renderCases(StringBuilder html, BattleRunResponse response) {
        html.append("<section class=\"card\"><h2>用例明细</h2></section>");
        for (BattleRunResponse.CaseBattleResult caseResult : response.getCases()) {
            html.append("<section class=\"card\"><div class=\"case-title\">用例")
                    .append(escape(caseResult.getCaseId())).append("：")
                    .append(escape(caseResult.getDiseaseName())).append("</div><div class=\"muted\">系统分类：")
                    .append(escape(caseResult.getSystemCategory())).append(" ｜ 风险标签：")
                    .append(escape(caseResult.getRiskLabel())).append("</div><p><b>输入：</b>")
                    .append(escape(caseResult.getUserInput())).append("</p>");
            html.append("<table><thead><tr><th>Baseline</th><th>Action</th><th class=\"num\">风险</th><th class=\"num\">耗时(ms)</th><th class=\"num\">结果分</th><th class=\"num\">过程分</th><th class=\"num\">加权分</th><th>理由/错误</th></tr></thead><tbody>");
            for (BattleRunResponse.BaselineResult result : caseResult.getResults()) {
                BattleScore score = result.getScore();
                html.append("<tr><td><span class=\"pill\">").append(escape(result.getBaseline())).append("</span></td>")
                        .append("<td>").append(escape(result.getAction())).append("</td>")
                        .append("<td class=\"num\">").append(result.getRiskLevel() == null ? "" : result.getRiskLevel()).append("</td>")
                        .append("<td class=\"num\">").append(result.getElapsedMillis() == null ? "" : result.getElapsedMillis()).append("</td>")
                        .append("<td class=\"num\">").append(score == null ? "" : score.getOutcomeScore()).append("</td>")
                        .append("<td class=\"num\">").append(score == null ? "" : score.getProcessScore()).append("</td>")
                        .append("<td class=\"num ").append(scoreClass(score == null ? null : score.getWeightedScore())).append("\">").append(score == null ? "" : fmt(score.getWeightedScore())).append("</td>")
                        .append("<td class=\"reason\">").append(escape(result.getError() == null ? (score == null ? "" : score.getReason()) : result.getError())).append("</td></tr>");
            }
            html.append("</tbody></table>");
            for (BattleRunResponse.BaselineResult result : caseResult.getResults()) {
                html.append("<details><summary>").append(escape(result.getBaseline())).append(" 对话日志</summary><div class=\"log\">")
                        .append(escape(result.getConversationLog())).append("</div></details>");
            }
            html.append("</section>");
        }
    }

    private Path resolveProjectRoot() {
        String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory != null && !multiModuleProjectDirectory.isBlank()) {
            return Paths.get(multiModuleProjectDirectory);
        }
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("bootstrap/pom.xml"))) {
            return current;
        }
        if (current.getParent() != null && Files.exists(current.getParent().resolve("bootstrap/pom.xml"))) {
            return current.getParent();
        }
        return current;
    }

    private String fmt(Double value) {
        return value == null ? "" : String.format("%.2f", value);
    }

    private String scoreClass(Double value) {
        if (value == null) {
            return "";
        }
        if (value >= 80) {
            return "score-high";
        }
        if (value >= 60) {
            return "score-mid";
        }
        return "score-low";
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
