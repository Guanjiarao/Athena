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

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.controller.request.AthenaAskRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AthenaAskVO;
import com.nageoffer.ai.ragent.rag.controller.vo.AthenaNoteReferenceVO;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import com.nageoffer.ai.ragent.rag.service.AthenaRagAskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面向 Athena 的问答服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthenaRagAskServiceImpl implements AthenaRagAskService {

    private static final int DEFAULT_FALLBACK_AGE = 30;
    private static final int RETRIEVE_TOP_K = 5;
    private static final String FALLBACK_KB_CODE = "kbadult";
    private static final String SYSTEM_PROMPT = "你是 Athena 的知识问答助手。请严格基于提供的知识片段回答，优先给出直接结论，语言简洁自然；如果知识片段不足以支持结论，就明确说明。";

    private final RetrieverService retrieverService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final LLMService llmService;

    @Override
    public AthenaAskVO ask(AthenaAskRequest request) {
        validateRequest(request);

        log.info("[AthenaRagAsk] 收到问答请求, age={}, question={}", request.getAge(), abbreviateQuestion(request.getQuestion()));

        Integer resolvedAge = resolveAge(request.getAge());
        List<String> kbCodes = resolveKbCodes(resolvedAge);
        log.info("[AthenaRagAsk] 年龄路由完成, resolvedAge={}, kbCodes={}", resolvedAge, kbCodes);

        List<RetrievedChunk> chunks = retrieveChunks(request.getQuestion(), kbCodes);
        log.info("[AthenaRagAsk] 检索完成, chunkCount={}, chunkIds={}", chunks.size(), chunks.stream()
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList()));

        String answer = generateAnswer(request.getQuestion(), chunks);
        List<AthenaNoteReferenceVO> references = buildReferences(chunks);
        log.info("[AthenaRagAsk] 问答完成, answerLength={}, referenceCount={}",
                StrUtil.length(answer), references.size());

        return AthenaAskVO.builder()
                .answer(answer)
                .resolvedAge(resolvedAge)
                .kbCodes(kbCodes)
                .references(references)
                .build();
    }

    private void validateRequest(AthenaAskRequest request) {
        Assert.notNull(request, () -> new ClientException("问答请求不能为空"));
        Assert.isTrue(StrUtil.isNotBlank(request.getQuestion()), () -> new ClientException("问题不能为空"));
    }

    private Integer resolveAge(Integer age) {
        if (age == null || age <= 0) {
            log.info("[AthenaRagAsk] 年龄缺失或非法, 使用兜底年龄={}", DEFAULT_FALLBACK_AGE);
            return DEFAULT_FALLBACK_AGE;
        }
        return age;
    }

    private List<String> resolveKbCodes(Integer age) {
        String kbCode;
        if (age < 12) {
            kbCode = "kbchild";
        } else if (age <= 22) {
            kbCode = "kbteen";
        } else if (age <= 55) {
            kbCode = "kbadult";
        } else {
            kbCode = "kbsenior";
        }

        List<String> kbCodes = new ArrayList<>();
        kbCodes.add(kbCode);
        kbCodes.add("kbcommon");
        return kbCodes;
    }

    private List<RetrievedChunk> retrieveChunks(String question, List<String> kbCodes) {
        Map<String, RetrievedChunk> deduplicated = new LinkedHashMap<>();

        for (String kbCode : kbCodes) {
            String collectionName = resolveCollectionName(kbCode);
            log.info("[AthenaRagAsk] 开始检索知识库, kbCode={}, collectionName={}", kbCode, collectionName);

            List<RetrievedChunk> currentChunks = retrieverService.retrieve(
                    RetrieveRequest.builder()
                            .query(question)
                            .collectionName(collectionName)
                            .topK(RETRIEVE_TOP_K)
                            .build()
            );
            log.info("[AthenaRagAsk] 单库检索完成, kbCode={}, hitCount={}", kbCode,
                    currentChunks == null ? 0 : currentChunks.size());

            if (CollUtil.isEmpty(currentChunks)) {
                continue;
            }
            for (RetrievedChunk chunk : currentChunks) {
                if (chunk == null || StrUtil.isBlank(chunk.getId())) {
                    continue;
                }
                deduplicated.merge(chunk.getId(), chunk, (existing, incoming) ->
                        compareScore(incoming, existing) > 0 ? incoming : existing);
            }
        }

        return deduplicated.values().stream()
                .sorted((left, right) -> compareScore(right, left))
                .limit(RETRIEVE_TOP_K)
                .toList();
    }

    private int compareScore(RetrievedChunk left, RetrievedChunk right) {
        Float leftScore = left == null ? null : left.getScore();
        Float rightScore = right == null ? null : right.getScore();
        return Comparator.nullsLast(Float::compareTo).compare(leftScore, rightScore);
    }

    private String resolveCollectionName(String kbCode) {
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getCollectionName, kbCode)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (knowledgeBase == null || StrUtil.isBlank(knowledgeBase.getCollectionName())) {
            if (Objects.equals(kbCode, FALLBACK_KB_CODE) || Objects.equals(kbCode, "kbcommon")) {
                log.warn("[AthenaRagAsk] 知识库记录缺失, 使用默认 collectionName, kbCode={}", kbCode);
                return kbCode;
            }
            throw new ClientException("目标知识库不存在，kbCode=" + kbCode);
        }
        return knowledgeBase.getCollectionName();
    }

    private String generateAnswer(String question, List<RetrievedChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            log.warn("[AthenaRagAsk] 未检索到有效知识片段, question={}", abbreviateQuestion(question));
            return "暂时没有检索到足够相关的知识内容，请换个问法试试。";
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            contextBuilder.append("[片段").append(i + 1).append("]\n")
                    .append(StrUtil.blankToDefault(chunk.getText(), ""))
                    .append("\n\n");
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(SYSTEM_PROMPT),
                        ChatMessage.user("问题：" + question + "\n\n知识片段：\n" + contextBuilder + "请基于这些片段直接回答。")
                ))
                .temperature(0D)
                .build();
        log.info("[AthenaRagAsk] 开始调用 LLM 生成答案, question={}, chunkCount={}", abbreviateQuestion(question), chunks.size());
        return llmService.chat(chatRequest);
    }

    private List<AthenaNoteReferenceVO> buildReferences(List<RetrievedChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return List.of();
        }

        LinkedHashSet<Long> addedIds = new LinkedHashSet<>();
        List<AthenaNoteReferenceVO> references = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            Map<String, Object> metadata = chunk.getMetadata();
            Long noteId = extractNoteId(metadata);
            if (noteId == null || !addedIds.add(noteId)) {
                continue;
            }
            references.add(AthenaNoteReferenceVO.builder()
                    .noteId(noteId)
                    .title(extractTitle(metadata, noteId))
                    .snippet(buildSnippet(chunk.getText()))
                    .score(chunk.getScore())
                    .build());
        }
        return references;
    }

    private Long extractNoteId(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object noteId = metadata.get("noteId");
        if (noteId instanceof Number number) {
            return number.longValue();
        }
        if (noteId instanceof String text && text.chars().allMatch(Character::isDigit)) {
            return Long.valueOf(text);
        }
        return null;
    }

    private String extractTitle(Map<String, Object> metadata, Long noteId) {
        if (metadata != null) {
            Object title = metadata.get("title");
            if (title != null && StrUtil.isNotBlank(String.valueOf(title))) {
                return String.valueOf(title);
            }
        }
        return "Athena 笔记 #" + noteId;
    }

    private String buildSnippet(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private String abbreviateQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return "";
        }
        String normalized = question.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60) + "...";
    }
}
