package athena.cognition.biz.agenttask;

import athena.cognition.biz.agenttask.AgentTaskService.FeedbackTaskContext;
import athena.cognition.biz.agenttask.AgentTaskService.GraphTaskContext;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionIds;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphSnapshot;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.repository.CognitionJdbcRepository.ClueRow;
import athena.cognition.biz.rpc.agent.CognitionAgentClient;
import athena.cognition.biz.rpc.agent.CognitionAgentException;
import athena.cognition.biz.rpc.agent.dto.ActionFeedbackSubmission;
import athena.cognition.biz.rpc.agent.dto.ActionFeedbackWorkflowRequest;
import athena.cognition.biz.rpc.agent.dto.ActionFeedbackWorkflowResponse;
import athena.cognition.biz.rpc.agent.dto.AgentError;
import athena.cognition.biz.rpc.agent.dto.CanonicalEvidence;
import athena.cognition.biz.rpc.agent.dto.ClueIntent;
import athena.cognition.biz.rpc.agent.dto.CluePayload;
import athena.cognition.biz.rpc.agent.dto.ClueType;
import athena.cognition.biz.rpc.agent.dto.CycleRelation;
import athena.cognition.biz.rpc.agent.dto.EvidenceCandidate;
import athena.cognition.biz.rpc.agent.dto.EvidenceSourceType;
import athena.cognition.biz.rpc.agent.dto.GraphContract;
import athena.cognition.biz.rpc.agent.dto.GraphTriggerType;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationRequest;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationResponse;
import athena.cognition.biz.rpc.agent.dto.HelpRequestType;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationRequest;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationResponse;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationStatus;
import athena.cognition.biz.rpc.agent.dto.NextRoute;
import athena.cognition.biz.rpc.agent.dto.QuestionType;
import athena.cognition.biz.rpc.agent.dto.RelationType;
import athena.cognition.biz.rpc.agent.dto.TriggerType;
import athena.cognition.biz.service.CognitionGraphService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Executes persisted Agent tasks on the worker thread pool. All user scoping
 * comes from the task row (never from UserIdHolder, which is request-thread
 * bound).
 *
 * <p>Graph-update chain (handoff sections 2/4/6.1): load or create the user's
 * graph, persist the immutable context snapshot, run node 1 for clue-created
 * tasks (RELATED continues; QUESTION / KNOWLEDGE_ONLY finish the task without
 * touching the graph; NEEDS_CLARIFICATION parks the task), assemble
 * EvidenceCandidate per the fixed section 2.1 mapping (summary is always
 * clue.selectedText, never regenerated), call the main workflow, then handle
 * the response status per section 6.1 — PROPOSAL_READY stores the proposal,
 * STALE re-reads the graph and re-runs once with a new runId, BLOCKED/REJECTED
 * fail without retry, FAILED retries with the same idempotencyKey and a new
 * runId only when error.retryable, up to maxRetry before DEAD.
 *
 * <p>Feedback chain (sections 5/6.2): fixed ACTION_FEEDBACK workflow, same
 * status handling.
 */
@Slf4j
@Component
public class AgentTaskWorker {

    /** Handoff section 4: at most 50 evidence candidates per run. */
    private static final int MAX_CANDIDATES = 50;

    private final CognitionAgentJdbcRepository agentRepository;
    private final CognitionJdbcRepository clueRepository;
    private final CognitionGraphService graphService;
    private final CognitionAgentClient agentClient;
    private final AgentTaskResultStore resultStore;
    private final ObjectMapper objectMapper;

    public AgentTaskWorker(CognitionAgentJdbcRepository agentRepository,
                           CognitionJdbcRepository clueRepository,
                           CognitionGraphService graphService,
                           CognitionAgentClient agentClient,
                           AgentTaskResultStore resultStore,
                           ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.clueRepository = clueRepository;
        this.graphService = graphService;
        this.agentClient = agentClient;
        this.resultStore = resultStore;
        this.objectMapper = objectMapper;
    }

    // ---------- graph-update workflow ----------

