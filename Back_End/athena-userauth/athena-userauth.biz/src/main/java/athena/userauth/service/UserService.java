package athena.userauth.service;


import athena.athenaframework.DTO.UserDTO;
import athena.userauth.domain.dataobject.User;
import athena.userauth.domain.dataobject.UserAll;

import java.time.LocalDate;
import java.util.List;

public interface UserService {

    /**
     * 保存用户（继承 BaseMapper 的方法）
     */
    boolean saveUser(User user);

    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return 用户信息
     */
    User selectByPhone(String phone);

    /**
     * 根据ID更新用户头像
     * @param icon 头像路径/URL
     * @return 是否更新成功
     */
    boolean updateIconById(String icon);

    /**
     * 根据ID更新用户昵称
     * @param nickName 新昵称
     * @return 是否更新成功
     */
    boolean updateNickNameById(String nickName);

    /**
     * 根据ID更新用户手机号
     * @param phone 新手机号
     * @return 是否更新成功
     */
    boolean updatePhoneById(String phone);

    UserDTO findById(Long id);

    List<UserDTO> findByUserIds(List<Long> userIds);

    /**
     * 根据ID获取用户全部详情信息
     * @param id 用户ID
     * @return 用户详情数据对象
     */
    UserAll findUserInfoById(Long id);

    /**
     * 更新用户生日
     * @param birthday 新的生日
     * @return 是否更新成功
     */
    boolean updateBirthday(LocalDate birthday);
}
