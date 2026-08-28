package athena.cognition.biz.agenttask;

import athena.cognition.biz.agenttask.AgentTaskService.FeedbackTaskContext;
import athena.cognition.biz.agenttask.AgentTaskService.GraphTaskContext;
import athena.cognition.biz.rpc.agent.dto.GraphActionFeedbackResult;

import java.time.Instant;
import java.util.List;

/**
 * Persisted execution context of an agent task (cognition_agent_task.payload_json).
 * The MQ message carries only taskId + triggerType, so the consumer and the
 * crash-recovery sweeper rebuild the worker context from this snapshot —
 * without it a lost message would be unrecoverable for feedback/user-request
 * tasks whose context exists nowhere else.
 *
 * <p>occurredAt is carried as an ISO-8601 string to stay mapper-agnostic.
 */
public record AgentTaskPayload(String clueId, List<String> clueIds,
                               String suggestedTopicTitle, String userSelectedTopicId,
                               String feedbackId, String actionId, String result,
                               String note, String occurredAt) {

    public static AgentTaskPayload forGraph(String clueId, List<String> clueIds,
                                            String suggestedTopicTitle, String userSelectedTopicId) {
        return new AgentTaskPayload(clueId, clueIds, suggestedTopicTitle, userSelectedTopicId,
                null, null, null, null, null);
    }

    public static AgentTaskPayload forFeedback(String feedbackId, String actionId,
                                               GraphActionFeedbackResult result, String note, Instant occurredAt) {
        return new AgentTaskPayload(null, null, null, null, feedbackId, actionId,
                result == null ? null : result.name(), note,
                occurredAt == null ? null : occurredAt.toString());
    }

    public GraphTaskContext toGraphContext(String triggerType) {
        return new GraphTaskContext(triggerType, clueId, clueIds, suggestedTopicTitle, userSelectedTopicId);
    }

    public FeedbackTaskContext toFeedbackContext() {
        return new FeedbackTaskContext(feedbackId, actionId,
                result == null ? null : GraphActionFeedbackResult.valueOf(result), note,
                occurredAt == null ? null : Instant.parse(occurredAt));
    }
}
