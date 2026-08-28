package athena.cognition.biz.agenttask.mq;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import athena.cognition.biz.agenttask.AgentTaskPayload;
import athena.cognition.biz.agenttask.AgentTaskService.FeedbackTaskContext;
import athena.cognition.biz.agenttask.AgentTaskService.GraphTaskContext;
import athena.cognition.biz.agenttask.AgentTaskWorker;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import athena.cognition.biz.rpc.agent.dto.GraphActionFeedbackResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer rebuilds the worker context from the persisted payload and
 * delegates on the callback thread; unrecoverable messages are ACKed without
 * touching the worker.
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskConsumerTest {

    private static final String TASK_ID = "task_1";

    @Mock
    private AgentTaskWorker worker;
    @Mock
    private CognitionAgentJdbcRepository agentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private AgentTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AgentTaskConsumer(worker, agentRepository, objectMapper);
    }

    @Test
    void graphTaskMessageDelegatesToWorkerWithPersistedContext() throws Exception {
        AgentTaskRow task = task("USER_REQUEST");
        when(agentRepository.findTaskByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        AgentTaskPayload payload = AgentTaskPayload.forGraph(null, List.of("clue_1", "clue_2"),
                "睡眠", "topic_1");
        when(agentRepository.findTaskPayload(TASK_ID))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(payload)));

        consumer.onMessage(body(new AgentTaskMessage(TASK_ID, "USER_REQUEST")));

        ArgumentCaptor<GraphTaskContext> context = ArgumentCaptor.forClass(GraphTaskContext.class);
        verify(worker).executeGraphTask(eq(TASK_ID), context.capture());
        assertThat(context.getValue().triggerType()).isEqualTo("USER_REQUEST");
        assertThat(context.getValue().clueIds()).containsExactly("clue_1", "clue_2");
        assertThat(context.getValue().suggestedTopicTitle()).isEqualTo("睡眠");
        assertThat(context.getValue().userSelectedTopicId()).isEqualTo("topic_1");
        verify(worker, never()).executeFeedbackTask(any(), any());
    }

    @Test
    void feedbackTaskMessageDelegatesToFeedbackWorker() throws Exception {
        AgentTaskRow task = task("ACTION_FEEDBACK");
        when(agentRepository.findTaskByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        Instant occurredAt = Instant.parse("2026-08-01T10:15:30Z");
        AgentTaskPayload payload = AgentTaskPayload.forFeedback("fb_action_1", "action_1",
                GraphActionFeedbackResult.OCCURRED, "真的出现了", occurredAt);
        when(agentRepository.findTaskPayload(TASK_ID))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(payload)));

        consumer.onMessage(body(new AgentTaskMessage(TASK_ID, "ACTION_FEEDBACK")));

        ArgumentCaptor<FeedbackTaskContext> context = ArgumentCaptor.forClass(FeedbackTaskContext.class);
        verify(worker).executeFeedbackTask(eq(TASK_ID), context.capture());
        assertThat(context.getValue().feedbackId()).isEqualTo("fb_action_1");
        assertThat(context.getValue().actionId()).isEqualTo("action_1");
        assertThat(context.getValue().result()).isEqualTo(GraphActionFeedbackResult.OCCURRED);
        assertThat(context.getValue().note()).isEqualTo("真的出现了");
        assertThat(context.getValue().occurredAt()).isEqualTo(occurredAt);
        verify(worker, never()).executeGraphTask(any(), any());
    }

    @Test
    void missingTaskRowIsAckedWithoutExecution() {
        when(agentRepository.findTaskByTaskId(TASK_ID)).thenReturn(Optional.empty());

        consumer.onMessage(body(new AgentTaskMessage(TASK_ID, "CLUE_CREATED")));

        verify(worker, never()).executeGraphTask(any(), any());
        verify(worker, never()).executeFeedbackTask(any(), any());
    }

    @Test
    void legacyClueTaskWithoutPayloadFallsBackToIdempotencyKey() {
        AgentTaskRow task = task("CLUE_CREATED");
        when(agentRepository.findTaskByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(agentRepository.findTaskPayload(TASK_ID)).thenReturn(Optional.empty());

        consumer.onMessage(body(new AgentTaskMessage(TASK_ID, "CLUE_CREATED")));

        ArgumentCaptor<GraphTaskContext> context = ArgumentCaptor.forClass(GraphTaskContext.class);
        verify(worker).executeGraphTask(eq(TASK_ID), context.capture());
        assertThat(context.getValue().clueId()).isEqualTo("clue_101");
    }

    @Test
    void corruptPayloadBubblesUpForReconsume() {
        AgentTaskRow task = task("USER_REQUEST");
        when(agentRepository.findTaskByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(agentRepository.findTaskPayload(TASK_ID)).thenReturn(Optional.of("{not-json"));

        assertThatThrownBy(() -> consumer.onMessage(body(new AgentTaskMessage(TASK_ID, "USER_REQUEST"))))
                .isInstanceOf(IllegalStateException.class);
        verify(worker, never()).executeGraphTask(any(), any());
    }

    private String body(AgentTaskMessage message) {
        return JsonUtils.toJsonString(MessageWrapper.<AgentTaskMessage>builder().body(message).build());
    }

    private AgentTaskRow task(String triggerType) {
        String idempotencyKey = "CLUE_CREATED".equals(triggerType)
                ? "clue:clue_101:cognition-graph-workflow-v1" : triggerType.toLowerCase() + ":x";
        return new AgentTaskRow(1, TASK_ID, 7L, "cognition-graph-workflow-v1", idempotencyKey,
                triggerType, "PENDING", 0, 3, null, null, null, null, Instant.now(), Instant.now());
    }
}
