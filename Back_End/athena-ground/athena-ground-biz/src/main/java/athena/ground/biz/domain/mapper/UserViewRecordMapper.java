package athena.ground.biz.domain.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 用户浏览记录 Mapper
 */
public interface UserViewRecordMapper {

    int deleteByNoteId(@Param("noteId") Long noteId);

    /**
     * 插入或更新浏览记录（基于 uk_user_note 唯一索引）
     * 首次浏览 → INSERT；重复浏览 → 更新 last_view_time + view_count+1 + duration
     */
    int upsertViewRecord(@Param("userId") Long userId,
                         @Param("noteId") Long noteId,
                         @Param("viewTime") String viewTime,
                         @Param("duration") Integer duration);
}
