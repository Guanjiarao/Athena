package athena.relation.biz.mapper;

import athena.relation.biz.domain.dataobject.FollowDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FollowDOMapper extends BaseMapper<FollowDO> {
    /**
     * 查询用户是否关注了目标用户
     */
    @Select("SELECT id, user_id as userId, follow_user_id as followUserId, create_time as createTime " +
            "FROM tb_follow WHERE user_id = #{userId} AND follow_user_id = #{followUserId}")
    FollowDO selectByUserIdAndFollowUserId(@Param("userId") Long userId, @Param("followUserId") Long followUserId);

    /**
     * 取消关注（删除记录）
     */
    @Delete("DELETE FROM tb_follow WHERE user_id = #{userId} AND follow_user_id = #{followUserId}")
    int deleteByUserIdAndFollowUserId(@Param("userId") Long userId, @Param("followUserId") Long followUserId);

    /**
     * 查询用户的关注列表
     */
    @Select("SELECT follow_user_id as followUserId FROM tb_follow WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Long> selectFollowListByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的关注数量
     */
    @Select("SELECT COUNT(1) FROM tb_follow WHERE user_id = #{userId}")
    Long selectFollowCountByUserId(@Param("userId") Long userId);
}
