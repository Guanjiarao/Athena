

package com.nageoffer.ai.ragent.triage.eval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 分诊评测组件单元测试
 */
@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TriageEvalComponentTests {

    private final TriageCaseLoader caseLoader;
    private final TriageInvoker invoker;

    @Test
    public void testCaseLoader() {
        List<TriageEvalCase> cases = caseLoader.loadAllCases();

        Assertions.assertNotNull(cases);
        Assertions.assertEquals(100, cases.size(), "应该加载100个测试用例");

        TriageEvalCase firstCase = cases.get(0);
        log.info("第一个用例: {}", firstCase.getCaseId());
        log.info("  疾病名称: {}", firstCase.getDiseaseName());
        log.info("  风险等级: {}", firstCase.getRiskLabel());
        log.info("  用户输入: {}", firstCase.getUserInput());
        log.info("  对话轮次: {}", firstCase.getStandardDialogue().size());

        Assertions.assertNotNull(firstCase.getCaseId());
        Assertions.assertNotNull(firstCase.getDiseaseName());
        Assertions.assertNotNull(firstCase.getUserInput());
        Assertions.assertNotNull(firstCase.getCriteria());
        Assertions.assertFalse(firstCase.getStandardDialogue().isEmpty());
    }

    @Test
    public void testInvokerWithSingleCase() {
        List<TriageEvalCase> cases = caseLoader.loadAllCases();
        TriageEvalCase firstCase = cases.get(0);

        log.info("测试调用分诊系统: 用例{} - {}", firstCase.getCaseId(), firstCase.getDiseaseName());

        TriageEvalResult result = invoker.invoke(firstCase);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(firstCase.getCaseId(), result.getCaseId());
        Assertions.assertNotNull(result.getActualResponse());

        log.info("调用结果状态: {}", result.getStatus());
        log.info("实际响应长度: {} 字符", result.getActualResponse().length());
        log.info("实际响应预览:\n{}", result.getActualResponse().substring(0, Math.min(500, result.getActualResponse().length())));
    }
}
