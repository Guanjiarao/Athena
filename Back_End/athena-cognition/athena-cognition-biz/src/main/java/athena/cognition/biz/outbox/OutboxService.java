package athena.cognition.biz.outbox;

import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Outbox writer (handoff section 11: events must be written inside the graph
 * transaction and only published after commit). First version is record-only:
 * events are persisted with status NEW and the {@link OutboxPublisher} relay
 * logs and marks them SENT; real push channels come later.
 */
@Service
public class OutboxService {

    public static final String GRAPH_UPDATED = "GRAPH_UPDATED";

    private final CognitionAgentJdbcRepository agentRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(CognitionAgentJdbcRepository agentRepository, ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
    }

    /** Joins the caller's transaction so the event rolls back together with the graph change. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveEvent(long userId, String eventType, Object payload) {
        agentRepository.insertOutboxEvent("event_" + UUID.randomUUID(), userId, eventType, writeJson(payload));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize json", ex);
        }
    }
}