    public void executeGraphTask(String taskId, GraphTaskContext context) {
        AgentTaskRow task;
        try {
            task = agentRepository.findTaskByTaskId(taskId)
                    .orElseThrow(() -> new IllegalStateException("agent task not found: " + taskId));
        } catch (RuntimeException ex) {
            // outside the retry loop's protected region; never die silently on a pool thread
            log.error("agent task {} cannot start", taskId, ex);
            return;
        }
        if (!"PENDING".equals(task.status()) && !"FAILED".equals(task.status())) {
            log.info("agent task {} already {}, skip execution", taskId, task.status());
            return;
        }
        long userId = task.userId();
        int retries = task.retryCount();
        boolean failureRetry = false;
        boolean staleRetried = false;
        List<EvidenceCandidate> candidates = null;
        String suggestedTitle = context.suggestedTopicTitle();

        while (true) {
            String runId = newRunId();
            agentRepository.markTaskRunning(taskId, runId, failureRetry);
            if (failureRetry) retries++;
            long startedAt = System.currentTimeMillis();
            try {
                GraphSnapshot snapshot = graphService.getOrCreateGraph(userId);
                List<CanonicalEvidence> existingEvidence = excludeCandidates(
                        graphService.listCanonicalEvidence(userId), context);
                String contextSnapshotId = newSnapshotId();
                agentRepository.insertContextSnapshot(contextSnapshotId, userId,
                        snapshot.graph().graphId(), snapshot.graph().graphVersion(),
                        writeJson(evidenceIdsOf(existingEvidence)), writeJson(candidateIdsOf(context)));

                if (context.clueId() != null && candidates == null) {
                    // node 1 first for article clues (handoff section 2.1 routing table)
                    ClueRow clue = loadClue(userId, context.clueId());
                    IntentClassificationResponse intent = agentClient.classifyIntent(
                            buildIntentRequest(task, runId, contextSnapshotId, clue, failureRetry));
                    IntentRoute route = routeOf(intent);
                    if (route == IntentRoute.CONTINUE) {
                        String evidenceId = intent.evidenceIds == null || intent.evidenceIds.isEmpty()
                                ? context.clueId() : intent.evidenceIds.get(0);
                        candidates = List.of(toCandidate(clue, evidenceId));
                        // handoff section 4: suggestedTopicTitle comes from the page/business rule;
                        // for clue-created tasks that is the clue's own title, otherwise node 3
                        // has no branch title to work with and parks at NEEDS_CONFIRMATION
                        if (suggestedTitle == null || suggestedTitle.isBlank()) {
                            suggestedTitle = clue.suggestedTopicTitle();
                        }
                    } else if (route == IntentRoute.FINISH_SUCCEEDED) {
                        // QUESTION / KNOWLEDGE_ONLY: the legacy clue flow already kept the clue state
                        recordRun(task, runId, "SUCCEEDED", null, startedAt, intent.observation);
                        agentRepository.markTaskFinished(taskId, "SUCCEEDED", null, null, null);
                        return;
                    } else if (route == IntentRoute.NEEDS_CONFIRMATION) {
                        recordRun(task, runId, "NEEDS_CONFIRMATION", null, startedAt, intent.observation);
                        agentRepository.markTaskFinished(taskId, "NEEDS_CONFIRMATION", null, null, null);
                        return;
                    } else {
                        AgentError error = intent.error;
                        String errorCode = error != null && error.code != null ? error.code.name() : "INTENT_FAILED";
                        boolean retryable = error != null && error.retryable;
                        recordRun(task, runId, "FAILED", errorCode, startedAt, intent.observation);
                        if (retryable && retries < task.maxRetry()) {
                            failureRetry = true;
                            continue;
                        }
                        agentRepository.markTaskFinished(taskId, retryable ? "DEAD" : "FAILED",
                                null, errorCode, retryable);
                        return;
                    }
                } else if (candidates == null) {
                    // USER_REQUEST: no node 1, candidates straight from the requested clues
                    candidates = context.clueIds().stream()
                            .map(clueId -> toCandidate(loadClue(userId, clueId), clueId)).toList();
                }

                if (candidates.isEmpty()) {
                    recordRun(task, runId, "NO_CHANGE", null, startedAt, null);
                    agentRepository.markTaskFinished(taskId, "NO_CHANGE", null, null, null);
                    return;
                }
                if (candidates.size() > MAX_CANDIDATES) {
                    candidates = candidates.subList(0, MAX_CANDIDATES);
                }

                GraphUpdatePreparationRequest request = new GraphUpdatePreparationRequest();
                request.runId = runId;
                request.idempotencyKey = task.idempotencyKey();
                request.triggerType = failureRetry ? GraphTriggerType.RETRY
                        : GraphTriggerType.valueOf(context.triggerType());
                request.contextSnapshotId = contextSnapshotId;
                request.graph = graphService.toAgentGraph(snapshot);
                request.candidates = candidates;
                request.existingEvidence = existingEvidence;
                request.userSelectedTopicId = context.userSelectedTopicId();
                request.suggestedTopicTitle = suggestedTitle;
                request.requestedAt = OffsetDateTime.now().toString();

                GraphUpdatePreparationResponse response = agentClient.prepareGraphUpdate(request);
                String errorCode = errorCodeOf(response.error);
                switch (response.status) {
                    case PROPOSAL_READY -> {
                        recordRun(task, runId, "SUCCEEDED", null, startedAt, response.observation);
                        resultStore.saveProposalOutcome(task, runId, response.proposal, response.graphPreview);
                        return;
                    }
                    case NO_CHANGE -> {
                        recordRun(task, runId, "NO_CHANGE", null, startedAt, response.observation);
                        agentRepository.markTaskFinished(taskId, "NO_CHANGE", null, null, null);
                        return;
                    }
                    case NEEDS_CONFIRMATION -> {
                        // ambiguous target topic: candidates stay in the run observation for the user to pick
                        recordRun(task, runId, "NEEDS_CONFIRMATION", null, startedAt, response.observation);
                        agentRepository.markTaskFinished(taskId, "NEEDS_CONFIRMATION", null, null, null);
                        return;
                    }
                    case STALE -> {
                        recordRun(task, runId, "STALE", errorCode, startedAt, response.observation);
                        if (!staleRetried) {
                            // section 6.1: drop the snapshot, re-read the graph and re-run once (new runId)
                            staleRetried = true;
                            continue;
                        }
                        agentRepository.markTaskFinished(taskId, "FAILED", null,
                                errorCode != null ? errorCode : "GRAPH_VERSION_CONFLICT", false);
                        return;
                    }
                    case BLOCKED, REJECTED -> {
                        recordRun(task, runId, response.status.name(), errorCode, startedAt, response.observation);
                        agentRepository.markTaskFinished(taskId, "FAILED", null, errorCode, false);
                        return;
                    }
                    default -> {
                        boolean retryable = response.error != null && response.error.retryable;
                        recordRun(task, runId, "FAILED", errorCode, startedAt, response.observation);
                        if (retryable && retries < task.maxRetry()) {
                            failureRetry = true;
                            continue;
                        }
                        agentRepository.markTaskFinished(taskId, retryable ? "DEAD" : "FAILED",
                                null, errorCode, retryable);
                        return;
                    }
                }
            } catch (CognitionAgentException ex) {
                recordRun(task, runId, "FAILED", ex.errorCode(), startedAt, null);
                if (ex.retryable() && retries < task.maxRetry()) {
                    failureRetry = true;
                    continue;
                }
                agentRepository.markTaskFinished(taskId, ex.retryable() ? "DEAD" : "FAILED",
                        null, ex.errorCode(), ex.retryable());
                return;
            } catch (CognitionException ex) {
                recordRun(task, runId, "FAILED", ex.errorCode(), startedAt, null);
                agentRepository.markTaskFinished(taskId, "FAILED", null, ex.errorCode(), false);
                return;
            } catch (RuntimeException ex) {
                log.error("agent task {} failed unexpectedly", taskId, ex);
                recordRun(task, runId, "FAILED", CognitionException.AGENT_TASK_FAILED, startedAt, null);
                agentRepository.markTaskFinished(taskId, "FAILED", null,
                        CognitionException.AGENT_TASK_FAILED, false);
                return;
            }
        }
    }

