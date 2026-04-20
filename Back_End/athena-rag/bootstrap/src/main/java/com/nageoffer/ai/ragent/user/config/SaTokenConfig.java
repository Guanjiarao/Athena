/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.config.DemoModeInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SaToken 配置类
 * 配置登录拦截和用户上下文拦截器
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private static final String USER_ID_HEADER = "userId";
    private static final String ATHENA_RAG_ASK_PATH = "/athena/rag/ask";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String[] GATEWAY_USER_API_PATTERNS = {
            "/athena/rag/ask",
            "/rag/v3/**",
            "/api/ragent/rag/v3/**",
            "/conversations/**",
            "/api/ragent/conversations/**",
            "/conversations/messages/**",
            "/api/ragent/conversations/messages/**",
            "/user/me",
            "/api/ragent/user/me",
            "/knowledge-base/*/docs/upload",
            "/api/ragent/knowledge-base/*/docs/upload",
            "/knowledge-base/docs/*/chunk",
            "/api/ragent/knowledge-base/docs/*/chunk",
            "/**"
    };

    /**
     * 体验环境只读模式拦截器
     */
    private final DemoModeInterceptor demoModeInterceptor;

    /**
     * 用户上下文拦截器
     */
    private final UserContextInterceptor userContextInterceptor;

    /**
     * 添加拦截器配置
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 SaToken 登录拦截器
        registry.addInterceptor(new SaInterceptor(handler -> {
                    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs != null) {
                        HttpServletRequest request = attrs.getRequest();
                        // 异步调度请求跳过登录检查（SSE 完成回调会触发 asyncDispatch，此时 SaToken 上下文已丢失）
                        if (request.getDispatcherType() == DispatcherType.ASYNC) {
                            return;
                        }
                        // 预检请求直接放行，避免 CORS 被拦截
                        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                            return;
                        }
                        // 普通问答域接口：若已携带 gateway 注入的 userId，则不再要求 ragent 本地登录
                        if (isGatewayUserApi(request) && StrUtil.isNotBlank(request.getHeader(USER_ID_HEADER))) {
                            return;
                        }
                    }
                    // 其它情况仍走 ragent 本地登录态
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/error", ATHENA_RAG_ASK_PATH);

        registry.addInterceptor(demoModeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/error", ATHENA_RAG_ASK_PATH);

        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/error", ATHENA_RAG_ASK_PATH);
    }

    private boolean isGatewayUserApi(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String pattern : GATEWAY_USER_API_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
