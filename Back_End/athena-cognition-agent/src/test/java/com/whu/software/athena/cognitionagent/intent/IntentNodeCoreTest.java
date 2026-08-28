package com.whu.software.athena.cognitionagent.intent;

import com.whu.software.athena.cognitionagent.intent.context.IntentContext;
import com.whu.software.athena.cognitionagent.intent.context.IntentContextBuilder;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.ClueType;
import com.whu.software.athena.cognitionagent.intent.contract.EvidenceClass;
import com.whu.software.athena.cognitionagent.intent.contract.FactEligibility;
import com.whu.software.athena.cognitionagent.intent.contract.HelpRequestType;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationStatus;
import com.whu.software.athena.cognitionagent.intent.contract.NextRoute;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;
import com.whu.software.athena.cognitionagent.intent.contract.TriggerType;
import com.whu.software.athena.cognitionagent.intent.policy.IntentPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.service.IntentClassificationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentNodeCoreTest {

    private final IntentClassificationService service = new IntentClassificationService();

    @Test
    void relatedCurrentBecomesCandidatePersonalClaim() {
        IntentClassificationResponse response = service.classify(request(
                ClueIntent.RELATED, RelationType.CURRENT, HelpRequestType.OBSERVE));

        assertEquals(IntentClassificationStatus.SUCCEEDED, response.status);
        assertEquals(ClueIntent.RELATED, response.intent);
        assertEquals(EvidenceClass.USER_PERSONAL_CLAIM, response.evidenceClass);
        assertEquals(FactEligibility.CANDIDATE_ONLY, response.factEligibility);
        assertEquals(NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE, response.nextRoute);
        assertEquals(List.of("clue_test_1"), response.evidenceIds);
        assertEquals(PolicyResult.PASS, response.policyResult);
        assertNull(response.error);
    }

    @Test
    void relatedObserveUsesDeclaredRelevance() {
        IntentClassificationResponse response = service.classify(request(
                ClueIntent.RELATED, RelationType.OBSERVE, HelpRequestType.OBSERVE));

        assertEquals(EvidenceClass.USER_DECLARED_RELEVANCE, response.evidenceClass);
        assertEquals(FactEligibility.CANDIDATE_ONLY, response.factEligibility);
    }

    @Test
    void questionNeverBecomesBodyFact() {
        IntentClassificationRequest request = request(
                ClueIntent.QUESTION, null, HelpRequestType.KNOWLEDGE);
        request.clue.questionType = com.whu.software.athena.cognitionagent.intent.contract.QuestionType.IS_COMMON;
        request.clue.questionText = "这是否常见？";

        IntentClassificationResponse response = service.classify(request);

        assertEquals(IntentClassificationStatus.SUCCEEDED, response.status);
        assertEquals(EvidenceClass.USER_QUESTION, response.evidenceClass);
        assertEquals(FactEligibility.NOT_BODY_FACT, response.factEligibility);
        assertEquals(NextRoute.QUESTION_INBOX, response.nextRoute);
    }

    @Test
    void knowledgeOnlyNeverEntersTopicRoute() {
        IntentClassificationResponse response = service.classify(request(
                ClueIntent.KNOWLEDGE_ONLY, RelationType.KNOWLEDGE_ONLY, HelpRequestType.SAVE_ONLY));

        assertEquals(EvidenceClass.ARTICLE_KNOWLEDGE, response.evidenceClass);
        assertEquals(FactEligibility.NOT_BODY_FACT, response.factEligibility);
        assertEquals(NextRoute.KNOWLEDGE_INBOX, response.nextRoute);
    }

    @Test
    void invalidQuestionWithoutQuestionTextIsRejected() {
        IntentClassificationRequest request = request(
                ClueIntent.QUESTION, null, HelpRequestType.KNOWLEDGE);
        request.clue.questionType = com.whu.software.athena.cognitionagent.intent.contract.QuestionType.IS_COMMON;

        IntentClassificationResponse response = service.classify(request);

        assertEquals(IntentClassificationStatus.REJECTED, response.status);
        assertEquals(AgentErrorCode.MISSING_REQUIRED_FIELD, response.error.code);
        assertEquals("clue.questionText", response.error.field);
    }

    @Test
    void invalidKnowledgeRelationIsRejected() {
        IntentClassificationRequest request = request(
                ClueIntent.KNOWLEDGE_ONLY, RelationType.OBSERVE, HelpRequestType.SAVE_ONLY);

        IntentClassificationResponse response = service.classify(request);

        assertEquals(IntentClassificationStatus.REJECTED, response.status);
        assertEquals(AgentErrorCode.INVALID_REQUEST, response.error.code);
    }

    @Test
    void unsupportedClueTypeIsRejected() {
        IntentClassificationRequest request = request(
                ClueIntent.RELATED, RelationType.CURRENT, HelpRequestType.OBSERVE);
        request.clue.type = ClueType.BODY_RECORD;

        IntentClassificationResponse response = service.classify(request);

        assertEquals(IntentClassificationStatus.REJECTED, response.status);
        assertEquals(AgentErrorCode.UNSUPPORTED_SOURCE_TYPE, response.error.code);
    }

    @Test
    void contextBuilderCopiesOnlyTheClueContract() {
        IntentClassificationRequest request = request(
                ClueIntent.RELATED, RelationType.PAST, HelpRequestType.OBSERVE);

        IntentContext context = new IntentContextBuilder().build(request.clue);

        assertEquals(request.clue.id, context.id());
        assertEquals(request.clue.intent, context.intent());
        assertEquals(request.clue.selectedText, context.selectedText());
        assertEquals(request.clue.articleTitle, context.articleTitle());
    }

    @Test
    void policyBlocksQuestionThatWasMarkedAsCandidateFact() {
        IntentClassificationRequest request = request(
                ClueIntent.QUESTION, null, HelpRequestType.KNOWLEDGE);
        request.clue.questionType = com.whu.software.athena.cognitionagent.intent.contract.QuestionType.IS_COMMON;
        request.clue.questionText = "这是否常见？";

        IntentClassificationResponse response = new IntentClassificationResponse();
        response.status = IntentClassificationStatus.SUCCEEDED;
        response.clueId = request.clue.id;
        response.intent = ClueIntent.QUESTION;
        response.evidenceClass = EvidenceClass.USER_PERSONAL_CLAIM;
        response.factEligibility = FactEligibility.CANDIDATE_ONLY;
        response.nextRoute = NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE;
        response.evidenceIds = List.of(request.clue.id);
        response.policyResult = PolicyResult.PASS;

        var result = new IntentPolicyValidator().validate(request, response);

        assertFalse(result.allowed());
        assertEquals(AgentErrorCode.POLICY_BLOCKED, result.errorCode());
    }

    private IntentClassificationRequest request(ClueIntent intent,
                                                RelationType relationType,
                                                HelpRequestType helpRequestType) {
        IntentClassificationRequest request = new IntentClassificationRequest();
        request.contractVersion = "cognition-agent-v1";
        request.nodeVersion = "intent-evidence-v1";
        request.runId = "run_test_1";
        request.idempotencyKey = "clue_test_1:intent-evidence-v1";
        request.triggerType = TriggerType.CLUE_CREATED;
        request.contextSnapshotId = "ctx_test_1";

        request.clue = new com.whu.software.athena.cognitionagent.intent.contract.CluePayload();
        request.clue.id = "clue_test_1";
        request.clue.type = ClueType.ARTICLE_HIGHLIGHT;
        request.clue.intent = intent;
        request.clue.relationType = relationType;
        request.clue.helpRequestType = helpRequestType;
        request.clue.articleId = "1024";
        request.clue.articleTitle = "经期前情绪变化值得怎样记录";
        request.clue.articleType = 100;
        request.clue.selectedText = "经期前几天出现的情绪变化，需要继续观察。";
        request.clue.cycleRelation = com.whu.software.athena.cognitionagent.intent.contract.CycleRelation.BEFORE_PERIOD;
        request.clue.source = "KNOWLEDGE_ARTICLE";
        request.clue.status = com.whu.software.athena.cognitionagent.intent.contract.ClueStatus.PENDING;
        request.clue.originalLabel = "和我有关";
        request.clue.createdAt = "2026-08-20T10:00:00+08:00";
        request.clue.updatedAt = "2026-08-20T10:00:00+08:00";
        return request;
    }
}
