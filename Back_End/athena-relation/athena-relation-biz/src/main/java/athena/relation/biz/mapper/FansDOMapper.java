package athena.relation.biz.mapper;

import athena.relation.biz.domain.dataobject.FansDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FansDOMapper extends BaseMapper<FansDO> {
    /**
     * 取消粉丝关联（删除记录）
     */
    @Delete("DELETE FROM tb_fans WHERE user_id = #{userId} AND fans_user_id = #{fansUserId}")
    int deleteByUserIdAndFansUserId(@Param("userId") Long userId, @Param("fansUserId") Long fansUserId);

    /**
     * 查询用户的粉丝列表
     */
    @Select("SELECT fans_user_id as fansUserId FROM tb_fans WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Long> selectFanListByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的粉丝数量
     */
    @Select("SELECT COUNT(1) FROM tb_fans WHERE user_id = #{userId}")
    Long selectFanCountByUserId(@Param("userId") Long userId);
}