    // ---------- action-feedback workflow ----------

    public void executeFeedbackTask(String taskId, FeedbackTaskContext context) {
        AgentTaskRow task;
        try {
            task = agentRepository.findTaskByTaskId(taskId)
                    .orElseThrow(() -> new IllegalStateException("agent task not found: " + taskId));
        } catch (RuntimeException ex) {
            // outside the retry loop's protected region; never die silently on a pool thread
            log.error("agent task {} cannot start", taskId, ex);
            return;
        }
        if (!"PENDING".equals(task.status()) && !"FAILED".equals(task.status())) {
            log.info("agent task {} already {}, skip execution", taskId, task.status());
            return;
        }
        long userId = task.userId();
        int retries = task.retryCount();
        boolean failureRetry = false;
        boolean staleRetried = false;

        while (true) {
            String runId = newRunId();
            agentRepository.markTaskRunning(taskId, runId, failureRetry);
            if (failureRetry) retries++;
            long startedAt = System.currentTimeMillis();
            try {
                GraphSnapshot snapshot = graphService.getOrCreateGraph(userId);
                List<CanonicalEvidence> existingEvidence = graphService.listCanonicalEvidence(userId);
                String contextSnapshotId = newSnapshotId();
                agentRepository.insertContextSnapshot(contextSnapshotId, userId,
                        snapshot.graph().graphId(), snapshot.graph().graphVersion(),
                        writeJson(evidenceIdsOf(existingEvidence)), writeJson(List.of(context.feedbackId())));

                ActionFeedbackWorkflowRequest request = new ActionFeedbackWorkflowRequest();
                request.runId = runId;
                request.idempotencyKey = task.idempotencyKey();
                request.contextSnapshotId = contextSnapshotId;
                request.graph = graphService.toAgentGraph(snapshot);
                request.existingEvidence = existingEvidence;
                ActionFeedbackSubmission feedback = new ActionFeedbackSubmission();
                feedback.feedbackId = context.feedbackId();
                feedback.actionId = context.actionId();
                feedback.result = context.result();
                feedback.note = context.note();
                feedback.occurredAt = context.occurredAt() == null ? null : context.occurredAt().toString();
                request.feedback = feedback;

                ActionFeedbackWorkflowResponse response = agentClient.prepareActionFeedback(request);
                String errorCode = errorCodeOf(response.error);
                switch (response.status) {
                    case PROPOSAL_READY -> {
                        recordRun(task, runId, "SUCCEEDED", null, startedAt, response.observation);
                        resultStore.saveProposalOutcome(task, runId, response.proposal, response.graphPreview);
                        return;
                    }
                    case NO_CHANGE -> {
                        // section 6.2: the same feedback was already processed; nothing to close again
                        recordRun(task, runId, "NO_CHANGE", null, startedAt, response.observation);
                        agentRepository.markTaskFinished(taskId, "NO_CHANGE", null, null, null);
                        return;
                    }
                    case STALE -> {
                        recordRun(task, runId, "STALE", errorCode, startedAt, response.observation);
                        if (!staleRetried) {
                            staleRetried = true;
                            continue;
                        }
                        agentRepository.markTaskFinished(taskId, "FAILED", null,
                                errorCode != null ? errorCode : "GRAPH_VERSION_CONFLICT", false);
                        return;
                    }
                    case BLOCKED, REJECTED -> {
                        recordRun(task, runId, response.status.name(), errorCode, startedAt, response.observation);
                        agentRepository.markTaskFinished(taskId, "FAILED", null, errorCode, false);
                        return;
                    }
                    default -> {
                        boolean retryable = response.error != null && response.error.retryable;
                        recordRun(task, runId, "FAILED", errorCode, startedAt, response.observation);
                        if (retryable && retries < task.maxRetry()) {
                            failureRetry = true;
                            continue;
                        }
                        agentRepository.markTaskFinished(taskId, retryable ? "DEAD" : "FAILED",
                                null, errorCode, retryable);
                        return;
                    }
                }
            } catch (CognitionAgentException ex) {
                recordRun(task, runId, "FAILED", ex.errorCode(), startedAt, null);
                if (ex.retryable() && retries < task.maxRetry()) {
                    failureRetry = true;
                    continue;
                }
                agentRepository.markTaskFinished(taskId, ex.retryable() ? "DEAD" : "FAILED",
                        null, ex.errorCode(), ex.retryable());
                return;
            } catch (CognitionException ex) {
                recordRun(task, runId, "FAILED", ex.errorCode(), startedAt, null);
                agentRepository.markTaskFinished(taskId, "FAILED", null, ex.errorCode(), false);
                return;
            } catch (RuntimeException ex) {
                log.error("feedback task {} failed unexpectedly", taskId, ex);
                recordRun(task, runId, "FAILED", CognitionException.AGENT_TASK_FAILED, startedAt, null);
                agentRepository.markTaskFinished(taskId, "FAILED", null,
                        CognitionException.AGENT_TASK_FAILED, false);
                return;
            }
        }
    }

