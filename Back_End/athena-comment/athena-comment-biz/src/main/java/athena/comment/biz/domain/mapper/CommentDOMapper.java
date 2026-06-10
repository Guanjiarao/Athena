package athena.comment.biz.domain.mapper;

import athena.comment.biz.domain.dataobject.CommentDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface CommentDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(CommentDO record);

    int insertSelective(CommentDO record);

    CommentDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentDO record);

    int updateByPrimaryKey(CommentDO record);
    /**
     * 分页查询一级评论（按创建时间倒序）
     * @param noteId 笔记ID
     * @param offset 分页偏移量
     * @param pageSize 页大小
     * @return 一级评论列表
     */
    @Select("SELECT * FROM tb_comment WHERE note_id = #{noteId} AND level = 1 " +
            "ORDER BY is_top DESC, create_time DESC LIMIT #{offset}, #{pageSize}")
    List<CommentDO> selectFirstLevelCommentsByNoteId(
            @Param("noteId") Long noteId,
            @Param("offset") Long offset,
            @Param("pageSize") Long pageSize);

    /**
     * 分页查询二级评论（按创建时间倒序）
     * @param parentId 一级评论ID
     * @param offset 分页偏移量
     * @param pageSize 页大小
     * @return 二级评论列表
     */
    @Select("SELECT id FROM tb_comment WHERE parent_id = #{parentId} AND level = 2 AND reply_user_id !=0 " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{pageSize}")
    List<Long> selectSecondLevelCommentsByParentId(
            @Param("parentId") Long parentId,
            @Param("offset") Long offset,
            @Param("pageSize") Long pageSize);

    /**
     * 插入评论主表
     * @param commentDO 评论DO
     * @return 影响行数
     */
    @Insert("INSERT INTO tb_comment (note_id, user_id, is_content_empty, image_url, level, " +
            "reply_total, like_total, parent_id, reply_comment_id, reply_user_id, is_top, " +
            "create_time, update_time) " +
            "VALUES (#{noteId}, #{userId}, #{isContentEmpty}, #{imageUrl}, #{level}, " +
            "0, 0, #{parentId}, #{replyCommentId}, #{replyUserId}, 0, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertComment(CommentDO commentDO);

    /**
     * 更新一级评论的回复数
     * @param commentId 一级评论ID
     */
    @Update("UPDATE tb_comment SET reply_total = reply_total + 1, update_time = NOW(), first_reply_comment_id = #{firstReplyCommentId} " +
            "WHERE id = #{commentId} AND level = 1")
    void incrementReplyTotalAndFirstComment(@Param("commentId") Long commentId,@Param("firstReplyCommentId")Long firstReplyCommentId);

    /**
     * 查询评论总数（一级评论）
     * @param noteId 笔记ID
     * @return 总数
     */
    @Select("SELECT COUNT(*) FROM tb_comment WHERE note_id = #{noteId} AND level = 1")
    Long countFirstLevelComments(@Param("noteId") Long noteId);

    /**
     * 查询二级评论总数
     * @param parentId 一级评论ID
     * @return 总数
     */
    @Select("SELECT COUNT(*) FROM tb_comment WHERE parent_id = #{parentId} AND level = 2")
    Long countSecondLevelComments(@Param("parentId") Long parentId);

    /**
     * 根据评论ID查询发布该评论的用户ID
     * @param commentId 评论ID
     * @return 用户ID（不存在返回null）
     */
    @Select("SELECT user_id FROM tb_comment WHERE id = #{commentId}")
    Long selectUserIdByCommentId(@Param("commentId") Long commentId);

    @Select("SELECT * FROM tb_comment WHERE id = #{commentId}")
    CommentDO selectAllById(@Param("commentId") Long commentId);

    @Select("SELECT id FROM tb_comment WHERE note_id = #{noteId}")
    List<Long> selectIdsByNoteId(@Param("noteId") Long noteId);

    @Delete("DELETE FROM tb_comment WHERE note_id = #{noteId}")
    int deleteByNoteId(@Param("noteId") Long noteId);


}