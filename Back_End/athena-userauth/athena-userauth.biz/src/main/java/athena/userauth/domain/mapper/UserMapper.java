package athena.userauth.domain.mapper;


import athena.athenaframework.DTO.UserDTO;
import athena.userauth.domain.dataobject.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM tb_user WHERE phone = #{phone}")
    User selectByPhone(String phone);

    /**
     * 根据用户ID列表查询多个用户
     * @param userIds 用户ID列表
     * @return 用户列表
     */
    @Select("<script>" +
            "SELECT * FROM tb_user WHERE id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<UserDTO> selectByUserIds(@Param("userIds") List<Long> userIds);
}
