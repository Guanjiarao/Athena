package athena.cognition.biz.outbox;

import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.OutboxEventRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Record-only outbox relay: scans NEW events, logs the event type plus a
 * payload summary, then marks SENT. Delivery failures mark the event FAILED
 * and bump retry_count so a later relay can pick it up. Real push channels
 * (health home refresh / next-action notifications) replace the log line later.
 */
@Slf4j
@Component
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final int PAYLOAD_LOG_LIMIT = 200;

    private final CognitionAgentJdbcRepository agentRepository;

    public OutboxPublisher(CognitionAgentJdbcRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Scheduled(fixedDelayString = "10000")
    public void publishPendingEvents() {
        List<OutboxEventRow> events = agentRepository.listNewOutboxEvents(BATCH_SIZE);
        for (OutboxEventRow event : events) {
            try {
                log.info("outbox event: eventId={} type={} userId={} payload={}",
                        event.eventId(), event.eventType(), event.userId(), abbreviate(event.payloadJson()));
                agentRepository.markOutboxSent(event.eventId());
            } catch (RuntimeException ex) {
                log.warn("outbox event {} publish failed", event.eventId(), ex);
                agentRepository.markOutboxFailed(event.eventId());
            }
        }
    }

    private static String abbreviate(String payload) {
        if (payload == null) return null;
        return payload.length() <= PAYLOAD_LOG_LIMIT ? payload
                : payload.substring(0, PAYLOAD_LOG_LIMIT) + "...";
    }
}
