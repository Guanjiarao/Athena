package athena.cognition.biz.agenttask;

import athena.cognition.biz.agenttask.mq.AgentTaskProducer;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Crash-recovery sweeper: lost messages (task stuck PENDING) are redelivered;
 * hung workers (task stuck RUNNING) are timed out to FAILED/retryable.
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskRecoveryJobTest {

    @Mock
    private CognitionAgentJdbcRepository agentRepository;
    @Mock
    private AgentTaskProducer producer;

    private AgentTaskRecoveryJob job;

    @BeforeEach
    void setUp() {
        job = new AgentTaskRecoveryJob(agentRepository, producer);
    }

    @Test
    void pendingTaskWithoutRunIsRedelivered() {
        AgentTaskRow stuck = task("task_stale", "PENDING", null);
        when(agentRepository.listPendingRedispatchTasks(any(), anyInt())).thenReturn(List.of(stuck));

        job.redeliverPendingTasks();

        verify(producer).send("task_stale", "CLUE_CREATED");
    }

    @Test
    void redeliverFailureIsSwallowedForNextRound() {
        AgentTaskRow stuck = task("task_stale", "PENDING", null);
        when(agentRepository.listPendingRedispatchTasks(any(), anyInt())).thenReturn(List.of(stuck));
        doThrow(new RuntimeException("broker down")).when(producer).send(any(), any());

        job.redeliverPendingTasks(); // must not throw; the next sweep retries
    }

    @Test
    void stuckRunningTaskIsMarkedFailedRetryableWithWorkerTimeout() {
        AgentTaskRow stuck = task("task_running", "RUNNING", "run_1");
        when(agentRepository.listStuckRunningTasks(any(), anyInt())).thenReturn(List.of(stuck));

        job.failStuckRunningTasks();

        verify(agentRepository).markTaskFinished("task_running", "FAILED", null,
                CognitionException.WORKER_TIMEOUT, true);
    }

    @Test
    void recoverRunsBothPasses() {
        when(agentRepository.listPendingRedispatchTasks(any(), anyInt())).thenReturn(List.of());
        when(agentRepository.listStuckRunningTasks(any(), anyInt())).thenReturn(List.of());

        job.recover();

        verify(agentRepository).listPendingRedispatchTasks(any(), eq(AgentTaskRecoveryJob.BATCH_LIMIT));
        verify(agentRepository).listStuckRunningTasks(any(), eq(AgentTaskRecoveryJob.BATCH_LIMIT));
        verifyNoInteractions(producer);
    }

    private AgentTaskRow task(String taskId, String status, String lastRunId) {
        return new AgentTaskRow(1, taskId, 7L, "cognition-graph-workflow-v1",
                "clue:clue_101:cognition-graph-workflow-v1", "CLUE_CREATED", status, 0, 3, lastRunId,
                null, null, null, Instant.now(), Instant.now());
    }
}
