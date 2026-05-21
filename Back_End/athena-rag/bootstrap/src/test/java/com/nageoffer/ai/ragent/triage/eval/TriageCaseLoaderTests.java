

package com.nageoffer.ai.ragent.triage.eval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 分诊用例加载器测试
 */
@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TriageCaseLoaderTests {

    private final TriageCaseLoader caseLoader;

    @Test
    public void testLoadAllCases() {
        List<TriageEvalCase> cases = caseLoader.loadAllCases();

        Assertions.assertNotNull(cases);
        Assertions.assertFalse(cases.isEmpty(), "测试用例不能为空");
        log.info("成功加载 {} 个测试用例", cases.size());

        TriageEvalCase firstCase = cases.get(0);
        log.info("第一个用例: caseId={}, diseaseName={}, riskLabel={}, userInput={}",
                firstCase.getCaseId(),
                firstCase.getDiseaseName(),
                firstCase.getRiskLabel(),
                firstCase.getUserInput());

        Assertions.assertNotNull(firstCase.getCaseId());
        Assertions.assertNotNull(firstCase.getUserInput());
        Assertions.assertNotNull(firstCase.getCriteria());
    }
}
