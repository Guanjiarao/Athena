package athena.cognition.biz.domain;

import athena.cognition.biz.domain.CognitionModels.ActionFeedbackResult;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Request/response models of the graph-update proposal pipeline
 * (cognition-agent-backend-handoff-v1.md). Parallel to {@link CognitionModels}
 * which serves the clue/digest/topic flow; the two pipelines share nothing.
 */
public final class CognitionGraphModels {

    private CognitionGraphModels() {
    }

    /** User decision on a proposal (handoff section 9 decision table). */
    public enum ProposalDecision { ACCEPT, KEEP_AS_KNOWLEDGE, REJECT }

    // ---------- requests ----------

    /** POST /graph-update-tasks: manual "organize for me" entry (triggerType fixed USER_REQUEST). */
    public record GraphUpdateTaskCreateRequest(String triggerType, List<String> clueIds,
                                               String suggestedTopicTitle, String userSelectedTopicId) {
    }

    /** POST /proposals/{proposalId}/decision */
    public record ProposalDecisionRequest(@NotNull ProposalDecision decision) {
    }

    /** POST /graph-actions/{actionId}/feedback; result mirrors the Agent contract's four options. */
    public record GraphActionFeedbackRequest(@NotNull ActionFeedbackResult result, String note, Instant occurredAt) {
    }

    // ---------- views ----------

    /**
     * @param clueIds             clue external ids from the task payload (CLUE_CREATED: one element,
     *                            USER_REQUEST: many, ACTION_FEEDBACK: empty)
     * @param suggestedTopicTitle suggested branch title from the task payload, nullable
     * @param candidates          topic candidates for the user to pick; only set when the task
     *                            ended NEEDS_CONFIRMATION, null otherwise (frontend then lets the
     *                            user pick a topic freely)
     */
    public record AgentTaskView(String taskId, String workflowVersion, String idempotencyKey, String triggerType,
                                String status, int retryCount, int maxRetry, String proposalId,
                                String errorCode, Boolean errorRetryable, Instant createdAt, Instant updatedAt,
                                List<String> clueIds, String suggestedTopicTitle,
                                List<CandidateTopic> candidates) {
    }

    /** A topic the user may confirm as the target of a NEEDS_CONFIRMATION task. */
    public record CandidateTopic(String topicId, String title) {
    }

    public record ProposalSummaryView(String proposalId, String status, String route, String targetTopicId,
                                      long baseGraphVersion, String changeSummary, boolean requiresUserConfirmation,
                                      String userDecision, Instant decidedAt, Instant createdAt) {
    }

    public record ProposalOperationView(int operationIndex, String operationType, String targetId,
                                        JsonNode node, JsonNode edge, String supersededByNodeId,
                                        List<String> evidenceIds, String reason) {
    }

    public record ProposalDetailView(String proposalId, String status, String route, String targetTopicId,
                                     long baseGraphVersion, String changeSummary, List<String> evidenceIds,
                                     List<ProposalOperationView> operations, JsonNode graphPreview,
                                     boolean requiresUserConfirmation, String workflowVersion, String runId,
                                     String userDecision, Instant decidedAt, Instant createdAt) {
    }

    public record ProposalDecisionView(String proposalId, String status, String userDecision, Long appliedGraphVersion) {
    }
}
