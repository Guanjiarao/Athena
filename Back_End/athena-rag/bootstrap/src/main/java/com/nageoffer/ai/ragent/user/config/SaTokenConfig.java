

package com.nageoffer.ai.ragent.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.rag.config.DemoModeInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SaToken 配置类
 * 配置登录拦截和用户上下文拦截器
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 体验环境只读模式拦截器
     */
    private final DemoModeInterceptor demoModeInterceptor;

    /**
     * 用户上下文拦截器
     */
    private final UserContextInterceptor userContextInterceptor;

    /**
     * Athena 自动认证拦截器
     */
    private final AthenaAutoAuthInterceptor athenaAutoAuthInterceptor;

    /**
     * 添加拦截器配置
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Athena 自动认证拦截器（必须在 SaToken 登录检查之前）
        registry.addInterceptor(athenaAutoAuthInterceptor)
                // 拦截 triage 和 rag 相关路径
                .addPathPatterns("/triage/**", "/rag/**", "/api/ragent/**")
                .order(0); // 最高优先级

        // 注册 SaToken 登录拦截器
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // 异步调度请求跳过登录检查（SSE 完成回调会触发 asyncDispatch，此时 SaToken 上下文已丢失）
                    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs != null) {
                        HttpServletRequest request = attrs.getRequest();
                        // 判断是否为异步调度请求，如果是则跳过登录检查
                        if (request.getDispatcherType() == DispatcherType.ASYNC) {
                            return;
                        }
                        // 预检请求直接放行，避免 CORS 被拦截
                        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                            return;
                        }
                        // 来自 Athena 后端的内部服务调用直接放行
                        if (isFromAthenaServer(request)) {
                            return;
                        }
                    }
                    // 执行登录检查
                    StpUtil.checkLogin();
                }))
                // 拦截所有路径
                .addPathPatterns("/**")
                // 排除认证相关路径和错误页面
                .excludePathPatterns("/auth/**", "/error");

        // 注册体验环境只读模式拦截器
        registry.addInterceptor(demoModeInterceptor)
                // 拦截所有路径
                .addPathPatterns("/**")
                // 排除认证相关路径和错误页面
                .excludePathPatterns("/auth/**", "/error");

        // 注册用户上下文拦截器
        registry.addInterceptor(userContextInterceptor)
                // 拦截所有路径
                .addPathPatterns("/**")
                // 排除认证相关路径和错误页面
                .excludePathPatterns("/auth/**", "/error");
    }

    /**
     * 判断请求是否来自 Athena 后端服务
     */
    private boolean isFromAthenaServer(HttpServletRequest request) {
        // 方式1：通过 userId header 判断（Athena 后端调用时会设置 userId header）
        String userId = request.getHeader("userId");
        if (userId != null && !userId.isEmpty()) {
            return true;
        }
        // 方式2：通过 Referer 或 Origin 判断
        String origin = request.getHeader("Origin");
        if (origin != null && origin.contains("13715")) {
            return true;
        }
        // 方式3：通过自定义 header 判断（Athena 后端调用时带上 X-Athena-Internal: true）
        String athenaHeader = request.getHeader("X-Athena-Internal");
        if ("true".equalsIgnoreCase(athenaHeader)) {
            return true;
        }
        // 方式4：通过来源端口判断（本地调用）
        int remotePort = request.getRemotePort();
        String remoteHost = request.getRemoteHost();
        if (("127.0.0.1".equals(remoteHost) || "localhost".equals(remoteHost) || "0:0:0:0:0:0:0:1".equals(remoteHost))) {
            // 本地服务间调用，检查是否带了 Athena 的 Bearer token
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                return true;
            }
        }
        return false;
    }
}
