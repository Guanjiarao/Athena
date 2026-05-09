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

package com.nageoffer.ai.ragent.rag.core.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class PgVectorStoreServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RAGDefaultProperties ragDefaultProperties;

    @Test
    public void testChineseCharacterInsertion() throws Exception {
        String chunkId = "test_chunk_001";
        String collectionName = "test_collection";
        String docId = "test_doc_001";
        Integer chunkIndex = 0;
        String content = "这是一段中文测试内容，包含各种字符：你好世界！";

        int dimension = ragDefaultProperties.getDimension();
        float[] embedding = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            embedding[i] = 0.1f;
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        String vectorLiteral = sb.append("]").toString();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("collection_name", collectionName);
        metadata.put("doc_id", docId);
        metadata.put("chunk_index", chunkIndex);
        String metadataJson = objectMapper.writeValueAsString(metadata);

        String sql = "INSERT INTO t_knowledge_vector (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector)";

        jdbcTemplate.update(sql, chunkId, content, metadataJson, vectorLiteral);

        String querySql = "SELECT id, content, metadata FROM t_knowledge_vector WHERE id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(querySql, chunkId);

        Assertions.assertFalse(results.isEmpty());
        Map<String, Object> row = results.get(0);
        Assertions.assertEquals(chunkId, row.get("id"));
        Assertions.assertEquals(content, row.get("content"));
        Assertions.assertNotNull(row.get("metadata"));

        jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE id = ?", chunkId);
    }
}
