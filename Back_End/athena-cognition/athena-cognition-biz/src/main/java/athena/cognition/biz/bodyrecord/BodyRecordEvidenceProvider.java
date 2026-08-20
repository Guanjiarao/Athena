package athena.cognition.biz.bodyrecord;

import java.time.Instant;
import java.util.List;

/**
 * Extension point for RULE_2 (contract section 10.1): user-confirmed body
 * records that can count towards the digest threshold.
 *
 * P3-3 will back this with the existing athena-insight -> athena-record
 * daily_record Feign read chain (contract section 4.8). Until then the default
 * bean confirms nothing, so RULE_2 never fires and no fake data is produced.
 */
public interface BodyRecordEvidenceProvider {

    /**
     * Returns user-confirmed body records relevant to a candidate topic group.
     * Implementations must only return records the user actually recorded;
     * never fabricate data to reach the threshold.
     */
    List<ConfirmedBodyRecord> findConfirmedBodyRecords(long userId, String suggestedTopicId, String suggestedTopicTitle);

    /** Snapshot of a confirmed daily_record row (raw record stays in athena-record). */
    record ConfirmedBodyRecord(String dailyRecordId, String summary, Instant occurredAt) {
    }
}
