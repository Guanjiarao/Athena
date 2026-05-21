

package com.nageoffer.ai.ragent.rag.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG V3 评测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagV3EvalResult {

    private String caseId;

    private String question;

    private String category;

    private String answer;

    private String conversationId;

    private String taskId;

    private String status;

    private List<String> referenceTitles;

    private Map<String, String> checkResults;

    private List<String> findings;
}
