package athena.comment.biz.domain.mapper;

import athena.comment.biz.domain.dataobject.CommentContentDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CommentContentDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(CommentContentDO record);

    int insertSelective(CommentContentDO record);

    CommentContentDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentContentDO record);

    int updateByPrimaryKeyWithBLOBs(CommentContentDO record);

    int updateByPrimaryKey(CommentContentDO record);

    /**
     * 查询评论内容
     * @param commentId 评论ID
     * @return 评论内容
     */
    @Select("SELECT content FROM tb_comment_content WHERE comment_id = #{commentId}")
    String selectCommentContentByCommentId(@Param("commentId") Long commentId);

    /**
     * 插入评论内容表
     * @param commentId 评论ID
     * @param content 评论内容
     * @return 影响行数
     */
    @Insert("INSERT INTO tb_comment_content (comment_id, content, create_time, update_time) " +
            "VALUES (#{commentId}, #{content}, NOW(), NOW())")
    int insertCommentContent(@Param("commentId") Long commentId, @Param("content") String content);

    @Delete({"<script>",
            "DELETE FROM tb_comment_content WHERE comment_id IN",
            "<foreach collection='commentIds' item='commentId' open='(' separator=',' close=')'>",
            "#{commentId}",
            "</foreach>",
            "</script>"})
    int deleteByCommentIds(@Param("commentIds") List<Long> commentIds);
}