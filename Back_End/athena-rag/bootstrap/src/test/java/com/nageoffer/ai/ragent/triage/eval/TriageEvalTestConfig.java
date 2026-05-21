

package com.nageoffer.ai.ragent.triage.eval;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.config.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试配置：提供内存版的 SessionManager
 */
@TestConfiguration
public class TriageEvalTestConfig {

    @Bean
    @Primary
    public TriageSessionManager testTriageSessionManager(ObjectMapper objectMapper, TriageSessionProperties properties) {
        return new InMemoryTriageSessionManager(null, objectMapper, properties);
    }

    /**
     * 内存版 SessionManager 实现
     * 不依赖 Redis，使用 ConcurrentHashMap 存储会话
     */
    @Slf4j
    public static class InMemoryTriageSessionManager extends TriageSessionManager {
        private final ConcurrentHashMap<String, String> sessionStore = new ConcurrentHashMap<>();
        private final ObjectMapper objectMapper;

        public InMemoryTriageSessionManager(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, TriageSessionProperties properties) {
            super(redisTemplate, objectMapper, properties);
            this.objectMapper = objectMapper;
        }

        @Override
        public TriageContext getContext(String sessionId) {
            if (StrUtil.isBlank(sessionId)) {
                return null;
            }
            String payload = sessionStore.get(sessionId);
            if (StrUtil.isBlank(payload)) {
                log.debug("内存中未找到 session: sessionId={}", sessionId);
                return null;
            }
            try {
                TriageContext context = objectMapper.readValue(payload, TriageContext.class);
                context.ensureCollections();
                log.info("✓ 从内存恢复 session: sessionId={}, lastAskedSlots={}",
                    sessionId, context.getLastAskedSlots());
                return context;
            } catch (Exception ex) {
                log.warn("读取内存会话失败, sessionId={}", sessionId, ex);
                return null;
            }
        }

        @Override
        public void saveContext(TriageContext ctx) {
            if (ctx == null || StrUtil.isBlank(ctx.getSessionId())) {
                return;
            }
            ctx.ensureCollections();
            try {
                String payload = objectMapper.writeValueAsString(ctx);
                sessionStore.put(ctx.getSessionId(), payload);
                log.info("✓ 保存 session 到内存: sessionId={}, lastAskedSlots={}",
                    ctx.getSessionId(), ctx.getLastAskedSlots());
            } catch (Exception ex) {
                log.warn("保存内存会话失败, sessionId={}", ctx.getSessionId(), ex);
            }
        }
    }
}
