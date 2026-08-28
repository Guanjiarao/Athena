package athena.cognition.biz.agenttask;

import athena.cognition.biz.domain.CognitionGraphModels.AgentTaskView;
import athena.cognition.biz.domain.CognitionGraphModels.GraphActionFeedbackRequest;
import athena.cognition.biz.domain.CognitionModels.ActionFeedbackResult;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphNodeRow;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.rpc.agent.dto.GraphContract;
import athena.cognition.biz.service.CognitionGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Handoff section 13.8: repeated feedback submissions of the same action
 * produce exactly one logical task (the idempotency unique key is
 * feedback:{actionId}:action-feedback-workflow-v1).
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

    private static final long USER_ID = 7L;
    private static final String ACTION_ID = "action_1";
    private static final String IDEMPOTENCY_KEY = "feedback:action_1:action-feedback-workflow-v1";

    @Mock
    private CognitionAgentJdbcRepository agentRepository;
    @Mock
    private CognitionJdbcRepository clueRepository;
    @Mock
    private CognitionGraphService graphService;
    @Mock
    private AgentTaskWorker worker;

    /** Direct executor so the submitted runnable runs inline against the worker mock. */
    private final Executor directExecutor = Runnable::run;
    private AgentTaskService service;

    @BeforeEach
    void setUp() {
        service = new AgentTaskService(agentRepository, clueRepository, graphService, worker, directExecutor);
    }

    @Test
    void repeatedFeedbackOfSameActionYieldsOneTaskAndOneExecution() {
        GraphNodeRow actionNode = new GraphNodeRow(1, "graph_1", ACTION_ID, "ACTION", "ACTIVE", "topic_1",
                "记录一次相关身体变化", null, null, null, "RECORD_BODY", "PENDING", null, null, 1,
                Instant.now(), Instant.now());
        when(graphService.requireActionNode(USER_ID, ACTION_ID)).thenReturn(actionNode);
        AgentTaskRow created = task("PENDING");
        AgentTaskRow processed = new AgentTaskRow(1, created.taskId(), USER_ID,
                GraphContract.FEEDBACK_WORKFLOW_VERSION, IDEMPOTENCY_KEY, "ACTION_FEEDBACK", "SUCCEEDED",
                0, 3, "run_1", "prop_1", null, null, Instant.now(), Instant.now());
        when(agentRepository.findTask(USER_ID, GraphContract.FEEDBACK_WORKFLOW_VERSION, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.of(processed));
        when(agentRepository.findOrCreateTask(eq(USER_ID), eq(GraphContract.FEEDBACK_WORKFLOW_VERSION),
                eq(IDEMPOTENCY_KEY), any(), eq("ACTION_FEEDBACK"), eq(AgentTaskService.DEFAULT_MAX_RETRY)))
                .thenReturn(created);
        GraphActionFeedbackRequest request = new GraphActionFeedbackRequest(
                ActionFeedbackResult.OCCURRED, "真的出现了", null);

        AgentTaskView first = service.createFeedbackTask(USER_ID, ACTION_ID, request);
        AgentTaskView second = service.createFeedbackTask(USER_ID, ACTION_ID, request);

        assertThat(second.taskId()).isEqualTo(first.taskId());
        // the second submission returned the existing task and never re-executed
        verify(worker, times(1)).executeFeedbackTask(eq(first.taskId()), any());
        verify(agentRepository, times(1)).findOrCreateTask(anyLong(), any(), any(), any(), any(), anyInt());
    }

    private AgentTaskRow task(String status) {
        return new AgentTaskRow(1, "task_feedback_1", USER_ID, GraphContract.FEEDBACK_WORKFLOW_VERSION,
                IDEMPOTENCY_KEY, "ACTION_FEEDBACK", status, 0, 3, null, null, null, null,
                Instant.now(), Instant.now());
    }
}
