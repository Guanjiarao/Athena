package athena.cognition.biz.agenttask;

import athena.cognition.biz.agenttask.mq.AgentTaskProducer;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionGraphModels.AgentTaskView;
import athena.cognition.biz.domain.CognitionGraphModels.CandidateTopic;
import athena.cognition.biz.domain.CognitionGraphModels.GraphActionFeedbackRequest;
import athena.cognition.biz.domain.CognitionGraphModels.GraphUpdateTaskCreateRequest;
import athena.cognition.biz.domain.CognitionIds;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphNodeRow;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.rpc.agent.dto.GraphActionFeedbackResult;
import athena.cognition.biz.rpc.agent.dto.GraphContract;
import athena.cognition.biz.service.CognitionGraphService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent task creation and asynchronous submission (handoff section 11: persist
 * the business input first, create the Agent task, return the task id
 * immediately, let a worker call the Agent asynchronously).
 *
 * <p>Idempotency: tasks are keyed by (userId, workflowVersion, idempotencyKey)
 * with a database unique constraint; a repeated submission returns the existing
 * task without re-executing. The worker reads userId from the task row, never
 * from the request thread's UserIdHolder.
 *
 * <p>Submission is RocketMQ-driven: the task row (including the payload_json
 * execution context) is the source of truth, the message only carries
 * taskId + triggerType, and the consumer delegates to {@link AgentTaskWorker}
 * on its callback thread. A lost message is recovered by
 * {@link AgentTaskRecoveryJob} from the persisted row.
 */
@Slf4j
@Service
public class AgentTaskService {

    public static final int DEFAULT_MAX_RETRY = 3;
    private static final int MAX_TASK_LIST_LIMIT = 50;
    /** 轻量限流：同一用户 1 分钟内最多创建 5 个 agent 任务（DB 计数，超出抛 COGNITION_RATE_LIMITED/409）。 */
    static final int MAX_TASKS_PER_USER_PER_MINUTE = 5;
    static final long RATE_LIMIT_WINDOW_SECONDS = 60;

    private final CognitionAgentJdbcRepository agentRepository;
    private final CognitionJdbcRepository clueRepository;
    private final CognitionGraphService graphService;
    private final AgentTaskProducer producer;
    private final ObjectMapper objectMapper;

    public AgentTaskService(CognitionAgentJdbcRepository agentRepository,
                            CognitionJdbcRepository clueRepository,
                            CognitionGraphService graphService,
                            AgentTaskProducer producer,
                            ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.clueRepository = clueRepository;
        this.graphService = graphService;
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    /** Execution context of a graph-update task, handed to the worker via the persisted payload. */
    public record GraphTaskContext(String triggerType, String clueId, List<String> clueIds,
                                   String suggestedTopicTitle, String userSelectedTopicId) {
    }

    /** Execution context of an action-feedback task, handed to the worker via the persisted payload. */
    public record FeedbackTaskContext(String feedbackId, String actionId, GraphActionFeedbackResult result,
                                      String note, Instant occurredAt) {
    }

    // ---------- task creation ----------

    /**
     * Wired into clue creation: a saved RELATED clue spawns one graph workflow
     * task keyed by clue:{clueId}:cognition-graph-workflow-v1. Repeated clue
     * submissions hit the unique constraint and return the existing task.
     *
     * <p>Must be called INSIDE the clue transaction (handoff section 11: persist
     * the business input first); the async submission is done separately via
     * {@link #submitClueTask} after commit, so the consumer can always see
     * the task row.
     */
    public AgentTaskView createClueTaskRecord(long userId, String clueExternalId) {
        checkRateLimit(userId);
        String idempotencyKey = "clue:" + clueExternalId + ":" + GraphContract.WORKFLOW_VERSION;
        AgentTaskRow task = findOrCreate(userId, GraphContract.WORKFLOW_VERSION, idempotencyKey, "CLUE_CREATED",
                writePayload(AgentTaskPayload.forGraph(clueExternalId, null, null, null)));
        return toView(task);
    }

    /** Submits a clue-created task to the MQ queue; call after the clue transaction commits. */
    public void submitClueTask(String taskId, String clueExternalId) {
        AgentTaskRow task = agentRepository.findTaskByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("agent task not found: " + taskId));
        if (task.status().equals("PENDING") && task.lastRunId() == null) {
            submit(task.taskId(), task.triggerType());
        }
    }

    /**
     * POST /graph-update-tasks: the manual "organize for me" entry. Skips node 1
     * and assembles candidates directly from the given clues. Each manual request
     * is a new logical action with a fresh idempotency key.
     */
    public AgentTaskView createUserRequestTask(long userId, GraphUpdateTaskCreateRequest request) {
        checkRateLimit(userId);
        if (request.triggerType() != null && !"USER_REQUEST".equals(request.triggerType())) {
            throw CognitionException.invalidArgument("triggerType 只支持 USER_REQUEST");
        }
        List<String> clueIds = request.clueIds() == null ? List.of() : request.clueIds();
        if (!clueIds.isEmpty()) {
            List<Long> numericIds = clueIds.stream().map(id -> CognitionIds.parse(CognitionIds.CLUE, id)).toList();
            if (clueRepository.findClues(userId, numericIds).size() != numericIds.size()) {
                throw CognitionException.notFound();
            }
        }
        if (request.userSelectedTopicId() != null && !request.userSelectedTopicId().isBlank()) {
            graphService.requireTopicNode(userId, request.userSelectedTopicId());
        }
        String idempotencyKey = "user-request:" + UUID.randomUUID() + ":" + GraphContract.WORKFLOW_VERSION;
        AgentTaskRow task = agentRepository.findOrCreateTask(userId, GraphContract.WORKFLOW_VERSION,
                idempotencyKey, newTaskId(), "USER_REQUEST", DEFAULT_MAX_RETRY,
                writePayload(AgentTaskPayload.forGraph(null, clueIds,
                        request.suggestedTopicTitle(), request.userSelectedTopicId())));
        submit(task.taskId(), task.triggerType());
        return toView(task);
    }

