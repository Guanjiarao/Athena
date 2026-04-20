package athena.userauth.service.Impl;


import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.utils.UserIdHolder;
import athena.userauth.domain.dataobject.User;
import athena.userauth.domain.dataobject.UserAll;
import athena.userauth.domain.dataobject.UserInfo;
import athena.userauth.domain.mapper.UserInfoMapper;
import athena.userauth.domain.mapper.UserMapper;
import athena.userauth.service.UserService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    public boolean saveUser(User user) {
        return userMapper.insert(user) > 0;
    }

    @Override
    public User selectByPhone(String phone) {
        return userMapper.selectByPhone(phone);
    }

    @Override
    public boolean updateIconById(String icon) {
        Long id = UserIdHolder.getUserId();
        if (id == null || icon == null || icon.trim().isEmpty()) {
            return false;
        }
        return updateUserField(id, User::getIcon, icon);
    }

    @Override
    public boolean updateNickNameById(String nickName) {
        Long id = UserIdHolder.getUserId();
        if (id == null || nickName == null || nickName.trim().isEmpty()) {
            return false;
        }
        return updateUserField(id, User::getNickName, nickName);
    }

    @Override
    public boolean updatePhoneById(String phone) {
        Long id = UserIdHolder.getUserId();
        if (id == null || phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return updateUserField(id, User::getPhone, phone);
    }

    @Override
    public UserDTO findById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return null;
        UserDTO userDTO = new UserDTO();

        userDTO.setIcon(user.getIcon());
        userDTO.setPriority(user.getPriority());
        userDTO.setNickName(user.getNickName());
        userDTO.setUserId(user.getId());
        return userDTO;
    }

    @Override
    public List<UserDTO> findByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyList();
        return userMapper.selectByUserIds(userIds);
    }

    @Override
    public UserAll findUserInfoById(Long id) {
        if (id == null) id = UserIdHolder.getUserId();
        UserInfo userInfoById = userInfoMapper.selectById(id);
        UserDTO basic = findById(id);
        UserAll userAll = new UserAll();
        BeanUtils.copyProperties(basic, userAll);
        BeanUtils.copyProperties(userInfoById, userAll);
        return userAll;
    }

    @Override
    public boolean updateBirthday(LocalDate birthday) {
        Long currentUserId = UserIdHolder.getUserId();
        if (currentUserId == null) return false;
        int rows = userInfoMapper.updateBirthday(currentUserId, birthday);
        return rows > 0;
    }

    private <T> boolean updateUserField(Long userId,
                                        com.baomidou.mybatisplus.core.toolkit.support.SFunction<User, T> field,
                                        T value) {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId)
                .set(field, value)
                .set(User::getUpdateTime, LocalDateTime.now());
        return userMapper.update(null, updateWrapper) > 0;
    }
}
