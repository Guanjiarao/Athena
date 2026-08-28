package athena.cognition.biz.agenttask;

import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.ProposalOperationWrite;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.ProposalWrite;
import athena.cognition.biz.rpc.agent.dto.GraphPatchOperation;
import athena.cognition.biz.rpc.agent.dto.GraphProposalStatus;
import athena.cognition.biz.rpc.agent.dto.GraphUpdateProposal;
import athena.cognition.biz.rpc.agent.dto.PersonalCognitionGraph;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Transactional persistence of a PROPOSAL_READY outcome: proposal row +
 * operation rows + task terminal state go in one transaction. Kept separate
 * from {@link AgentTaskWorker} so the transactional boundary does not span the
 * Agent HTTP call and self-invocation does not bypass the proxy.
 *
 * <p>Idempotent on the (userId, workflowVersion, idempotencyKey) unique
 * constraint: a retry after a timeout that re-produces a proposal falls back
 * to the already-stored proposal instead of failing (handoff section 13.9).
 */
@Service
public class AgentTaskResultStore {

    private final CognitionAgentJdbcRepository agentRepository;
    private final ObjectMapper objectMapper;

    public AgentTaskResultStore(CognitionAgentJdbcRepository agentRepository, ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Stores the proposal (status READY_FOR_CONFIRMATION), its operations and
     * marks the task SUCCEEDED with the proposal id.
     *
     * @return the persisted proposal id (the existing one when this idempotency
     * key already produced a proposal)
     */
    @Transactional
    public String saveProposalOutcome(AgentTaskRow task, String runId, GraphUpdateProposal proposal,
                                      PersonalCognitionGraph graphPreview) {
        try {
            agentRepository.insertProposal(new ProposalWrite(
                    proposal.proposalId, task.userId(), proposal.graphId, proposal.baseGraphVersion,
                    GraphProposalStatus.READY_FOR_CONFIRMATION.name(),
                    proposal.route == null ? null : proposal.route.name(),
                    proposal.targetTopicId, writeJson(proposal.evidenceIds), proposal.changeSummary,
                    writeJson(proposal.operations),
                    graphPreview == null ? null : writeJson(graphPreview),
                    proposal.requiresUserConfirmation, task.workflowVersion(), runId,
                    task.idempotencyKey()));
            agentRepository.insertProposalOperations(proposal.proposalId, toOperationWrites(proposal.operations));
            agentRepository.markTaskFinished(task.taskId(), "SUCCEEDED", proposal.proposalId, null, null);
            return proposal.proposalId;
        } catch (DuplicateKeyException duplicate) {
            // same logical action re-executed after a timeout: reuse the stored proposal
            String existingProposalId = agentRepository
                    .findProposalByIdempotencyKey(task.userId(), task.workflowVersion(), task.idempotencyKey())
                    .orElseThrow(() -> duplicate)
                    .proposalId();
            agentRepository.markTaskFinished(task.taskId(), "SUCCEEDED", existingProposalId, null, null);
            return existingProposalId;
        }
    }

    private List<ProposalOperationWrite> toOperationWrites(List<GraphPatchOperation> operations) {
        List<ProposalOperationWrite> writes = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            GraphPatchOperation op = operations.get(i);
            writes.add(new ProposalOperationWrite(i,
                    op.operationType == null ? null : op.operationType.name(),
                    op.targetId,
                    op.node == null ? null : writeJson(op.node),
                    op.edge == null ? null : writeJson(op.edge),
                    op.supersededByNodeId,
                    writeJson(op.evidenceIds),
                    op.reason));
        }
        return writes;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize json", ex);
        }
    }
}