    /**
     * POST /graph-actions/{actionId}/feedback: feedbackId is deterministic
     * (fb_{actionId}) so one action can only be fed back once; a repeated
     * submission returns the existing task via the idempotency unique constraint.
     */
    public AgentTaskView createFeedbackTask(long userId, String actionId, GraphActionFeedbackRequest request) {
        checkRateLimit(userId);
        GraphNodeRow actionNode = graphService.requireActionNode(userId, actionId);
        if (actionNode.actionStatus() != null && !"PENDING".equals(actionNode.actionStatus())) {
            throw CognitionException.stateConflict("该行动已反馈或已关闭", actionId, actionNode.actionStatus());
        }
        String feedbackId = "fb_" + actionId;
        String idempotencyKey = "feedback:" + actionId + ":" + GraphContract.FEEDBACK_WORKFLOW_VERSION;
        GraphActionFeedbackResult result = GraphActionFeedbackResult.valueOf(request.result().name());
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        AgentTaskRow task = findOrCreate(userId, GraphContract.FEEDBACK_WORKFLOW_VERSION, idempotencyKey,
                "ACTION_FEEDBACK",
                writePayload(AgentTaskPayload.forFeedback(feedbackId, actionId, result, request.note(), occurredAt)));
        if (task.status().equals("PENDING") && task.lastRunId() == null) {
            submit(task.taskId(), task.triggerType());
        }
        return toView(task);
    }

    // ---------- task query ----------

    public List<AgentTaskView> listTasks(long userId, int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, MAX_TASK_LIST_LIMIT);
        return agentRepository.listTasksByUser(userId, size).stream().map(this::toView).toList();
    }

    public AgentTaskView getTask(long userId, String taskId) {
        AgentTaskRow task = agentRepository.findTaskByTaskId(taskId).orElseThrow(CognitionException::notFound);
        if (task.userId() != userId) throw CognitionException.notFound();
        return toView(task);
    }

    /**
     * Reverse lookup of the clue-created task by its deterministic idempotency key
     * (clue:{clueId}:cognition-graph-workflow-v1); a second handle for the frontend
     * when the clue creation response did not carry the task.
     */
    public AgentTaskView getTaskByClue(long userId, String clueExternalId) {
        String idempotencyKey = "clue:" + clueExternalId + ":" + GraphContract.WORKFLOW_VERSION;
        return agentRepository.findTask(userId, GraphContract.WORKFLOW_VERSION, idempotencyKey)
                .map(this::toView)
                .orElseThrow(CognitionException::notFound);
    }

    // ---------- internals ----------

    private void checkRateLimit(long userId) {
        long recent = agentRepository.countRecentTasksByUser(userId,
                Instant.now().minusSeconds(RATE_LIMIT_WINDOW_SECONDS));
        if (recent >= MAX_TASKS_PER_USER_PER_MINUTE) {
            throw CognitionException.rateLimited();
        }
    }

    private AgentTaskRow findOrCreate(long userId, String workflowVersion, String idempotencyKey,
                                      String triggerType, String payloadJson) {
        Optional<AgentTaskRow> existing = agentRepository.findTask(userId, workflowVersion, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        return agentRepository.findOrCreateTask(userId, workflowVersion, idempotencyKey,
                newTaskId(), triggerType, DEFAULT_MAX_RETRY, payloadJson);
    }

    private void submit(String taskId, String triggerType) {
        try {
            producer.send(taskId, triggerType);
        } catch (RuntimeException ex) {
            // 发送失败不让业务请求失败：任务行已落库，清扫器会重新投递
            log.error("agent task {} submission failed", taskId, ex);
        }
    }

    private String writePayload(AgentTaskPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize task payload", ex);
        }
    }

    private static String newTaskId() {
        return "task_" + UUID.randomUUID();
    }

    private AgentTaskView toView(AgentTaskRow row) {
        AgentTaskPayload payload = readPayload(row.taskId());
        List<String> clueIds = List.of();
        String suggestedTopicTitle = null;
        List<CandidateTopic> candidates = null;
        if (payload != null) {
            clueIds = payload.clueIds() != null ? payload.clueIds()
                    : (payload.clueId() != null ? List.of(payload.clueId()) : List.of());
            suggestedTopicTitle = payload.suggestedTopicTitle();
            candidates = payload.candidates();
        }
        return new AgentTaskView(row.taskId(), row.workflowVersion(), row.idempotencyKey(), row.triggerType(),
                row.status(), row.retryCount(), row.maxRetry(), row.proposalId(), row.errorCode(),
                row.errorRetryable(), row.createdAt(), row.updatedAt(),
                clueIds, suggestedTopicTitle, candidates);
    }

    /** Best-effort payload read: an unparseable/missing payload never breaks the task query. */
    private AgentTaskPayload readPayload(String taskId) {
        String json = agentRepository.findTaskPayload(taskId).orElse(null);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentTaskPayload.class);
        } catch (Exception ex) {
            log.warn("cannot parse payload of agent task {} for view assembly", taskId, ex);
            return null;
        }
    }
}