    // ---------- node 1 ----------

    private enum IntentRoute { CONTINUE, FINISH_SUCCEEDED, NEEDS_CONFIRMATION, FAILED }

    private IntentRoute routeOf(IntentClassificationResponse response) {
        if (response.status == IntentClassificationStatus.NEEDS_CLARIFICATION
                || response.nextRoute == NextRoute.NEEDS_CLARIFICATION) {
            return IntentRoute.NEEDS_CONFIRMATION;
        }
        if (response.status == IntentClassificationStatus.SUCCEEDED) {
            if (response.intent == ClueIntent.RELATED
                    && response.nextRoute == NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE) {
                return IntentRoute.CONTINUE;
            }
            if (response.intent == ClueIntent.QUESTION || response.intent == ClueIntent.KNOWLEDGE_ONLY
                    || response.nextRoute == NextRoute.QUESTION_INBOX
                    || response.nextRoute == NextRoute.KNOWLEDGE_INBOX) {
                return IntentRoute.FINISH_SUCCEEDED;
            }
        }
        return IntentRoute.FAILED;
    }

    private IntentClassificationRequest buildIntentRequest(AgentTaskRow task, String runId,
                                                           String contextSnapshotId, ClueRow clue, boolean retry) {
        IntentClassificationRequest request = new IntentClassificationRequest();
        request.contractVersion = GraphContract.CONTRACT_VERSION;
        request.nodeVersion = GraphContract.INTENT_NODE_VERSION;
        request.runId = runId;
        request.idempotencyKey = task.idempotencyKey();
        request.triggerType = retry ? TriggerType.RETRY : TriggerType.CLUE_CREATED;
        request.contextSnapshotId = contextSnapshotId;
        request.clue = toCluePayload(clue);
        return request;
    }

