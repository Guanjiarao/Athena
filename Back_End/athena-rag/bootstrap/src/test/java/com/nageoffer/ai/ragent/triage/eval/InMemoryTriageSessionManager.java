

package com.nageoffer.ai.ragent.triage.eval;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试专用的内存版 SessionManager
 * 用于在测试环境中替代 Redis，避免外部依赖
 */
@Slf4j
@RequiredArgsConstructor
public class InMemoryTriageSessionManager {

    private final ConcurrentHashMap<String, String> sessionStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TriageContext getContext(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        String payload = sessionStore.get(sessionId);
        if (StrUtil.isBlank(payload)) {
            return null;
        }
        try {
            TriageContext context = objectMapper.readValue(payload, TriageContext.class);
            context.ensureCollections();
            log.debug("从内存恢复 session: sessionId={}, lastAskedSlots={}",
                sessionId, context.getLastAskedSlots());
            return context;
        } catch (Exception ex) {
            log.warn("读取内存会话失败, sessionId={}", sessionId, ex);
            return null;
        }
    }

    public void saveContext(TriageContext ctx) {
        if (ctx == null || StrUtil.isBlank(ctx.getSessionId())) {
            return;
        }
        ctx.ensureCollections();
        try {
            String payload = objectMapper.writeValueAsString(ctx);
            sessionStore.put(ctx.getSessionId(), payload);
            log.debug("保存 session 到内存: sessionId={}, lastAskedSlots={}",
                ctx.getSessionId(), ctx.getLastAskedSlots());
        } catch (Exception ex) {
            log.warn("保存内存会话失败, sessionId={}", ctx.getSessionId(), ex);
        }
    }
}
