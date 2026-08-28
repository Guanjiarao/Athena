package athena.cognition.biz.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC persistence for the Agent-side pipeline tables (cognition_agent_v1.sql):
 * cognition_proposal / cognition_proposal_operation / cognition_context_snapshot /
 * cognition_agent_task / cognition_agent_run / cognition_agent_node_run / outbox_event.
 *
 * <p>Enum-valued columns (GraphProposalStatus, GraphUpdateRoute, GraphTriggerType,
 * task/run status, outbox status) live in the athena-cognition-agent module which is
 * not on this module's classpath, so they are carried as contract {@code String}
 * names here. JSON columns are passed through as pre-serialized strings.
 */
@Repository
public class CognitionAgentJdbcRepository {

    private final JdbcTemplate jdbc;

    public CognitionAgentJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- agent task ----------

    /**
     * Idempotent task creation keyed by (userId, workflowVersion, idempotencyKey):
     * the unique constraint uk_cognition_agent_task_idem is the race guard — a
     * concurrent duplicate insert is caught and the existing task is returned.
     * (InnoDB duplicate-key errors do not abort the surrounding transaction.)
     */
    public AgentTaskRow findOrCreateTask(long userId, String workflowVersion, String idempotencyKey,
                                         String taskId, String triggerType, int maxRetry, String payloadJson) {
        Optional<AgentTaskRow> existing = findTask(userId, workflowVersion, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbc.update("""
                    INSERT INTO cognition_agent_task
                    (task_id,user_id,workflow_version,idempotency_key,trigger_type,status,max_retry,payload_json)
                    VALUES (?,?,?,?,?,'PENDING',?,?)
                    """, taskId, userId, workflowVersion, idempotencyKey, triggerType, maxRetry, payloadJson);
        } catch (DuplicateKeyException duplicate) {
            // lost the race against a concurrent insert with the same idempotency key; fall through to re-read
        }
        return findTask(userId, workflowVersion, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("task insert raced but winning row not found"));
    }

    public Optional<AgentTaskRow> findTask(long userId, String workflowVersion, String idempotencyKey) {
        return first(jdbc.query("""
                SELECT * FROM cognition_agent_task
                WHERE user_id=? AND workflow_version=? AND idempotency_key=?
                """, TASK_ROW, userId, workflowVersion, idempotencyKey));
    }

    public Optional<AgentTaskRow> findTaskByTaskId(String taskId) {
        return first(jdbc.query("SELECT * FROM cognition_agent_task WHERE task_id=?",
                TASK_ROW, taskId));
    }

    /** User's tasks, newest first, for the task status query endpoint. */
    public List<AgentTaskRow> listTasksByUser(long userId, int limit) {
        return jdbc.query("SELECT * FROM cognition_agent_task WHERE user_id=? ORDER BY id DESC LIMIT ?",
                TASK_ROW, userId, limit);
    }

    /** Row lock for retry/dead-letter transitions; call inside a transaction. */
    public Optional<AgentTaskRow> findTaskByTaskIdForUpdate(String taskId) {
        return first(jdbc.query("SELECT * FROM cognition_agent_task WHERE task_id=? FOR UPDATE",
                TASK_ROW, taskId));
    }

    public void markTaskRunning(String taskId, String runId, boolean retry) {
        jdbc.update("""
                UPDATE cognition_agent_task
                SET status='RUNNING', last_run_id=?, retry_count=retry_count+?, error_code=NULL, error_retryable=NULL
                WHERE task_id=?
                """, runId, retry ? 1 : 0, taskId);
    }

    /**
     * Terminal transition of a task. Status values: SUCCEEDED / NO_CHANGE /
     * NEEDS_CONFIRMATION / FAILED / DEAD. proposalId is set when a proposal was
     * produced, errorCode/errorRetryable when it failed.
     */
    public void markTaskFinished(String taskId, String status, String proposalId,
                                 String errorCode, Boolean errorRetryable) {
        jdbc.update("""
                UPDATE cognition_agent_task
                SET status=?, proposal_id=COALESCE(?, proposal_id), error_code=?, error_retryable=?
                WHERE task_id=?
                """, status, proposalId, errorCode,
                errorRetryable == null ? null : (errorRetryable ? 1 : 0), taskId);
    }

