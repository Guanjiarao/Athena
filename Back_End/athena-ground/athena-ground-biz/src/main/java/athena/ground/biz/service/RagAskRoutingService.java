package athena.ground.biz.service;

import java.util.List;

/**
 * RAG 问答知识库路由服务
 */
public interface RagAskRoutingService {

    /**
     * 根据年龄解析问答知识库路由
     *
     * @param age 用户年龄
     * @return 目标知识库编码列表
     */
    List<String> resolveKbCodesByAge(Integer age);
}
