package athena.comment.biz.domain.mapper;

import athena.comment.biz.domain.dataobject.CommentLikeDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentLikeDOMapper {

    /**
     * 根据用户ID和评论ID查询点赞记录
     */
    @Select("SELECT id, user_id, comment_id, create_time, status FROM tb_comment_like WHERE user_id = #{userId} AND comment_id = #{commentId}")
    CommentLikeDO selectByUserIdAndCommentId(@Param("userId") Long userId, @Param("commentId") Long commentId);

    /**
     * 新增点赞记录
     */
    @Insert("INSERT INTO tb_comment_like (user_id, comment_id, create_time, status) VALUES (#{userId}, #{commentId}, #{createTime}, #{status})")
    int insert(CommentLikeDO commentLikeDO);

    /**
     * 更新点赞状态
     */
    @Update("UPDATE tb_comment_like SET status = #{status} WHERE id = #{id}")
    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);

    @Delete({"<script>",
            "DELETE FROM tb_comment_like WHERE comment_id IN",
            "<foreach collection='commentIds' item='commentId' open='(' separator=',' close=')'>",
            "#{commentId}",
            "</foreach>",
            "</script>"})
    int deleteByCommentIds(@Param("commentIds") List<Long> commentIds);
}