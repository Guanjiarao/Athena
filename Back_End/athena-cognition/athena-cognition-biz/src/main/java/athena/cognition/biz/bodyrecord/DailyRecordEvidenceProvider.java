package athena.cognition.biz.bodyrecord;

import athena.athenaframework.result.Result;
import athena.cognition.biz.rpc.DailyRecordSnapshot;
import athena.cognition.biz.rpc.RecordInternalFeignApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RULE_2 / section 4.8 implementation backed by the existing athena-record
 * internal read chain. Deterministic selection only: recordItemId in the
 * agreed symptom/mood set (see {@link RecordItemMeaning}), most recent first.
 * Degradation: when athena-record is unreachable, RULE_2 is treated as
 * unsatisfied (RULE_1 and user-requested digests are never blocked) and
 * liveness checks fail open; warnings log ids and reason classes only, never
 * record content (section 13.4).
 */
@Slf4j
@Component
public class DailyRecordEvidenceProvider implements BodyRecordEvidenceProvider {

    /** Lookback window for both RULE_2 candidates and liveness checks. */
    private static final int LOOKBACK_DAYS = 366;
    private static final ZoneOffset DISPLAY_ZONE = ZoneOffset.ofHours(8);

    private final RecordInternalFeignApi recordApi;

    public DailyRecordEvidenceProvider(RecordInternalFeignApi recordApi) {
        this.recordApi = recordApi;
    }

    @Override
    public List<ConfirmedBodyRecord> findConfirmedBodyRecords(long userId, String suggestedTopicId,
                                                              String suggestedTopicTitle) {
        List<DailyRecordSnapshot> records = fetchRecords(userId);
        if (records == null) {
            // Degraded: RULE_2 counts as unsatisfied, nothing is fabricated
            return List.of();
        }
        return records.stream()
                .filter(record -> RecordItemMeaning.isBodyFactItem(record.recordItemId()))
                .max(Comparator.comparing(DailyRecordSnapshot::recordDate))
                .map(record -> new ConfirmedBodyRecord(
                        String.valueOf(record.id()),
                        RecordItemMeaning.summaryOf(record.recordItemId(), record.recordValue()),
                        record.recordDate().atStartOfDay().toInstant(DISPLAY_ZONE)))
                .map(List::of)
                .orElse(List.of());
    }

    @Override
    public Set<String> filterExistingRecordIds(long userId, Collection<String> dailyRecordIds) {
        if (dailyRecordIds == null || dailyRecordIds.isEmpty()) {
            return Set.of();
        }
        List<DailyRecordSnapshot> records = fetchRecords(userId);
        if (records == null) {
            // Degraded: fail open, an outage must not silently kill evidence
            return new HashSet<>(dailyRecordIds);
        }
        Set<String> alive = records.stream().map(record -> String.valueOf(record.id()))
                .collect(Collectors.toSet());
        return dailyRecordIds.stream().filter(alive::contains).collect(Collectors.toSet());
    }

    /** Returns null on degradation, an empty list when the user truly has none. */
    private List<DailyRecordSnapshot> fetchRecords(long userId) {
        try {
            LocalDate end = LocalDate.now();
            Result<List<DailyRecordSnapshot>> result = recordApi.getRecords(userId,
                    end.minusDays(LOOKBACK_DAYS).toString(), end.toString());
            if (result == null || result.getCode() == null || result.getCode() != 200
                    || result.getData() == null) {
                log.warn("[DailyRecordEvidenceProvider] 读取身体记录失败, userId={}", userId);
                return null;
            }
            return result.getData();
        } catch (Exception ex) {
            log.warn("[DailyRecordEvidenceProvider] 身体记录服务不可用, userId={}, reason={}",
                    userId, ex.getClass().getSimpleName());
            return null;
        }
    }
}
