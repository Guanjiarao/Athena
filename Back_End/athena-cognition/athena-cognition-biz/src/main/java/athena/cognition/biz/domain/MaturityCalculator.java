package athena.cognition.biz.domain;

import athena.cognition.biz.domain.CognitionModels.ActionFeedbackResult;
import athena.cognition.biz.domain.CognitionModels.EvidenceSourceType;
import athena.cognition.biz.domain.CognitionModels.FactLevel;
import athena.cognition.biz.domain.CognitionModels.Maturity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Section 10.2 maturity ladder as a pure function. Only confirmed body
 * observations count: BODY_RECORD or ACTION_FEEDBACK evidence at
 * SELF_REPORTED / OBSERVED level. KNOWLEDGE / QUESTION facts and UNCERTAIN
 * feedback never raise maturity. The ladder itself is monotonic per
 * calculation; callers combine with {@link #higherOf} so a single
 * NOT_OCCURRED can never downgrade a topic.
 */
public final class MaturityCalculator {

    /** Display timezone for date / cycle bucketing, consistent with cycleCount. */
    private static final ZoneOffset DISPLAY_ZONE = ZoneOffset.ofHours(8);

    /** Flattened evidence fact the ladder reasons about. */
    public record EvidenceFact(EvidenceSourceType sourceType, FactLevel factLevel,
                               ActionFeedbackResult feedbackResult, Instant occurredAt) {
    }

    private MaturityCalculator() {
    }

    public static Maturity calculate(List<EvidenceFact> facts) {
        List<Instant> confirmed = facts.stream()
                .filter(MaturityCalculator::isConfirmedBodyObservation)
                .map(EvidenceFact::occurredAt)
                .toList();
        // Cycle granularity follows the cycleCount convention: distinct months
        long months = confirmed.stream().filter(Objects::nonNull)
                .map(at -> YearMonth.from(at.atOffset(DISPLAY_ZONE))).distinct().count();
        // Direction stability cannot be evaluated from occurredAt alone; with
        // V1 data >=3 cycles is treated as stable (see P3-3 for record values)
        if (months >= 3) return Maturity.RELATIVELY_STABLE;
        if (months >= 2) return Maturity.REPEATED_PATTERN;
        long dates = confirmed.stream().filter(Objects::nonNull)
                .map(at -> LocalDate.from(at.atOffset(DISPLAY_ZONE))).distinct().count();
        if (dates >= 2) return Maturity.EARLY_LINK;
        // 0 confirmed records -> CLUE (still just clues, no body confirmation);
        // any confirmed record without a repeat -> INSUFFICIENT
        return confirmed.isEmpty() ? Maturity.CLUE : Maturity.INSUFFICIENT;
    }

    public static boolean isConfirmedBodyObservation(EvidenceFact fact) {
        if (fact.factLevel() != FactLevel.SELF_REPORTED && fact.factLevel() != FactLevel.OBSERVED) {
            return false; // KNOWLEDGE / QUESTION never become body facts (section 4.4)
        }
        if (fact.sourceType() == EvidenceSourceType.BODY_RECORD) {
            return true;
        }
        // UNCERTAIN feedback is saved but must not raise maturity (section 10.2)
        return fact.sourceType() == EvidenceSourceType.ACTION_FEEDBACK
                && fact.feedbackResult() != ActionFeedbackResult.UNCERTAIN;
    }

    /** Observation accumulates and never rolls back: keep the higher rung. */
    public static Maturity higherOf(Maturity current, Maturity calculated) {
        if (current == null) return calculated;
        return calculated.ordinal() > current.ordinal() ? calculated : current;
    }
}
