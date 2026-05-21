

package com.nageoffer.ai.ragent.rag.controller;





import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.nageoffer.ai.ragent.rag.controller.request.MessageFeedbackRequest;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.service.MessageFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话消息反馈控制器
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "RAG接口")
public class MessageFeedbackController {

    private final MessageFeedbackService feedbackService;

    /**
     * 提交点赞/踩反馈（异步，通过 MQ 持久化）
     */
    @PostMapping("/conversations/messages/{messageId}/feedback")
    @Operation(summary = "提交点赞/踩反馈（异步，通过 MQ 持久化）")
    public Result<Void> submitFeedback(@PathVariable String messageId,
                                       @RequestBody MessageFeedbackRequest request) {
        feedbackService.submitFeedbackAsync(messageId, request);
        return Results.success();
    }
}
