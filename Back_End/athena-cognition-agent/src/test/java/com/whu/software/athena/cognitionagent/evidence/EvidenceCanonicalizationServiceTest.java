package com.whu.software.athena.cognitionagent.evidence;

import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationRequest;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationResponse;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationStatus;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceDuplicateStatus;
import com.whu.software.athena.cognitionagent.evidence.service.EvidenceCanonicalizationService;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCandidate;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class EvidenceCanonicalizationServiceTest {

    @Autowired EvidenceCanonicalizationService service;

    @Test
    void repeatedSourceReturnsNoChangeInsteadOfAnotherEvidence() {
        EvidenceCanonicalizationRequest firstRequest = request();
        EvidenceCanonicalizationResponse first = service.canonicalize(firstRequest);
        assertEquals(EvidenceCanonicalizationStatus.SUCCEEDED, first.status);
        assertEquals(PolicyResult.PASS, first.policyResult);

        EvidenceCanonicalizationRequest secondRequest = request();
        secondRequest.existingEvidence = first.acceptedEvidence;
        EvidenceCanonicalizationResponse second = service.canonicalize(secondRequest);

        assertEquals(EvidenceCanonicalizationStatus.NO_CHANGE, second.status);
        assertEquals(0, second.acceptedEvidence.size());
        assertEquals(EvidenceDuplicateStatus.EXACT_SOURCE_DUPLICATE,
                second.decisions.get(0).duplicateStatus);
    }

    @Test
    void sameSourceWithChangedContentIsAnIdempotencyConflict() {
        EvidenceCanonicalizationResponse first = service.canonicalize(request());
        EvidenceCanonicalizationRequest changed = request();
        changed.existingEvidence = first.acceptedEvidence;
        changed.candidates.get(0).summary = "Changed content under the same source id.";

        EvidenceCanonicalizationResponse response = service.canonicalize(changed);

        assertEquals(EvidenceCanonicalizationStatus.REJECTED, response.status);
        assertEquals(EvidenceDuplicateStatus.SOURCE_CONFLICT,
                response.decisions.get(0).duplicateStatus);
        assertEquals(AgentErrorCode.IDEMPOTENCY_CONFLICT, response.error.code);
    }

    @Test
    void repeatedBodyObservationAtAnotherTimeRemainsNewEvidence() {
        EvidenceCanonicalizationRequest firstRequest = bodyRequest(
                "body_record_1", "2026-08-20T08:00:00+08:00");
        EvidenceCanonicalizationResponse first = service.canonicalize(firstRequest);
        EvidenceCanonicalizationRequest secondRequest = bodyRequest(
                "body_record_2", "2026-08-27T08:00:00+08:00");
        secondRequest.existingEvidence = first.acceptedEvidence;

        EvidenceCanonicalizationResponse second = service.canonicalize(secondRequest);

        assertEquals(EvidenceCanonicalizationStatus.SUCCEEDED, second.status);
        assertEquals(EvidenceDuplicateStatus.UNIQUE,
                second.decisions.get(0).duplicateStatus);
    }

    private EvidenceCanonicalizationRequest request() {
        EvidenceCanonicalizationRequest value = new EvidenceCanonicalizationRequest();
        value.runId = "run_evidence_1";
        value.idempotencyKey = "clue_1:evidence-v1";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_evidence_1";
        value.candidates.add(GraphTestFixtures.relatedCandidate("evidence_1", "clue_1"));
        return value;
    }

    private EvidenceCanonicalizationRequest bodyRequest(String sourceId, String occurredAt) {
        EvidenceCanonicalizationRequest value = new EvidenceCanonicalizationRequest();
        value.runId = "run_" + sourceId;
        value.idempotencyKey = sourceId + ":evidence-v1";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_" + sourceId;
        EvidenceCandidate candidate = new EvidenceCandidate();
        candidate.evidenceId = "evidence_" + sourceId;
        candidate.sourceType = EvidenceSourceType.BODY_RECORD;
        candidate.sourceId = sourceId;
        candidate.intent = ClueIntent.RELATED;
        candidate.summary = "Mild cramps were recorded.";
        candidate.occurredAt = occurredAt;
        value.candidates.add(candidate);
        return value;
    }
}
