package athena.ground.biz.service;

import athena.athenaframework.result.Result;

/**
 * 笔记互动服务：点赞、收藏、互动计数
 */
public interface NoteInteractionService {

    Result likeNote(Long blogId);

    Result collectNote(Long blogId);

    Result isLikeNote(Long blogId);

    Result isCollectNote(Long blogId);

    Result likeList();

    Result collectList();

    Result likeAdd(Long noteId, Long num);

    Result collectAdd(Long noteId, Long num);

    Result commentAdd(Long noteId, Long num);
}
