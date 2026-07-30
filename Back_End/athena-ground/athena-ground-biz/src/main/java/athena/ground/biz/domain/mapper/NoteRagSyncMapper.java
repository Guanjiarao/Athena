package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteRagSyncDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteRagSyncMapper {

    NoteRagSyncDO selectByNoteId(@Param("noteId") Long noteId);

    List<NoteRagSyncDO> selectRefreshCandidates(@Param("noteId") Long noteId,
                                                @Param("limit") Integer limit);

    int insert(NoteRagSyncDO record);

    int updateByNoteIdSelective(NoteRagSyncDO record);

    int increaseRetryCount(@Param("noteId") Long noteId);
}