    /** Tasks the scheduler may pick up: PENDING, plus FAILED rows still below max_retry. */
    public List<AgentTaskRow> listRunnableTasks(int limit) {
        return jdbc.query("""
                SELECT * FROM cognition_agent_task
                WHERE status='PENDING' OR (status='FAILED' AND error_retryable=1 AND retry_count<max_retry)
                ORDER BY id LIMIT ?
                """, TASK_ROW, limit);
    }

    /** Execution context snapshot stored at creation; read by the MQ consumer / recovery sweeper. */
    public Optional<String> findTaskPayload(String taskId) {
        return jdbc.queryForList("SELECT payload_json FROM cognition_agent_task WHERE task_id=?",
                        String.class, taskId)
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** Per-user rate limiting: tasks created since the given instant (DB count, no Redis). */
    public long countRecentTasksByUser(long userId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cognition_agent_task WHERE user_id=? AND created_at>=?
                """, Long.class, userId, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    /**
     * Crash recovery: tasks still PENDING with no run long after creation — their
     * MQ message was lost (or the service died between commit and send); the
     * sweeper redelivers them.
     */
    public List<AgentTaskRow> listPendingRedispatchTasks(Instant createdBefore, int limit) {
        return jdbc.query("""
                SELECT * FROM cognition_agent_task
                WHERE status='PENDING' AND last_run_id IS NULL AND created_at<?
                ORDER BY id LIMIT ?
                """, TASK_ROW, Timestamp.from(createdBefore), limit);
    }

    /** Crash recovery: tasks stuck RUNNING past the worker timeout (worker died or hung). */
    public List<AgentTaskRow> listStuckRunningTasks(Instant updatedBefore, int limit) {
        return jdbc.query("""
                SELECT * FROM cognition_agent_task
                WHERE status='RUNNING' AND updated_at<?
                ORDER BY id LIMIT ?
                """, TASK_ROW, Timestamp.from(updatedBefore), limit);
    }

    // ---------- proposal ----------

    public void insertProposal(ProposalWrite proposal) {
        jdbc.update("""
                INSERT INTO cognition_proposal
                (proposal_id,user_id,graph_id,base_graph_version,status,route,target_topic_id,
                 evidence_ids,change_summary,operations_json,graph_preview_json,
                 requires_user_confirmation,workflow_version,run_id,idempotency_key)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, proposal.proposalId(), proposal.userId(), proposal.graphId(), proposal.baseGraphVersion(),
                proposal.status(), proposal.route(), proposal.targetTopicId(), proposal.evidenceIdsJson(),
                proposal.changeSummary(), proposal.operationsJson(), proposal.graphPreviewJson(),
                proposal.requiresUserConfirmation() ? 1 : 0, proposal.workflowVersion(),
                proposal.runId(), proposal.idempotencyKey());
    }

    public Optional<ProposalRow> findProposal(String proposalId) {
        return first(jdbc.query("SELECT * FROM cognition_proposal WHERE proposal_id=?",
                PROPOSAL_ROW, proposalId));
    }

    /** Lookup by the idempotency unique key, used when a retry re-produces a proposal. */
    public Optional<ProposalRow> findProposalByIdempotencyKey(long userId, String workflowVersion, String idempotencyKey) {
        return first(jdbc.query("""
                SELECT * FROM cognition_proposal
                WHERE user_id=? AND workflow_version=? AND idempotency_key=?
                """, PROPOSAL_ROW, userId, workflowVersion, idempotencyKey));
    }

    /** User's proposals filtered by status, newest first; page is (offset, limit). */
    public List<ProposalRow> listProposals(long userId, String status, int offset, int limit) {
        if (status == null || status.isBlank()) {
            return listProposals(userId, offset, limit);
        }
        return jdbc.query("""
                SELECT * FROM cognition_proposal WHERE user_id=? AND status=? ORDER BY id DESC LIMIT ? OFFSET ?
                """, PROPOSAL_ROW, userId, status, limit, offset);
    }

