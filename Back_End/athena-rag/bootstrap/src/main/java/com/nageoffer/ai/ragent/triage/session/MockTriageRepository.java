

package com.nageoffer.ai.ragent.triage.session;

import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock repository used to simulate persistence for demos and tests.
 */
@Repository
@Profile("test")
public class MockTriageRepository implements TriageRepository {

    private final ConcurrentHashMap<String, TriageContext> storage = new ConcurrentHashMap<>();

    @Override
    public void save(TriageContext context) {
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            return;
        }
        context.ensureCollections();
        storage.put(context.getSessionId(), context);
    }

    @Override
    public TriageContext findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return storage.get(sessionId);
    }
}
