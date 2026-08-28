package com.whu.software.athena.cognitionagent.intent.service;

import com.whu.software.athena.cognitionagent.intent.context.IntentContext;
import com.whu.software.athena.cognitionagent.intent.context.IntentContextBuilder;
import com.whu.software.athena.cognitionagent.intent.context.IntentModelContextBuilder;
import com.whu.software.athena.cognitionagent.intent.contract.AgentContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationStatus;
import com.whu.software.athena.cognitionagent.intent.contract.IntentModelSuggestionView;
import com.whu.software.athena.cognitionagent.intent.contract.IntentNodeObservation;
import com.whu.software.athena.cognitionagent.intent.contract.IntentRunObservation;
import com.whu.software.athena.cognitionagent.intent.contract.ModelCallStatus;
import com.whu.software.athena.cognitionagent.intent.contract.NextRoute;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.observability.IntentTelemetryRecorder;
import com.whu.software.athena.cognitionagent.intent.policy.IntentModelPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.policy.IntentPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProvider;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelSuggestion;
import com.whu.software.athena.cognitionagent.intent.provider.MockIntentModelProvider;
import com.whu.software.athena.cognitionagent.intent.schema.IntentModelOutputSchemaValidator;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;
import com.whu.software.athena.cognitionagent.intent.validation.IntentRequestValidator;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

/** Synchronous first-node workflow with optional, non-authoritative model assistance. */
@Service
public class IntentClassificationService {

    private final IntentRequestValidator requestValidator;
    private final IntentContextBuilder contextBuilder;
    private final IntentModelContextBuilder modelContextBuilder;
    private final RuleFirstIntentClassifier classifier;
    private final IntentPolicyValidator policyValidator;
    private final IntentModelOutputSchemaValidator modelSchemaValidator;
    private final IntentModelPolicyValidator modelPolicyValidator;
    private final IntentModelProvider modelProvider;
    private final IntentTelemetryRecorder telemetryRecorder;

    public IntentClassificationService() {
        this(new MockIntentModelProvider(),
                new IntentTelemetryRecorder(new SimpleMeterRegistry()));
    }

    @Autowired
    public IntentClassificationService(IntentModelProvider modelProvider,
                                       IntentTelemetryRecorder telemetryRecorder) {
        this(new IntentRequestValidator(),
                new IntentContextBuilder(),
                new IntentModelContextBuilder(),
                new RuleFirstIntentClassifier(),
                new IntentPolicyValidator(),
                new IntentModelOutputSchemaValidator(),
                new IntentModelPolicyValidator(),
                modelProvider,
                telemetryRecorder);
    }

    public IntentClassificationService(IntentRequestValidator requestValidator,
                                       IntentContextBuilder contextBuilder,
                                       IntentModelContextBuilder modelContextBuilder,
                                       RuleFirstIntentClassifier classifier,
                                       IntentPolicyValidator policyValidator,
                                       IntentModelOutputSchemaValidator modelSchemaValidator,
                                       IntentModelPolicyValidator modelPolicyValidator,
                                       IntentModelProvider modelProvider,
                                       IntentTelemetryRecorder telemetryRecorder) {
        this.requestValidator = requestValidator;
        this.contextBuilder = contextBuilder;
        this.modelContextBuilder = modelContextBuilder;
        this.classifier = classifier;
        this.policyValidator = policyValidator;
        this.modelSchemaValidator = modelSchemaValidator;
        this.modelPolicyValidator = modelPolicyValidator;
        this.modelProvider = modelProvider;
        this.telemetryRecorder = telemetryRecorder;
    }

