package athena.cognition.biz.proposal;

import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDecision;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDecisionView;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDetailView;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalOperationView;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalSummaryView;
import athena.cognition.biz.outbox.OutboxService;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.ProposalOperationRow;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.ProposalRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.EdgeWrite;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphEdgeRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphNodeRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphOperationInput;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphSnapshot;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.NodeWrite;
import athena.cognition.biz.rpc.agent.dto.GraphEdge;
import athena.cognition.biz.rpc.agent.dto.GraphNode;
import athena.cognition.biz.rpc.agent.dto.GraphNodeType;
import athena.cognition.biz.rpc.agent.dto.GraphProposalStatus;
import athena.cognition.biz.service.CognitionService.PagedResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proposal confirmation and the transactional patch applier (handoff section
 * 9). The Agent never writes the real graph; only after the user confirms does
 * this service apply proposal.operations inside a single transaction.
 *
 * <p>The 13 steps of section 9, in order: take userId from the login context
 * (controller) -> load the proposal and verify ownership -> lock the graph row
 * FOR UPDATE -> proposal must still be READY_FOR_CONFIRMATION (a repeated
 * decision is a STATE_CONFLICT) -> baseGraphVersion must equal the current
 * graphVersion (otherwise mark the proposal STALE and throw VERSION_CONFLICT)
 * -> replay operations one by one against the whitelist rules -> verify final
 * integrity (every non-topic node belongs to a topic, at most one PENDING
 * action per topic) -> graphVersion + 1 -> write history and the proposal
 * terminal state -> write the outbox event -> commit. Any failure rolls the
 * whole transaction back.
 *
 * <p>KEEP_AS_KNOWLEDGE and REJECT only change the proposal state; the patch is
 * never applied. graphPreview is display-only and never written to the graph
 * tables.
 */
