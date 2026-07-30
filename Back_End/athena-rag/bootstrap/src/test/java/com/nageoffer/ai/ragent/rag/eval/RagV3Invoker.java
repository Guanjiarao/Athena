

package com.nageoffer.ai.ragent.rag.eval;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.rag.service.RAGChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * RAG V3 主链路调用适配器
 */
@Component
@RequiredArgsConstructor
public class RagV3Invoker {

    private static final long DEFAULT_TIMEOUT_SECONDS = 90L;

    private final RAGChatService ragChatService;

    public RagV3InvokeResult invoke(RagV3EvalCase evalCase) {
        String conversationId = StrUtil.blankToDefault(evalCase.getConversationId(), IdUtil.getSnowflakeNextIdStr());
        Boolean deepThinking = evalCase.getDeepThinking();

        try {
            UserContext.set(LoginUser.builder()
                    .userId("rag-eval-user")
                    .username("rag-eval")
                    .role("admin")
                    .build());

            ragChatService.streamChat(
                    evalCase.getQuestion(),
                    conversationId,
                    deepThinking,
                    new SseEmitter(DEFAULT_TIMEOUT_SECONDS * 1000L)
            );

            return RagV3InvokeResult.builder()
                    .conversationId(conversationId)
                    .answer("")
                    .referenceTitles(List.of())
                    .build();
        } finally {
            UserContext.clear();
        }
    }

    @lombok.Builder
    public record RagV3InvokeResult(String conversationId,
                                    String taskId,
                                    String answer,
                                    List<String> referenceTitles) {
    }

}
