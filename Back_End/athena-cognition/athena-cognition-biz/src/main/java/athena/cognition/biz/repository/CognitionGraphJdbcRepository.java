package athena.cognition.biz.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for the cognition graph tables (cognition_agent_v1.sql):
 * cognition_graph / cognition_graph_node / cognition_graph_edge /
 * cognition_graph_history.
 *
 * <p>This is the storage foundation of the graph-update proposal pipeline and
 * is fully parallel to the clue/digest/topic flow in {@link CognitionJdbcRepository}.
 * Enum-typed contract values (GraphNodeType, GraphOperationType, ...) live in the
 * athena-cognition-agent module which is not on this module's classpath, so they
 * are carried as plain {@code String} here; validation against the contract
 * belongs to the service layer. JSON columns are passed through as serialized
 * strings for the same reason.
 *
 * <p>All mutating methods expect the caller to run inside a transaction and to
 * have locked the graph row via {@link #findGraphForUpdate(long)} first.
 */
@Repository
public class CognitionGraphJdbcRepository {

    /** operator values for cognition_graph_history (fixed set, see table comment). */
    public static final String OPERATOR_USER = "USER";
    public static final String OPERATOR_SYSTEM = "SYSTEM";

    private final JdbcTemplate jdbc;

    public CognitionGraphJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- graph ----------

    public Optional<GraphRow> findGraphByUserId(long userId) {
        return first(jdbc.query("SELECT * FROM cognition_graph WHERE user_id=?",
                GRAPH_ROW, userId));
    }

    /** Row lock on the user's graph; must be called inside a transaction before applying a proposal. */
    public Optional<GraphRow> findGraphForUpdate(long userId) {
        return first(jdbc.query("SELECT * FROM cognition_graph WHERE user_id=? FOR UPDATE",
                GRAPH_ROW, userId));
    }

    /**
     * Creates the empty graph for a user (graphVersion=0). The external graph id
     * is generated locally in the CognitionIds style ("graph_" prefix) because
     * the graph is born in the main backend before any Agent run exists.
     */
    public GraphRow createEmptyGraph(long userId, String graphSchemaVersion) {
        String graphId = "graph_" + UUID.randomUUID();
        jdbc.update("INSERT INTO cognition_graph (user_id,graph_id,graph_schema_version,graph_version) VALUES (?,?,?,0)",
                userId, graphId, graphSchemaVersion);
        return findGraphByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("graph insert succeeded but row not found"));
    }

    /** Loads the graph with all of its nodes and edges assembled; empty when the user has no graph yet. */
    public Optional<GraphSnapshot> loadGraph(long userId) {
        Optional<GraphRow> graph = findGraphByUserId(userId);
        if (graph.isEmpty()) {
            return Optional.empty();
        }
        List<GraphNodeRow> nodes = jdbc.query(
                "SELECT * FROM cognition_graph_node WHERE graph_id=? ORDER BY id",
                NODE_ROW, graph.get().graphId());
        List<GraphEdgeRow> edges = jdbc.query(
                "SELECT * FROM cognition_graph_edge WHERE graph_id=? ORDER BY id",
                EDGE_ROW, graph.get().graphId());
        return Optional.of(new GraphSnapshot(graph.get(), nodes, edges));
    }

    /** Loads the user's graph, creating an empty one (graphVersion=0) on first access. */
    public GraphSnapshot getOrCreateGraph(long userId, String graphSchemaVersion) {
        return loadGraph(userId).orElseGet(() ->
                new GraphSnapshot(createEmptyGraph(userId, graphSchemaVersion), List.of(), List.of()));
    }

    // ---------- apply operations (inside caller's transaction) ----------

    /**
     * Applies the operations of an accepted proposal to a locked graph:
     * writes nodes/edges per operation, bumps graph_version by 1 with an
     * optimistic check against {@code lockedGraph.graphVersion()}, and appends
     * a history row.
     *
     * @return the new graph version
     * @throws IllegalStateException when the graph version moved under us (STALE proposal)
     */
    public long applyGraphUpdate(GraphRow lockedGraph, long userId, String proposalId, String operator,
                                 List<GraphOperationInput> operations, String operationsJson) {
        for (GraphOperationInput op : operations) {
            switch (op.operationType()) {
                case "ADD_NODE", "UPDATE_NODE" -> upsertNode(lockedGraph.graphId(), op.node());
                case "ADD_EDGE" -> upsertEdge(lockedGraph.graphId(), op.edge());
                case "SUPERSEDE_NODE" -> supersedeNode(lockedGraph.graphId(), op.targetId());
                case "DEACTIVATE_EDGE" -> deactivateEdge(lockedGraph.graphId(), op.targetId());
                default -> throw new IllegalArgumentException("unknown graph operation: " + op.operationType());
            }
        }
        long newVersion = lockedGraph.graphVersion() + 1;
        int updated = jdbc.update(
                "UPDATE cognition_graph SET graph_version=? WHERE id=? AND graph_version=?",
                newVersion, lockedGraph.id(), lockedGraph.graphVersion());
        if (updated == 0) {
            throw new IllegalStateException(
                    "graph version conflict, expected " + lockedGraph.graphVersion() + " for " + lockedGraph.graphId());
        }
        insertHistory(lockedGraph.graphId(), newVersion, userId, proposalId, operator, operationsJson);
        return newVersion;
    }

    /** ADD_NODE / UPDATE_NODE share one upsert; an existing node is overwritten and its version bumps. */
    public void upsertNode(String graphId, NodeWrite node) {
        if (node == null) {
            throw new IllegalArgumentException("node write is required for ADD_NODE/UPDATE_NODE");
        }
        jdbc.update("""
                INSERT INTO cognition_graph_node
                (graph_id,node_id,type,status,topic_id,title,content,domain,evidence_ids,
                 action_type,action_status,due_at,feedback_options,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1)
                ON DUPLICATE KEY UPDATE
                 type=VALUES(type), status=VALUES(status), topic_id=VALUES(topic_id),
                 title=VALUES(title), content=VALUES(content), domain=VALUES(domain),
                 evidence_ids=VALUES(evidence_ids), action_type=VALUES(action_type),
                 action_status=VALUES(action_status), due_at=VALUES(due_at),
                 feedback_options=VALUES(feedback_options), version=version+1
                """, graphId, node.nodeId(), node.type(), node.status(), node.topicId(), node.title(),
                node.content(), node.domain(), node.evidenceIdsJson(), node.actionType(), node.actionStatus(),
                node.dueAt() == null ? null : Timestamp.from(node.dueAt()), node.feedbackOptionsJson());
    }

    /** ADD_EDGE upsert; a re-added edge becomes active again and its version bumps. */
    public void upsertEdge(String graphId, EdgeWrite edge) {
        if (edge == null) {
            throw new IllegalArgumentException("edge write is required for ADD_EDGE");
        }
        jdbc.update("""
                INSERT INTO cognition_graph_edge
                (graph_id,edge_id,type,from_node_id,to_node_id,evidence_ids,active,version)
                VALUES (?,?,?,?,?,?,?,1)
                ON DUPLICATE KEY UPDATE
                 type=VALUES(type), from_node_id=VALUES(from_node_id), to_node_id=VALUES(to_node_id),
                 evidence_ids=VALUES(evidence_ids), active=VALUES(active), version=version+1
                """, graphId, edge.edgeId(), edge.type(), edge.fromNodeId(), edge.toNodeId(),
                edge.evidenceIdsJson(), edge.active() ? 1 : 0);
    }

    /** SUPERSEDE_NODE: mark the target node SUPERSEDED. The replacement node arrives as its own ADD_NODE op. */
    public void supersedeNode(String graphId, String nodeId) {
        jdbc.update("""
                UPDATE cognition_graph_node SET status='SUPERSEDED', version=version+1
                WHERE graph_id=? AND node_id=?
                """, graphId, nodeId);
    }

    /** DEACTIVATE_EDGE: logical deactivate only, never physically delete. */
    public void deactivateEdge(String graphId, String edgeId) {
        jdbc.update("""
                UPDATE cognition_graph_edge SET active=0, version=version+1
                WHERE graph_id=? AND edge_id=?
                """, graphId, edgeId);
    }

    // ---------- history ----------

    public void insertHistory(String graphId, long graphVersion, long userId, String proposalId,
                              String operator, String operationsJson) {
        if (!OPERATOR_USER.equals(operator) && !OPERATOR_SYSTEM.equals(operator)) {
            throw new IllegalArgumentException("operator must be USER or SYSTEM");
        }
        jdbc.update("""
                INSERT INTO cognition_graph_history
                (graph_id,graph_version,user_id,proposal_id,operator,operations_json)
                VALUES (?,?,?,?,?,?)
                """, graphId, graphVersion, userId, proposalId, operator, operationsJson);
    }

    public List<GraphHistoryRow> listHistory(String graphId, int offset, int limit) {
        return jdbc.query(
                "SELECT * FROM cognition_graph_history WHERE graph_id=? ORDER BY graph_version DESC LIMIT ? OFFSET ?",
                HISTORY_ROW, graphId, limit, offset);
    }

    // ---------- helpers ----------

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    // ---------- row mappers ----------

    private static final RowMapper<GraphRow> GRAPH_ROW = (rs, row) -> new GraphRow(
            rs.getLong("id"), rs.getLong("user_id"), rs.getString("graph_id"),
            rs.getString("graph_schema_version"), rs.getLong("graph_version"),
            instant(rs.getTimestamp("updated_at")), instant(rs.getTimestamp("created_at")));

    private static final RowMapper<GraphNodeRow> NODE_ROW = (rs, row) -> new GraphNodeRow(
            rs.getLong("id"), rs.getString("graph_id"), rs.getString("node_id"),
            rs.getString("type"), rs.getString("status"), rs.getString("topic_id"),
            rs.getString("title"), rs.getString("content"), rs.getString("domain"),
            rs.getString("evidence_ids"), rs.getString("action_type"), rs.getString("action_status"),
            instant(rs.getTimestamp("due_at")), rs.getString("feedback_options"),
            rs.getInt("version"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<GraphEdgeRow> EDGE_ROW = (rs, row) -> new GraphEdgeRow(
            rs.getLong("id"), rs.getString("graph_id"), rs.getString("edge_id"),
            rs.getString("type"), rs.getString("from_node_id"), rs.getString("to_node_id"),
            rs.getString("evidence_ids"), rs.getBoolean("active"), rs.getInt("version"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<GraphHistoryRow> HISTORY_ROW = (rs, row) -> new GraphHistoryRow(
            rs.getLong("id"), rs.getString("graph_id"), rs.getLong("graph_version"),
            rs.getLong("user_id"), rs.getString("proposal_id"), rs.getString("operator"),
            rs.getString("operations_json"), instant(rs.getTimestamp("created_at")));

    // ---------- row records (enum-valued columns carried as contract String names) ----------

    public record GraphRow(long id, long userId, String graphId, String graphSchemaVersion,
                           long graphVersion, Instant updatedAt, Instant createdAt) {
    }

    public record GraphNodeRow(long id, String graphId, String nodeId, String type, String status,
                               String topicId, String title, String content, String domain,
                               String evidenceIdsJson, String actionType, String actionStatus,
                               Instant dueAt, String feedbackOptionsJson, int version,
                               Instant createdAt, Instant updatedAt) {
    }

    public record GraphEdgeRow(long id, String graphId, String edgeId, String type,
                               String fromNodeId, String toNodeId, String evidenceIdsJson,
                               boolean active, int version, Instant createdAt, Instant updatedAt) {
    }

    public record GraphHistoryRow(long id, String graphId, long graphVersion, long userId,
                                  String proposalId, String operator, String operationsJson,
                                  Instant createdAt) {
    }

    /** Graph plus its nodes and edges, mirroring the Agent-side PersonalCognitionGraph. */
    public record GraphSnapshot(GraphRow graph, List<GraphNodeRow> nodes, List<GraphEdgeRow> edges) {
    }

    /** Write model for ADD_NODE / UPDATE_NODE; evidenceIdsJson / feedbackOptionsJson are pre-serialized JSON. */
    public record NodeWrite(String nodeId, String type, String status, String topicId, String title,
                            String content, String domain, String evidenceIdsJson, String actionType,
                            String actionStatus, Instant dueAt, String feedbackOptionsJson) {
    }

    /** Write model for ADD_EDGE; evidenceIdsJson is pre-serialized JSON. */
    public record EdgeWrite(String edgeId, String type, String fromNodeId, String toNodeId,
                            String evidenceIdsJson, boolean active) {
    }

    /**
     * One patch operation as applied to the graph, mirroring the Agent-side
     * GraphPatchOperation: node is set for ADD_NODE/UPDATE_NODE, edge for
     * ADD_EDGE, targetId for SUPERSEDE_NODE/DEACTIVATE_EDGE.
     */
    public record GraphOperationInput(String operationType, String targetId, NodeWrite node, EdgeWrite edge,
                                      String supersededByNodeId) {
    }
}
