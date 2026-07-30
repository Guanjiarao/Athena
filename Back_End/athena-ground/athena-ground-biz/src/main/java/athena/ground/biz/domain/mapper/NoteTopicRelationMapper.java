package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteTopicRelationDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteTopicRelationMapper {

    int deleteByNoteId(@Param("noteId") Long noteId);

    int batchInsert(@Param("relations") List<NoteTopicRelationDO> relations);
}
