package athena.userauth.domain.mapper;

import athena.userauth.domain.dataobject.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 根据用户ID更新生日
     */
    @Update("UPDATE tb_user_info SET birthday = #{birthday}, update_time = NOW() WHERE user_id = #{userId}")
    int updateBirthday(@Param("userId") Long userId, @Param("birthday") LocalDate birthday);

}