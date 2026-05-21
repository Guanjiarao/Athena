

package com.nageoffer.ai.ragent.rag.controller;





import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.controller.request.AthenaAskRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AthenaAskVO;
import com.nageoffer.ai.ragent.rag.service.AthenaRagAskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向 Athena 的 RAG 问答接口。
 *
 * @deprecated 该接口为 Athena 早期从 `athena-ground` 侧接入 `athena-rag` 的过渡接口，
 * 当前主链路已切换为通过网关直接调用 RAG V3 通用问答接口，不再继续扩展。
 */
@Deprecated
@RestController
@RequiredArgsConstructor
@Tag(name = "RAG接口")
public class AthenaRagAskController {

    private final AthenaRagAskService athenaRagAskService;

    @PostMapping("/athena/rag/ask")
    @Operation(summary = "接口操作")
    public Result<AthenaAskVO> ask(@RequestBody AthenaAskRequest request) {
        return Results.success(athenaRagAskService.ask(request));
    }
}
