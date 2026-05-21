

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
