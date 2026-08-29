package athena.cognition.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.cognition.biz.agenttask.AgentTaskService;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionGraphModels.AgentTaskView;
import athena.cognition.biz.domain.CognitionGraphModels.GraphActionFeedbackRequest;
import athena.cognition.biz.domain.CognitionGraphModels.GraphUpdateTaskCreateRequest;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDecisionRequest;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDecisionView;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalDetailView;
import athena.cognition.biz.domain.CognitionGraphModels.ProposalSummaryView;
import athena.cognition.biz.proposal.ProposalApplyService;
import athena.cognition.biz.rpc.agent.dto.PersonalCognitionGraph;
import athena.cognition.biz.service.CognitionGraphService;
import athena.cognition.biz.service.CognitionService.PagedResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTTP endpoints of the graph-update proposal pipeline
 * (cognition-agent-backend-handoff-v1.md). Fully parallel to
 * {@link CognitionController}: same /athena/cognition prefix, Result<T>
 * envelope and UserIdHolder login context; the legacy 14 endpoints are
 * untouched.
 */
@RestController
@RequestMapping("/athena/cognition")
public class CognitionGraphController {

    private final AgentTaskService agentTaskService;
    private final ProposalApplyService proposalApplyService;
    private final CognitionGraphService graphService;

    public CognitionGraphController(AgentTaskService agentTaskService,
                                    ProposalApplyService proposalApplyService,
                                    CognitionGraphService graphService) {
        this.agentTaskService = agentTaskService;
        this.proposalApplyService = proposalApplyService;
        this.graphService = graphService;
    }

    /** Manual "organize for me" entry (triggerType=USER_REQUEST). */
    @PostMapping("/graph-update-tasks")
    public Result<AgentTaskView> createGraphUpdateTask(@RequestBody GraphUpdateTaskCreateRequest request) {
        return Result.ok(agentTaskService.createUserRequestTask(userId(), request));
    }

    /** Agent task status query. */
    @GetMapping("/agent-tasks")
    public Result<List<AgentTaskView>> listAgentTasks(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(agentTaskService.listTasks(userId(), limit));
    }

    @GetMapping("/agent-tasks/{taskId}")
    public Result<AgentTaskView> getAgentTask(@PathVariable String taskId) {
        return Result.ok(agentTaskService.getTask(userId(), taskId));
    }

    /** Reverse lookup of a clue's graph-workflow task (idempotency key clue:{clueId}:cognition-graph-workflow-v1). */
    @GetMapping("/agent-tasks/by-clue/{clueId}")
    public Result<AgentTaskView> getAgentTaskByClue(@PathVariable String clueId) {
        return Result.ok(agentTaskService.getTaskByClue(userId(), clueId));
    }

    /** Proposal list / detail (detail carries operations and graphPreview for the confirmation page). */
    @GetMapping("/proposals")
    public Result<List<ProposalSummaryView>> listProposals(@RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        PagedResult<ProposalSummaryView> result = proposalApplyService.listProposals(userId(), status, page, pageSize);
        return Result.ok(result.items(), result.total());
    }

    @GetMapping("/proposals/{proposalId}")
    public Result<ProposalDetailView> getProposal(@PathVariable String proposalId) {
        return Result.ok(proposalApplyService.getProposal(userId(), proposalId));
    }

    /** User confirmation: ACCEPT applies the patch in one transaction; KEEP_AS_KNOWLEDGE / REJECT do not. */
    @PostMapping("/proposals/{proposalId}/decision")
    public Result<ProposalDecisionView> decideProposal(@PathVariable String proposalId,
                                                       @Valid @RequestBody ProposalDecisionRequest request) {
        return Result.ok(proposalApplyService.decide(userId(), proposalId, request.decision()));
    }

    /** Feedback on a graph ACTION node (parallel to the legacy /actions/{actionId}/feedback). */
    @PostMapping("/graph-actions/{actionId}/feedback")
    public Result<AgentTaskView> submitGraphActionFeedback(@PathVariable String actionId,
                                                           @Valid @RequestBody GraphActionFeedbackRequest request) {
        return Result.ok(agentTaskService.createFeedbackTask(userId(), actionId, request));
    }

    /** Current user graph (manual testing). */
    @GetMapping("/graph")
    public Result<PersonalCognitionGraph> getGraph() {
        return Result.ok(graphService.getGraphView(userId()));
    }

    private long userId() {
        Long userId = UserIdHolder.getUserId();
        if (userId == null || userId <= 0) throw CognitionException.unauthenticated();
        return userId;
    }
}
