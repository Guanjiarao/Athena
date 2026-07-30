package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteLikeDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface NoteLikeDOMapper {
    int deleteByPrimaryKey(Long id);

    int deleteByNoteId(@Param("noteId") Long noteId);

    int insert(NoteLikeDO record);

    int insertSelective(NoteLikeDO record);

    NoteLikeDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteLikeDO record);

    /**
     * 新增/更新点赞记录（幂等）
     */
    @Insert("INSERT INTO tb_note_like (user_id, note_id, create_time, status) " +
            "VALUES (#{userId}, #{noteId}, NOW(), 1) " +
            "ON DUPLICATE KEY UPDATE status = 1, create_time = NOW()")
    int saveOrUpdateLike(NoteLikeDO noteLikeDO);

    /**
     * 取消点赞（软删除）
     */
    @Update("UPDATE tb_note_like SET status = 0, create_time = NOW() " +
            "WHERE user_id = #{userId} AND note_id = #{noteId} AND status = 1")
    int cancelLike(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 查询用户是否点赞过该笔记
     */
    @Select("SELECT COUNT(1) FROM tb_note_like WHERE user_id = #{userId} AND note_id = #{noteId} AND status = 1")
    int countLikeByUserAndNote(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 查询用户的点赞列表（只返回有效点赞）
     */
    @Select("SELECT n.note_id FROM tb_note_like n " +
            "WHERE n.user_id = #{userId} AND n.status = 1 ORDER BY n.create_time DESC")
    List<Long> listLikeByUserId(@Param("userId") Long userId);
}