    private CluePayload toCluePayload(ClueRow row) {
        CluePayload clue = new CluePayload();
        clue.id = CognitionIds.of(CognitionIds.CLUE, row.id());
        clue.type = ClueType.valueOf(row.type().name());
        clue.intent = ClueIntent.valueOf(row.intent().name());
        clue.relationType = row.relationType() == null ? null : RelationType.valueOf(row.relationType().name());
        clue.helpRequestType = row.helpRequestType() == null ? null
                : HelpRequestType.valueOf(row.helpRequestType().name());
        clue.articleId = row.articleId();
        clue.articleTitle = row.articleTitle();
        clue.articleType = row.articleType();
        clue.selectedText = row.selectedText();
        clue.questionType = row.questionType() == null ? null : QuestionType.valueOf(row.questionType().name());
        clue.questionText = row.questionText();
        clue.occurredAt = row.occurredAt() == null ? null : row.occurredAt().toString();
        clue.cycleRelation = row.cycleRelation() == null ? null : CycleRelation.valueOf(row.cycleRelation().name());
        clue.severity = row.severity();
        clue.resolved = row.resolved();
        clue.source = row.source() == null ? null : row.source().name();
        clue.status = athena.cognition.biz.rpc.agent.dto.ClueStatus.valueOf(row.status().name());
        clue.suggestedTopicId = row.suggestedTopicId();
        clue.suggestedTopicTitle = row.suggestedTopicTitle();
        clue.originalLabel = row.originalLabel();
        clue.createdAt = row.createdAt() == null ? null : row.createdAt().toString();
        clue.updatedAt = row.updatedAt() == null ? null : row.updatedAt().toString();
        return clue;
    }

    // ---------- candidates ----------

    /**
     * Handoff section 2.1 fixed mapping: summary is always clue.selectedText
     * (the backend never regenerates a body conclusion).
     */
    private EvidenceCandidate toCandidate(ClueRow clue, String evidenceId) {
        EvidenceCandidate candidate = new EvidenceCandidate();
        candidate.evidenceId = evidenceId;
        candidate.sourceType = EvidenceSourceType.ARTICLE_HIGHLIGHT;
        candidate.sourceId = CognitionIds.of(CognitionIds.CLUE, clue.id());
        candidate.intent = ClueIntent.RELATED;
        candidate.relationType = clue.relationType() == null ? null
                : RelationType.valueOf(clue.relationType().name());
        candidate.summary = clue.selectedText();
        candidate.occurredAt = clue.occurredAt() == null ? null : clue.occurredAt().toString();
        candidate.cycleRelation = clue.cycleRelation() == null ? null
                : CycleRelation.valueOf(clue.cycleRelation().name());
        candidate.severity = clue.severity();
        candidate.resolved = clue.resolved();
        candidate.relatedActionId = null;
        candidate.feedbackResult = null;
        return candidate;
    }

