

package com.nageoffer.ai.ragent.user.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.enums.UserRole;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器
 *
 * <p>优先使用 gateway 透传的 userId 构建普通用户上下文，未透传时回退到 ragent 本地登录态。</p>
 */
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "userId";
    private static final String ATHENA_USER_PREFIX = "athena_";
    private static final String DEFAULT_GATEWAY_USER_PASSWORD = "ATHENA_GATEWAY_USER";
    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/583231?v=4";

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        // 异步调度请求跳过（SSE 完成回调会触发 asyncDispatch，此时 SaToken 上下文已丢失）
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        // 预检请求放行，避免 CORS 阻断
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String gatewayUserId = StrUtil.trimToNull(request.getHeader(USER_ID_HEADER));
        if (gatewayUserId != null) {
            UserContext.set(toLoginUser(findOrCreateGatewayUser(gatewayUserId)));
            return true;
        }

        String loginId = StpUtil.getLoginIdAsString();
        UserContext.set(toLoginUser(loadById(loginId)));
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        UserContext.clear();
    }

    private UserDO findOrCreateGatewayUser(String gatewayUserId) {
        String username = ATHENA_USER_PREFIX + gatewayUserId;
        UserDO existing = userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getDeleted, 0)
                        .last("limit 1")
        );
        if (existing != null) {
            return existing;
        }
        UserDO created = UserDO.builder()
                .username(username)
                .password(DEFAULT_GATEWAY_USER_PASSWORD)
                .role(UserRole.USER.getCode())
                .avatar(DEFAULT_AVATAR_URL)
                .build();
        userMapper.insert(created);
        return created;
    }

    private UserDO loadById(String id) {
        UserDO record = userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getId, id)
                        .eq(UserDO::getDeleted, 0)
                        .last("limit 1")
        );
        if (record == null) {
            throw new ClientException("用户不存在");
        }
        return record;
    }

    private LoginUser toLoginUser(UserDO user) {
        return LoginUser.builder()
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(StrUtil.blankToDefault(user.getAvatar(), DEFAULT_AVATAR_URL))
                .build();
    }
}
