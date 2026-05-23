

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
 * <p>
 * 认证方式：
 * 1. 优先使用 userId header（Athena Gateway 会从 Redis 查询 token 对应的用户 ID 并设置到 header）
 * 2. 降级使用 Authorization header 中的 token（用于直接调用，不经过 Gateway）
 * <p>
 * Username 格式：
 * - 通过 userId header：athena_{userId}
 * - 通过 token：athena_token_{token前16位}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AthenaAutoAuthInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info("[AthenaAutoAuth] ===== 拦截器被触发 ===== path={}, method={}",
                request.getRequestURI(), request.getMethod());

        // 异步调度请求跳过（SSE 完成回调会触发 asyncDispatch，此时 SaToken 上下文已丢失）
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC) {
            log.info("[AthenaAutoAuth] 异步请求，跳过");
            return true;
        }

        // 如果已经登录，直接放行
        if (StpUtil.isLogin()) {
            log.info("[AthenaAutoAuth] 用户已登录，直接放行: path={}", request.getRequestURI());
            return true;
        }

        // 优先从 userId header 获取用户 ID（Athena Gateway 会设置）
        String userIdHeader = request.getHeader("userId");
        String authHeader = request.getHeader("Authorization");
        log.info("[AthenaAutoAuth] 收到请求: path={}, userId={}, Authorization={}",
                request.getRequestURI(), userIdHeader, authHeader != null ? "存在" : "不存在");

        if (StrUtil.isNotBlank(userIdHeader) && !"null".equals(userIdHeader)) {
            try {
                Long userId = Long.parseLong(userIdHeader);
                String username = "athena_" + userId;
                log.info("[AthenaAutoAuth] 解析 userId header 成功: userId={}, username={}", userId, username);

                // 查询用户是否存在
                LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username);
                UserDO user = userMapper.selectOne(queryWrapper);

                if (user == null) {
                    // 用户不存在，自动注册
                    user = new UserDO();
                    user.setUsername(username);
                    user.setPassword("athena" + userId);
                    user.setRole(UserRole.USER.name());
                    userMapper.insert(user);
                    log.info("[AthenaAutoAuth] 用户自动注册成功: username={}, athenaUserId={}", username, userId);
                } else {
                    log.info("[AthenaAutoAuth] 用户已存在: username={}, ragUserId={}", username, user.getId());
                }

                // 自动登录
                StpUtil.login(user.getId());
                log.info("[AthenaAutoAuth] 用户自动登录成功: username={}, ragUserId={}, athenaUserId={}", username, user.getId(), userId);
                return true;

            } catch (Exception e) {
                log.error("[AthenaAutoAuth] 通过 userId header 自动认证失败: userId={}", userIdHeader, e);
            }
        } else {
            log.warn("[AthenaAutoAuth] userId header 为空，尝试降级方案（使用 Authorization token）");
        }

        // 降级方案：从 Authorization header 获取 token（用于直接调用，不经过 Gateway）
        String authorization = request.getHeader("Authorization");
        if (StrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return true;
        }

        String token = authorization.substring(7);
        if (StrUtil.isBlank(token)) {
            return true;
        }

        // 使用 token 前16位作为用户标识
        String username = "athena_token_" + token.substring(0, Math.min(16, token.length()));

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
                log.info("[AthenaAutoAuth] 用户自动注册(token): username={}", username);
            }

            // 自动登录
            StpUtil.login(user.getId());
            log.info("[AthenaAutoAuth] 用户自动登录(token): username={}, userId={}", username, user.getId());

        } catch (Exception e) {
            log.error("[AthenaAutoAuth] 通过 token 自动认证失败: username={}", username, e);
        }

        return true;
    }
}
