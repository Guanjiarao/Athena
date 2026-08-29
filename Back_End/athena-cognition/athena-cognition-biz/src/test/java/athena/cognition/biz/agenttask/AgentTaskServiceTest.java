package athena.cognition.biz.agenttask;

import athena.cognition.biz.agenttask.mq.AgentTaskProducer;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionGraphModels.AgentTaskView;
import athena.cognition.biz.domain.CognitionGraphModels.GraphActionFeedbackRequest;
import athena.cognition.biz.domain.CognitionModels.ActionFeedbackResult;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphNodeRow;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.rpc.agent.dto.GraphContract;
import athena.cognition.biz.service.CognitionGraphService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Handoff section 13.8: repeated feedback submissions of the same action
 * produce exactly one logical task (the idempotency unique key is
 * feedback:{actionId}:action-feedback-workflow-v1). Submission is MQ-driven:
 * only the first creation dispatches a message.
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
    private AgentTaskProducer producer;

    private AgentTaskService service;

    @BeforeEach
    void setUp() {
        service = new AgentTaskService(agentRepository, clueRepository, graphService, producer,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void repeatedFeedbackOfSameActionYieldsOneTaskAndOneDispatch() {
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
                eq(IDEMPOTENCY_KEY), any(), eq("ACTION_FEEDBACK"), eq(AgentTaskService.DEFAULT_MAX_RETRY), any()))
                .thenReturn(created);
        GraphActionFeedbackRequest request = new GraphActionFeedbackRequest(
                ActionFeedbackResult.OCCURRED, "真的出现了", null);

        AgentTaskView first = service.createFeedbackTask(USER_ID, ACTION_ID, request);
        AgentTaskView second = service.createFeedbackTask(USER_ID, ACTION_ID, request);

        assertThat(second.taskId()).isEqualTo(first.taskId());
        // the second submission returned the existing task and never re-dispatched
        verify(producer, times(1)).send(first.taskId(), "ACTION_FEEDBACK");
        verify(agentRepository, times(1)).findOrCreateTask(anyLong(), any(), any(), any(), any(), anyInt(), any());
    }

    // ---------- per-user rate limiting: at most 5 task creations per minute ----------

    @Test
    void rateLimitExceededRejectsTaskCreation() {
        when(agentRepository.countRecentTasksByUser(eq(USER_ID), any()))
                .thenReturn((long) AgentTaskService.MAX_TASKS_PER_USER_PER_MINUTE);
        GraphActionFeedbackRequest request = new GraphActionFeedbackRequest(
                ActionFeedbackResult.OCCURRED, "真的出现了", null);

        assertThatThrownBy(() -> service.createFeedbackTask(USER_ID, ACTION_ID, request))
                .isInstanceOf(CognitionException.class)
                .hasMessage("操作过于频繁，请稍后再试")
                .extracting(ex -> ((CognitionException) ex).errorCode())
                .isEqualTo(CognitionException.RATE_LIMITED);
        assertThatThrownBy(() -> service.createClueTaskRecord(USER_ID, "clue_101"))
                .isInstanceOf(CognitionException.class)
                .extracting(ex -> ((CognitionException) ex).errorCode())
                .isEqualTo(CognitionException.RATE_LIMITED);
        verify(agentRepository, never()).findOrCreateTask(anyLong(), any(), any(), any(), any(), anyInt(), any());
        verify(producer, never()).send(any(), any());
    }

    @Test
    void rateLimitNotExceededAllowsTaskCreation() {
        when(agentRepository.countRecentTasksByUser(eq(USER_ID), any()))
                .thenReturn((long) AgentTaskService.MAX_TASKS_PER_USER_PER_MINUTE - 1);
        AgentTaskRow created = new AgentTaskRow(2, "task_user_request_1", USER_ID, GraphContract.WORKFLOW_VERSION,
                "user-request:uuid:cognition-graph-workflow-v1", "USER_REQUEST", "PENDING", 0, 3, null, null,
                null, null, Instant.now(), Instant.now());
        when(agentRepository.findOrCreateTask(eq(USER_ID), eq(GraphContract.WORKFLOW_VERSION),
                any(), any(), eq("USER_REQUEST"), eq(AgentTaskService.DEFAULT_MAX_RETRY), any()))
                .thenReturn(created);

        AgentTaskView view = service.createUserRequestTask(USER_ID,
                new athena.cognition.biz.domain.CognitionGraphModels.GraphUpdateTaskCreateRequest(
                        null, null, null, null));

        assertThat(view.taskId()).isEqualTo(created.taskId());
        verify(producer).send(created.taskId(), "USER_REQUEST");
    }

    private AgentTaskRow task(String status) {
        return new AgentTaskRow(1, "task_feedback_1", USER_ID, GraphContract.FEEDBACK_WORKFLOW_VERSION,
                IDEMPOTENCY_KEY, "ACTION_FEEDBACK", status, 0, 3, null, null, null, null,
                Instant.now(), Instant.now());
    }

    // ---------- by-clue reverse lookup + payload-derived view fields ----------

    @Test
    void getTaskByClueReturnsTheClueCreatedTask() throws Exception {
        String key = "clue:clue_101:" + GraphContract.WORKFLOW_VERSION;
        AgentTaskRow created = new AgentTaskRow(3, "task_clue_1", USER_ID, GraphContract.WORKFLOW_VERSION,
                key, "CLUE_CREATED", "PENDING", 0, 3, null, null, null, null,
                Instant.now(), Instant.now());
        when(agentRepository.findTask(USER_ID, GraphContract.WORKFLOW_VERSION, key))
                .thenReturn(Optional.of(created));
        String payload = new ObjectMapper().writeValueAsString(
                AgentTaskPayload.forGraph("clue_101", null, "经前情绪变化", null));
        when(agentRepository.findTaskPayload("task_clue_1")).thenReturn(Optional.of(payload));

        AgentTaskView view = service.getTaskByClue(USER_ID, "clue_101");

        assertThat(view.taskId()).isEqualTo("task_clue_1");
        assertThat(view.clueIds()).containsExactly("clue_101");
        assertThat(view.suggestedTopicTitle()).isEqualTo("经前情绪变化");
        assertThat(view.candidates()).isNull();
    }

    @Test
    void getTaskByClueMissesAsNotFound() {
        when(agentRepository.findTask(eq(USER_ID), eq(GraphContract.WORKFLOW_VERSION), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTaskByClue(USER_ID, "clue_999"))
                .isInstanceOf(CognitionException.class)
                .extracting(ex -> ((CognitionException) ex).errorCode())
                .isEqualTo(CognitionException.NOT_FOUND);
    }

    @Test
    void userRequestViewCarriesAllClueIdsAndFeedbackViewHasNone() throws Exception {
        AgentTaskRow userRequest = new AgentTaskRow(4, "task_ur_1", USER_ID, GraphContract.WORKFLOW_VERSION,
                "user-request:uuid:" + GraphContract.WORKFLOW_VERSION, "USER_REQUEST", "PENDING",
                0, 3, null, null, null, null, Instant.now(), Instant.now());
        when(agentRepository.findTaskByTaskId("task_ur_1")).thenReturn(Optional.of(userRequest));
        String payload = new ObjectMapper().writeValueAsString(
                AgentTaskPayload.forGraph(null, java.util.List.of("clue_1", "clue_2"), "睡眠", null));
        when(agentRepository.findTaskPayload("task_ur_1")).thenReturn(Optional.of(payload));

        AgentTaskView view = service.getTask(USER_ID, "task_ur_1");
        assertThat(view.clueIds()).containsExactly("clue_1", "clue_2");

        AgentTaskRow feedback = new AgentTaskRow(5, "task_fb_1", USER_ID, GraphContract.FEEDBACK_WORKFLOW_VERSION,
                IDEMPOTENCY_KEY, "ACTION_FEEDBACK", "PENDING", 0, 3, null, null, null, null,
                Instant.now(), Instant.now());
        when(agentRepository.findTaskByTaskId("task_fb_1")).thenReturn(Optional.of(feedback));
        String feedbackPayload = new ObjectMapper().writeValueAsString(
                AgentTaskPayload.forFeedback("fb_action_1", "action_1",
                        athena.cognition.biz.rpc.agent.dto.GraphActionFeedbackResult.OCCURRED, "真的出现了",
                        Instant.parse("2026-08-01T00:00:00Z")));
        when(agentRepository.findTaskPayload("task_fb_1")).thenReturn(Optional.of(feedbackPayload));

        AgentTaskView feedbackView = service.getTask(USER_ID, "task_fb_1");
        assertThat(feedbackView.clueIds()).isEmpty();
        assertThat(feedbackView.suggestedTopicTitle()).isNull();
        assertThat(feedbackView.candidates()).isNull();
    }
}