    public IntentClassificationResponse classify(IntentClassificationRequest request) {
        long startedAt = System.nanoTime();
        IntentRunObservation observation = startObservation(request);

        ValidationResult validation = requestValidator.validate(request);
        if (!validation.isValid()) {
            IntentClassificationResponse response = rejectedResponse(request, validation.firstIssue());
            observation.nodes.add(new IntentNodeObservation(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            finish(response, observation, startedAt);
            return response;
        }
        observation.nodes.add(new IntentNodeObservation(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));

        IntentContext context = contextBuilder.build(request.clue);
        IntentClassificationDecision decision = classifier.classify(context);
        IntentClassificationResponse response = successfulResponse(request, decision);
        observation.nodes.add(new IntentNodeObservation(
                "DETERMINISTIC_CLASSIFICATION",
                "intent=" + request.clue.intent + ",relationType=" + nullableName(request.clue.relationType),
                "intent=" + response.intent + ",nextRoute=" + response.nextRoute));

        PolicyValidationResult businessPolicy = policyValidator.validate(request, response);
        if (!businessPolicy.allowed()) {
            response.status = IntentClassificationStatus.REJECTED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(
                    businessPolicy.errorCode(), businessPolicy.message(), false,
                    businessPolicy.field(), null);
            observation.nodes.add(new IntentNodeObservation(
                    "BUSINESS_POLICY", "classificationResult", "BLOCK"));
            finish(response, observation, startedAt);
            return response;
        }
        observation.nodes.add(new IntentNodeObservation(
                "BUSINESS_POLICY", "classificationResult", "PASS"));

        applyModelAssistance(context, response, observation);
        finish(response, observation, startedAt);
        return response;
    }

    private void applyModelAssistance(IntentContext context,
                                      IntentClassificationResponse response,
                                      IntentRunObservation observation) {
        try {
            IntentModelSuggestion suggestion = modelProvider.suggest(
                    modelContextBuilder.build(context));
            SchemaValidationResult schemaValidation = modelSchemaValidator.validate(suggestion);
            if (!schemaValidation.valid()) {
                response.schemaResult = SchemaResult.FAIL;
                observation.modelCallStatus = ModelCallStatus.REJECTED;
                observation.modelErrorCode = AgentErrorCode.MODEL_OUTPUT_INVALID.name();
                observation.nodes.add(new IntentNodeObservation(
                        "MODEL_SCHEMA", "modelSuggestion",
                        "FAIL:" + String.join(";", schemaValidation.violations())));
                return;
            }

            response.schemaResult = SchemaResult.PASS;
            observation.nodes.add(new IntentNodeObservation(
                    "MODEL_SCHEMA", "modelSuggestion", "PASS"));

            PolicyValidationResult modelPolicy = modelPolicyValidator.validate(suggestion);
            observation.modelPolicyResult = modelPolicy.allowed()
                    ? PolicyResult.PASS : PolicyResult.BLOCK;
            if (!modelPolicy.allowed()) {
                observation.modelCallStatus = ModelCallStatus.REJECTED;
                observation.modelErrorCode = AgentErrorCode.POLICY_BLOCKED.name();
                observation.nodes.add(new IntentNodeObservation(
                        "MODEL_POLICY", "rationaleLength=" + suggestion.rationale().length(), "BLOCK"));
                return;
            }

            observation.nodes.add(new IntentNodeObservation(
                    "MODEL_POLICY", "rationaleLength=" + suggestion.rationale().length(), "PASS"));
            response.modelSuggestion = toView(suggestion);
            response.modelConflict = suggestion.suggestedIntent() != response.intent;
            observation.modelConflict = response.modelConflict;
            observation.modelCallStatus = ModelCallStatus.SUCCEEDED;
            copyUsage(suggestion, observation);
            observation.nodes.add(new IntentNodeObservation(
                    "MODEL_ASSISTANCE",
                    "provider=" + suggestion.provider() + ",model=" + suggestion.modelName(),
                    "suggestedIntent=" + suggestion.suggestedIntent()
                            + ",conflict=" + response.modelConflict));
        } catch (IntentModelProviderException exception) {
            response.schemaResult = exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                    ? SchemaResult.FAIL : SchemaResult.NOT_RUN;
            observation.modelCallStatus = exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                    ? ModelCallStatus.REJECTED : ModelCallStatus.FAILED;
            observation.modelErrorCode = exception.errorCode().name();
            observation.nodes.add(new IntentNodeObservation(
                    "MODEL_ASSISTANCE", "provider=" + modelProvider.providerName(),
                    "FAILED:" + exception.errorCode()));
        } catch (RuntimeException exception) {
            response.schemaResult = SchemaResult.NOT_RUN;
            observation.modelCallStatus = ModelCallStatus.FAILED;
            observation.modelErrorCode = AgentErrorCode.INTERNAL_ERROR.name();
            observation.nodes.add(new IntentNodeObservation(
                    "MODEL_ASSISTANCE", "provider=" + modelProvider.providerName(),
                    "FAILED:" + AgentErrorCode.INTERNAL_ERROR));
        }
    }

    private IntentClassificationResponse successfulResponse(
            IntentClassificationRequest request,
            IntentClassificationDecision decision) {
        IntentClassificationResponse response = new IntentClassificationResponse();
        response.contractVersion = request.contractVersion;
        response.nodeVersion = request.nodeVersion;
        response.runId = request.runId;
        response.clueId = request.clue.id;
        response.nodeId = AgentContract.NODE_ID;
        response.status = decision.nextRoute() == NextRoute.NEEDS_CLARIFICATION
                ? IntentClassificationStatus.NEEDS_CLARIFICATION
                : IntentClassificationStatus.SUCCEEDED;
        response.intent = decision.intent();
        response.evidenceClass = decision.evidenceClass();
        response.factEligibility = decision.factEligibility();
        response.decisionSource = decision.decisionSource();
        response.ambiguityCode = decision.ambiguityCode();
        response.nextRoute = decision.nextRoute();
        response.evidenceIds = List.of(request.clue.id);
        response.policyResult = PolicyResult.PASS;
        response.schemaResult = SchemaResult.NOT_RUN;
        return response;
    }

    private IntentClassificationResponse rejectedResponse(IntentClassificationRequest request,
                                                          ValidationIssue issue) {
        IntentClassificationResponse response = new IntentClassificationResponse();
        if (request != null) {
            response.contractVersion = request.contractVersion;
            response.nodeVersion = request.nodeVersion;
            response.runId = request.runId;
            if (request.clue != null) {
                response.clueId = request.clue.id;
            }
        }
        response.nodeId = AgentContract.NODE_ID;
        response.status = IntentClassificationStatus.REJECTED;
        response.schemaResult = SchemaResult.NOT_RUN;
        response.error = issue == null
                ? new AgentError(AgentErrorCode.INVALID_REQUEST, "request is invalid", false, null, null)
                : new AgentError(issue.code(), issue.message(), false, issue.field(), null);
        return response;
    }

    private IntentRunObservation startObservation(IntentClassificationRequest request) {
        IntentRunObservation observation = new IntentRunObservation();
        observation.runId = request == null ? null : request.runId;
        observation.triggerType = request == null ? null : request.triggerType;
        observation.userDecision = request == null || request.clue == null
                ? null : request.clue.intent;
        observation.workflowVersion = AgentContract.WORKFLOW_VERSION;
        observation.nodeVersion = AgentContract.NODE_VERSION;
        observation.promptVersion = AgentContract.PROMPT_VERSION;
        observation.modelProvider = modelProvider.providerName();
        observation.modelName = modelProvider.modelName();
        observation.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        if (request != null && request.clue != null && request.clue.id != null) {
            observation.evidenceIds = List.of(request.clue.id);
        }
        return observation;
    }

    private void finish(IntentClassificationResponse response,
                        IntentRunObservation observation,
                        long startedAt) {
        observation.latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult == null
                ? SchemaResult.NOT_RUN : response.schemaResult;
        observation.policyResult = response.policyResult;
        observation.nodes.add(new IntentNodeObservation(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status + ",intent=" + response.intent));
        response.observation = observation;
        telemetryRecorder.record(response);
    }

    private IntentModelSuggestionView toView(IntentModelSuggestion suggestion) {
        IntentModelSuggestionView view = new IntentModelSuggestionView();
        view.provider = suggestion.provider();
        view.modelName = suggestion.modelName();
        view.promptVersion = suggestion.promptVersion();
        view.suggestedIntent = suggestion.suggestedIntent();
        view.rationale = suggestion.rationale();
        view.inputTokens = suggestion.inputTokens();
        view.outputTokens = suggestion.outputTokens();
        view.totalTokens = suggestion.totalTokens();
        view.estimatedCost = suggestion.estimatedCost();
        return view;
    }

    private void copyUsage(IntentModelSuggestion suggestion,
                           IntentRunObservation observation) {
        observation.inputTokens = suggestion.inputTokens();
        observation.outputTokens = suggestion.outputTokens();
        observation.totalTokens = suggestion.totalTokens();
        observation.estimatedCost = suggestion.estimatedCost();
    }

    private String inputSummary(IntentClassificationRequest request) {
        if (request == null) {
            return "request=null";
        }
        if (request.clue == null) {
            return "clue=null";
        }
        return "clueType=" + nullableName(request.clue.type)
                + ",intent=" + nullableName(request.clue.intent)
                + ",selectedTextLength=" + length(request.clue.selectedText)
                + ",questionTextLength=" + length(request.clue.questionText);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String nullableName(Enum<?> value) {
        return value == null ? "null" : value.name();
    }
}
