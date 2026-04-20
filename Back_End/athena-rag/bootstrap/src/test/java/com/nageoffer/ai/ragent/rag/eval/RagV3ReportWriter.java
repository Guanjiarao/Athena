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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * RAG V3 评测报告输出器
 */
@Component
@RequiredArgsConstructor
public class RagV3ReportWriter {

    private final ObjectMapper objectMapper;
    private final RagV3CaseLoader caseLoader;

    public void writeReport(RagV3EvalReport report) {
        Path outputDir = caseLoader.resolveProjectRoot().resolve("resources/eval/outputs");
        String suiteName = report.getSuiteName();
        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("latest-report.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                    StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("latest-report.md"),
                    buildMarkdown(report),
                    StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve(suiteName + "-report.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                    StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve(suiteName + "-report.md"),
                    buildMarkdown(report),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("写入评测报告失败: " + outputDir, ex);
        }
    }

    private String buildMarkdown(RagV3EvalReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# RAG V3 Eval Report\n\n");
        builder.append("- Suite: ").append(report.getSuiteName()).append("\n");
        builder.append("- Total: ").append(report.getTotal()).append("\n");
        builder.append("- Pass: ").append(report.getPassed()).append("\n");
        builder.append("- Warning: ").append(report.getWarnings()).append("\n");
        builder.append("- Fail: ").append(report.getFailed()).append("\n");
        builder.append("- Skipped: ").append(report.getSkipped()).append("\n\n");

        builder.append("## Results\n\n");
        for (RagV3EvalResult result : report.getResults()) {
            builder.append("### ").append(result.getCaseId()).append(" - ").append(result.getStatus()).append("\n\n");
            builder.append("- Question: ").append(nullToEmpty(result.getQuestion())).append("\n");
            builder.append("- Category: ").append(nullToEmpty(result.getCategory())).append("\n");
            builder.append("- Conversation ID: ").append(nullToEmpty(result.getConversationId())).append("\n");
            builder.append("- Task ID: ").append(nullToEmpty(result.getTaskId())).append("\n");
            builder.append("- References: ").append(formatList(result.getReferenceTitles())).append("\n");
            builder.append("- Findings: ").append(formatList(result.getFindings())).append("\n\n");
            builder.append("#### Answer\n\n");
            builder.append(nullToEmpty(result.getAnswer())).append("\n\n");
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        return items.toString();
    }
}
