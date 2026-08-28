package athena.cognition.biz.service;

import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionIds;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphEdgeRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphNodeRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphSnapshot;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.repository.CognitionJdbcRepository.EvidenceRow;
import athena.cognition.biz.rpc.agent.dto.CanonicalEvidence;
import athena.cognition.biz.rpc.agent.dto.EvidenceFactLevel;
import athena.cognition.biz.rpc.agent.dto.EvidenceSourceType;
import athena.cognition.biz.rpc.agent.dto.GraphActionStatus;
import athena.cognition.biz.rpc.agent.dto.GraphActionFeedbackResult;
import athena.cognition.biz.rpc.agent.dto.GraphActionType;
import athena.cognition.biz.rpc.agent.dto.GraphContract;
import athena.cognition.biz.rpc.agent.dto.GraphEdge;
import athena.cognition.biz.rpc.agent.dto.GraphEdgeType;
import athena.cognition.biz.rpc.agent.dto.GraphNode;
import athena.cognition.biz.rpc.agent.dto.GraphNodeStatus;
import athena.cognition.biz.rpc.agent.dto.GraphNodeType;
import athena.cognition.biz.rpc.agent.dto.PersonalCognitionGraph;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Graph loading/assembly for the graph-update proposal pipeline: loads (or
 * creates) the user's personal cognition graph, converts it to the Agent
 * contract form, assembles the user's existing canonical evidence for Agent
 * node 2 dedup, and validates graph resource ownership before any Agent call
 * (handoff section 14: user isolation happens before calling the Agent).
 */
@Service
public class CognitionGraphService {

    private final CognitionGraphJdbcRepository graphRepository;
    private final CognitionJdbcRepository repository;
    private final ObjectMapper objectMapper;

