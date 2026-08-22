package athena.cognition.biz.rpc;

import java.time.LocalDate;

/**
 * Snapshot of athena-record's daily_record row as returned by the internal
 * insight endpoint (see RecordInsightController in athena-record-biz). The
 * cognition service keeps only display snapshots in evidence; the raw record
 * stays owned by athena-record (contract section 4.8).
 */
public record DailyRecordSnapshot(
        Long id,
        Long userId,
        LocalDate recordDate,
        Integer modeType,
        Integer recordItemId,
        String recordValue
) {
}
