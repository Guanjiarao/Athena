package com.whu.software.athena.cognitionagent.evidence.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.evidence.context.EvidenceCanonicalizationContext;
import com.whu.software.athena.cognitionagent.evidence.context.EvidenceCanonicalizationContextBuilder;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCandidate;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationRequest;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationResponse;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationStatus;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceDecision;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceDuplicateStatus;
import com.whu.software.athena.cognitionagent.evidence.policy.EvidenceCanonicalizationPolicyValidator;
import com.whu.software.athena.cognitionagent.evidence.validation.EvidenceRequestValidator;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class EvidenceCanonicalizationService {

    private final EvidenceRequestValidator validator;
    private final EvidenceCanonicalizationContextBuilder contextBuilder;
    private final EvidenceCanonicalizationPolicyValidator policyValidator;
    private final WorkflowTelemetryRecorder telemetry;

    @Autowired
    public EvidenceCanonicalizationService(WorkflowTelemetryRecorder telemetry) {
        this(new EvidenceRequestValidator(), new EvidenceCanonicalizationContextBuilder(),
                new EvidenceCanonicalizationPolicyValidator(), telemetry);
    }

    EvidenceCanonicalizationService(EvidenceRequestValidator validator,
                                    EvidenceCanonicalizationContextBuilder contextBuilder,
                                    EvidenceCanonicalizationPolicyValidator policyValidator,
                                    WorkflowTelemetryRecorder telemetry) {
        this.validator = validator;
        this.contextBuilder = contextBuilder;
        this.policyValidator = policyValidator;
        this.telemetry = telemetry;
    }

    public EvidenceCanonicalizationResponse canonicalize(
            EvidenceCanonicalizationRequest request) {
        long startedAt = System.nanoTime();
        EvidenceCanonicalizationResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);

        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = EvidenceCanonicalizationStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(issue.code(), issue.message(),
                    false, issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        response.schemaResult = SchemaResult.PASS;
        observation.steps.add(new WorkflowNodeStep(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));

        EvidenceCanonicalizationContext context = contextBuilder.build(request);
        Map<String, CanonicalEvidence> sourceOwners = new HashMap<>();
        Map<String, String> fingerprintOwners = new HashMap<>();
        seedExisting(context.existingEvidence(), sourceOwners, fingerprintOwners);
        Map<String, CanonicalEvidence> accepted = new LinkedHashMap<>();

        for (EvidenceCandidate candidate : context.candidates()) {
            CanonicalEvidence canonical = canonicalize(candidate);
            EvidenceDecision decision = new EvidenceDecision();
            decision.evidenceId = candidate.evidenceId;
            String sourceKey = sourceKey(canonical);
            CanonicalEvidence sourceOwner = sourceOwners.get(sourceKey);
            String fingerprintOwner = fingerprintOwners.get(canonical.contentFingerprint);
            if (sourceOwner != null) {
                if (Objects.equals(sourceOwner.contentFingerprint,
                        canonical.contentFingerprint)) {
                    duplicate(decision, EvidenceDuplicateStatus.EXACT_SOURCE_DUPLICATE,
                            sourceOwner.evidenceId, "SOURCE_ALREADY_PRESENT");
                } else {
                    duplicate(decision, EvidenceDuplicateStatus.SOURCE_CONFLICT,
                            sourceOwner.evidenceId, "SOURCE_CONTENT_CONFLICT");
                }
            } else if (fingerprintOwner != null) {
                duplicate(decision, EvidenceDuplicateStatus.EXACT_CONTENT_DUPLICATE,
                        fingerprintOwner, "CONTENT_ALREADY_PRESENT");
            } else {
                decision.duplicateStatus = EvidenceDuplicateStatus.UNIQUE;
                decision.reasonCode = "ACCEPTED";
                accepted.put(canonical.evidenceId, canonical);
                sourceOwners.put(sourceKey, canonical);
                fingerprintOwners.put(canonical.contentFingerprint, canonical.evidenceId);
            }
            response.decisions.add(decision);
        }
        response.acceptedEvidence.addAll(accepted.values());
        observation.steps.add(new WorkflowNodeStep(
                "CANONICALIZE_AND_DEDUPLICATE",
                "candidateCount=" + context.candidates().size()
                        + ",existingCount=" + context.existingEvidence().size(),
                "acceptedCount=" + response.acceptedEvidence.size()
                        + ",duplicateCount="
                        + (response.decisions.size() - response.acceptedEvidence.size())));

        if (response.decisions.stream().anyMatch(decision ->
                decision.duplicateStatus == EvidenceDuplicateStatus.SOURCE_CONFLICT)) {
            response.status = EvidenceCanonicalizationStatus.REJECTED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(AgentErrorCode.IDEMPOTENCY_CONFLICT,
                    "the same evidence source id was reused with different content",
                    false, "candidates.sourceId", null);
            response.acceptedEvidence.clear();
            observation.steps.add(new WorkflowNodeStep(
                    "IDEMPOTENCY_GATE", "sourceIdReused=true", "SOURCE_CONFLICT"));
            return finish(response, observation, startedAt);
        }

        PolicyValidationResult policy = policyValidator.validate(response.acceptedEvidence);
        response.policyResult = policy.allowed() ? PolicyResult.PASS : PolicyResult.BLOCK;
        if (!policy.allowed()) {
            response.status = EvidenceCanonicalizationStatus.REJECTED;
            response.error = new AgentError(policy.errorCode(), policy.message(),
                    false, policy.field(), null);
            response.acceptedEvidence.clear();
            observation.steps.add(new WorkflowNodeStep(
                    "POLICY", "canonicalEvidence", "BLOCK"));
            return finish(response, observation, startedAt);
        }
        observation.steps.add(new WorkflowNodeStep("POLICY", "canonicalEvidence", "PASS"));
        response.status = response.acceptedEvidence.isEmpty()
                ? EvidenceCanonicalizationStatus.NO_CHANGE
                : EvidenceCanonicalizationStatus.SUCCEEDED;
        return finish(response, observation, startedAt);
    }

    private CanonicalEvidence canonicalize(EvidenceCandidate candidate) {
        CanonicalEvidence result = new CanonicalEvidence();
        result.evidenceId = candidate.evidenceId;
        result.sourceType = candidate.sourceType;
        result.sourceId = candidate.sourceId;
        result.factLevel = factLevel(candidate);
        result.summary = candidate.summary.trim();
        result.contentFingerprint = fingerprint(fingerprintMaterial(candidate));
        result.occurredAt = candidate.occurredAt;
        result.cycleRelation = candidate.cycleRelation;
        result.severity = candidate.severity;
        result.resolved = candidate.resolved;
        result.relatedActionId = candidate.relatedActionId;
        result.feedbackResult = candidate.feedbackResult;
        return result;
    }

    private EvidenceFactLevel factLevel(EvidenceCandidate candidate) {
        if (candidate.sourceType == EvidenceSourceType.ACTION_FEEDBACK) {
            return switch (candidate.feedbackResult) {
                case OCCURRED, NOT_OCCURRED -> EvidenceFactLevel.OBSERVED;
                case UNCERTAIN -> EvidenceFactLevel.QUESTION;
                case SKIPPED -> EvidenceFactLevel.PROCESS_EVENT;
            };
        }
        if (candidate.sourceType == EvidenceSourceType.BODY_RECORD) {
            return EvidenceFactLevel.OBSERVED;
        }
        if (candidate.relationType == RelationType.CURRENT
                || candidate.relationType == RelationType.PAST) {
            return EvidenceFactLevel.SELF_REPORTED;
        }
        return EvidenceFactLevel.DECLARED_RELEVANCE;
    }

    private void seedExisting(List<CanonicalEvidence> existing,
                              Map<String, CanonicalEvidence> sourceOwners,
                              Map<String, String> fingerprintOwners) {
        for (CanonicalEvidence item : existing) {
            if (item == null || blank(item.evidenceId)) continue;
            if (!blank(item.sourceId) && item.sourceType != null) {
                sourceOwners.putIfAbsent(sourceKey(item), item);
            }
            if (!blank(item.contentFingerprint)) {
                fingerprintOwners.putIfAbsent(item.contentFingerprint, item.evidenceId);
            }
        }
    }

    private void duplicate(EvidenceDecision decision,
                           EvidenceDuplicateStatus status,
                           String duplicateOf,
                           String reason) {
        decision.duplicateStatus = status;
        decision.duplicateOfEvidenceId = duplicateOf;
        decision.reasonCode = reason;
    }

    private String sourceKey(CanonicalEvidence evidence) {
        return evidence.sourceType + "|" + evidence.sourceId;
    }

    private String fingerprintMaterial(EvidenceCandidate candidate) {
        StringBuilder value = new StringBuilder(candidate.sourceType.name())
                .append('|').append(normalized(candidate.summary));
        if (candidate.sourceType == EvidenceSourceType.BODY_RECORD
                || candidate.sourceType == EvidenceSourceType.ACTION_FEEDBACK) {
            value.append('|').append(normalized(candidate.occurredAt));
        }
        if (candidate.sourceType == EvidenceSourceType.ACTION_FEEDBACK) {
            value.append('|').append(normalized(candidate.relatedActionId))
                    .append('|').append(candidate.feedbackResult);
        }
        return value.toString();
    }

    private String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String fingerprint(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private EvidenceCanonicalizationResponse baseResponse(
            EvidenceCanonicalizationRequest request) {
        EvidenceCanonicalizationResponse response = new EvidenceCanonicalizationResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(EvidenceCanonicalizationRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.WORKFLOW_VERSION;
        value.nodeId = GraphContract.EVIDENCE_NODE_ID;
        value.nodeVersion = GraphContract.EVIDENCE_NODE_VERSION;
        value.modelProvider = "none";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        return value;
    }

    private EvidenceCanonicalizationResponse finish(
            EvidenceCanonicalizationResponse response,
            WorkflowRunObservation observation,
            long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        observation.evidenceIds = response.acceptedEvidence.stream()
                .map(item -> item.evidenceId).toList();
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(), observation);
        return response;
    }

    private String inputSummary(EvidenceCanonicalizationRequest request) {
        int candidates = request == null || request.candidates == null
                ? 0 : request.candidates.size();
        int existing = request == null || request.existingEvidence == null
                ? 0 : request.existingEvidence.size();
        return "candidateCount=" + candidates + ",existingEvidenceCount=" + existing;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
