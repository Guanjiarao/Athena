

package com.nageoffer.ai.ragent.rag.eval;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 评测检索接口出参（纯检索证据，无 LLM 输出）
 */
@Data
@Builder
public class EvalResponse {

    /**
     * 召回的文档 ID 列表（业务 docId，已去重；通过 chunkId 反查 t_knowledge_chunk.docId）
     */
    private List<String> retrievedDocIds;

    /**
     * 召回的 chunk 主键列表（RetrievedChunk.id，已去重；用于调试）
     */
    private List<String> retrievedChunkIds;

    /**
     * 召回的 chunk 文本列表（与 retrievedChunkIds 顺序对应）
     */
    private List<String> retrievedContexts;

    /**
     * MCP 工具调用结果（无 MCP 分支时为空字符串）
     */
    private String mcpContext;

    /**
     * 是否走了 MCP 分支
     */
    private boolean hasMcp;

    /**
     * 是否走了 KB 检索
     */
    private boolean hasKb;

    /**
     * 子问题列表（改写拆分结果）
     */
    private List<String> subIntents;

    /**
     * 每个子问题 top-1 的意图叶子节点 id，与 subIntents 同序（无候选时为 null）
     * 评测脚本据此与评估集 intent_l2 比对，计算 Top-1 准确率
     */
    private List<String> intentLeafIds;

    /**
     * 总耗时（毫秒）
     */
    private long latencyMs;
}
