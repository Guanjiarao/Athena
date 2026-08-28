package athena.cognition.biz.monitoring;

import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.StatusCount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight agent-run alerting: every 5 minutes, aggregate the final_status
 * distribution of cognition_agent_run over the last 15 minutes and emit
 * uniform WARN lines (type=... window=... value=... threshold=...) that a
 * log-scraping alert platform can pick up later. Thresholds are constants for
 * now — externalize to @ConfigurationProperties when a real alert channel lands.
 */
@Slf4j
@Component
public class AgentRunAlertJob {

    /** FAILED 占比告警阈值：窗口内 FAILED/total 超过 30% 且样本达标时告警。 */
    static final double FAILED_RATIO_THRESHOLD = 0.30;
    /** 比例判定的最小样本量：窗口内运行总数不足 5 条不判定，防小样本抖动。 */
    static final long FAILED_RATIO_MIN_SAMPLE = 5;
    /** 窗口内 BLOCKED 运行数达到 3 告警（工作流被策略拦截，多为异常输入）。 */
    static final long BLOCKED_WARN_COUNT = 3;
    /** 窗口内 STALE 运行数达到 3 告警（图谱版本冲突频发，多为并发写入异常）。 */
    static final long STALE_WARN_COUNT = 3;
    /** 连续 3 个窗口无任何运行记录视为服务静默（info 级提示即可）。 */
    static final int SILENT_WINDOWS = 3;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final CognitionAgentJdbcRepository agentRepository;

    private int consecutiveEmptyWindows;

    public AgentRunAlertJob(CognitionAgentJdbcRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Scheduled(fixedDelayString = "300000", initialDelayString = "300000")
    public void checkAgentRuns() {
        List<StatusCount> stats = agentRepository.countRunStatusSince(Instant.now().minus(WINDOW));
        AlertVerdict verdict = evaluate(stats, consecutiveEmptyWindows);
        consecutiveEmptyWindows = verdict.emptyStreak();
        for (String warning : verdict.warnings()) {
            log.warn("[AgentRunAlert] {}", warning);
        }
        if (verdict.silent()) {
            log.info("[AgentRunAlert] type=SILENT window=15m emptyStreak={} threshold={} (连续无运行记录，服务静默)",
                    verdict.emptyStreak(), SILENT_WINDOWS);
        }
    }

    /** Pure decision logic, separated from scheduling/logging for unit testing. */
    AlertVerdict evaluate(List<StatusCount> stats, int emptyStreak) {
        long total = stats.stream().mapToLong(StatusCount::count).sum();
        if (total == 0) {
            int streak = emptyStreak + 1;
            return new AlertVerdict(List.of(), streak, streak >= SILENT_WINDOWS);
        }
        long failed = countOf(stats, "FAILED");
        long blocked = countOf(stats, "BLOCKED");
        long stale = countOf(stats, "STALE");
        List<String> warnings = new ArrayList<>();
        if (total >= FAILED_RATIO_MIN_SAMPLE && (double) failed / total > FAILED_RATIO_THRESHOLD) {
            warnings.add(String.format(
                    "type=FAILED_RATIO window=15m failed=%d total=%d ratio=%.2f threshold=%.2f",
                    failed, total, (double) failed / total, FAILED_RATIO_THRESHOLD));
        }
        if (blocked >= BLOCKED_WARN_COUNT) {
            warnings.add(String.format("type=BLOCKED window=15m count=%d threshold=%d",
                    blocked, BLOCKED_WARN_COUNT));
        }
        if (stale >= STALE_WARN_COUNT) {
            warnings.add(String.format("type=STALE window=15m count=%d threshold=%d",
                    stale, STALE_WARN_COUNT));
        }
        return new AlertVerdict(List.copyOf(warnings), 0, false);
    }

    private static long countOf(List<StatusCount> stats, String status) {
        return stats.stream().filter(s -> status.equals(s.status())).mapToLong(StatusCount::count).sum();
    }

    /**
     * @param warnings    uniform alert lines to emit at WARN level
     * @param emptyStreak consecutive windows without any run record
     * @param silent      true once the empty streak reached the silence threshold
     */
    record AlertVerdict(List<String> warnings, int emptyStreak, boolean silent) {
    }
}
