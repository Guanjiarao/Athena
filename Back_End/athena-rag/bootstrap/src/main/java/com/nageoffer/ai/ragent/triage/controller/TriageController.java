

package com.nageoffer.ai.ragent.triage.controller;





import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.triage.controller.request.ClientChatCompletionRequest;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.request.VisionAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.ClientChatCompletionResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.VisionAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.service.TriageAiProxyService;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 就医助手控制器。
 *
 * <p>这里同时承担两类职责：
 * 1. 对外暴露 triage 编排入口；
 * 2. 作为前端安全收口层，提供文本/视觉代理入口，彻底下线前端密钥。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "分诊助手接口")
public class TriageController {

    private final TriageOrchestratorService triageOrchestratorService;
    private final TriageAiProxyService triageAiProxyService;

    /**
     * 就医助手主入口。
     *
     * <p>由于前端要求固定返回结构，因此这里显式兜底所有异常，
     * 避免被全局异常处理器包装成其他协议。</p>
     */
    @PostMapping("/triage/analyze")
    @Operation(summary = "就医助手主入口。")
    public TriageAnalyzeResponse analyze(@Valid @RequestBody TriageAnalyzeRequest request) {
        try {
            return triageOrchestratorService.analyze(request);
        } catch (ClientException ex) {
            log.warn("分诊请求参数错误: {}", ex.getErrorMessage());
            return TriageAnalyzeResponse.builder()
                    .action("ASK_CLARIFICATION")
                    .data(null)
                    .message(ex.getErrorMessage())
                    .riskLevel(0)
                    .build();
        } catch (Exception ex) {
            log.error("分诊接口异常", ex);
            return TriageAnalyzeResponse.builder()
                    .action("ASK_CLARIFICATION")
                    .data(null)
                    .message("系统暂时繁忙，请稍后重试，并重新补充症状、持续时间和不适部位。")
                    .riskLevel(0)
                    .build();
        }
    }

    /**
     * 文本补全代理入口。
     */
    @PostMapping("/triage/llm/complete")
    @Operation(summary = "文本补全代理入口。")
    public ClientChatCompletionResponse complete(@Valid @RequestBody ClientChatCompletionRequest request) {
        try {
            return triageAiProxyService.complete(request);
        } catch (ClientException ex) {
            log.warn("文本补全代理请求错误: {}", ex.getErrorMessage());
            return ClientChatCompletionResponse.builder()
                    .content(ex.getErrorMessage())
                    .model("server-managed")
                    .build();
        } catch (Exception ex) {
            log.error("文本补全代理异常", ex);
            return ClientChatCompletionResponse.builder()
                    .content("系统繁忙，请稍后再试。")
                    .model("server-managed")
                    .build();
        }
    }

    /**
     * 视觉分析代理入口。
     */
    @PostMapping("/triage/vision/analyze")
    @Operation(summary = "视觉分析代理入口。")
    public VisionAnalyzeResponse analyzeVision(@Valid @RequestBody VisionAnalyzeRequest request) {
        try {
            return triageAiProxyService.analyzeVision(request);
        } catch (ClientException | ServiceException ex) {
            log.warn("视觉分析代理失败: {}", ex.getErrorMessage());
            return VisionAnalyzeResponse.builder()
                    .content(ex.getErrorMessage())
                    .model(request == null ? "server-managed" : request.getModel())
                    .build();
        } catch (Exception ex) {
            log.error("视觉分析代理异常", ex);
            return VisionAnalyzeResponse.builder()
                    .content("系统繁忙，请稍后再试。")
                    .model(request == null ? "server-managed" : request.getModel())
                    .build();
        }
    }
}
