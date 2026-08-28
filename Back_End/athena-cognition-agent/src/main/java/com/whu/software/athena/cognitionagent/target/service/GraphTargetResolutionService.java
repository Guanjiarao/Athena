package com.whu.software.athena.cognitionagent.target.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.policy.GraphTextPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.DecisionSource;
import com.whu.software.athena.cognitionagent.intent.contract.ModelCallStatus;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.target.context.GraphTargetContext;
import com.whu.software.athena.cognitionagent.target.context.GraphTargetContextBuilder;
import com.whu.software.athena.cognitionagent.target.context.TopicCandidateContext;
import com.whu.software.athena.cognitionagent.target.contract.GraphMatchStrength;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionResponse;
import com.whu.software.athena.cognitionagent.target.contract.TargetResolutionStatus;
import com.whu.software.athena.cognitionagent.target.provider.GraphTargetModelProvider;
import com.whu.software.athena.cognitionagent.target.provider.TargetModelSuggestion;
import com.whu.software.athena.cognitionagent.target.validation.GraphTargetRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GraphTargetResolutionService {

    private final GraphTargetRequestValidator validator;
    private final GraphTargetContextBuilder contextBuilder;
    private final GraphTargetModelProvider modelProvider;
    private final GraphTextPolicyValidator textPolicy;
    private final WorkflowTelemetryRecorder telemetry;

    @Autowired
    public GraphTargetResolutionService(GraphTargetModelProvider modelProvider,
                                        WorkflowTelemetryRecorder telemetry) {
        this(new GraphTargetRequestValidator(), new GraphTargetContextBuilder(),
                modelProvider, new GraphTextPolicyValidator(), telemetry);
    }

    GraphTargetResolutionService(GraphTargetRequestValidator validator,
                                 GraphTargetContextBuilder contextBuilder,
                                 GraphTargetModelProvider modelProvider,
                                 GraphTextPolicyValidator textPolicy,
                                 WorkflowTelemetryRecorder telemetry) {
        this.validator = validator;
        this.contextBuilder = contextBuilder;
        this.modelProvider = modelProvider;
        this.textPolicy = textPolicy;
        this.telemetry = telemetry;
    }

    public GraphTargetResolutionResponse resolve(GraphTargetResolutionRequest request) {
        long startedAt = System.nanoTime();
        GraphTargetResolutionResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = TargetResolutionStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(
                    issue.code(), issue.message(), false, issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        response.schemaResult = SchemaResult.PASS;
        observation.steps.add(new WorkflowNodeStep(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));
        GraphTargetContext context = contextBuilder.build(request);

        if (allEvidenceAlreadyLinked(context)) {
            route(response, GraphUpdateRoute.NO_CHANGE, null,
                    context.suggestedTopicTitle(), GraphMatchStrength.NONE,
                    DecisionSource.RULE, "All accepted evidence already exists in the graph.");
            response.status = TargetResolutionStatus.NO_CHANGE;
            response.policyResult = PolicyResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "DETERMINISTIC_ROUTING", "acceptedEvidence", "NO_CHANGE"));
            return finish(response, observation, startedAt);
        }

        TopicCandidateContext selected =
                findById(context.activeTopics(), context.userSelectedTopicId());
        if (!blank(context.userSelectedTopicId())) {
            if (selected == null) {
                needsConfirmation(response,
                        "The user-selected topic is no longer active.");
            } else {
                route(response, GraphUpdateRoute.UPDATE_EXISTING, selected.topicId(),
                        selected.title(), GraphMatchStrength.EXACT,
                        DecisionSource.USER_DECLARED,
                        "The user explicitly selected the target topic.");
                response.status = TargetResolutionStatus.SUCCEEDED;
            }
            response.policyResult = PolicyResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "USER_TARGET", "userSelectedTopicIdPresent=true",
                    "route=" + response.route));
            return finish(response, observation, startedAt);
        }

        List<TopicCandidateContext> exact = context.activeTopics().stream()
                .filter(topic -> normalized(topic.title())
                        .equals(normalized(context.suggestedTopicTitle())))
                .toList();
        if (!blank(context.suggestedTopicTitle()) && exact.size() == 1) {
            TopicCandidateContext topic = exact.get(0);
            route(response, GraphUpdateRoute.UPDATE_EXISTING, topic.topicId(), topic.title(),
                    GraphMatchStrength.EXACT, DecisionSource.RULE,
                    "The normalized suggested title exactly matches one active topic.");
            response.status = TargetResolutionStatus.SUCCEEDED;
            response.policyResult = PolicyResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "EXACT_MATCH", "activeTopicCount=" + context.activeTopics().size(),
                    "targetTopicId=" + topic.topicId()));
            return finish(response, observation, startedAt);
        }

        if (context.activeTopics().isEmpty()) {
            if (blank(context.suggestedTopicTitle())) {
                needsConfirmation(response,
                        "No active topic exists and no meaningful branch title was supplied.");
            } else {
                route(response, GraphUpdateRoute.CREATE_BRANCH, null,
                        context.suggestedTopicTitle().trim(), GraphMatchStrength.NONE,
                        DecisionSource.RULE,
                        "The graph has no active topic, so this is the first branch candidate.");
                response.status = TargetResolutionStatus.SUCCEEDED;
            }
            response.policyResult = PolicyResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "EMPTY_GRAPH_ROUTING", "activeTopicCount=0", "route=" + response.route));
            return finish(response, observation, startedAt);
        }

        observation.steps.add(new WorkflowNodeStep(
                "MODEL_CONTEXT", "fullGraphNodeCount=" + context.graph().nodes.size(),
                "candidateTopicCount=" + context.activeTopics().size()
                        + ",evidenceCount=" + context.evidence().size()));
        applyModel(context, response, observation);
        return finish(response, observation, startedAt);
    }

    private void applyModel(GraphTargetContext context,
                            GraphTargetResolutionResponse response,
                            WorkflowRunObservation observation) {
        try {
            TargetModelSuggestion suggestion =
                    modelProvider.resolve(contextBuilder.buildModelContext(context));
            observation.schemaResult = SchemaResult.PASS;
            response.schemaResult = SchemaResult.PASS;
            copyUsage(suggestion, observation);

            PolicyValidationResult textResult =
                    textPolicy.validate("rationale", suggestion.rationale());
            if (!textResult.allowed()) {
                observation.modelPolicyResult = PolicyResult.BLOCK;
                observation.modelCallStatus = ModelCallStatus.REJECTED;
                observation.modelErrorCode = AgentErrorCode.POLICY_BLOCKED.name();
                needsConfirmation(response, "The model suggestion crossed the policy boundary.");
                response.policyResult = PolicyResult.PASS;
                return;
            }
            observation.modelPolicyResult = PolicyResult.PASS;

            if (suggestion.route() == GraphUpdateRoute.UPDATE_EXISTING) {
                TopicCandidateContext matched =
                        findById(context.activeTopics(), suggestion.matchedTopicId());
                if (matched == null) {
                    observation.modelCallStatus = ModelCallStatus.REJECTED;
                    observation.modelErrorCode = AgentErrorCode.MODEL_OUTPUT_INVALID.name();
                    needsConfirmation(response,
                            "The model selected a topic outside the supplied candidate list.");
                } else {
                    route(response, GraphUpdateRoute.UPDATE_EXISTING, matched.topicId(),
                            matched.title(), GraphMatchStrength.POSSIBLE,
                            DecisionSource.MODEL_ASSISTED, suggestion.rationale());
                    response.status = TargetResolutionStatus.SUCCEEDED;
                    observation.modelCallStatus = ModelCallStatus.SUCCEEDED;
                }
            } else if (suggestion.route() == GraphUpdateRoute.CREATE_BRANCH
                    && !blank(suggestion.suggestedTopicTitle())) {
                PolicyValidationResult titleResult =
                        textPolicy.validate("suggestedTopicTitle", suggestion.suggestedTopicTitle());
                if (!titleResult.allowed()) {
                    observation.modelCallStatus = ModelCallStatus.REJECTED;
                    observation.modelPolicyResult = PolicyResult.BLOCK;
                    observation.modelErrorCode = AgentErrorCode.POLICY_BLOCKED.name();
                    needsConfirmation(response, "The suggested title crossed policy.");
                } else {
                    route(response, GraphUpdateRoute.CREATE_BRANCH, null,
                            suggestion.suggestedTopicTitle(), GraphMatchStrength.POSSIBLE,
                            DecisionSource.MODEL_ASSISTED, suggestion.rationale());
                    response.status = TargetResolutionStatus.SUCCEEDED;
                    observation.modelCallStatus = ModelCallStatus.SUCCEEDED;
                }
            } else {
                needsConfirmation(response, suggestion.rationale());
                observation.modelCallStatus = ModelCallStatus.SUCCEEDED;
            }
            response.policyResult = PolicyResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "MODEL_ROUTING", "candidateTopicCount=" + context.activeTopics().size(),
                    "route=" + response.route + ",targetTopicId=" + response.targetTopicId));
        } catch (IntentModelProviderException exception) {
            observation.modelCallStatus = exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                    ? ModelCallStatus.REJECTED : ModelCallStatus.FAILED;
            observation.modelErrorCode = exception.errorCode().name();
            observation.schemaResult = exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                    ? SchemaResult.FAIL : SchemaResult.NOT_RUN;
            response.schemaResult = observation.schemaResult;
            response.policyResult = PolicyResult.PASS;
            needsConfirmation(response,
                    "The model was unavailable or returned an invalid suggestion.");
        } catch (RuntimeException exception) {
            observation.modelCallStatus = ModelCallStatus.FAILED;
            observation.modelErrorCode = AgentErrorCode.INTERNAL_ERROR.name();
            response.policyResult = PolicyResult.PASS;
            needsConfirmation(response, "Target resolution could not be completed safely.");
        }
    }

    private boolean allEvidenceAlreadyLinked(GraphTargetContext context) {
        Set<String> linked = new HashSet<>();
        for (GraphNode node : context.graph().nodes) {
            if (node.evidenceIds != null) linked.addAll(node.evidenceIds);
        }
        return context.evidence().stream()
                .map(item -> item.evidenceId).allMatch(linked::contains);
    }

    private TopicCandidateContext findById(List<TopicCandidateContext> topics, String id) {
        if (blank(id)) return null;
        return topics.stream().filter(topic -> id.equals(topic.topicId())).findFirst().orElse(null);
    }

    private void needsConfirmation(GraphTargetResolutionResponse response, String rationale) {
        route(response, GraphUpdateRoute.NEEDS_CONFIRMATION, null,
                response.suggestedTopicTitle, GraphMatchStrength.NONE,
                DecisionSource.RULE, rationale);
        response.status = TargetResolutionStatus.NEEDS_CONFIRMATION;
    }

    private void route(GraphTargetResolutionResponse response,
                       GraphUpdateRoute route,
                       String topicId,
                       String title,
                       GraphMatchStrength strength,
                       DecisionSource source,
                       String rationale) {
        response.route = route;
        response.targetTopicId = topicId;
        response.suggestedTopicTitle = title;
        response.matchStrength = strength;
        response.decisionSource = source;
        response.rationale = rationale;
    }

    private GraphTargetResolutionResponse baseResponse(GraphTargetResolutionRequest request) {
        GraphTargetResolutionResponse response = new GraphTargetResolutionResponse();
        response.runId = request == null ? null : request.runId;
        response.suggestedTopicTitle = request == null ? null : request.suggestedTopicTitle;
        return response;
    }

    private WorkflowRunObservation observation(GraphTargetResolutionRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.WORKFLOW_VERSION;
        value.nodeId = GraphContract.TARGET_NODE_ID;
        value.nodeVersion = GraphContract.TARGET_NODE_VERSION;
        value.promptVersion = GraphContract.TARGET_PROMPT_VERSION;
        value.modelProvider = modelProvider.providerName();
        value.modelName = modelProvider.modelName();
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        return value;
    }

    private GraphTargetResolutionResponse finish(GraphTargetResolutionResponse response,
                                                 WorkflowRunObservation observation,
                                                 long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status + ",route=" + response.route));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(), observation);
        return response;
    }

    private void copyUsage(TargetModelSuggestion suggestion,
                           WorkflowRunObservation observation) {
        observation.inputTokens = suggestion.inputTokens();
        observation.outputTokens = suggestion.outputTokens();
        observation.totalTokens = suggestion.totalTokens();
        observation.estimatedCost = suggestion.estimatedCost();
    }

    private String inputSummary(GraphTargetResolutionRequest request) {
        int nodes = request == null || request.graph == null || request.graph.nodes == null
                ? 0 : request.graph.nodes.size();
        int evidence = request == null || request.evidence == null ? 0 : request.evidence.size();
        return "graphNodeCount=" + nodes + ",evidenceCount=" + evidence
                + ",userTargetPresent="
                + (request != null && !blank(request.userSelectedTopicId));
    }

    private String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
