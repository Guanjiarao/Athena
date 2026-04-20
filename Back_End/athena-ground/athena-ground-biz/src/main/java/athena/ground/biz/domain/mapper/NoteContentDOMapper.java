package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteContentDO;

public interface NoteContentDOMapper {
    int deleteByPrimaryKey(Long contentId);

    int deleteByNoteId(Long noteId);

    int insert(NoteContentDO record);

    int insertSelective(NoteContentDO record);

    NoteContentDO selectByPrimaryKey(Long contentId);

    int updateByPrimaryKeySelective(NoteContentDO record);

    int updateByPrimaryKeyWithBLOBs(NoteContentDO record);

    int updateByPrimaryKey(NoteContentDO record);

    NoteContentDO selectByNoteId(Long NoteId);
}