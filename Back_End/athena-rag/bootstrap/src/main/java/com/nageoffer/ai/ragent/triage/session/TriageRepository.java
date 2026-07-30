

package com.nageoffer.ai.ragent.triage.session;

import com.nageoffer.ai.ragent.triage.model.TriageContext;

/**
 * Repository abstraction for storing a finished triage session.
 */
public interface TriageRepository {

    void save(TriageContext context);

    TriageContext findBySessionId(String sessionId);
}
