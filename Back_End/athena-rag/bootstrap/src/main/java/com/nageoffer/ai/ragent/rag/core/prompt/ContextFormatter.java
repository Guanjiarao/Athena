

package com.nageoffer.ai.ragent.rag.core.prompt;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
// import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.List;
import java.util.Map;

/**
 * 上下文格式化器，负责将知识库检索结果和 MCP 工具调用结果格式化为可嵌入 Prompt 的文本
 */
public interface ContextFormatter {

    /**
     * 格式化知识库检索上下文
     *
     * @param kbIntents        知识库意图节点及其得分列表
     * @param rerankedByIntent 按意图分组的重排序后检索文档块
     * @param topK             每个意图下保留的最大文档块数量
     * @return 格式化后的知识库上下文文本
     */
    String formatKbContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> rerankedByIntent, int topK);

    /**
     * 格式化 MCP 工具调用上下文
     * MCP 功能暂时禁用
     *
     * @param toolResults MCP 工具调用结果，按工具名称分组
     * @param mcpIntents  MCP 意图节点及其得分列表
     * @return 格式化后的 MCP 上下文文本
     */
    String formatMcpContext(Map<String, Object> toolResults, List<NodeScore> mcpIntents);
}
