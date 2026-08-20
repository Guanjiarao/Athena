package athena.cognition.biz.domain;

import athena.cognition.biz.domain.CognitionModels.ActionFeedbackResult;
import athena.cognition.biz.domain.CognitionModels.EvidenceSourceType;
import athena.cognition.biz.domain.CognitionModels.FactLevel;
import athena.cognition.biz.domain.CognitionModels.Maturity;
import athena.cognition.biz.domain.MaturityCalculator.EvidenceFact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Section 10.2 ladder boundaries and exclusion rules. */
class MaturityCalculatorTest {

    @Test
    void zeroConfirmedRecordsIsClue() {
        assertThat(MaturityCalculator.calculate(List.of())).isEqualTo(Maturity.CLUE);
    }

    @Test
    void oneConfirmedRecordIsInsufficient() {
        assertThat(MaturityCalculator.calculate(List.of(bodyRecord("2026-08-10"))))
                .isEqualTo(Maturity.INSUFFICIENT);
    }

    @Test
    void twoRecordsOnTheSameDateStayInsufficient() {
        assertThat(MaturityCalculator.calculate(List.of(
                bodyRecord("2026-08-10T02:00:00Z"), bodyRecord("2026-08-10T09:00:00Z"))))
                .isEqualTo(Maturity.INSUFFICIENT);
    }

    @Test
    void twoDistinctDatesIsEarlyLink() {
        assertThat(MaturityCalculator.calculate(List.of(
                bodyRecord("2026-08-10"), bodyRecord("2026-08-12"))))
                .isEqualTo(Maturity.EARLY_LINK);
    }

    @Test
    void twoDistinctCyclesIsRepeatedPattern() {
        assertThat(MaturityCalculator.calculate(List.of(
                bodyRecord("2026-07-10"), bodyRecord("2026-08-10"))))
                .isEqualTo(Maturity.REPEATED_PATTERN);
    }

    @Test
    void threeCyclesIsRelativelyStable() {
        assertThat(MaturityCalculator.calculate(List.of(
                bodyRecord("2026-06-10"), bodyRecord("2026-07-10"), bodyRecord("2026-08-10"))))
                .isEqualTo(Maturity.RELATIVELY_STABLE);
    }

    @Test
    void knowledgeAndQuestionFactsNeverCount() {
        assertThat(MaturityCalculator.calculate(List.of(
                new EvidenceFact(EvidenceSourceType.BODY_RECORD, FactLevel.KNOWLEDGE, null, at("2026-07-10")),
                new EvidenceFact(EvidenceSourceType.CLUE, FactLevel.QUESTION, null, at("2026-08-10")))))
                .isEqualTo(Maturity.CLUE);
    }

    @Test
    void articleClueFactsAreNotConfirmedBodyRecords() {
        assertThat(MaturityCalculator.calculate(List.of(
                new EvidenceFact(EvidenceSourceType.CLUE, FactLevel.SELF_REPORTED, null, at("2026-07-10")),
                new EvidenceFact(EvidenceSourceType.CLUE, FactLevel.SELF_REPORTED, null, at("2026-08-10")))))
                .isEqualTo(Maturity.CLUE);
    }

    @Test
    void uncertainFeedbackNeverRaisesMaturity() {
        // two UNCERTAIN observations across cycles: nothing counts
        assertThat(MaturityCalculator.calculate(List.of(
                feedback(ActionFeedbackResult.UNCERTAIN, "2026-07-10"),
                feedback(ActionFeedbackResult.UNCERTAIN, "2026-08-10"))))
                .isEqualTo(Maturity.CLUE);
        // one real occurrence plus one uncertain: only the occurrence counts
        assertThat(MaturityCalculator.calculate(List.of(
                feedback(ActionFeedbackResult.OCCURRED, "2026-07-10"),
                feedback(ActionFeedbackResult.UNCERTAIN, "2026-08-10"))))
                .isEqualTo(Maturity.INSUFFICIENT);
    }

    @Test
    void notOccurredCountsAsValidObservation() {
        assertThat(MaturityCalculator.calculate(List.of(
                feedback(ActionFeedbackResult.NOT_OCCURRED, "2026-07-10"),
                feedback(ActionFeedbackResult.OCCURRED, "2026-08-10"))))
                .isEqualTo(Maturity.REPEATED_PATTERN);
    }

    @Test
    void higherOfNeverDowngrades() {
        assertThat(MaturityCalculator.higherOf(Maturity.REPEATED_PATTERN, Maturity.INSUFFICIENT))
                .isEqualTo(Maturity.REPEATED_PATTERN);
        assertThat(MaturityCalculator.higherOf(Maturity.CLUE, Maturity.EARLY_LINK))
                .isEqualTo(Maturity.EARLY_LINK);
        assertThat(MaturityCalculator.higherOf(null, Maturity.CLUE)).isEqualTo(Maturity.CLUE);
    }

    private EvidenceFact bodyRecord(String dateTime) {
        return new EvidenceFact(EvidenceSourceType.BODY_RECORD, FactLevel.SELF_REPORTED, null, at(dateTime));
    }

    private EvidenceFact feedback(ActionFeedbackResult result, String dateTime) {
        return new EvidenceFact(EvidenceSourceType.ACTION_FEEDBACK, FactLevel.SELF_REPORTED, result, at(dateTime));
    }

    private static Instant at(String dateTime) {
        String normalized = dateTime.length() == 10 ? dateTime + "T04:00:00Z" : dateTime;
        return Instant.parse(normalized);
    }
}
