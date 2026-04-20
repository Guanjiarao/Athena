package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteCollectionDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface NoteCollectionDOMapper {
    int deleteByPrimaryKey(Long id);

    int deleteByNoteId(@Param("noteId") Long noteId);

    int insert(NoteCollectionDO record);

    int insertSelective(NoteCollectionDO record);

    NoteCollectionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCollectionDO record);

    /**
     * 新增/更新收藏记录（幂等）
     */
    @Insert("INSERT INTO tb_note_collection (user_id, note_id, create_time, status) " +
            "VALUES (#{userId}, #{noteId}, NOW(), 1) " +
            "ON DUPLICATE KEY UPDATE status = 1, create_time = NOW()")
    int saveOrUpdateCollection(NoteCollectionDO noteCollectionDO);

    /**
     * 取消收藏（软删除）
     */
    @Update("UPDATE tb_note_collection SET status = 0, create_time = NOW() " +
            "WHERE user_id = #{userId} AND note_id = #{noteId} AND status = 1")
    int cancelCollection(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 查询用户是否收藏过该笔记
     */
    @Select("SELECT COUNT(1) FROM tb_note_collection WHERE user_id = #{userId} AND note_id = #{noteId} AND status = 1")
    int countCollectionByUserAndNote(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 查询用户的收藏列表（只返回有效收藏）
     */
    @Select("SELECT n.note_id FROM tb_note_collection n " +
            "WHERE n.user_id = #{userId} AND n.status = 1 ORDER BY n.create_time DESC")
    List<Long> listCollectionByUserId(@Param("userId") Long userId);
}