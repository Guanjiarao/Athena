

package com.nageoffer.ai.ragent.knowledge.service;

/**
 * Athena 知识库路由服务
 */
public interface KnowledgeBaseRoutingService {

    /**
     * 根据笔记类型解析目标知识库编码
     *
     * @param type 笔记类型
     * @return 目标知识库编码
     */
    String resolveKbCodeByType(Integer type);
}