    private ClueRow loadClue(long userId, String clueExternalId) {
        long clueId = CognitionIds.parse(CognitionIds.CLUE, clueExternalId);
        return clueRepository.findClue(userId, clueId).orElseThrow(CognitionException::notFound);
    }

    // ---------- run record / helpers ----------

    private void recordRun(AgentTaskRow task, String runId, String finalStatus, String errorCode,
                           long startedAt, JsonNode observation) {
        String observationJson = null;
        String modelProvider = null;
        String modelName = null;
        if (observation != null) {
            observationJson = writeJson(observation);
            modelProvider = textOrNull(observation.get("modelProvider"));
            modelName = textOrNull(observation.get("modelName"));
        }
        agentRepository.insertRun(runId, task.taskId(), task.workflowVersion(), finalStatus, errorCode,
                Math.max(0, System.currentTimeMillis() - startedAt), modelProvider, modelName, observationJson);
        if (observation != null) {
            recordNodeRuns(runId, observation);
        }
    }

    /**
     * Node-level run records: each WorkflowNodeStep of the observation's steps
     * array becomes one cognition_agent_node_run row (node_id=stepId,
     * node_version from the observation top level, observation_json=the step).
     *
     * <p>(run_id, node_id) is unique but the same stepId may appear twice in
     * one run (retry loops) — on conflict we KEEP THE FIRST row and skip the
     * duplicates: the first record already localizes the node's behaviour, and
     * skipping never breaks the main flow.
     */
    private void recordNodeRuns(String runId, JsonNode observation) {
        JsonNode steps = observation.get("steps");
        if (steps == null || !steps.isArray()) {
            return;
        }
        String nodeVersion = textOrNull(observation.get("nodeVersion"));
        for (JsonNode step : steps) {
            String stepId = textOrNull(step.get("stepId"));
            if (stepId == null || stepId.isBlank()) {
                continue;
            }
            try {
                agentRepository.insertNodeRun(runId, stepId, nodeVersion, writeJson(step));
            } catch (DuplicateKeyException duplicate) {
                log.debug("node run {} already recorded for run {}, keeping the first row", stepId, runId);
            }
        }
    }

    private List<String> evidenceIdsOf(List<CanonicalEvidence> existingEvidence) {
        return existingEvidence.stream().map(evidence -> evidence.evidenceId).toList();
    }

    private List<String> candidateIdsOf(GraphTaskContext context) {
        return context.clueId() != null ? List.of(context.clueId())
                : (context.clueIds() == null ? List.of() : context.clueIds());
    }

    /**
     * Drops the candidates' own evidence rows from existingEvidence. The legacy
     * digest pipeline writes a cognition_evidence row for each organized clue
     * before the worker runs, so without this filter the agent's node 2 would
     * judge every clue candidate an EXACT_SOURCE/CONTENT_DUPLICATE of itself
     * and the whole run would end as NO_CHANGE (handoff section 4:
     * existingEvidence is the user's PRE-EXISTING evidence, not the candidates).
     */
    private List<CanonicalEvidence> excludeCandidates(List<CanonicalEvidence> existingEvidence,
                                                      GraphTaskContext context) {
        List<String> candidateSourceIds = candidateIdsOf(context);
        if (candidateSourceIds.isEmpty()) {
            return existingEvidence;
        }
        return existingEvidence.stream()
                .filter(evidence -> evidence.sourceId == null
                        || !candidateSourceIds.contains(evidence.sourceId))
                .toList();
    }

    private static String errorCodeOf(AgentError error) {
        return error != null && error.code != null ? error.code.name() : null;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String newRunId() {
        return "run_" + UUID.randomUUID();
    }

    private static String newSnapshotId() {
        return "ctx_" + UUID.randomUUID();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize json", ex);
        }
    }
}
