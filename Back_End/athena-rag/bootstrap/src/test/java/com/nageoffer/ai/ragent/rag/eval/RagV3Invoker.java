/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.eval;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.dto.ChatReferencePayload;
import com.nageoffer.ai.ragent.rag.service.RAGChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * RAG V3 主链路调用适配器
 */
@Component
@RequiredArgsConstructor
public class RagV3Invoker {

    private static final long DEFAULT_TIMEOUT_SECONDS = 90L;

    private final RAGChatService ragChatService;

    public RagV3InvokeResult invoke(RagV3EvalCase evalCase) {
        CollectingStreamCallback callback = new CollectingStreamCallback();
        String conversationId = StrUtil.blankToDefault(evalCase.getConversationId(), IdUtil.getSnowflakeNextIdStr());
        Boolean deepThinking = evalCase.getDeepThinking();

        try {
            UserContext.set(LoginUser.builder()
                    .userId("rag-eval-user")
                    .username("rag-eval")
                    .role("admin")
                    .build());

            RAGChatService.ChatStreamSession session = ragChatService.streamChat(
                    evalCase.getQuestion(),
                    conversationId,
                    deepThinking,
                    callback
            );
            callback.await();

            return RagV3InvokeResult.builder()
                    .conversationId(session.conversationId())
                    .taskId(session.taskId())
                    .answer(callback.getAnswer())
                    .referenceTitles(extractReferenceTitles(session.references()))
                    .build();
        } finally {
            UserContext.clear();
        }
    }

    private List<String> extractReferenceTitles(List<ChatReferencePayload> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return references.stream()
                .map(ChatReferencePayload::title)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    @lombok.Builder
    public record RagV3InvokeResult(String conversationId,
                                    String taskId,
                                    String answer,
                                    List<String> referenceTitles) {
    }

    private static class CollectingStreamCallback implements StreamCallback {

        private final CountDownLatch doneLatch = new CountDownLatch(1);
        private final StringBuilder answerBuilder = new StringBuilder();
        private volatile Throwable error;

        @Override
        public void onContent(String content) {
            if (StrUtil.isNotBlank(content)) {
                answerBuilder.append(content);
            }
        }

        @Override
        public void onComplete() {
            doneLatch.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            doneLatch.countDown();
        }

        void await() {
            try {
                boolean completed = doneLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!completed) {
                    throw new IllegalStateException("RAG V3 评测调用超时，未在预期时间内完成。");
                }
                if (error != null) {
                    throw new IllegalStateException("RAG V3 评测调用失败", error);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("RAG V3 评测调用被中断", ex);
            }
        }

        String getAnswer() {
            return answerBuilder.toString();
        }
    }
}
