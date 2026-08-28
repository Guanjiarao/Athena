package athena.cognition.biz.agenttask;

import athena.cognition.biz.agenttask.mq.AgentTaskProducer;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Crash-recovery sweeper, the second safety net under the MQ-driven dispatch:
 *
 * <ul>
 *   <li>Message loss: a task still PENDING with no run 30s after creation never
 *   reached a consumer (service died between commit and send, broker outage) —
 *   redeliver it. Duplicate delivery is safe (worker state guard).</li>
 *   <li>Worker death/hang: a task RUNNING for over 10 minutes without an update
 *   is marked FAILED with errorCode=WORKER_TIMEOUT, retryable=true, so it stays
 *   below the DEAD letter and can be re-executed.</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentTaskRecoveryJob {

    /** 投递兜底：任务创建超过 30s 仍 PENDING 且无运行记录，视为消息丢失重新投递。 */
    static final long PENDING_REDELIVER_AFTER_SECONDS = 30;
    /** worker 卡死兜底：RUNNING 超过 10 分钟未更新，标记 FAILED（可重试）。 */
    static final long RUNNING_TIMEOUT_MINUTES = 10;
    static final int BATCH_LIMIT = 50;

    private final CognitionAgentJdbcRepository agentRepository;
    private final AgentTaskProducer producer;

    public AgentTaskRecoveryJob(CognitionAgentJdbcRepository agentRepository, AgentTaskProducer producer) {
        this.agentRepository = agentRepository;
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "60000", initialDelayString = "60000")
    public void recover() {
        redeliverPendingTasks();
        failStuckRunningTasks();
    }

    void redeliverPendingTasks() {
        List<AgentTaskRow> stuck = agentRepository.listPendingRedispatchTasks(
                Instant.now().minusSeconds(PENDING_REDELIVER_AFTER_SECONDS), BATCH_LIMIT);
        for (AgentTaskRow task : stuck) {
            try {
                producer.send(task.taskId(), task.triggerType());
                log.info("[AgentTaskRecovery] 重新投递丢失的任务消息, taskId={}, triggerType={}",
                        task.taskId(), task.triggerType());
            } catch (RuntimeException ex) {
                // 本轮发送失败等下一轮，不重试风暴
                log.error("[AgentTaskRecovery] 重新投递失败, taskId={}", task.taskId(), ex);
            }
        }
    }

    void failStuckRunningTasks() {
        List<AgentTaskRow> stuck = agentRepository.listStuckRunningTasks(
                Instant.now().minus(RUNNING_TIMEOUT_MINUTES, ChronoUnit.MINUTES), BATCH_LIMIT);
        for (AgentTaskRow task : stuck) {
            agentRepository.markTaskFinished(task.taskId(), "FAILED", null,
                    CognitionException.WORKER_TIMEOUT, true);
            log.warn("[AgentTaskRecovery] RUNNING 超时标记 FAILED, taskId={}, lastRunId={}",
                    task.taskId(), task.lastRunId());
        }
    }
}
