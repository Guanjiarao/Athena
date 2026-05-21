

package com.nageoffer.ai.ragent.triage.service;

import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;

/**
 * 就医助手编排服务。
 */
public interface TriageOrchestratorService {

    /**
     * 执行一轮完整的 Orchestrator-Worker 分诊分析。
     */
    TriageAnalyzeResponse analyze(TriageAnalyzeRequest request);
}
