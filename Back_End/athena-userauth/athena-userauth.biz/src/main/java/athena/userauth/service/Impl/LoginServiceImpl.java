package athena.userauth.service.Impl;


import athena.athenaframework.result.Result;
import athena.athenaframework.utils.RegexUtils;
import athena.userauth.VO.LoginFromVO;
import athena.userauth.domain.DTO.LoginDTO;
import athena.userauth.domain.dataobject.User;
import athena.userauth.domain.dataobject.UserInfo;
import athena.userauth.domain.mapper.UserInfoMapper;
import athena.userauth.domain.mapper.UserMapper;
import athena.userauth.service.LoginService;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static athena.athenaframework.utils.RedisConstants.LOGIN_CODE_KEY;
import static athena.athenaframework.utils.RedisConstants.LOGIN_CODE_TTL;


@Slf4j
@Service
public class LoginServiceImpl implements LoginService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String messagecode = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, messagecode, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        System.out.println("发送验证码阶段" + messagecode);
        return Result.ok(messagecode);
    }

    @Override
    public Result login(LoginFromVO loginForm) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        System.out.println("登录验证阶段" + loginForm.getCode());
        if (RegexUtils.isCodeInvalid(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        if (!loginForm.getCode().equals(stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone))) {
            return Result.fail("验证码错误");
        }

        User user = userMapper.selectByPhone(phone);
        LoginDTO loginToDTO = new LoginDTO();
        loginToDTO.setFirstLogin(false);
        if (user == null) {
            user = createuser(phone);
            loginToDTO.setFirstLogin(true);
        }
        Long userId = user.getId();
        StpUtil.login(userId);
        log.info("登录成功");
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        loginToDTO.setToken(tokenInfo.getTokenValue());
        return Result.ok(loginToDTO);
    }

    private User createuser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("athena用户" + RandomUtil.randomNumbers(6));
        userMapper.insert(user);

        Long userId = user.getId();
        if (userId == null) {
            return userMapper.selectByPhone(phone);
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setCity("");
        userInfo.setIntroduction("");
        userInfo.setFansTotal(0);
        userInfo.setFollowingTotal(0);
        userInfo.setGender((byte) 0);
        userInfo.setBirthday(null);
        userInfo.setCredits(0);
        userInfo.setLevel((byte) 0);
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfo.setContentTotal(0L);
        userInfo.setLikeTotal(0L);
        userInfo.setCollectTotal(0L);
        userInfoMapper.insert(userInfo);

        return user;
    }
}
