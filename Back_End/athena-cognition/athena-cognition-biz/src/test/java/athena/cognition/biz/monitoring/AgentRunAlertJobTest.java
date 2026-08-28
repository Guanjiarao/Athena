package athena.cognition.biz.monitoring;

import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.StatusCount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alert decision logic: FAILED ratio (with a minimum sample), BLOCKED / STALE
 * counts, and the silence detector over consecutive empty windows.
 */
class AgentRunAlertJobTest {

    private final AgentRunAlertJob job = new AgentRunAlertJob(null);

    @Test
    void failedRatioAboveThresholdWithEnoughSamplesWarns() {
        AgentRunAlertJob.AlertVerdict verdict = job.evaluate(
                List.of(new StatusCount("FAILED", 4), new StatusCount("SUCCEEDED", 6)), 0);

        assertThat(verdict.warnings()).anySatisfy(w -> assertThat(w)
                .startsWith("type=FAILED_RATIO").contains("failed=4").contains("total=10"));
        assertThat(verdict.emptyStreak()).isZero();
        assertThat(verdict.silent()).isFalse();
    }

    @Test
    void failedRatioWithTooFewSamplesDoesNotWarn() {
        AgentRunAlertJob.AlertVerdict verdict = job.evaluate(
                List.of(new StatusCount("FAILED", 2)), 0);

        assertThat(verdict.warnings()).isEmpty();
    }

    @Test
    void failedRatioBelowThresholdDoesNotWarn() {
        AgentRunAlertJob.AlertVerdict verdict = job.evaluate(
                List.of(new StatusCount("FAILED", 1), new StatusCount("SUCCEEDED", 9)), 0);

        assertThat(verdict.warnings()).isEmpty();
    }

    @Test
    void blockedAndStaleCountsAtThresholdWarn() {
        AgentRunAlertJob.AlertVerdict verdict = job.evaluate(
                List.of(new StatusCount("BLOCKED", 3), new StatusCount("STALE", 4),
                        new StatusCount("SUCCEEDED", 3)), 0);

        assertThat(verdict.warnings()).anySatisfy(w -> assertThat(w).startsWith("type=BLOCKED"));
        assertThat(verdict.warnings()).anySatisfy(w -> assertThat(w).startsWith("type=STALE"));
    }

    @Test
    void blockedBelowThresholdDoesNotWarn() {
        AgentRunAlertJob.AlertVerdict verdict = job.evaluate(
                List.of(new StatusCount("BLOCKED", 2), new StatusCount("SUCCEEDED", 8)), 0);

        assertThat(verdict.warnings()).isEmpty();
    }

    @Test
    void silenceDetectedAfterThreeConsecutiveEmptyWindows() {
        AgentRunAlertJob.AlertVerdict first = job.evaluate(List.of(), 0);
        AgentRunAlertJob.AlertVerdict second = job.evaluate(List.of(), first.emptyStreak());
        AgentRunAlertJob.AlertVerdict third = job.evaluate(List.of(), second.emptyStreak());

        assertThat(first.silent()).isFalse();
        assertThat(second.silent()).isFalse();
        assertThat(third.silent()).isTrue();
        assertThat(third.emptyStreak()).isEqualTo(AgentRunAlertJob.SILENT_WINDOWS);
        assertThat(third.warnings()).isEmpty();
    }

    @Test
    void nonEmptyWindowResetsTheEmptyStreak() {
        AgentRunAlertJob.AlertVerdict verdict = job.evaluate(List.of(new StatusCount("SUCCEEDED", 1)), 2);

        assertThat(verdict.emptyStreak()).isZero();
        assertThat(verdict.silent()).isFalse();
    }
}
