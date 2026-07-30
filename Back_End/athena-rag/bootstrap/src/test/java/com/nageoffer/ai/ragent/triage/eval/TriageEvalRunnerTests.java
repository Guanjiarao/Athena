

package com.nageoffer.ai.ragent.triage.eval;

import com.nageoffer.ai.ragent.framework.distributedid.SnowflakeIdInitializer;
import com.nageoffer.ai.ragent.knowledge.config.SemaphoreInitializer;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import com.nageoffer.ai.ragent.rag.service.ratelimit.ChatQueueLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * 分诊评测运行器
 */
@Slf4j
@SpringBootTest
@Import({TestRedisConfig.class, TriageEvalTestConfig.class})
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TriageEvalRunnerTests {

    @MockBean
    private StreamTaskManager streamTaskManager;

    @MockBean
    private SemaphoreInitializer semaphoreInitializer;

    @MockBean
    private ChatQueueLimiter chatQueueLimiter;

    @MockBean
    private SnowflakeIdInitializer snowflakeIdInitializer;

    // Mock RAG 相关的服务，避免数据库依赖
    @MockBean(name = "queryTermMappingService")
    private com.nageoffer.ai.ragent.rag.core.rewrite.QueryTermMappingService queryTermMappingService;

    // Mock 分诊会话记录 Mapper，避免数据库依赖
    @MockBean
    private com.nageoffer.ai.ragent.triage.session.dao.TriageSessionRecordMapper triageSessionRecordMapper;

    private final TriageCaseLoader caseLoader;
    private final TriageInvoker invoker;
    private final TriageLLMJudge judge;
    private final TriageReportWriter reportWriter;

    /**
     * 运行所有测试用例
     */
    @Test
    public void runAllCases() {
        List<TriageEvalCase> cases = caseLoader.loadAllCases();
        Assertions.assertFalse(cases.isEmpty(), "测试用例不能为空");

        log.info("开始运行分诊评测，共 {} 个用例", cases.size());

        List<TriageEvalResult> results = cases.stream()
                .map(this::executeCase)
                .toList();

        TriageEvalReport report = buildReport("triage-eval-all", results);
        logReport(report);
        reportWriter.writeReport(report);

        log.info("评测完成！通过率: {}/{} ({:.1f}%)",
                report.getPassed(), report.getTotal(),
                report.getPassed() * 100.0 / report.getTotal());
    }

    /**
     * 运行前10个测试用例（快速验证）
     */
    @Test
    public void runSmokeCases() {
        List<TriageEvalCase> allCases = caseLoader.loadAllCases();
        List<TriageEvalCase> smokeCases = allCases.stream().limit(10).toList();

        log.info("开始运行快速验证，共 {} 个用例", smokeCases.size());

        List<TriageEvalResult> results = smokeCases.stream()
                .map(this::executeCase)
                .toList();

        TriageEvalReport report = buildReport("triage-eval-smoke", results);
        logReport(report);
        reportWriter.writeReport(report);
    }

    /**
     * 运行 Group A 测试用例（用例 001-005）
     */
    @Test
    public void runGroupA() {
        log.info("=== 开始运行 Group A 测试（用例 001-005）===");

        List<TriageEvalCase> allCases = caseLoader.loadAllCases();
        List<TriageEvalCase> groupACases = allCases.stream()
                .filter(c -> {
                    int caseNum = Integer.parseInt(c.getCaseId());
                    return caseNum >= 1 && caseNum <= 5;
                })
                .toList();

        log.info("Group A 用例数量: {}", groupACases.size());

        List<TriageEvalResult> results = groupACases.stream()
                .map(this::executeCase)
                .toList();

        TriageEvalReport report = buildReport("triage-eval-groupA", results);
        logReport(report);
        reportWriter.writeReport(report);

        log.info("=== Group A 测试完成 ===");
        log.info("平均分: {:.2f}", report.getAverageScore());
        log.info("通过率: {:.1f}%", report.getPassed() * 100.0 / report.getTotal());
    }

    /**
     * 运行 Group B 测试用例（用例 006-010）
     */
    @Test
    public void runGroupB() {
        log.info("=== 开始运行 Group B 测试（用例 006-010）===");

        List<TriageEvalCase> allCases = caseLoader.loadAllCases();
        List<TriageEvalCase> groupBCases = allCases.stream()
                .filter(c -> {
                    int caseNum = Integer.parseInt(c.getCaseId());
                    return caseNum >= 6 && caseNum <= 10;
                })
                .toList();

        log.info("Group B 用例数量: {}", groupBCases.size());

        List<TriageEvalResult> results = groupBCases.stream()
                .map(this::executeCase)
                .toList();

        TriageEvalReport report = buildReport("triage-eval-groupB", results);
        logReport(report);
        reportWriter.writeReport(report);

        log.info("=== Group B 测试完成 ===");
        log.info("平均分: {:.2f}", report.getAverageScore());
        log.info("通过率: {:.1f}%", report.getPassed() * 100.0 / report.getTotal());
    }

    /**
     * 调试单个用例（用例001：急性肠胃炎）
     */
    @Test
    public void debugSingleCase() throws Exception {
        // 加载用例 001
        List<TriageEvalCase> allCases = caseLoader.loadAllCases();
        TriageEvalCase testCase = allCases.stream()
                .filter(c -> "001".equals(c.getCaseId()))
                .findFirst()
                .orElseThrow();

        System.out.println("=== 调试用例 001: " + testCase.getDiseaseName() + " ===");
        System.out.println("用户输入: " + testCase.getUserInput());

        // 运行完整对话
        TriageInvoker.InvokeResult result = invoker.invoke(testCase, Integer.MAX_VALUE);

        System.out.println("\n=== 对话记录 ===");
        System.out.println(result.getConversationLog());

        System.out.println("\n=== 最终报告 ===");
        if (result.getFinalReport() != null) {
            System.out.println(result.getFinalReport());
        } else {
            System.out.println("未生成报告");
        }
    }

    /**
     * 执行单个用例
     */
    private TriageEvalResult executeCase(TriageEvalCase evalCase) {
        log.info("执行用例 {}: {}", evalCase.getCaseId(), evalCase.getDiseaseName());

        try {
            // 1. 调用分诊系统
            TriageEvalResult result = invoker.invoke(evalCase);

            // 2. LLM Judge 打分
            result = judge.judge(evalCase, result);

            log.info("用例 {} 完成，总分: {}", evalCase.getCaseId(), result.getTotalScore());
            return result;

        } catch (Exception ex) {
            log.error("用例 {} 执行失败", evalCase.getCaseId(), ex);
            return TriageEvalResult.builder()
                    .caseId(evalCase.getCaseId())
                    .diseaseName(evalCase.getDiseaseName())
                    .userInput(evalCase.getUserInput())
                    .status("error")
                    .errorMessage(ex.getMessage())
                    .totalScore(0)
                    .build();
        }
    }

    /**
     * 构建评测报告
     */
    private TriageEvalReport buildReport(String suiteName, List<TriageEvalResult> results) {
        int passed = 0;
        int failed = 0;
        int errors = 0;
        int totalScore = 0;
        int maxScore = 0;
        int minScore = 100;

        int sumRiskLevel = 0;
        int sumDepartment = 0;
        int sumChiefComplaint = 0;
        int sumSymptoms = 0;
        int sumRiskAnalysis = 0;
        int sumActionAdvice = 0;

        for (TriageEvalResult result : results) {
            switch (result.getStatus()) {
                case "pass" -> passed++;
                case "fail" -> failed++;
                case "error" -> errors++;
            }

            totalScore += result.getTotalScore();
            maxScore = Math.max(maxScore, result.getTotalScore());
            minScore = Math.min(minScore, result.getTotalScore());

            if (result.getScores() != null) {
                sumRiskLevel += result.getScores().getRiskLevelScore();
                sumDepartment += result.getScores().getDepartmentScore();
                sumChiefComplaint += result.getScores().getChiefComplaintScore();
                sumSymptoms += result.getScores().getSymptomsScore();
                sumRiskAnalysis += result.getScores().getRiskAnalysisScore();
                sumActionAdvice += result.getScores().getActionAdviceScore();
            }
        }

        TriageEvalScore averageScores = TriageEvalScore.builder()
                .riskLevelScore(sumRiskLevel)
                .departmentScore(sumDepartment)
                .chiefComplaintScore(sumChiefComplaint)
                .symptomsScore(sumSymptoms)
                .riskAnalysisScore(sumRiskAnalysis)
                .actionAdviceScore(sumActionAdvice)
                .build();

        return TriageEvalReport.builder()
                .suiteName(suiteName)
                .total(results.size())
                .passed(passed)
                .failed(failed)
                .errors(errors)
                .averageScore(totalScore / (double) results.size())
                .maxScore(maxScore)
                .minScore(minScore)
                .averageScores(averageScores)
                .results(results)
                .build();
    }

    /**
     * 打印报告摘要
     */
    private void logReport(TriageEvalReport report) {
        log.info("\n===== 分诊评测报告 =====\n"
                        + "测试套件: {}\n"
                        + "总用例数: {}\n"
                        + "通过: {} ({:.1f}%)\n"
                        + "失败: {} ({:.1f}%)\n"
                        + "错误: {} ({:.1f}%)\n"
                        + "平均分: {:.2f}\n"
                        + "最高分: {}\n"
                        + "最低分: {}\n"
                        + "=======================",
                report.getSuiteName(),
                report.getTotal(),
                report.getPassed(), report.getPassed() * 100.0 / report.getTotal(),
                report.getFailed(), report.getFailed() * 100.0 / report.getTotal(),
                report.getErrors(), report.getErrors() * 100.0 / report.getTotal(),
                report.getAverageScore(),
                report.getMaxScore(),
                report.getMinScore());
    }
}
