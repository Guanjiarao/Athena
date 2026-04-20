package athena.ground.biz.service;

/**
 * Athena note 知识库路由服务
 */
public interface AthenaKnowledgeRouteService {

    /**
     * 解析上传目标
     *
     * @param type 笔记类型
     * @return 上传目标配置
     */
    KnowledgeTarget resolveTarget(Integer type);

    /**
     * 上传目标
     */
    record KnowledgeTarget(String kbCode, String kbId, String pipelineId) {
    }
}
