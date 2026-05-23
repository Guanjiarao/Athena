

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgRetrieverService implements RetrieverService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final SearchChannelProperties searchChannelProperties;

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        List<Float> embedding = embeddingService.embed(request.getQuery());
        float[] vector = normalize(toArray(embedding));
        return retrieveByVector(vector, request);
    }

    @Override
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest request) {
        // 设置ef_search提升召回率
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        jdbcTemplate.execute("SET hnsw.ef_search = 200");

        String vectorLiteral = toVectorLiteral(vector);
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        List<RetrievedChunk> allChunks = jdbcTemplate.query("SELECT id, content, metadata, 1 - (embedding <=> ?::vector) AS score FROM t_knowledge_vector WHERE metadata->>'collection_name' = ? ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> {
                    String metadataJson = rs.getString("metadata");
                    java.util.Map<String, Object> metadata = null;
                    if (metadataJson != null) {
                        try {
                            metadata = new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadataJson, java.util.Map.class);
                        } catch (Exception e) {
                            log.warn("解析 metadata 失败: {}", metadataJson, e);
                        }
                    }
                    log.debug("[PgRetriever] chunk id={}, score={}, hasMetadata={}",
                            rs.getString("id"), rs.getFloat("score"), metadata != null);
                    return RetrievedChunk.builder()
                            .id(rs.getString("id"))
                            .text(rs.getString("content"))
                            .score(rs.getFloat("score"))
                            .metadata(metadata)
                            .build();
                },
                vectorLiteral, request.getCollectionName(), vectorLiteral, request.getTopK()
        );

        // 过滤低分结果
        double minScoreThreshold = searchChannelProperties.getMinScoreThreshold();
        List<RetrievedChunk> chunks = allChunks.stream()
                .filter(chunk -> chunk.getScore() >= minScoreThreshold)
                .toList();

        log.info("[PgRetriever] 检索完成, collectionName={}, 原始结果: {}, 过滤后: {}, 阈值: {}, 有 metadata 的: {}",
                request.getCollectionName(), allChunks.size(), chunks.size(), minScoreThreshold,
                chunks.stream().filter(c -> c.getMetadata() != null).count());
        return chunks;
    }

    private float[] normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    private float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }
}
