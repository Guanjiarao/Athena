package athena.ground.biz.service;

import athena.athenaframework.result.Result;
import athena.ground.biz.domain.dto.NoteApproveDTO;
import athena.ground.biz.domain.dto.NoteRejectDTO;

public interface NoteReviewService {

    Result getPendingList(Integer pageNum, Integer pageSize, Byte type, Integer channelId);

    Result getReviewDetail(Long noteId);

    Result approve(NoteApproveDTO request);

    Result reject(NoteRejectDTO request);
}
