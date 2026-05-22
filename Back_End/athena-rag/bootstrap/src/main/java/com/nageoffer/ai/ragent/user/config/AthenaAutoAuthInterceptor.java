

package com.nageoffer.ai.ragent.user.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Athena 自动认证拦截器
 * <p>
 * 当 Athena 前端调用 RAG 接口时，自动注册并登录用户
 * 通过 Authorization header 中的 token 识别 Athena 用户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AthenaAutoAuthInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 异步调度请求跳过（SSE 完成回调会触发 asyncDispatch，此时 SaToken 上下文已丢失）
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC) {
            return true;
        }

        // 如果已经登录，直接放行
        if (StpUtil.isLogin()) {
            return true;
        }

        // 获取 Authorization header
        String authorization = request.getHeader("Authorization");
        if (StrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return true;
        }

        String token = authorization.substring(7);
        if (StrUtil.isBlank(token)) {
            return true;
        }

        // 从 token 中提取用户标识（Athena 使用 token 作为用户唯一标识）
        String username = "athena_" + token.substring(0, Math.min(16, token.length()));

        try {
            // 查询用户是否存在
            LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                    .eq(UserDO::getUsername, username);
            UserDO user = userMapper.selectOne(queryWrapper);

            if (user == null) {
                // 用户不存在，自动注册
                user = new UserDO();
                user.setUsername(username);
                user.setPassword("athena" + token.substring(0, Math.min(16, token.length())));
                user.setRole(UserRole.USER.name());
                userMapper.insert(user);
                log.info("[AthenaAutoAuth] 用户自动注册: username={}", username);
            }

            // 自动登录
            StpUtil.login(user.getId());
            log.info("[AthenaAutoAuth] 用户自动登录: username={}, userId={}", username, user.getId());

        } catch (Exception e) {
            log.error("[AthenaAutoAuth] 自动认证失败: username={}", username, e);
        }

        return true;
    }
}
