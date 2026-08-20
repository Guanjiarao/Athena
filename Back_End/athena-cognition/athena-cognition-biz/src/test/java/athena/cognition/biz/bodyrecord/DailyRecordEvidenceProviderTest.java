package athena.cognition.biz.bodyrecord;

import athena.athenaframework.result.Result;
import athena.cognition.biz.bodyrecord.BodyRecordEvidenceProvider.ConfirmedBodyRecord;
import athena.cognition.biz.rpc.DailyRecordSnapshot;
import athena.cognition.biz.rpc.RecordInternalFeignApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRecordEvidenceProviderTest {

    private static final long USER_ID = 7L;

    @Mock
    private RecordInternalFeignApi recordApi;

    private DailyRecordEvidenceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DailyRecordEvidenceProvider(recordApi);
    }

    @Test
    void mapsSymptomAndMoodItemsWithAgreedMeaning() {
        stubRecords(
                new DailyRecordSnapshot(2001L, USER_ID, LocalDate.of(2026, 8, 1), 0, 3, "乳房胀痛"),
                new DailyRecordSnapshot(2002L, USER_ID, LocalDate.of(2026, 8, 5), 0, 4, "低落"));

        List<ConfirmedBodyRecord> result = provider.findConfirmedBodyRecords(USER_ID, null, "经前情绪变化");

        assertThat(result).hasSize(1);
        // most recent record wins; summary is a display snapshot, sourceId is the real daily_record.id
        assertThat(result.get(0).dailyRecordId()).isEqualTo("2002");
        assertThat(result.get(0).summary()).isEqualTo("心情：低落");
        assertThat(result.get(0).occurredAt()).isEqualTo(Instant.parse("2026-08-04T16:00:00Z"));
    }

    @Test
    void recordItemMeaningMappingIsExplicit() {
        assertThat(RecordItemMeaning.isBodyFactItem(3)).isTrue();
        assertThat(RecordItemMeaning.isBodyFactItem(4)).isTrue();
        assertThat(RecordItemMeaning.isBodyFactItem(1)).isFalse();
        assertThat(RecordItemMeaning.isBodyFactItem(null)).isFalse();
        assertThat(RecordItemMeaning.summaryOf(3, "乳房胀痛")).isEqualTo("症状：乳房胀痛");
        assertThat(RecordItemMeaning.summaryOf(4, "低落")).isEqualTo("心情：低落");
    }

    @Test
    void ignoresUnmappedRecordItems() {
        stubRecords(
                new DailyRecordSnapshot(2001L, USER_ID, LocalDate.of(2026, 8, 1), 0, 1, "x"),
                new DailyRecordSnapshot(2002L, USER_ID, LocalDate.of(2026, 8, 5), 0, 99, "y"));

        assertThat(provider.findConfirmedBodyRecords(USER_ID, null, null)).isEmpty();
    }

    @Test
    void picksMostRecentBodyFactRecord() {
        stubRecords(
                new DailyRecordSnapshot(2001L, USER_ID, LocalDate.of(2026, 7, 1), 0, 3, "头痛"),
                new DailyRecordSnapshot(2002L, USER_ID, LocalDate.of(2026, 8, 1), 0, 3, "腹痛"));

        List<ConfirmedBodyRecord> result = provider.findConfirmedBodyRecords(USER_ID, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dailyRecordId()).isEqualTo("2002");
    }

    @Test
    void feignFailureDegradesRule2ToUnsatisfied() {
        when(recordApi.getRecords(anyLong(), any(), any())).thenThrow(new RuntimeException("timeout"));

        assertThat(provider.findConfirmedBodyRecords(USER_ID, null, null)).isEmpty();
    }

    @Test
    void non200ResultDegradesRule2ToUnsatisfied() {
        when(recordApi.getRecords(anyLong(), any(), any())).thenReturn(Result.fail("boom"));

        assertThat(provider.findConfirmedBodyRecords(USER_ID, null, null)).isEmpty();
    }

    @Test
    void livenessCheckKeepsOnlyExistingRecords() {
        stubRecords(new DailyRecordSnapshot(2001L, USER_ID, LocalDate.of(2026, 8, 1), 0, 3, "乳房胀痛"));

        Set<String> alive = provider.filterExistingRecordIds(USER_ID, List.of("2001", "2002"));

        assertThat(alive).containsExactly("2001");
    }

    @Test
    void livenessCheckFailsOpenOnOutage() {
        when(recordApi.getRecords(anyLong(), any(), any())).thenThrow(new RuntimeException("timeout"));

        Set<String> alive = provider.filterExistingRecordIds(USER_ID, List.of("2001", "2002"));

        assertThat(alive).containsExactlyInAnyOrder("2001", "2002");
    }

    private void stubRecords(DailyRecordSnapshot... records) {
        when(recordApi.getRecords(anyLong(), any(), any())).thenReturn(Result.ok(List.of(records)));
    }
}
