

package com.nageoffer.ai.ragent.rag.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG V3 评测报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagV3EvalReport {

    private String suiteName;

    private Integer total;

    private Integer passed;

    private Integer warnings;

    private Integer failed;

    private Integer skipped;

    private List<RagV3EvalResult> results;
}
