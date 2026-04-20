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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * RAG V3 评测用例加载器
 */
@Component
@RequiredArgsConstructor
public class RagV3CaseLoader {

    private final ObjectMapper objectMapper;

    public List<RagV3EvalCase> loadSmokeCases() {
        return loadCases("resources/eval/athena-rag-smoke-cases.json");
    }

    public List<RagV3EvalCase> loadBadCases() {
        return loadCases("resources/eval/athena-rag-bad-cases.json");
    }

    private List<RagV3EvalCase> loadCases(String relativePath) {
        Path root = resolveProjectRoot();
        Path filePath = root.resolve(relativePath);
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return objectMapper.readValue(inputStream, new TypeReference<List<RagV3EvalCase>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("加载评测集失败: " + filePath, ex);
        }
    }

    Path resolveProjectRoot() {
        String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory != null && !multiModuleProjectDirectory.isBlank()) {
            return Paths.get(multiModuleProjectDirectory);
        }
        return Paths.get("").toAbsolutePath().normalize().getParent();
    }
}
