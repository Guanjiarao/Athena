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

package com.nageoffer.ai.ragent.rag.core.retrieve.postprocessor;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rerank 后置处理器
 * <p>
 * 使用 Rerank 模型对结果进行重排序
 * 这是最后一个处理器，输出最终的 Top-K 结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankPostProcessor implements SearchResultPostProcessor {

    private final RerankService rerankService;

    @Override
    public String getName() {
        return "Rerank";
    }

    @Override
    public int getOrder() {
        return 10;  // 最后执行
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return true;  // 始终启用
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            log.info("Chunk 列表为空，跳过 Rerank");
            return chunks;
        }

        List<RetrievedChunk> reranked = rerankService.rerank(
                context.getMainQuestion(),
                chunks,
                context.getTopK()
        );

        if (reranked.isEmpty()) {
            log.info("Rerank 完成，但无结果 - question={}", context.getMainQuestion());
            return reranked;
        }

        String topSummary = reranked.stream()
                .limit(Math.min(5, reranked.size()))
                .map(this::formatChunkSummary)
                .collect(Collectors.joining(" | "));

        Float top1Score = reranked.get(0).getScore();
        Float top3Avg = (float) reranked.stream()
                .limit(Math.min(3, reranked.size()))
                .map(RetrievedChunk::getScore)
                .filter(score -> score != null)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0D);

        log.info("Rerank Top结果 - question={}, top1Score={}, top3Avg={}, topK={}, topChunks=[{}]",
                context.getMainQuestion(),
                top1Score,
                top3Avg,
                reranked.size(),
                topSummary
        );

        return reranked;
    }

    private String formatChunkSummary(RetrievedChunk chunk) {
        if (chunk == null) {
            return "chunk=null";
        }
        Map<String, Object> metadata = chunk.getMetadata();
        Object noteId = metadata == null ? null : metadata.get("noteId");
        Object title = metadata == null ? null : metadata.get("title");
        return String.format("score=%s,noteId=%s,title=%s,chunkId=%s",
                chunk.getScore(),
                noteId,
                StrUtil.blankToDefault(title == null ? null : String.valueOf(title), "-"),
                StrUtil.blankToDefault(chunk.getId(), "-"));
    }
}