@Service
public class ProposalApplyService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CognitionAgentJdbcRepository agentRepository;
    private final CognitionGraphJdbcRepository graphRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public ProposalApplyService(CognitionAgentJdbcRepository agentRepository,
                                CognitionGraphJdbcRepository graphRepository,
                                OutboxService outboxService, ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.graphRepository = graphRepository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    // ---------- proposal query ----------

    public PagedResult<ProposalSummaryView> listProposals(long userId, String status, int page, int pageSize) {
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (Math.max(page, 1) - 1) * size;
        List<ProposalSummaryView> items = agentRepository.listProposals(userId, status, offset, size)
                .stream().map(this::toSummary).toList();
        return new PagedResult<>(items, agentRepository.countProposals(userId, status));
    }

    public ProposalDetailView getProposal(long userId, String proposalId) {
        ProposalRow proposal = findOwnedProposal(userId, proposalId);
        List<ProposalOperationView> operations = agentRepository.listProposalOperations(proposalId).stream()
                .map(this::toOperationView).toList();
        return new ProposalDetailView(proposal.proposalId(), proposal.status(), proposal.route(),
                proposal.targetTopicId(), proposal.baseGraphVersion(), proposal.changeSummary(),
                readStringList(proposal.evidenceIdsJson()), operations, readJson(proposal.graphPreviewJson()),
                proposal.requiresUserConfirmation(), proposal.workflowVersion(), proposal.runId(),
                proposal.userDecision(), proposal.decidedAt(), proposal.createdAt());
    }

    // ---------- section 9: decision + transactional apply ----------

    @Transactional
    public ProposalDecisionView decide(long userId, String proposalId, ProposalDecision decision) {
        // steps 1-2: userId comes from the login context (controller); step 5 (ownership part)
        ProposalRow proposal = findOwnedProposal(userId, proposalId);

        // step 4: lock the user's graph row
        GraphRow graph = graphRepository.findGraphForUpdate(userId).orElseThrow(CognitionException::notFound);
        if (!graph.graphId().equals(proposal.graphId())) throw CognitionException.notFound();

        // steps 5/7: status gate; an already-decided proposal is a plain conflict, never a version problem
        if (!GraphProposalStatus.READY_FOR_CONFIRMATION.name().equals(proposal.status())) {
            if (proposal.userDecision() != null
                    || GraphProposalStatus.STALE.name().equals(proposal.status())) {
                throw CognitionException.stateConflict("提案已处理", proposalId, proposal.status());
            }
            throw CognitionException.proposalNotReady(proposalId, proposal.status());
        }

        // step 6: optimistic base version check; a moved graph marks the proposal STALE, never overwrites
        if (proposal.baseGraphVersion() != graph.graphVersion()) {
            agentRepository.updateProposalStatus(proposalId, GraphProposalStatus.STALE.name());
            throw CognitionException.versionConflict(proposalId, String.valueOf(graph.graphVersion()));
        }

        if (decision != ProposalDecision.ACCEPT) {
            // KEEP_AS_KNOWLEDGE / REJECT: state change only, the patch is not applied
            String status = decision == ProposalDecision.KEEP_AS_KNOWLEDGE
                    ? GraphProposalStatus.KEPT_AS_KNOWLEDGE.name() : GraphProposalStatus.REJECTED.name();
            agentRepository.recordProposalDecision(proposalId, status, decision.name());
            return new ProposalDecisionView(proposalId, status, decision.name(), null);
        }

        // step 8: replay operations against the whitelist on an in-memory model of the locked graph
        GraphSnapshot snapshot = graphRepository.loadGraph(userId).orElseThrow(CognitionException::notFound);
        Map<String, NodeState> nodes = new LinkedHashMap<>();
        for (GraphNodeRow row : snapshot.nodes()) {
            nodes.put(row.nodeId(), new NodeState(row.nodeId(), row.type(), row.status(), row.topicId(),
                    row.actionStatus(), row.version()));
        }
        Map<String, EdgeState> edges = new LinkedHashMap<>();
        for (GraphEdgeRow row : snapshot.edges()) {
            edges.put(row.edgeId(), new EdgeState(row.edgeId(), row.type(), row.fromNodeId(), row.toNodeId(),
                    row.active()));
        }

        List<ProposalOperationRow> operations = agentRepository.listProposalOperations(proposalId);
        List<GraphOperationInput> inputs = new ArrayList<>();
        for (ProposalOperationRow op : operations) {
            validateAndStage(proposal, op, operations.size(), nodes, edges, inputs);
        }

        // step 9: final integrity on the resulting graph
        verifyFinalIntegrity(nodes);

        // steps 10-11: graphVersion + 1, history and proposal terminal state
        long newVersion = graphRepository.applyGraphUpdate(graph, userId, proposalId,
                CognitionGraphJdbcRepository.OPERATOR_USER, inputs, proposal.operationsJson());
        agentRepository.recordProposalDecision(proposalId, GraphProposalStatus.ACCEPTED.name(), decision.name());

        // step 12: outbox event inside the same transaction (published only after commit)
        outboxService.saveEvent(userId, OutboxService.GRAPH_UPDATED, Map.of(
                "graphId", graph.graphId(), "graphVersion", newVersion, "proposalId", proposalId));
        return new ProposalDecisionView(proposalId, GraphProposalStatus.ACCEPTED.name(), decision.name(), newVersion);
    }

    // ---------- operation whitelist (section 9 table) ----------

    private void validateAndStage(ProposalRow proposal, ProposalOperationRow op, int operationCount,
                                  Map<String, NodeState> nodes, Map<String, EdgeState> edges,
                                  List<GraphOperationInput> inputs) {
        String type = op.operationType();
        switch (type) {
            case "ADD_NODE" -> {
                GraphNode node = readNode(op);
                if (node.id == null || node.id.isBlank()) throw invalidOperation(op, "节点缺少 ID");
                if (nodes.containsKey(node.id)) throw invalidOperation(op, "ADD_NODE 的节点 ID 已存在");
                if (node.type != GraphNodeType.TOPIC) {
                    // non-topic nodes must belong to the target topic
                    if (node.topicId == null || node.topicId.isBlank()) {
                        throw invalidOperation(op, "非主题节点必须属于目标主题");
                    }
                    if (proposal.targetTopicId() != null && !proposal.targetTopicId().equals(node.topicId)) {
                        throw invalidOperation(op, "非主题节点必须属于目标主题");
                    }
                }
                nodes.put(node.id, new NodeState(node.id, node.type.name(),
                        node.status == null ? "ACTIVE" : node.status.name(), node.topicId,
                        node.actionStatus == null ? null : node.actionStatus.name(), node.version));
                inputs.add(new GraphOperationInput("ADD_NODE", node.id, toNodeWrite(node), null, null));
            }
            case "UPDATE_NODE" -> {
                GraphNode node = readNode(op);
                String targetId = op.targetId() != null ? op.targetId() : node.id;
                NodeState existing = targetId == null ? null : nodes.get(targetId);
                if (existing == null) throw invalidOperation(op, "UPDATE_NODE 的目标节点不存在");
                if (node.version != existing.version + 1) {
                    throw invalidOperation(op, "节点版本必须是旧版本 + 1");
                }
                nodes.put(targetId, new NodeState(targetId, node.type.name(),
                        node.status == null ? existing.status : node.status.name(), node.topicId,
                        node.actionStatus == null ? null : node.actionStatus.name(), node.version));
                inputs.add(new GraphOperationInput("UPDATE_NODE", targetId, toNodeWrite(node), null, null));
            }
            case "ADD_EDGE" -> {
                GraphEdge edge = readEdge(op);
                if (edge.id == null || edge.id.isBlank()) throw invalidOperation(op, "边缺少 ID");
                EdgeState existing = edges.get(edge.id);
                if (existing != null && existing.active) throw invalidOperation(op, "ADD_EDGE 的边 ID 已存在");
                boolean duplicateRelation = edges.values().stream().anyMatch(current ->
                        current.active && current.type.equals(edge.type.name())
                                && current.fromNodeId.equals(edge.fromNodeId)
                                && current.toNodeId.equals(edge.toNodeId));
                if (duplicateRelation) throw invalidOperation(op, "活动关系不能重复");
                if (!nodes.containsKey(edge.fromNodeId) || !nodes.containsKey(edge.toNodeId)) {
                    throw invalidOperation(op, "边两端节点必须已存在");
                }
                edges.put(edge.id, new EdgeState(edge.id, edge.type.name(), edge.fromNodeId, edge.toNodeId, true));
                inputs.add(new GraphOperationInput("ADD_EDGE", edge.id, null, toEdgeWrite(edge), null));
            }
            case "SUPERSEDE_NODE" -> {
                NodeState target = op.targetId() == null ? null : nodes.get(op.targetId());
                if (target == null) throw invalidOperation(op, "SUPERSEDE_NODE 的旧节点不存在");
                NodeState replacement = op.supersededByNodeId() == null ? null : nodes.get(op.supersededByNodeId());
                if (replacement == null) throw invalidOperation(op, "替代节点必须已存在（通常由同一提案的 ADD_NODE 引入）");
                if (!target.type.equals(replacement.type)) {
                    throw invalidOperation(op, "旧节点与替代节点类型必须一致");
                }
                target.status = "SUPERSEDED";
                inputs.add(new GraphOperationInput("SUPERSEDE_NODE", op.targetId(), null, null,
                        op.supersededByNodeId()));
            }
            case "DEACTIVATE_EDGE" -> {
                EdgeState edge = op.targetId() == null ? null : edges.get(op.targetId());
                if (edge == null || !edge.active) throw invalidOperation(op, "DEACTIVATE_EDGE 的边不存在或已停用");
                edge.active = false;
                inputs.add(new GraphOperationInput("DEACTIVATE_EDGE", op.targetId(), null, null, null));
            }
            case "NO_OP" -> {
                if (operationCount != 1) throw invalidOperation(op, "NO_OP 只能单独存在于无变化提案");
            }
            default -> throw invalidOperation(op, "未知的图谱操作: " + type);
        }
    }

    /** Every non-topic ACTIVE node must belong to an ACTIVE topic; each topic has at most one PENDING action. */
    private void verifyFinalIntegrity(Map<String, NodeState> nodes) {
        Map<String, Integer> pendingActionsByTopic = new LinkedHashMap<>();
        for (NodeState node : nodes.values()) {
            if (!"ACTIVE".equals(node.status)) continue;
            if (GraphNodeType.TOPIC.name().equals(node.type)) continue;
            NodeState topic = node.topicId == null ? null : nodes.get(node.topicId);
            if (topic == null || !GraphNodeType.TOPIC.name().equals(topic.type)
                    || !"ACTIVE".equals(topic.status)) {
                throw CognitionException.invalidArgument("图谱完整性校验失败：非主题节点必须属于目标主题");
            }
            if (GraphNodeType.ACTION.name().equals(node.type) && "PENDING".equals(node.actionStatus)) {
                pendingActionsByTopic.merge(node.topicId, 1, Integer::sum);
            }
        }
        if (pendingActionsByTopic.values().stream().anyMatch(count -> count > 1)) {
            throw CognitionException.invalidArgument("图谱完整性校验失败：每个主题最多一条待办行动");
        }
    }

    // ---------- write-model conversion ----------

    private NodeWrite toNodeWrite(GraphNode node) {
        return new NodeWrite(node.id, node.type.name(),
                node.status == null ? "ACTIVE" : node.status.name(), node.topicId, node.title, node.content,
                node.domain, writeJson(node.evidenceIds),
                node.actionType == null ? null : node.actionType.name(),
                node.actionStatus == null ? null : node.actionStatus.name(),
                parseInstant(node.dueAt),
                node.feedbackOptions == null ? null : writeJson(node.feedbackOptions));
    }

    private EdgeWrite toEdgeWrite(GraphEdge edge) {
        return new EdgeWrite(edge.id, edge.type.name(), edge.fromNodeId, edge.toNodeId,
                writeJson(edge.evidenceIds), edge.active);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }

    // ---------- view mapping / helpers ----------

    private ProposalRow findOwnedProposal(long userId, String proposalId) {
        ProposalRow proposal = agentRepository.findProposal(proposalId).orElseThrow(CognitionException::notFound);
        if (proposal.userId() != userId) throw CognitionException.notFound();
        return proposal;
    }

    private ProposalSummaryView toSummary(ProposalRow row) {
        return new ProposalSummaryView(row.proposalId(), row.status(), row.route(), row.targetTopicId(),
                row.baseGraphVersion(), row.changeSummary(), row.requiresUserConfirmation(),
                row.userDecision(), row.decidedAt(), row.createdAt());
    }

    private ProposalOperationView toOperationView(ProposalOperationRow row) {
        return new ProposalOperationView(row.operationIndex(), row.operationType(), row.targetId(),
                readJson(row.nodeJson()), readJson(row.edgeJson()), row.supersededByNodeId(),
                readStringList(row.evidenceIdsJson()), row.reason());
    }

    private static CognitionException invalidOperation(ProposalOperationRow op, String message) {
        return CognitionException.invalidArgument("非法的图谱操作（" + op.operationType() + "）: " + message);
    }

    private GraphNode readNode(ProposalOperationRow op) {
        if (op.nodeJson() == null) throw invalidOperation(op, "缺少节点数据");
        try {
            return objectMapper.readValue(op.nodeJson(), GraphNode.class);
        } catch (Exception ex) {
            throw invalidOperation(op, "节点数据无法解析");
        }
    }

    private GraphEdge readEdge(ProposalOperationRow op) {
        if (op.edgeJson() == null) throw invalidOperation(op, "缺少边数据");
        try {
            return objectMapper.readValue(op.edgeJson(), GraphEdge.class);
        } catch (Exception ex) {
            throw invalidOperation(op, "边数据无法解析");
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize json", ex);
        }
    }

    /** Mutable in-memory node state while replaying a patch. */
    private static final class NodeState {
        private final String nodeId;
        private final String type;
        private String status;
        private final String topicId;
        private final String actionStatus;
        private final int version;

        private NodeState(String nodeId, String type, String status, String topicId, String actionStatus,
                          int version) {
            this.nodeId = nodeId;
            this.type = type;
            this.status = status;
            this.topicId = topicId;
            this.actionStatus = actionStatus;
            this.version = version;
        }
    }

    /** Mutable in-memory edge state while replaying a patch. */
    private static final class EdgeState {
        private final String edgeId;
        private final String type;
        private final String fromNodeId;
        private final String toNodeId;
        private boolean active;

        private EdgeState(String edgeId, String type, String fromNodeId, String toNodeId, boolean active) {
            this.edgeId = edgeId;
            this.type = type;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.active = active;
        }
    }
}
