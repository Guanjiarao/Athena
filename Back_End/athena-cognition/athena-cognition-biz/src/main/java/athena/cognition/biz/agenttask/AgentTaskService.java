package athena.cognition.biz.agenttask;

import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionGraphModels.AgentTaskView;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Agent task creation and asynchronous submission (handoff section 11: persist
 * the business input first, create the Agent task, return the task id
 * immediately, let a worker thread call the Agent).
 *
 * <p>Idempotency: tasks are keyed by (userId, workflowVersion, idempotencyKey)
 * with a database unique constraint; a repeated submission returns the existing
 * task without re-executing. The worker reads userId from the task row, never
 * from the request thread's UserIdHolder.
 */
@Slf4j
@Service
public class AgentTaskService {

    public static final int DEFAULT_MAX_RETRY = 3;
    private static final int MAX_TASK_LIST_LIMIT = 50;

    private final CognitionAgentJdbcRepository agentRepository;
    private final CognitionJdbcRepository clueRepository;
    private final CognitionGraphService graphService;
    private final AgentTaskWorker worker;
    private final Executor agentTaskExecutor;

    public AgentTaskService(CognitionAgentJdbcRepository agentRepository,
                            CognitionJdbcRepository clueRepository,
                            CognitionGraphService graphService,
                            AgentTaskWorker worker,
                            @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        this.agentRepository = agentRepository;
        this.clueRepository = clueRepository;
        this.graphService = graphService;
        this.worker = worker;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    /** Execution context of a graph-update task, handed to the worker in memory. */
    public record GraphTaskContext(String triggerType, String clueId, List<String> clueIds,
                                   String suggestedTopicTitle, String userSelectedTopicId) {
    }

    /** Execution context of an action-feedback task, handed to the worker in memory. */
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
     * {@link #submitClueTask} after commit, so the worker thread can always see
     * the task row. (Creating the row in afterCommit used the still-bound,
     * not-yet-cleanup connection, making the insert invisible to the worker.)
     */
    public AgentTaskView createClueTaskRecord(long userId, String clueExternalId) {
        String idempotencyKey = "clue:" + clueExternalId + ":" + GraphContract.WORKFLOW_VERSION;
        AgentTaskRow task = findOrCreate(userId, GraphContract.WORKFLOW_VERSION, idempotencyKey, "CLUE_CREATED");
        return toView(task);
    }

    /** Submits a clue-created task to the worker; call after the clue transaction commits. */
    public void submitClueTask(String taskId, String clueExternalId) {
        AgentTaskRow task = agentRepository.findTaskByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("agent task not found: " + taskId));
        if (task.status().equals("PENDING") && task.lastRunId() == null) {
            submit(task.taskId(), () -> worker.executeGraphTask(task.taskId(),
                    new GraphTaskContext("CLUE_CREATED", clueExternalId, null, null, null)));
        }
    }

    /**
     * POST /graph-update-tasks: the manual "organize for me" entry. Skips node 1
     * and assembles candidates directly from the given clues. Each manual request
     * is a new logical action with a fresh idempotency key.
     */
    public AgentTaskView createUserRequestTask(long userId, GraphUpdateTaskCreateRequest request) {
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
                idempotencyKey, newTaskId(), "USER_REQUEST", DEFAULT_MAX_RETRY);
        submit(task.taskId(), () -> worker.executeGraphTask(task.taskId(),
                new GraphTaskContext("USER_REQUEST", null, clueIds,
                        request.suggestedTopicTitle(), request.userSelectedTopicId())));
        return toView(task);
    }

    /**
     * POST /graph-actions/{actionId}/feedback: feedbackId is deterministic
     * (fb_{actionId}) so one action can only be fed back once; a repeated
     * submission returns the existing task via the idempotency unique constraint.
     */
    public AgentTaskView createFeedbackTask(long userId, String actionId, GraphActionFeedbackRequest request) {
        GraphNodeRow actionNode = graphService.requireActionNode(userId, actionId);
        if (actionNode.actionStatus() != null && !"PENDING".equals(actionNode.actionStatus())) {
            throw CognitionException.stateConflict("该行动已反馈或已关闭", actionId, actionNode.actionStatus());
        }
        String feedbackId = "fb_" + actionId;
        String idempotencyKey = "feedback:" + actionId + ":" + GraphContract.FEEDBACK_WORKFLOW_VERSION;
        AgentTaskRow task = findOrCreate(userId, GraphContract.FEEDBACK_WORKFLOW_VERSION, idempotencyKey, "ACTION_FEEDBACK");
        if (task.status().equals("PENDING") && task.lastRunId() == null) {
            GraphActionFeedbackResult result = GraphActionFeedbackResult.valueOf(request.result().name());
            submit(task.taskId(), () -> worker.executeFeedbackTask(task.taskId(),
                    new FeedbackTaskContext(feedbackId, actionId, result, request.note(),
                            request.occurredAt() == null ? Instant.now() : request.occurredAt())));
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

    // ---------- internals ----------

    private AgentTaskRow findOrCreate(long userId, String workflowVersion, String idempotencyKey, String triggerType) {
        Optional<AgentTaskRow> existing = agentRepository.findTask(userId, workflowVersion, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        return agentRepository.findOrCreateTask(userId, workflowVersion, idempotencyKey,
                newTaskId(), triggerType, DEFAULT_MAX_RETRY);
    }

    private void submit(String taskId, Runnable runnable) {
        try {
            agentTaskExecutor.execute(runnable);
        } catch (RuntimeException ex) {
            // CallerRunsPolicy should not reject; log instead of failing the business request
            log.error("agent task {} submission failed", taskId, ex);
        }
    }

    private static String newTaskId() {
        return "task_" + UUID.randomUUID();
    }

    private AgentTaskView toView(AgentTaskRow row) {
        return new AgentTaskView(row.taskId(), row.workflowVersion(), row.idempotencyKey(), row.triggerType(),
                row.status(), row.retryCount(), row.maxRetry(), row.proposalId(), row.errorCode(),
                row.errorRetryable(), row.createdAt(), row.updatedAt());
    }
}
