package athena.cognition.biz.agenttask;

import athena.cognition.biz.agenttask.AgentTaskService.GraphTaskContext;
import athena.cognition.biz.domain.CognitionModels;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphRow;
import athena.cognition.biz.repository.CognitionGraphJdbcRepository.GraphSnapshot;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.repository.CognitionJdbcRepository.ClueRow;
import athena.cognition.biz.rpc.agent.CognitionAgentClient;
import athena.cognition.biz.rpc.agent.dto.AgentError;
import athena.cognition.biz.rpc.agent.dto.AgentErrorCode;
import athena.cognition.biz.rpc.agent.dto.ClueIntent;
import athena.cognition.biz.rpc.agent.dto.GraphPreparationStatus;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationRequest;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationResponse;
import athena.cognition.biz.rpc.agent.dto.GraphUpdateProposal;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationResponse;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationStatus;
import athena.cognition.biz.rpc.agent.dto.NextRoute;
import athena.cognition.biz.rpc.agent.dto.PersonalCognitionGraph;
import athena.cognition.biz.service.CognitionGraphService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTaskWorkerTest {

    private static final long USER_ID = 7L;
    private static final String TASK_ID = "task_1";
    private static final String CLUE_EXTERNAL_ID = "clue_101";
    private static final String IDEMPOTENCY_KEY = "clue:clue_101:cognition-graph-workflow-v1";

    @Mock
    private CognitionAgentJdbcRepository agentRepository;
    @Mock
    private CognitionJdbcRepository clueRepository;
    @Mock
    private CognitionGraphService graphService;
    @Mock
    private CognitionAgentClient agentClient;
    @Mock
    private AgentTaskResultStore resultStore;

    private AgentTaskWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AgentTaskWorker(agentRepository, clueRepository, graphService, agentClient,
                resultStore, new ObjectMapper().findAndRegisterModules());
        GraphSnapshot snapshot = new GraphSnapshot(
                new GraphRow(1, USER_ID, "graph_1", "personal-cognition-graph-v1", 0,
                        Instant.now(), Instant.now()), List.of(), List.of());
        lenient().when(agentRepository.findTaskByTaskId(TASK_ID)).thenReturn(Optional.of(task(0, 3)));
        lenient().when(graphService.getOrCreateGraph(USER_ID)).thenReturn(snapshot);
        lenient().when(graphService.listCanonicalEvidence(USER_ID)).thenReturn(List.of());
        lenient().when(graphService.toAgentGraph(any())).thenReturn(new PersonalCognitionGraph());
        lenient().when(clueRepository.findClue(USER_ID, 101)).thenReturn(Optional.of(clue()));
    }

    // ---------- node 1 routing: QUESTION finishes the task without the main workflow ----------

    @Test
    void questionIntentFinishesSucceededWithoutCallingMainWorkflow() {
        when(agentClient.classifyIntent(any())).thenReturn(intent(
                IntentClassificationStatus.SUCCEEDED, ClueIntent.QUESTION, NextRoute.QUESTION_INBOX));

        worker.executeGraphTask(TASK_ID, clueContext());

        verify(agentRepository).markTaskFinished(TASK_ID, "SUCCEEDED", null, null, null);
        verify(agentClient, never()).prepareGraphUpdate(any());
        verify(resultStore, never()).saveProposalOutcome(any(), any(), any(), any());
        verify(agentRepository).insertRun(any(), eq(TASK_ID), any(), eq("SUCCEEDED"),
                isNull(), anyLong(), any(), any(), any());
    }

    // ---------- node 1 RELATED continues; PROPOSAL_READY stores the proposal ----------

    @Test
    void relatedIntentRunsMainWorkflowAndStoresProposal() {
        IntentClassificationResponse intent = intent(
                IntentClassificationStatus.SUCCEEDED, ClueIntent.RELATED, NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE);
        intent.evidenceIds = List.of(CLUE_EXTERNAL_ID);
        when(agentClient.classifyIntent(any())).thenReturn(intent);
        GraphUpdateProposal proposal = new GraphUpdateProposal();
        proposal.proposalId = "prop_1";
        proposal.graphId = "graph_1";
        when(agentClient.prepareGraphUpdate(any()))
                .thenReturn(workflowResponse(GraphPreparationStatus.PROPOSAL_READY, proposal, null));
        when(resultStore.saveProposalOutcome(any(), any(), any(), any())).thenReturn("prop_1");

        worker.executeGraphTask(TASK_ID, clueContext());

        // section 2.1 mapping: summary is the selected text, sourceId is the clue id
        ArgumentCaptor<GraphUpdatePreparationRequest> request =
                ArgumentCaptor.forClass(GraphUpdatePreparationRequest.class);
        verify(agentClient).prepareGraphUpdate(request.capture());
        assertThat(request.getValue().idempotencyKey).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(request.getValue().triggerType.name()).isEqualTo("CLUE_CREATED");
        assertThat(request.getValue().candidates).hasSize(1);
        assertThat(request.getValue().candidates.get(0).summary).isEqualTo("用户实际选中的文章原文");
        assertThat(request.getValue().candidates.get(0).sourceId).isEqualTo(CLUE_EXTERNAL_ID);
        verify(resultStore).saveProposalOutcome(any(AgentTaskRow.class), any(), eq(proposal), any());
    }

    // ---------- NO_CHANGE finishes the task without a proposal ----------

    @Test
    void noChangeMarksTaskNoChange() {
        when(agentClient.classifyIntent(any())).thenReturn(intent(
                IntentClassificationStatus.SUCCEEDED, ClueIntent.RELATED, NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE));
        when(agentClient.prepareGraphUpdate(any()))
                .thenReturn(workflowResponse(GraphPreparationStatus.NO_CHANGE, null, null));

        worker.executeGraphTask(TASK_ID, clueContext());

        verify(agentRepository).markTaskFinished(TASK_ID, "NO_CHANGE", null, null, null);
        verify(resultStore, never()).saveProposalOutcome(any(), any(), any(), any());
    }

    // ---------- FAILED retryable: same idempotency key, new runId, up to maxRetry ----------

    @Test
    void retryableFailureRetriesWithNewRunIdThenSucceeds() {
        when(agentClient.classifyIntent(any())).thenReturn(intent(
                IntentClassificationStatus.SUCCEEDED, ClueIntent.RELATED, NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE));
        when(agentClient.prepareGraphUpdate(any())).thenReturn(
                workflowResponse(GraphPreparationStatus.FAILED, null, error(true)),
                workflowResponse(GraphPreparationStatus.PROPOSAL_READY, new GraphUpdateProposal(), null));
        when(resultStore.saveProposalOutcome(any(), any(), any(), any())).thenReturn("prop_1");

        worker.executeGraphTask(TASK_ID, clueContext());

        // one retry: first attempt without retry bump, second with retryCount + 1
        verify(agentRepository).markTaskRunning(eq(TASK_ID), any(), eq(false));
        verify(agentRepository).markTaskRunning(eq(TASK_ID), any(), eq(true));
        verify(agentClient, times(2)).prepareGraphUpdate(any());
        // node 1 runs only once; the retry reuses the assembled candidates
        verify(agentClient, times(1)).classifyIntent(any());
        verify(resultStore).saveProposalOutcome(any(), any(), any(), any());
    }

    @Test
    void retryableFailureExhaustingMaxRetryEndsDead() {
        when(agentRepository.findTaskByTaskId(TASK_ID)).thenReturn(Optional.of(task(0, 2)));
        when(agentClient.classifyIntent(any())).thenReturn(intent(
                IntentClassificationStatus.SUCCEEDED, ClueIntent.RELATED, NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE));
        when(agentClient.prepareGraphUpdate(any()))
                .thenReturn(workflowResponse(GraphPreparationStatus.FAILED, null, error(true)));

        worker.executeGraphTask(TASK_ID, clueContext());

        // maxRetry=2: initial attempt + 2 retries, then DEAD
        verify(agentClient, times(3)).prepareGraphUpdate(any());
        verify(agentRepository).markTaskFinished(TASK_ID, "DEAD", null,
                AgentErrorCode.MODEL_TIMEOUT.name(), true);
        verify(resultStore, never()).saveProposalOutcome(any(), any(), any(), any());
    }

    // ---------- node-level run records: observation.steps are split into cognition_agent_node_run ----------

    @Test
    void observationStepsAreRecordedAsNodeRuns() throws Exception {
        IntentClassificationResponse intent = intent(
                IntentClassificationStatus.SUCCEEDED, ClueIntent.QUESTION, NextRoute.QUESTION_INBOX);
        intent.observation = new ObjectMapper().readTree("""
                {"nodeVersion":"intent-classification-v1","steps":[
                  {"stepId":"MODEL_CALL","inputSummary":"分类输入","outputSummary":"QUESTION"},
                  {"stepId":"POLICY","inputSummary":"分类输出","outputSummary":"PASS"}]}
                """);
        when(agentClient.classifyIntent(any())).thenReturn(intent);

        worker.executeGraphTask(TASK_ID, clueContext());

        verify(agentRepository).insertNodeRun(any(), eq("MODEL_CALL"), eq("intent-classification-v1"), any());
        verify(agentRepository).insertNodeRun(any(), eq("POLICY"), eq("intent-classification-v1"), any());
    }

    @Test
    void duplicateStepIdKeepsFirstNodeRunRowAndContinues() throws Exception {
        IntentClassificationResponse intent = intent(
                IntentClassificationStatus.SUCCEEDED, ClueIntent.QUESTION, NextRoute.QUESTION_INBOX);
        intent.observation = new ObjectMapper().readTree("""
                {"steps":[
                  {"stepId":"MODEL_CALL","inputSummary":"第一次","outputSummary":"QUESTION"},
                  {"stepId":"MODEL_CALL","inputSummary":"重试循环第二次","outputSummary":"QUESTION"}]}
                """);
        when(agentClient.classifyIntent(any())).thenReturn(intent);
        // (run_id, node_id) 唯一约束：第二次插入撞唯一键，冲突跳过、保留首条
        doNothing().doThrow(new DuplicateKeyException("uk_cognition_agent_node_run"))
                .when(agentRepository).insertNodeRun(any(), eq("MODEL_CALL"), any(), any());

        worker.executeGraphTask(TASK_ID, clueContext());

        verify(agentRepository, times(2)).insertNodeRun(any(), eq("MODEL_CALL"), isNull(), any());
        // 冲突不影响主流程：任务照常终态
        verify(agentRepository).markTaskFinished(TASK_ID, "SUCCEEDED", null, null, null);
    }

    // ---------- fixtures ----------

    private GraphTaskContext clueContext() {
        return new GraphTaskContext("CLUE_CREATED", CLUE_EXTERNAL_ID, null, null, null);
    }

    private AgentTaskRow task(int retryCount, int maxRetry) {
        return new AgentTaskRow(1, TASK_ID, USER_ID, "cognition-graph-workflow-v1", IDEMPOTENCY_KEY,
                "CLUE_CREATED", "PENDING", retryCount, maxRetry, null, null, null, null,
                Instant.now(), Instant.now());
    }

    private ClueRow clue() {
        return new ClueRow(101, CognitionModels.ClueType.ARTICLE_HIGHLIGHT, CognitionModels.ClueIntent.RELATED,
                CognitionModels.RelationType.OBSERVE, CognitionModels.HelpRequestType.OBSERVE,
                "article_1", "文章标题", 1, "用户实际选中的文章原文", null, null, null,
                CognitionModels.CycleRelation.NO_RELATION, null, null, CognitionModels.ClueSource.KNOWLEDGE_ARTICLE,
                CognitionModels.ClueStatus.PENDING, null, "经期前情绪变化", "和我有关",
                Instant.now(), Instant.now());
    }

    private IntentClassificationResponse intent(IntentClassificationStatus status, ClueIntent intent,
                                                NextRoute nextRoute) {
        IntentClassificationResponse response = new IntentClassificationResponse();
        response.status = status;
        response.intent = intent;
        response.nextRoute = nextRoute;
        return response;
    }

    private GraphUpdatePreparationResponse workflowResponse(GraphPreparationStatus status,
                                                            GraphUpdateProposal proposal, AgentError error) {
        GraphUpdatePreparationResponse response = new GraphUpdatePreparationResponse();
        response.status = status;
        response.proposal = proposal;
        response.error = error;
        return response;
    }

    private AgentError error(boolean retryable) {
        AgentError error = new AgentError();
        error.code = AgentErrorCode.MODEL_TIMEOUT;
        error.retryable = retryable;
        return error;
    }
}
