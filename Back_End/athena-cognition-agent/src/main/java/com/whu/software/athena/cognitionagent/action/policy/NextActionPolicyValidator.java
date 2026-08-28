package com.whu.software.athena.cognitionagent.action.policy;

import com.whu.software.athena.cognitionagent.action.context.NextActionContext;
import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningDecision;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlan;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
import com.whu.software.athena.cognitionagent.graph.policy.GraphTextPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NextActionPolicyValidator {

    private static final Set<GraphActionType> ALLOWED_TYPES = Set.of(
            GraphActionType.RECORD_BODY,
            GraphActionType.RECORD_MOOD,
            GraphActionType.RECORD_SLEEP,
            GraphActionType.ANSWER_QUESTION,
            GraphActionType.CONFIRM_STATUS);
    private static final List<GraphActionFeedbackResult> REQUIRED_FEEDBACK = List.of(
            GraphActionFeedbackResult.OCCURRED,
            GraphActionFeedbackResult.NOT_OCCURRED,
            GraphActionFeedbackResult.UNCERTAIN,
            GraphActionFeedbackResult.SKIPPED);
    private final GraphTextPolicyValidator textPolicy = new GraphTextPolicyValidator();

    public PolicyValidationResult validate(NextActionContext context, NextActionPlan plan) {
        if (context.pendingActionCount() > 1) {
            return block("graph.nodes",
                    "a topic cannot have multiple active pending next actions");
        }
        if (plan == null || plan.decision == null) {
            return block("plan", "an action planning decision is required");
        }
        if (!ALLOWED_TYPES.contains(plan.actionType)) {
            return block("plan.actionType", "action type is outside the observation boundary");
        }
        PolicyValidationResult text = textPolicy.validate("plan.title", plan.title);
        if (!text.allowed()) return text;
        text = textPolicy.validate("plan.description", plan.description);
        if (!text.allowed()) return text;
        text = textPolicy.validate("plan.rationale", plan.rationale);
        if (!text.allowed()) return text;
        if (blank(plan.title) || plan.title.length() > 80) {
            return block("plan.title", "action title must be 1-80 characters");
        }
        if (blank(plan.description) || plan.description.length() > 500) {
            return block("plan.description", "action description must be 1-500 characters");
        }
        if (plan.feedbackOptions == null
                || !new HashSet<>(plan.feedbackOptions).equals(new HashSet<>(REQUIRED_FEEDBACK))
                || plan.feedbackOptions.size() != REQUIRED_FEEDBACK.size()) {
            return block("plan.feedbackOptions",
                    "action must support occurred, not occurred, uncertain, and skipped");
        }
        Set<String> selected = new HashSet<>(
                context.request().scope.selectedEvidenceIds);
        Set<String> available = new HashSet<>();
        context.selectedEvidence().forEach(item -> available.add(item.evidenceId));
        if (!available.equals(selected)) {
            return block("scope.selectedEvidenceIds",
                    "every selected evidence item must be present in the node context");
        }
        if (plan.evidenceIds == null || plan.evidenceIds.isEmpty()
                || !selected.containsAll(plan.evidenceIds)) {
            return block("plan.evidenceIds", "action must cite selected evidence only");
        }
        if (plan.decision == ActionPlanningDecision.CREATE_NEW
                && !blank(plan.existingActionNodeId)) {
            return block("plan.existingActionNodeId",
                    "a new action cannot reference an existing action node");
        }
        if (plan.decision == ActionPlanningDecision.KEEP_EXISTING
                && (context.existingPendingAction() == null
                || !context.existingPendingAction().id.equals(plan.existingActionNodeId))) {
            return block("plan.existingActionNodeId",
                    "the retained action must be the active pending action in the target branch");
        }
        return PolicyValidationResult.pass();
    }

    public List<GraphActionFeedbackResult> feedbackOptions() {
        return REQUIRED_FEEDBACK;
    }

    private PolicyValidationResult block(String field, String message) {
        return PolicyValidationResult.block(
                AgentErrorCode.POLICY_BLOCKED, field, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
