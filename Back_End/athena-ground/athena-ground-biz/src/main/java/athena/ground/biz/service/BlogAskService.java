package athena.ground.biz.service;

import athena.ground.biz.domain.dto.BlogAskDTO;
import athena.ground.biz.domain.dto.BlogAskResultDTO;

/**
 * 面向博客场景的 RAG 问答服务
 */
public interface BlogAskService {

    /**
     * 执行博客问答
     *
     * @param request 问答请求
     * @return 问答结果
     */
    BlogAskResultDTO ask(BlogAskDTO request);
}
