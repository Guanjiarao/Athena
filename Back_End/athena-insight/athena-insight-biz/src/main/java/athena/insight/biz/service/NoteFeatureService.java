package athena.insight.biz.service;

import athena.insight.biz.domain.dataobject.NoteFeatureDO;

import java.util.List;

public interface NoteFeatureService {

    NoteFeatureDO getByNoteId(Long noteId);

    NoteFeatureDO refreshByNoteId(Long noteId);

    void deleteByNoteId(Long noteId);

    List<NoteFeatureDO> refreshPublicPool(Integer pageNum, Integer pageSize);
}
