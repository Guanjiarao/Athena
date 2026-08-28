package athena.cognition.biz.proposal;

import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDecision;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDecisionView;
import athena.cognition.biz.outbox.OutboxService;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.ProposalOperationRow;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.ProposalRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphOperationInput;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphSnapshot;
import athena.cognition.biz.rpc.agent.dto.GraphEdge;
import athena.cognition.biz.rpc.agent.dto.GraphEdgeType;
import athena.cognition.biz.rpc.agent.dto.GraphNode;
import athena.cognition.biz.rpc.agent.dto.GraphNodeType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalApplyServiceTest {

    private static final long USER_ID = 7L;
    private static final String PROPOSAL_ID = "prop_1";
    private static final String GRAPH_ID = "graph_1";

    @Mock
    private CognitionAgentJdbcRepository agentRepository;
    @Mock
    private CognitionGraphJdbcRepository graphRepository;
    @Mock
    private OutboxService outboxService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ProposalApplyService service;

    @BeforeEach
    void setUp() {
        service = new ProposalApplyService(agentRepository, graphRepository, outboxService, objectMapper);
    }

    // ---------- happy path: ACCEPT applies the whole patch in one go ----------

    @Test
    void acceptAppliesPatchAndWritesHistoryDecisionAndOutbox() throws Exception {
        ProposalRow proposal = proposal("READY_FOR_CONFIRMATION", 0, null);
        GraphRow graph = graph(0);
        when(agentRepository.findProposal(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(graphRepository.findGraphForUpdate(USER_ID)).thenReturn(Optional.of(graph));
        when(graphRepository.loadGraph(USER_ID)).thenReturn(Optional.of(new GraphSnapshot(graph, List.of(), List.of())));
        when(agentRepository.listProposalOperations(PROPOSAL_ID)).thenReturn(List.of(
                addNodeOp(0, topicNode("topic_1")),
                addNodeOp(1, factNode("fact_1", "topic_1")),
                addEdgeOp(2, edge("edge_1", "fact_1", "topic_1"))));
        when(graphRepository.applyGraphUpdate(eq(graph), eq(USER_ID), eq(PROPOSAL_ID),
                eq(CognitionGraphJdbcRepository.OPERATOR_USER), anyList(), any()))
                .thenReturn(1L);

        ProposalDecisionView view = service.decide(USER_ID, PROPOSAL_ID, ProposalDecision.ACCEPT);

        assertThat(view.status()).isEqualTo("ACCEPTED");
        assertThat(view.userDecision()).isEqualTo("ACCEPT");
        assertThat(view.appliedGraphVersion()).isEqualTo(1L);
        ArgumentCaptor<List<GraphOperationInput>> inputs = ArgumentCaptor.forClass(List.class);
        verify(graphRepository).applyGraphUpdate(eq(graph), eq(USER_ID), eq(PROPOSAL_ID),
                eq(CognitionGraphJdbcRepository.OPERATOR_USER), inputs.capture(), any());
        assertThat(inputs.getValue()).hasSize(3);
        verify(agentRepository).recordProposalDecision(PROPOSAL_ID, "ACCEPTED", "ACCEPT");
        verify(outboxService).saveEvent(eq(USER_ID), eq(OutboxService.GRAPH_UPDATED), argThat(payload ->
                payload.toString().contains(GRAPH_ID) && payload.toString().contains(PROPOSAL_ID)));
    }

    // ---------- section 9 step 6: stale base version ----------

    @Test
    void staleBaseVersionMarksProposalStaleAndThrowsVersionConflict() {
        ProposalRow proposal = proposal("READY_FOR_CONFIRMATION", 0, null);
        when(agentRepository.findProposal(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(graphRepository.findGraphForUpdate(USER_ID)).thenReturn(Optional.of(graph(3)));

        CognitionException ex = assertThrows(CognitionException.class,
                () -> service.decide(USER_ID, PROPOSAL_ID, ProposalDecision.ACCEPT));

        assertThat(ex.errorCode()).isEqualTo(CognitionException.VERSION_CONFLICT);
        verify(agentRepository).updateProposalStatus(PROPOSAL_ID, "STALE");
        verify(graphRepository, never()).applyGraphUpdate(any(), anyLong(), any(), any(), anyList(), any());
        verify(outboxService, never()).saveEvent(anyLong(), any(), any());
    }

    // ---------- section 9 step 7: repeated decision is a conflict ----------

    @Test
    void repeatedDecisionReturnsStateConflictWithoutSideEffects() {
        ProposalRow proposal = proposal("ACCEPTED", 0, "ACCEPT");
        when(agentRepository.findProposal(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(graphRepository.findGraphForUpdate(USER_ID)).thenReturn(Optional.of(graph(1)));

        CognitionException ex = assertThrows(CognitionException.class,
                () -> service.decide(USER_ID, PROPOSAL_ID, ProposalDecision.ACCEPT));

        assertThat(ex.errorCode()).isEqualTo(CognitionException.STATE_CONFLICT);
        verify(graphRepository, never()).applyGraphUpdate(any(), anyLong(), any(), any(), anyList(), any());
        verify(agentRepository, never()).recordProposalDecision(any(), any(), any());
    }

    // ---------- KEEP_AS_KNOWLEDGE / REJECT never apply the patch ----------

    @Test
    void keepAsKnowledgeOnlyChangesProposalState() {
        ProposalRow proposal = proposal("READY_FOR_CONFIRMATION", 0, null);
        when(agentRepository.findProposal(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(graphRepository.findGraphForUpdate(USER_ID)).thenReturn(Optional.of(graph(0)));

        ProposalDecisionView view = service.decide(USER_ID, PROPOSAL_ID, ProposalDecision.KEEP_AS_KNOWLEDGE);

        assertThat(view.status()).isEqualTo("KEPT_AS_KNOWLEDGE");
        assertThat(view.appliedGraphVersion()).isNull();
        verify(agentRepository).recordProposalDecision(PROPOSAL_ID, "KEPT_AS_KNOWLEDGE", "KEEP_AS_KNOWLEDGE");
        verify(graphRepository, never()).applyGraphUpdate(any(), anyLong(), any(), any(), anyList(), any());
        verify(outboxService, never()).saveEvent(anyLong(), any(), any());
    }

    @Test
    void rejectOnlyChangesProposalState() {
        ProposalRow proposal = proposal("READY_FOR_CONFIRMATION", 0, null);
        when(agentRepository.findProposal(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(graphRepository.findGraphForUpdate(USER_ID)).thenReturn(Optional.of(graph(0)));

        ProposalDecisionView view = service.decide(USER_ID, PROPOSAL_ID, ProposalDecision.REJECT);

        assertThat(view.status()).isEqualTo("REJECTED");
        verify(agentRepository).recordProposalDecision(PROPOSAL_ID, "REJECTED", "REJECT");
        verify(graphRepository, never()).applyGraphUpdate(any(), anyLong(), any(), any(), anyList(), any());
    }

    // ---------- whitelist: illegal operation aborts before any write ----------

    @Test
    void supersedeOfMissingNodeAbortsTheWholeApply() {
        ProposalRow proposal = proposal("READY_FOR_CONFIRMATION", 0, null);
        GraphRow graph = graph(0);
        when(agentRepository.findProposal(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(graphRepository.findGraphForUpdate(USER_ID)).thenReturn(Optional.of(graph));
        when(graphRepository.loadGraph(USER_ID)).thenReturn(Optional.of(new GraphSnapshot(graph, List.of(), List.of())));
        when(agentRepository.listProposalOperations(PROPOSAL_ID)).thenReturn(List.of(
                new ProposalOperationRow(1, PROPOSAL_ID, 0, "SUPERSEDE_NODE", "ghost_node", null, null,
                        "fact_new", null, "replace", Instant.now())));

        CognitionException ex = assertThrows(CognitionException.class,
                () -> service.decide(USER_ID, PROPOSAL_ID, ProposalDecision.ACCEPT));

        assertThat(ex.errorCode()).isEqualTo(CognitionException.INVALID_ARGUMENT);
        verify(graphRepository, never()).applyGraphUpdate(any(), anyLong(), any(), any(), anyList(), any());
        verify(agentRepository, never()).recordProposalDecision(any(), any(), any());
        verify(outboxService, never()).saveEvent(anyLong(), any(), any());
    }

    // ---------- fixtures ----------

    private ProposalRow proposal(String status, long baseGraphVersion, String userDecision) {
        return new ProposalRow(1, PROPOSAL_ID, USER_ID, GRAPH_ID, baseGraphVersion, status,
                "CREATE_BRANCH", "topic_1", "[\"clue_101\"]", "summary", "[]", null,
                true, "cognition-graph-workflow-v1", "run_1",
                "clue:clue_101:cognition-graph-workflow-v1", userDecision, null, Instant.now());
    }

    private GraphRow graph(long graphVersion) {
        return new GraphRow(1, USER_ID, GRAPH_ID, "personal-cognition-graph-v1", graphVersion,
                Instant.now(), Instant.now());
    }

    private GraphNode topicNode(String id) {
        GraphNode node = new GraphNode();
        node.id = id;
        node.type = GraphNodeType.TOPIC;
        node.title = "经期前情绪变化";
        node.version = 1;
        return node;
    }

    private GraphNode factNode(String id, String topicId) {
        GraphNode node = new GraphNode();
        node.id = id;
        node.type = GraphNodeType.SELF_REPORTED_FACT;
        node.topicId = topicId;
        node.content = "用户反馈相关变化出现过";
        node.version = 1;
        return node;
    }

    private GraphEdge edge(String id, String from, String to) {
        GraphEdge edge = new GraphEdge();
        edge.id = id;
        edge.type = GraphEdgeType.GROUNDS;
        edge.fromNodeId = from;
        edge.toNodeId = to;
        return edge;
    }

    private ProposalOperationRow addNodeOp(int index, GraphNode node) throws Exception {
        return new ProposalOperationRow(index + 1, PROPOSAL_ID, index, "ADD_NODE", node.id,
                objectMapper.writeValueAsString(node), null, null, "[\"clue_101\"]", "add", Instant.now());
    }

    private ProposalOperationRow addEdgeOp(int index, GraphEdge edge) throws Exception {
        return new ProposalOperationRow(index + 1, PROPOSAL_ID, index, "ADD_EDGE", edge.id,
                null, objectMapper.writeValueAsString(edge), null, "[]", "link", Instant.now());
    }
}