    public long countProposals(long userId, String status) {
        if (status == null || status.isBlank()) {
            return countProposals(userId);
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_proposal WHERE user_id=? AND status=?", Long.class, userId, status);
        return count == null ? 0 : count;
    }

    /** User's proposals, newest first; page is (offset, limit). */
    public List<ProposalRow> listProposals(long userId, int offset, int limit) {
        return jdbc.query("""
                SELECT * FROM cognition_proposal WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?
                """, PROPOSAL_ROW, userId, limit, offset);
    }

    public long countProposals(long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_proposal WHERE user_id=?", Long.class, userId);
        return count == null ? 0 : count;
    }

    public void updateProposalStatus(String proposalId, String status) {
        jdbc.update("UPDATE cognition_proposal SET status=? WHERE proposal_id=?", status, proposalId);
    }

    /** Records the user's decision together with the resulting proposal status. */
    public void recordProposalDecision(String proposalId, String status, String userDecision) {
        jdbc.update("""
                UPDATE cognition_proposal SET status=?, user_decision=?, decided_at=? WHERE proposal_id=?
                """, status, userDecision, Timestamp.from(Instant.now()), proposalId);
    }

    // ---------- proposal operation ----------

    public void insertProposalOperations(String proposalId, List<ProposalOperationWrite> operations) {
        jdbc.batchUpdate("""
                INSERT INTO cognition_proposal_operation
                (proposal_id,operation_index,operation_type,target_id,node_json,edge_json,
                 superseded_by_node_id,evidence_ids,reason)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, operations, operations.size(), (ps, op) -> {
            ps.setString(1, proposalId);
            ps.setInt(2, op.operationIndex());
            ps.setString(3, op.operationType());
            ps.setString(4, op.targetId());
            ps.setString(5, op.nodeJson());
            ps.setString(6, op.edgeJson());
            ps.setString(7, op.supersededByNodeId());
            ps.setString(8, op.evidenceIdsJson());
            ps.setString(9, op.reason());
        });
    }

    /** Operations of a proposal in application order; this is the source of truth when applying. */
    public List<ProposalOperationRow> listProposalOperations(String proposalId) {
        return jdbc.query("""
                SELECT * FROM cognition_proposal_operation WHERE proposal_id=? ORDER BY operation_index
                """, PROPOSAL_OPERATION_ROW, proposalId);
    }

    // ---------- context snapshot ----------

    public void insertContextSnapshot(String contextSnapshotId, long userId, String graphId, long graphVersion,
                                      String evidenceIdsJson, String candidateIdsJson) {
        jdbc.update("""
                INSERT INTO cognition_context_snapshot
                (context_snapshot_id,user_id,graph_id,graph_version,evidence_ids,candidate_ids)
                VALUES (?,?,?,?,?,?)
                """, contextSnapshotId, userId, graphId, graphVersion, evidenceIdsJson, candidateIdsJson);
    }

    public Optional<ContextSnapshotRow> findContextSnapshot(String contextSnapshotId) {
        return first(jdbc.query("SELECT * FROM cognition_context_snapshot WHERE context_snapshot_id=?",
                SNAPSHOT_ROW, contextSnapshotId));
    }

    // ---------- agent run ----------

    public void insertRun(String runId, String taskId, String workflowVersion, String finalStatus,
                          String errorCode, Long latencyMs, String modelProvider, String modelName,
                          String observationJson) {
        jdbc.update("""
                INSERT INTO cognition_agent_run
                (run_id,task_id,workflow_version,final_status,error_code,latency_ms,
                 model_provider,model_name,observation_json)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, runId, taskId, workflowVersion, finalStatus, errorCode, latencyMs,
                modelProvider, modelName, observationJson);
    }

    public Optional<AgentRunRow> findRun(String runId) {
        return first(jdbc.query("SELECT * FROM cognition_agent_run WHERE run_id=?",
                RUN_ROW, runId));
    }

    public List<AgentRunRow> listRunsByTask(String taskId) {
        return jdbc.query("SELECT * FROM cognition_agent_run WHERE task_id=? ORDER BY id",
                RUN_ROW, taskId);
    }

    /** Alerting: run-count per final_status since the given instant (grouped, small result). */
    public List<StatusCount> countRunStatusSince(Instant since) {
        return jdbc.query("""
                SELECT final_status, COUNT(*) AS cnt FROM cognition_agent_run
                WHERE created_at>=? GROUP BY final_status
                """, (rs, row) -> new StatusCount(rs.getString("final_status"), rs.getLong("cnt")),
                Timestamp.from(since));
    }

    // ---------- agent node run ----------

    public void insertNodeRun(String runId, String nodeId, String nodeVersion, String observationJson) {
        jdbc.update("""
                INSERT INTO cognition_agent_node_run (run_id,node_id,node_version,observation_json)
                VALUES (?,?,?,?)
                """, runId, nodeId, nodeVersion, observationJson);
    }

    public List<AgentNodeRunRow> listNodeRuns(String runId) {
        return jdbc.query("SELECT * FROM cognition_agent_node_run WHERE run_id=? ORDER BY id",
                NODE_RUN_ROW, runId);
    }

    // ---------- outbox ----------

    /** Written in the same transaction as the state change; a relay later delivers NEW events. */
    public void insertOutboxEvent(String eventId, long userId, String eventType, String payloadJson) {
        jdbc.update("""
                INSERT INTO outbox_event (event_id,user_id,event_type,payload_json,status)
                VALUES (?,?,?,?,'NEW')
                """, eventId, userId, eventType, payloadJson);
    }

    public List<OutboxEventRow> listNewOutboxEvents(int limit) {
        return jdbc.query("SELECT * FROM outbox_event WHERE status='NEW' ORDER BY id LIMIT ?",
                OUTBOX_ROW, limit);
    }

    public void markOutboxSent(String eventId) {
        jdbc.update("UPDATE outbox_event SET status='SENT', sent_at=? WHERE event_id=?",
                Timestamp.from(Instant.now()), eventId);
    }

    public void markOutboxFailed(String eventId) {
        jdbc.update("UPDATE outbox_event SET status='FAILED', retry_count=retry_count+1 WHERE event_id=?",
                eventId);
    }

    // ---------- helpers ----------

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    // ---------- row mappers ----------

    private static final RowMapper<AgentTaskRow> TASK_ROW = (rs, row) -> new AgentTaskRow(
            rs.getLong("id"), rs.getString("task_id"), rs.getLong("user_id"),
            rs.getString("workflow_version"), rs.getString("idempotency_key"),
            rs.getString("trigger_type"), rs.getString("status"),
            rs.getInt("retry_count"), rs.getInt("max_retry"),
            rs.getString("last_run_id"), rs.getString("proposal_id"), rs.getString("error_code"),
            nullableBoolean(rs, "error_retryable"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<ProposalRow> PROPOSAL_ROW = (rs, row) -> new ProposalRow(
            rs.getLong("id"), rs.getString("proposal_id"), rs.getLong("user_id"), rs.getString("graph_id"),
            rs.getLong("base_graph_version"), rs.getString("status"), rs.getString("route"),
            rs.getString("target_topic_id"), rs.getString("evidence_ids"), rs.getString("change_summary"),
            rs.getString("operations_json"), rs.getString("graph_preview_json"),
            rs.getBoolean("requires_user_confirmation"), rs.getString("workflow_version"),
            rs.getString("run_id"), rs.getString("idempotency_key"),
            rs.getString("user_decision"), instant(rs.getTimestamp("decided_at")),
            instant(rs.getTimestamp("created_at")));

    private static final RowMapper<ProposalOperationRow> PROPOSAL_OPERATION_ROW = (rs, row) -> new ProposalOperationRow(
            rs.getLong("id"), rs.getString("proposal_id"), rs.getInt("operation_index"),
            rs.getString("operation_type"), rs.getString("target_id"), rs.getString("node_json"),
            rs.getString("edge_json"), rs.getString("superseded_by_node_id"),
            rs.getString("evidence_ids"), rs.getString("reason"), instant(rs.getTimestamp("created_at")));

    private static final RowMapper<ContextSnapshotRow> SNAPSHOT_ROW = (rs, row) -> new ContextSnapshotRow(
            rs.getLong("id"), rs.getString("context_snapshot_id"), rs.getLong("user_id"),
            rs.getString("graph_id"), rs.getLong("graph_version"),
            rs.getString("evidence_ids"), rs.getString("candidate_ids"),
            instant(rs.getTimestamp("created_at")));

    private static final RowMapper<AgentRunRow> RUN_ROW = (rs, row) -> new AgentRunRow(
            rs.getLong("id"), rs.getString("run_id"), rs.getString("task_id"),
            rs.getString("workflow_version"), rs.getString("final_status"), rs.getString("error_code"),
            nullableLong(rs, "latency_ms"), rs.getString("model_provider"), rs.getString("model_name"),
            rs.getString("observation_json"), instant(rs.getTimestamp("created_at")));

    private static final RowMapper<AgentNodeRunRow> NODE_RUN_ROW = (rs, row) -> new AgentNodeRunRow(
            rs.getLong("id"), rs.getString("run_id"), rs.getString("node_id"),
            rs.getString("node_version"), rs.getString("observation_json"),
            instant(rs.getTimestamp("created_at")));

    private static final RowMapper<OutboxEventRow> OUTBOX_ROW = (rs, row) -> new OutboxEventRow(
            rs.getLong("id"), rs.getString("event_id"), rs.getLong("user_id"),
            rs.getString("event_type"), rs.getString("payload_json"), rs.getString("status"),
            rs.getInt("retry_count"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("sent_at")));

    // ---------- row records (enum-valued columns carried as contract String names) ----------

    public record AgentTaskRow(long id, String taskId, long userId, String workflowVersion,
                               String idempotencyKey, String triggerType, String status,
                               int retryCount, int maxRetry, String lastRunId, String proposalId,
                               String errorCode, Boolean errorRetryable,
                               Instant createdAt, Instant updatedAt) {
    }

    public record ProposalRow(long id, String proposalId, long userId, String graphId,
                              long baseGraphVersion, String status, String route, String targetTopicId,
                              String evidenceIdsJson, String changeSummary, String operationsJson,
                              String graphPreviewJson, boolean requiresUserConfirmation,
                              String workflowVersion, String runId, String idempotencyKey,
                              String userDecision, Instant decidedAt, Instant createdAt) {
    }

    public record ProposalOperationRow(long id, String proposalId, int operationIndex, String operationType,
                                       String targetId, String nodeJson, String edgeJson,
                                       String supersededByNodeId, String evidenceIdsJson, String reason,
                                       Instant createdAt) {
    }

    public record ContextSnapshotRow(long id, String contextSnapshotId, long userId, String graphId,
                                     long graphVersion, String evidenceIdsJson, String candidateIdsJson,
                                     Instant createdAt) {
    }

    public record AgentRunRow(long id, String runId, String taskId, String workflowVersion,
                              String finalStatus, String errorCode, Long latencyMs,
                              String modelProvider, String modelName, String observationJson,
                              Instant createdAt) {
    }

    public record AgentNodeRunRow(long id, String runId, String nodeId, String nodeVersion,
                                  String observationJson, Instant createdAt) {
    }

    public record OutboxEventRow(long id, String eventId, long userId, String eventType,
                                 String payloadJson, String status, int retryCount,
                                 Instant createdAt, Instant sentAt) {
    }

    /** GROUP BY final_status result for run-window statistics. */
    public record StatusCount(String status, long count) {
    }

    /** Write model for insertProposal; JSON fields are pre-serialized strings. */
    public record ProposalWrite(String proposalId, long userId, String graphId, long baseGraphVersion,
                                String status, String route, String targetTopicId, String evidenceIdsJson,
                                String changeSummary, String operationsJson, String graphPreviewJson,
                                boolean requiresUserConfirmation, String workflowVersion, String runId,
                                String idempotencyKey) {
    }

    /** Write model for insertProposalOperations; JSON fields are pre-serialized strings. */
    public record ProposalOperationWrite(int operationIndex, String operationType, String targetId,
                                         String nodeJson, String edgeJson, String supersededByNodeId,
                                         String evidenceIdsJson, String reason) {
    }
}
