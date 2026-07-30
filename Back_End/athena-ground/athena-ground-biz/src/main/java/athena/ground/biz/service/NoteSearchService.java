package athena.ground.biz.service;

import athena.athenaframework.result.Result;

public interface NoteSearchService {

    Result searchPublicNotes(String keyword, Integer type, Integer channelId, Integer pageNum, Integer pageSize);

    Result searchMyNotes(String keyword, Byte status, Integer type, Integer pageNum, Integer pageSize);
}