    public CognitionGraphService(CognitionGraphJdbcRepository graphRepository,
                                 CognitionJdbcRepository repository, ObjectMapper objectMapper) {
        this.graphRepository = graphRepository;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Loads the user's graph, creating the empty graph (graphVersion=0) on first access. */
    public GraphSnapshot getOrCreateGraph(long userId) {
        return graphRepository.getOrCreateGraph(userId, GraphContract.GRAPH_SCHEMA_VERSION);
    }

    /** GET /graph: the user's current graph in Agent contract form (manual testing). */
    public PersonalCognitionGraph getGraphView(long userId) {
        return toAgentGraph(getOrCreateGraph(userId));
    }

    public PersonalCognitionGraph toAgentGraph(GraphSnapshot snapshot) {
        PersonalCognitionGraph graph = new PersonalCognitionGraph();
        graph.graphId = snapshot.graph().graphId();
        graph.graphSchemaVersion = snapshot.graph().graphSchemaVersion();
        graph.graphVersion = snapshot.graph().graphVersion();
        graph.updatedAt = iso(snapshot.graph().updatedAt());
        graph.nodes = snapshot.nodes().stream().map(this::toAgentNode).toList();
        graph.edges = snapshot.edges().stream().map(this::toAgentEdge).toList();
        return graph;
    }

    /**
     * The user's existing canonical evidence (handoff section 4: full set in V1
     * so source/content dedup stays globally correct). CLUE evidence maps to the
     * Agent's ARTICLE_HIGHLIGHT source type; DEVICE evidence is not part of the
     * Agent contract and is skipped.
     */
    public List<CanonicalEvidence> listCanonicalEvidence(long userId) {
        return repository.listActiveEvidence(userId).stream()
                .map(this::toCanonicalEvidence).filter(Objects::nonNull).toList();
    }

    /** Validates that userSelectedTopicId is an ACTIVE TOPIC node of this user's graph. */
    public GraphNodeRow requireTopicNode(long userId, String topicNodeId) {
        GraphNodeRow node = findNode(userId, topicNodeId);
        if (!GraphNodeType.TOPIC.name().equals(node.type()) || !GraphNodeStatus.ACTIVE.name().equals(node.status())) {
            throw CognitionException.invalidArgument("所选主题不存在或已失效");
        }
        return node;
    }

    /** Validates that actionId is an ACTIVE ACTION node of this user's graph. */
    public GraphNodeRow requireActionNode(long userId, String actionNodeId) {
        GraphNodeRow node = findNode(userId, actionNodeId);
        if (!GraphNodeType.ACTION.name().equals(node.type()) || !GraphNodeStatus.ACTIVE.name().equals(node.status())) {
            throw CognitionException.notFound();
        }
        return node;
    }

    private GraphNodeRow findNode(long userId, String nodeId) {
        GraphSnapshot snapshot = graphRepository.loadGraph(userId).orElseThrow(CognitionException::notFound);
        return snapshot.nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .findFirst().orElseThrow(CognitionException::notFound);
    }

    private CanonicalEvidence toCanonicalEvidence(EvidenceRow row) {
        EvidenceSourceType sourceType = switch (row.sourceType()) {
            case CLUE -> EvidenceSourceType.ARTICLE_HIGHLIGHT;
            case BODY_RECORD -> EvidenceSourceType.BODY_RECORD;
            case ACTION_FEEDBACK -> EvidenceSourceType.ACTION_FEEDBACK;
            default -> null;
        };
        if (sourceType == null) {
            return null;
        }
        String occurredAt = iso(row.occurredAt());
        GraphActionFeedbackResult feedbackResult = row.feedbackResult() == null
                ? null : GraphActionFeedbackResult.valueOf(row.feedbackResult().name());
        CanonicalEvidence evidence = new CanonicalEvidence();
        evidence.evidenceId = CognitionIds.of(CognitionIds.EVIDENCE, row.id());
        evidence.sourceType = sourceType;
        evidence.sourceId = row.sourceId();
        evidence.factLevel = EvidenceFactLevel.valueOf(row.factLevel().name());
        evidence.summary = row.summary();
        evidence.occurredAt = occurredAt;
        evidence.feedbackResult = feedbackResult;
        evidence.contentFingerprint = EvidenceFingerprints.fingerprint(
                sourceType, row.summary(), occurredAt, null, feedbackResult);
        return evidence;
    }

    private GraphNode toAgentNode(GraphNodeRow row) {
        GraphNode node = new GraphNode();
        node.id = row.nodeId();
        node.type = GraphNodeType.valueOf(row.type());
        node.status = GraphNodeStatus.valueOf(row.status());
        node.topicId = row.topicId();
        node.title = row.title();
        node.content = row.content();
        node.domain = row.domain();
        node.evidenceIds = readStringList(row.evidenceIdsJson());
        node.actionType = row.actionType() == null ? null : GraphActionType.valueOf(row.actionType());
        node.actionStatus = row.actionStatus() == null ? null : GraphActionStatus.valueOf(row.actionStatus());
        node.dueAt = iso(row.dueAt());
        node.feedbackOptions = readStringList(row.feedbackOptionsJson()).stream()
                .map(GraphActionFeedbackResult::valueOf).toList();
        node.createdAt = iso(row.createdAt());
        node.updatedAt = iso(row.updatedAt());
        node.version = row.version();
        return node;
    }

    private GraphEdge toAgentEdge(GraphEdgeRow row) {
        GraphEdge edge = new GraphEdge();
        edge.id = row.edgeId();
        edge.type = GraphEdgeType.valueOf(row.type());
        edge.fromNodeId = row.fromNodeId();
        edge.toNodeId = row.toNodeId();
        edge.evidenceIds = readStringList(row.evidenceIdsJson());
        edge.active = row.active();
        edge.createdAt = iso(row.createdAt());
        edge.updatedAt = iso(row.updatedAt());
        edge.version = row.version();
        return edge;
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

    private static String iso(Instant value) {
        return value == null ? null : value.toString();
    }
}
