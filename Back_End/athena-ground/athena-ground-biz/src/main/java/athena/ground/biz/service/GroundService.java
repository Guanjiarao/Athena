package athena.ground.biz.service;

import athena.athenaframework.result.Result;
import athena.ground.biz.domain.dto.BlogAskDTO;
import athena.ground.biz.domain.dto.NoteSubmitDTO;

import java.util.List;

public interface GroundService {
    Result getBlogListPage(Integer pageNum,Integer pageSize);

    Result getBlogDetail(Long noteId);

    Result getNoteBasicListByNoteIdList(List<Long> noteIdList);

    Result submitNote(NoteSubmitDTO noteSubmitDTO);

    Result deleteNote(Long noteId);

    Result likeNote(Long blogId);

    Result collectNote(Long blogId);

    Result isLikeNote(Long blogId);

    Result isCollectNote(Long blogId);

    Result likeList();

    Result collectList();

    Result collectAdd(Long noteId,Long num);

    Result likeAdd(Long noteId,Long num);

    Result commentAdd(Long noteId,Long num);

    Result getBlogListByChannelId(Integer channelId, Integer pageNum, Integer pageSize);

    Result getBlogListByType(Integer type,Integer pageNum, Integer pageSize);

    Result getBlogListByUserId(Long userId,Integer pageNum, Integer pageSize);

    Result askBlog(BlogAskDTO request);
}
