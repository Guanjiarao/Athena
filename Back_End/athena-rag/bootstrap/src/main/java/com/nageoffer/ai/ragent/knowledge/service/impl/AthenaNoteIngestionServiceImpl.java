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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.ChunkingMode;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentSource;
import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import com.nageoffer.ai.ragent.ingestion.domain.enums.SourceType;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.PipelineDefinition;
import com.nageoffer.ai.ragent.ingestion.domain.settings.ChunkerSettings;
import com.nageoffer.ai.ragent.ingestion.domain.settings.IndexerSettings;
import com.nageoffer.ai.ragent.ingestion.engine.IngestionEngine;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.service.AthenaNoteIngestionService;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeBaseRoutingService;
import com.nageoffer.ai.ragent.knowledge.service.dto.AthenaNoteSyncRequest;
import com.nageoffer.ai.ragent.knowledge.service.dto.AthenaNoteSyncResult;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Athena 笔记摄取服务实现
 */
@Service
@RequiredArgsConstructor
public class AthenaNoteIngestionServiceImpl implements AthenaNoteIngestionService {

    private static final String ATHENA_NOTE_SOURCE = "athena-note";
    private static final String ATHENA_NOTE_PIPELINE_ID = "athena-note-ingestion";
    private static final List<String> DEFAULT_METADATA_FIELDS = List.of("noteId", "title", "type", "authorId", "source");

    private final KnowledgeBaseRoutingService knowledgeBaseRoutingService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IngestionEngine ingestionEngine;
    private final ObjectMapper objectMapper;

    @Override
    public AthenaNoteSyncResult ingest(AthenaNoteSyncRequest request) {
        validateRequest(request);

        String kbCode = knowledgeBaseRoutingService.resolveKbCodeByType(request.getType());
        KnowledgeBaseDO knowledgeBase = loadKnowledgeBase(kbCode);

        IngestionContext context = buildContext(request, knowledgeBase.getCollectionName());
        PipelineDefinition pipeline = buildPipelineDefinition(knowledgeBase);
        IngestionContext result = ingestionEngine.execute(pipeline, context);

        if (result.getError() != null) {
            throw new ClientException("Athena 笔记摄取失败: " + result.getError().getMessage());
        }
        if (result.getChunks() == null || result.getChunks().isEmpty()) {
            throw new ClientException("Athena 笔记摄取失败: 未生成任何分块");
        }

        return AthenaNoteSyncResult.builder()
                .noteId(request.getNoteId())
                .kbCode(kbCode)
                .collectionName(knowledgeBase.getCollectionName())
                .chunkCount(result.getChunks().size())
                .build();
    }

    private void validateRequest(AthenaNoteSyncRequest request) {
        Assert.notNull(request, () -> new ClientException("同步请求不能为空"));
        Assert.notNull(request.getNoteId(), () -> new ClientException("笔记 ID 不能为空"));
        Assert.notNull(request.getType(), () -> new ClientException("笔记类型不能为空"));
        Assert.notNull(request.getAuthorId(), () -> new ClientException("作者 ID 不能为空"));
        Assert.isTrue(StringUtils.hasText(request.getTitle()), () -> new ClientException("笔记标题不能为空"));
        Assert.isTrue(StringUtils.hasText(request.getContentHtml()), () -> new ClientException("笔记内容不能为空"));
    }

    private KnowledgeBaseDO loadKnowledgeBase(String kbCode) {
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getCollectionName, kbCode)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
                        .last("LIMIT 1")
        );
        Assert.notNull(knowledgeBase, () -> new ClientException("目标知识库不存在，kbCode=" + kbCode));
        return knowledgeBase;
    }

    private IngestionContext buildContext(AthenaNoteSyncRequest request, String collectionName) {
        return IngestionContext.builder()
                .taskId(String.valueOf(request.getNoteId()))
                .pipelineId(ATHENA_NOTE_PIPELINE_ID)
                .source(DocumentSource.builder()
                        .type(SourceType.URL)
                        .location(ATHENA_NOTE_SOURCE + ":" + request.getNoteId())
                        .fileName(buildFileName(request.getNoteId()))
                        .build())
                .rawBytes(request.getContentHtml().getBytes(StandardCharsets.UTF_8))
                .mimeType("text/html")
                .metadata(Map.of(
                        "noteId", request.getNoteId(),
                        "title", request.getTitle(),
                        "type", request.getType(),
                        "authorId", request.getAuthorId(),
                        "source", ATHENA_NOTE_SOURCE
                ))
                .vectorSpaceId(VectorSpaceId.builder()
                        .logicalName(collectionName)
                        .build())
                .build();
    }

    private PipelineDefinition buildPipelineDefinition(KnowledgeBaseDO knowledgeBase) {
        return PipelineDefinition.builder()
                .id(ATHENA_NOTE_PIPELINE_ID)
                .name("Athena 笔记摄取流水线")
                .description("复用现有 ingestion node 处理 Athena HTML 笔记")
                .nodes(List.of(
                        NodeConfig.builder()
                                .nodeId("fetcher")
                                .nodeType("fetcher")
                                .nextNodeId("parser")
                                .build(),
                        NodeConfig.builder()
                                .nodeId("parser")
                                .nodeType("parser")
                                .settings(objectMapper.valueToTree(buildHtmlParserSettings()))
                                .nextNodeId("chunker")
                                .build(),
                        NodeConfig.builder()
                                .nodeId("chunker")
                                .nodeType("chunker")
                                .settings(objectMapper.valueToTree(ChunkerSettings.builder()
                                        .strategy(ChunkingMode.STRUCTURE_AWARE)
                                        .chunkSize(1400)
                                        .overlapSize(0)
                                        .build()))
                                .nextNodeId("indexer")
                                .build(),
                        NodeConfig.builder()
                                .nodeId("indexer")
                                .nodeType("indexer")
                                .settings(objectMapper.valueToTree(IndexerSettings.builder()
                                        .embeddingModel(knowledgeBase.getEmbeddingModel())
                                        .metadataFields(DEFAULT_METADATA_FIELDS)
                                        .build()))
                                .build()
                ))
                .build();
    }

    private com.nageoffer.ai.ragent.ingestion.domain.settings.ParserSettings buildHtmlParserSettings() {
        return com.nageoffer.ai.ragent.ingestion.domain.settings.ParserSettings.builder()
                .rules(List.of(com.nageoffer.ai.ragent.ingestion.domain.settings.ParserSettings.ParserRule.builder()
                        .mimeType("HTML")
                        .options(Map.of("parserType", com.nageoffer.ai.ragent.core.parser.ParserType.HTML.getType()))
                        .build()))
                .build();
    }

    private String buildFileName(Long noteId) {
        return "athena-note-" + noteId + ".html";
    }
}
