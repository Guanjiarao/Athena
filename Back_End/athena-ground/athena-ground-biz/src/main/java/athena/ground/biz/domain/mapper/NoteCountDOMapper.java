package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteCountDO;
import org.apache.ibatis.annotations.*;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int deleteByNoteId(@Param("noteId") Long noteId);

    int insertSelective(NoteCountDO record);

    NoteCountDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);

    @Select("SELECT id, note_id as noteId, like_total as likeTotal, " +
            "collect_total as collectTotal, comment_total as commentTotal " +
            "FROM tb_note_count " +
            "WHERE note_id = #{noteId}")
    NoteCountDO selectByNoteId(@Param("noteId") Long noteId);

    /**
     * 新增笔记计数记录
     */
    @Insert("INSERT INTO tb_note_count (note_id, like_total, collect_total, comment_total) " +
            "VALUES (#{noteId}, #{likeTotal}, #{collectTotal}, #{commentTotal})")
    @Options(useGeneratedKeys = true, keyProperty = "id") // 自动回填主键ID
    int insert(NoteCountDO noteCountDO);

    /**
     * 增加点赞数
     */
    @Update("UPDATE tb_note_count SET like_total = GREATEST(like_total + #{num}, 0) WHERE note_id = #{noteId}")
    int incrementLikeTotal(@Param("noteId") Long noteId, @Param("num") Long num);

    /**
     * 增加收藏数
     */
    @Update("UPDATE tb_note_count SET collect_total =  GREATEST(collect_total + #{num}, 0) WHERE note_id = #{noteId}")
    int incrementCollectTotal(@Param("noteId") Long noteId, @Param("num") Long num);

    /**
     * 增加评论数
     */
    @Update("UPDATE tb_note_count SET comment_total = GREATEST(comment_total + #{num}, 0) WHERE note_id = #{noteId}")
    int incrementCommentTotal(@Param("noteId") Long noteId, @Param("num") Long num);
}