package athena.cognition.biz.service;

import athena.cognition.biz.bodyrecord.BodyRecordEvidenceProvider;
import athena.cognition.biz.bodyrecord.BodyRecordEvidenceProvider.ConfirmedBodyRecord;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionModels.*;
import athena.cognition.biz.generator.DigestGenerator;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.repository.CognitionJdbcRepository.ClueRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.DigestRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CognitionServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    private CognitionJdbcRepository repository;
    @Mock
    private DigestGenerator generator;
    @Mock
    private BodyRecordEvidenceProvider bodyRecordEvidenceProvider;

    private CognitionService service;

    @BeforeEach
    void setUp() {
        service = new CognitionService(repository, generator, bodyRecordEvidenceProvider,
                new ObjectMapper().findAndRegisterModules());
    }

    // ---------- section 6: three marker save rules ----------

    @Nested
    class MarkerSaveRules {

        @Test
        void relatedCurrentIsSavedAsPending() {
            ClueCreateRequest request = marker(ClueIntent.RELATED, RelationType.CURRENT);
            stubInsertAndFind(1L, ClueIntent.RELATED, ClueStatus.PENDING);

            ClueCreateView result = service.createClue(USER_ID, request);

            ArgumentCaptor<ClueIntent> intent = ArgumentCaptor.forClass(ClueIntent.class);
            ArgumentCaptor<ClueStatus> status = ArgumentCaptor.forClass(ClueStatus.class);
            verify(repository).insertClue(eq(USER_ID), eq(request), intent.capture(), status.capture(), any(), any());
            assertThat(intent.getValue()).isEqualTo(ClueIntent.RELATED);
            assertThat(status.getValue()).isEqualTo(ClueStatus.PENDING);
            assertThat(result.digestTask().triggered()).isFalse();
            assertThat(result.digestTask().taskId()).isNull();
        }

        @Test
        void relationKnowledgeOnlyIsSavedOrganizedAndOutOfThreshold() {
            ClueCreateRequest request = marker(ClueIntent.RELATED, RelationType.KNOWLEDGE_ONLY);
            stubInsertAndFind(2L, ClueIntent.KNOWLEDGE_ONLY, ClueStatus.ORGANIZED);

            service.createClue(USER_ID, request);

            ArgumentCaptor<ClueIntent> intent = ArgumentCaptor.forClass(ClueIntent.class);
            ArgumentCaptor<ClueStatus> status = ArgumentCaptor.forClass(ClueStatus.class);
            verify(repository).insertClue(eq(USER_ID), eq(request), intent.capture(), status.capture(), any(), any());
            assertThat(intent.getValue()).isEqualTo(ClueIntent.KNOWLEDGE_ONLY);
            assertThat(status.getValue()).isEqualTo(ClueStatus.ORGANIZED);
        }

        @Test
        void questionIsSavedPendingAndNeverCreatesDigest() {
            ClueCreateRequest request = new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, ClueIntent.QUESTION, null, HelpRequestType.KNOWLEDGE,
                    "1024", "文章", 100, "选中文字", QuestionType.IS_COMMON, "这是否常见？",
                    null, CycleRelation.UNKNOWN, null, null, ClueSource.KNOWLEDGE_ARTICLE,
                    null, null, "我有疑问");
            stubInsertAndFind(3L, ClueIntent.QUESTION, ClueStatus.PENDING);

            ClueCreateView result = service.createClue(USER_ID, request);

            ArgumentCaptor<ClueStatus> status = ArgumentCaptor.forClass(ClueStatus.class);
            verify(repository).insertClue(eq(USER_ID), eq(request), eq(ClueIntent.QUESTION), status.capture(), any(), any());
            assertThat(status.getValue()).isEqualTo(ClueStatus.PENDING);
            assertThat(result.digestTask().triggered()).isFalse();
        }

        @Test
        void questionRequiresTypeOrText() {
            ClueCreateRequest request = new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, ClueIntent.QUESTION, null, HelpRequestType.KNOWLEDGE,
                    "1024", "文章", 100, "选中文字", null, null,
                    null, null, null, null, null, null, null, "我有疑问");

            CognitionException ex = assertThrows(CognitionException.class, () -> service.createClue(USER_ID, request));
            assertThat(ex.errorCode()).isEqualTo(CognitionException.INVALID_ARGUMENT);
        }

        @Test
        void severityOutsideRangeIsRejected() {
            ClueCreateRequest request = new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, ClueIntent.RELATED, RelationType.CURRENT, HelpRequestType.OBSERVE,
                    "1024", "文章", 100, "选中文字", null, null,
                    null, null, 11, null, null, null, null, "和我有关");

            CognitionException ex = assertThrows(CognitionException.class, () -> service.createClue(USER_ID, request));
            assertThat(ex.errorCode()).isEqualTo(CognitionException.INVALID_ARGUMENT);
        }

        @Test
        void blankSelectedTextIsRejected() {
            ClueCreateRequest request = new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, ClueIntent.RELATED, RelationType.CURRENT, HelpRequestType.OBSERVE,
                    "1024", "文章", 100, "  ", null, null,
                    null, null, null, null, null, null, null, "和我有关");

            assertThrows(CognitionException.class, () -> service.createClue(USER_ID, request));
        }

        private ClueCreateRequest marker(ClueIntent intent, RelationType relationType) {
            return new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, intent, relationType, HelpRequestType.OBSERVE,
                    "1024", "文章", 100, "选中文字", null, null,
                    null, CycleRelation.BEFORE_PERIOD, 3, false, ClueSource.KNOWLEDGE_ARTICLE,
                    null, "经前情绪变化", "和我有关");
        }

        private void stubInsertAndFind(long id, ClueIntent intent, ClueStatus status) {
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(id);
            when(repository.findClue(USER_ID, id)).thenReturn(Optional.of(clueRow(id, intent, status)));
        }
    }

    // ---------- section 6.4: revoke rules ----------

    @Nested
    class RevokeRules {

        @Test
        void pendingClueNotInDigestCanBeRevoked() {
            when(repository.findClue(USER_ID, 1L))
                    .thenReturn(Optional.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING)));
            when(repository.isClueUsedInDigest(USER_ID, 1L)).thenReturn(false);

            String result = service.deleteClue(USER_ID, "clue_1");

            assertThat(result).isEqualTo("clue_1");
            verify(repository).logicalDeleteClue(USER_ID, 1L);
        }

        @Test
        void knowledgeOnlyClueNotInDigestCanBeRevoked() {
            when(repository.findClue(USER_ID, 2L))
                    .thenReturn(Optional.of(clueRow(2L, ClueIntent.KNOWLEDGE_ONLY, ClueStatus.ORGANIZED)));
            when(repository.isClueUsedInDigest(USER_ID, 2L)).thenReturn(false);

            service.deleteClue(USER_ID, "clue_2");

            verify(repository).logicalDeleteClue(USER_ID, 2L);
        }

        @Test
        void clueInDigestCannotBeRevoked() {
            when(repository.findClue(USER_ID, 3L))
                    .thenReturn(Optional.of(clueRow(3L, ClueIntent.RELATED, ClueStatus.PROCESSING)));
            when(repository.isClueUsedInDigest(USER_ID, 3L)).thenReturn(true);

            CognitionException ex = assertThrows(CognitionException.class,
                    () -> service.deleteClue(USER_ID, "clue_3"));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.CLUE_IN_USE);
            assertThat(ex.semanticCode()).isEqualTo(409);
            verify(repository, never()).logicalDeleteClue(anyLong(), anyLong());
        }

        @Test
        void dismissedClueCannotBeRevoked() {
            when(repository.findClue(USER_ID, 4L))
                    .thenReturn(Optional.of(clueRow(4L, ClueIntent.RELATED, ClueStatus.DISMISSED)));
            when(repository.isClueUsedInDigest(USER_ID, 4L)).thenReturn(false);

            CognitionException ex = assertThrows(CognitionException.class,
                    () -> service.deleteClue(USER_ID, "clue_4"));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.STATE_CONFLICT);
            verify(repository, never()).logicalDeleteClue(anyLong(), anyLong());
        }

        @Test
        void malformedClueIdIsInvalidArgument() {
            CognitionException ex = assertThrows(CognitionException.class,
                    () -> service.deleteClue(USER_ID, "topic_9"));
            assertThat(ex.errorCode()).isEqualTo(CognitionException.INVALID_ARGUMENT);
        }
    }

    // ---------- section 7.2: digest decision ----------

    @Nested
    class DigestDecisions {

        @Test
        void keepAsKnowledgeNeverCreatesTopicOrAction() {
            DigestRow ready = digestRow(9L, DigestStatus.READY, 1);
            DigestRow decided = digestRow(9L, DigestStatus.KEPT_AS_KNOWLEDGE, 2);
            when(repository.findDigest(USER_ID, 9L, true)).thenReturn(Optional.of(ready));
            when(repository.findDigest(USER_ID, 9L, false)).thenReturn(Optional.of(decided));
            when(repository.findDigestClues(USER_ID, 9L)).thenReturn(List.of());

            DigestDecisionView result = service.decideDigest(USER_ID, "digest_9",
                    new DigestDecisionRequest(DigestDecision.KEEP_AS_KNOWLEDGE, null, 1));

            assertThat(result.topic()).isNull();
            assertThat(result.action()).isNull();
            assertThat(result.digest().status()).isEqualTo(DigestStatus.KEPT_AS_KNOWLEDGE);
            verify(repository, never()).insertTopic(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
                    anyInt(), anyInt(), anyInt(), anyInt());
            verify(repository, never()).insertAction(anyLong(), anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        void rejectMarksSourceCluesDismissed() {
            DigestRow ready = digestRow(9L, DigestStatus.READY, 1);
            DigestRow decided = digestRow(9L, DigestStatus.REJECTED, 2);
            when(repository.findDigest(USER_ID, 9L, true)).thenReturn(Optional.of(ready));
            when(repository.findDigest(USER_ID, 9L, false)).thenReturn(Optional.of(decided));
            when(repository.findDigestClues(USER_ID, 9L))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PROCESSING)));

            DigestDecisionView result = service.decideDigest(USER_ID, "digest_9",
                    new DigestDecisionRequest(DigestDecision.REJECT, null, null));

            assertThat(result.digest().status()).isEqualTo(DigestStatus.REJECTED);
            verify(repository).updateClueStatus(USER_ID, List.of(1L), ClueStatus.DISMISSED);
        }

        @Test
        void decidedDigestCannotBeDecidedAgain() {
            when(repository.findDigest(USER_ID, 9L, true))
                    .thenReturn(Optional.of(digestRow(9L, DigestStatus.ACCEPTED, 2)));

            CognitionException ex = assertThrows(CognitionException.class, () -> service.decideDigest(
                    USER_ID, "digest_9", new DigestDecisionRequest(DigestDecision.REJECT, null, 2)));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.STATE_CONFLICT);
            assertThat(ex.currentStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        void staleClientVersionIsRejected() {
            when(repository.findDigest(USER_ID, 9L, true))
                    .thenReturn(Optional.of(digestRow(9L, DigestStatus.READY, 3)));

            CognitionException ex = assertThrows(CognitionException.class, () -> service.decideDigest(
                    USER_ID, "digest_9", new DigestDecisionRequest(DigestDecision.ACCEPT_AS_TOPIC, null, 1)));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.VERSION_CONFLICT);
        }

        @Test
        void acceptDigestComputesMaturityAndDomainFromEvidence() {
            when(repository.findDigest(USER_ID, 9L, true))
                    .thenReturn(Optional.of(digestRow(9L, DigestStatus.READY, 1)));
            when(repository.findDigestClues(USER_ID, 9L)).thenReturn(List.of(
                    clueRow(1L, ClueIntent.RELATED, ClueStatus.PROCESSING),
                    clueRow(2L, ClueIntent.RELATED, ClueStatus.PROCESSING)));
            // 1 article clue + 2 confirmed body records across two cycles
            when(repository.findDigestEvidence(USER_ID, 9L)).thenReturn(List.of(
                    evidenceRow(1L, EvidenceSourceType.CLUE, null, Instant.parse("2026-08-10T04:00:00Z")),
                    evidenceRow(2L, EvidenceSourceType.BODY_RECORD, null, Instant.parse("2026-06-10T04:00:00Z")),
                    evidenceRow(3L, EvidenceSourceType.BODY_RECORD, null, Instant.parse("2026-07-11T04:00:00Z"))));
            when(repository.insertTopic(eq(USER_ID), eq(9L), eq("经前情绪变化"), eq("MOOD"),
                    eq(Maturity.REPEATED_PATTERN), any(), any(), any(),
                    eq(3), eq(1), eq(2), eq(3))).thenReturn(7001L);
            when(repository.insertAction(eq(USER_ID), eq(7001L), any(), any(), eq(ActionType.RECORD_BODY),
                    any(), any())).thenReturn(8001L);
            when(repository.findTopic(USER_ID, 7001L, false)).thenReturn(Optional.of(
                    new CognitionJdbcRepository.TopicRow(7001L, 9L, "经前情绪变化", "MOOD",
                            Maturity.REPEATED_PATTERN, UserProgress.OBSERVING, RiskStatus.NONE, "可能联系",
                            "[]", "[]", 3, 1, 2, 3, 8001L, Instant.now(), 1, Instant.now())));
            when(repository.findAction(USER_ID, 8001L, false)).thenReturn(Optional.of(
                    new CognitionJdbcRepository.ActionRow(8001L, 7001L, "记录一次相关身体变化", "记录一次",
                            ActionType.RECORD_BODY, ActionStatus.PENDING, null, null, Instant.now())));
            when(repository.findDigest(USER_ID, 9L, false))
                    .thenReturn(Optional.of(digestRow(9L, DigestStatus.ACCEPTED, 2)));

            DigestDecisionView result = service.decideDigest(USER_ID, "digest_9",
                    new DigestDecisionRequest(DigestDecision.ACCEPT_AS_TOPIC, null, 1));

            assertThat(result.digest().status()).isEqualTo(DigestStatus.ACCEPTED);
            assertThat(result.topic().maturity()).isEqualTo(Maturity.REPEATED_PATTERN);
            assertThat(result.topic().domain()).isEqualTo("MOOD");
            assertThat(result.topic().userProgress()).isEqualTo(UserProgress.OBSERVING);
            assertThat(result.topic().evidenceCount()).isEqualTo(3);
            assertThat(result.action().id()).isEqualTo("action_8001");
            verify(repository).linkTopicEvidence(USER_ID, 7001L, List.of(1L, 2L, 3L));
        }
    }

    // ---------- section 8.6: digest task ----------

    @Nested
    class DigestTasks {

        @Test
        void taskWithoutValidRelatedCluesFails() {
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of());

            CognitionException ex = assertThrows(CognitionException.class, () -> service.createDigestTask(
                    USER_ID, new DigestTaskCreateRequest(TriggerType.USER_REQUEST, null, "经前情绪变化")));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.NO_VALID_EVIDENCE);
        }

        @Test
        void openDigestOnSameCluesBlocksNewTask() {
            ClueRow clue = clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING);
            when(repository.findClues(USER_ID, List.of(1L))).thenReturn(List.of(clue));
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(clue));
            when(repository.hasOpenDigestForClues(USER_ID, List.of(1L))).thenReturn(true);

            CognitionException ex = assertThrows(CognitionException.class, () -> service.createDigestTask(
                    USER_ID, new DigestTaskCreateRequest(TriggerType.USER_REQUEST, List.of("clue_1"), null)));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.TASK_RUNNING);
        }

        @Test
        void taskRejectsClueIdsNotOwnedByCurrentUser() {
            when(repository.findClues(USER_ID, List.of(1L, 2L)))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING)));

            assertThrows(CognitionException.class, () -> service.createDigestTask(USER_ID,
                    new DigestTaskCreateRequest(TriggerType.USER_REQUEST, List.of("clue_1", "clue_2"), null)));

            verify(repository, never()).insertTask(anyLong(), any(), any());
        }
    }

    // ---------- section 10.1 / TC-07: automatic thresholds ----------

    @Nested
    class AutoThreshold {

        @Test
        void rule1TriggersOnThirdRelatedClueInCandidateGroup() {
            ClueCreateRequest request = relatedRequest("经前情绪变化");
            ClueRow saved = clueRow(3L, ClueIntent.RELATED, ClueStatus.PENDING);
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(3L);
            when(repository.findClue(USER_ID, 3L)).thenReturn(Optional.of(saved));
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING),
                            clueRow(2L, ClueIntent.RELATED, ClueStatus.PENDING), saved));
            when(repository.hasOpenDigestForClues(USER_ID, List.of(1L, 2L, 3L))).thenReturn(false);
            when(repository.insertTask(USER_ID, TriggerType.RULE_THRESHOLD, "fixed-v1")).thenReturn(100L);
            when(repository.insertDigest(eq(USER_ID), any(), eq("fixed-v1"))).thenReturn(200L);
            when(generator.generate(any(), any())).thenReturn(generated());
            when(repository.findTask(USER_ID, 100L)).thenReturn(Optional.of(
                    new CognitionJdbcRepository.TaskRow(100L, 200L, DigestTaskStatus.SUCCEEDED,
                            TriggerType.RULE_THRESHOLD, "fixed-v1", 0, null, Instant.now())));
            when(repository.findDigest(USER_ID, 200L, false))
                    .thenReturn(Optional.of(digestRow(200L, DigestStatus.READY, 1)));

            ClueCreateView result = service.createClue(USER_ID, request);

            assertThat(result.digestTask().triggered()).isTrue();
            assertThat(result.digestTask().taskId()).isEqualTo("task_100");
            assertThat(result.digestTask().digestId()).isEqualTo("digest_200");
            assertThat(result.digestTask().status()).isEqualTo(DigestTaskStatus.SUCCEEDED);
            verify(repository).insertTask(USER_ID, TriggerType.RULE_THRESHOLD, "fixed-v1");
            verify(repository).updateClueStatus(USER_ID, List.of(1L, 2L, 3L), ClueStatus.PROCESSING);
        }

        @Test
        void belowThresholdDoesNotTrigger() {
            ClueCreateRequest request = relatedRequest("经前情绪变化");
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(1L);
            when(repository.findClue(USER_ID, 1L))
                    .thenReturn(Optional.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING)));
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING)));

            ClueCreateView result = service.createClue(USER_ID, request);

            assertThat(result.digestTask().triggered()).isFalse();
            assertThat(result.digestTask().taskId()).isNull();
            verify(repository, never()).insertTask(anyLong(), any(), any());
        }

        @Test
        void questionAndKnowledgeOnlyNeverCountIntoThreshold() {
            ClueCreateRequest question = new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, ClueIntent.QUESTION, null, HelpRequestType.KNOWLEDGE,
                    "1024", "文章", 100, "选中文字", QuestionType.IS_COMMON, "这是否常见？",
                    null, null, null, null, null, null, "经前情绪变化", "我有疑问");
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(5L, 6L);
            when(repository.findClue(USER_ID, 5L))
                    .thenReturn(Optional.of(clueRow(5L, ClueIntent.QUESTION, ClueStatus.PENDING)));
            when(repository.findClue(USER_ID, 6L))
                    .thenReturn(Optional.of(clueRow(6L, ClueIntent.KNOWLEDGE_ONLY, ClueStatus.ORGANIZED)));
            ClueCreateRequest knowledge = new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, ClueIntent.KNOWLEDGE_ONLY, RelationType.KNOWLEDGE_ONLY,
                    HelpRequestType.SAVE_ONLY, "1024", "文章", 100, "选中文字", null, null,
                    null, null, null, null, null, null, "经前情绪变化", "保存为知识");

            assertThat(service.createClue(USER_ID, question).digestTask().triggered()).isFalse();
            assertThat(service.createClue(USER_ID, knowledge).digestTask().triggered()).isFalse();

            verify(repository, never()).findPendingRelatedCluesForCandidate(anyLong(), any(), any());
            verify(repository, never()).insertTask(anyLong(), any(), any());
        }

        @Test
        void openDigestBlocksDuplicateAutomaticTask() {
            ClueCreateRequest request = relatedRequest("经前情绪变化");
            ClueRow saved = clueRow(3L, ClueIntent.RELATED, ClueStatus.PENDING);
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(3L);
            when(repository.findClue(USER_ID, 3L)).thenReturn(Optional.of(saved));
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING),
                            clueRow(2L, ClueIntent.RELATED, ClueStatus.PENDING), saved));
            when(repository.hasOpenDigestForClues(USER_ID, List.of(1L, 2L, 3L))).thenReturn(true);

            ClueCreateView result = service.createClue(USER_ID, request);

            assertThat(result.digestTask().triggered()).isFalse();
            verify(repository, never()).insertTask(anyLong(), any(), any());
        }

        @Test
        void rule2FiresWhenProviderConfirmsBodyRecord() {
            ClueCreateRequest request = relatedRequest("经前情绪变化");
            ClueRow saved = clueRow(2L, ClueIntent.RELATED, ClueStatus.PENDING);
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(2L);
            when(repository.findClue(USER_ID, 2L)).thenReturn(Optional.of(saved));
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING), saved));
            when(bodyRecordEvidenceProvider.findConfirmedBodyRecords(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(new ConfirmedBodyRecord("2001", "经期前记录过轻微情绪波动", Instant.now())));
            when(repository.hasOpenDigestForClues(USER_ID, List.of(1L, 2L))).thenReturn(false);
            when(repository.insertTask(USER_ID, TriggerType.RULE_THRESHOLD, "fixed-v1")).thenReturn(100L);
            when(repository.insertDigest(eq(USER_ID), any(), eq("fixed-v1"))).thenReturn(200L);
            when(generator.generate(any(), any())).thenReturn(generated());
            when(repository.findTask(USER_ID, 100L)).thenReturn(Optional.of(
                    new CognitionJdbcRepository.TaskRow(100L, 200L, DigestTaskStatus.SUCCEEDED,
                            TriggerType.RULE_THRESHOLD, "fixed-v1", 0, null, Instant.now())));
            when(repository.findDigest(USER_ID, 200L, false))
                    .thenReturn(Optional.of(digestRow(200L, DigestStatus.READY, 1)));

            ClueCreateView result = service.createClue(USER_ID, request);

            assertThat(result.digestTask().triggered()).isTrue();
            verify(repository).insertEvidence(eq(USER_ID), eq(EvidenceSourceType.BODY_RECORD), eq("2001"),
                    eq(FactLevel.SELF_REPORTED), any(), any());
        }

        @Test
        void rule2StaysInertWhenProviderConfirmsNothing() {
            ClueCreateRequest request = relatedRequest("经前情绪变化");
            ClueRow saved = clueRow(2L, ClueIntent.RELATED, ClueStatus.PENDING);
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(2L);
            when(repository.findClue(USER_ID, 2L)).thenReturn(Optional.of(saved));
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING), saved));

            ClueCreateView result = service.createClue(USER_ID, request);

            assertThat(result.digestTask().triggered()).isFalse();
            verify(repository, never()).insertTask(anyLong(), any(), any());
        }

        @Test
        void emptyCandidateGroupDoesNotAutoMerge() {
            ClueCreateRequest request = relatedRequest(null);
            ClueRow saved = new ClueRow(7L, ClueType.ARTICLE_HIGHLIGHT, ClueIntent.RELATED, RelationType.CURRENT,
                    HelpRequestType.OBSERVE, "1024", "文章", 100, "选中文字", null, null, null,
                    CycleRelation.UNKNOWN, null, null, ClueSource.KNOWLEDGE_ARTICLE, ClueStatus.PENDING,
                    null, null, "和我有关", Instant.now(), Instant.now());
            when(repository.insertClue(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(7L);
            when(repository.findClue(USER_ID, 7L)).thenReturn(Optional.of(saved));

            ClueCreateView result = service.createClue(USER_ID, request);

            assertThat(result.digestTask().triggered()).isFalse();
            verify(repository, never()).findPendingRelatedCluesForCandidate(anyLong(), any(), any());
            verify(repository, never()).insertTask(anyLong(), any(), any());
        }

        private ClueCreateRequest relatedRequest(String suggestedTopicTitle) {
            return new ClueCreateRequest(
                    ClueType.ARTICLE_HIGHLIGHT, ClueIntent.RELATED, RelationType.CURRENT, HelpRequestType.OBSERVE,
                    "1024", "文章", 100, "选中文字", null, null,
                    null, CycleRelation.BEFORE_PERIOD, 3, false, ClueSource.KNOWLEDGE_ARTICLE,
                    null, suggestedTopicTitle, "和我有关");
        }

        private DigestGenerator.GeneratedDigest generated() {
            return new DigestGenerator.GeneratedDigest("经前情绪变化", "共同点", "可能联系", "仍不确定",
                    "记录一次", "fixed-v1");
        }
    }

    // ---------- section 12 / TC-19: generation failure and retry ----------

    @Nested
    class FailureAndRetry {

        @Test
        void failedGenerationKeepsFailureStateQueryableAndCluesProcessing() {
            ClueRow clue = clueRow(1L, ClueIntent.RELATED, ClueStatus.PENDING);
            when(repository.findClues(USER_ID, List.of(1L))).thenReturn(List.of(clue));
            when(repository.findPendingRelatedCluesForCandidate(USER_ID, null, "经前情绪变化"))
                    .thenReturn(List.of(clue));
            when(repository.hasOpenDigestForClues(USER_ID, List.of(1L))).thenReturn(false);
            when(repository.insertTask(USER_ID, TriggerType.USER_REQUEST, "fixed-v1")).thenReturn(100L);
            when(repository.insertDigest(eq(USER_ID), any(), eq("fixed-v1"))).thenReturn(200L);
            when(generator.generate(any(), any())).thenThrow(new RuntimeException("boom"));
            when(repository.findTask(USER_ID, 100L)).thenReturn(Optional.of(
                    taskRow(100L, 200L, DigestTaskStatus.FAILED, 0, CognitionException.GENERATION_FAILED)));
            CognitionJdbcRepository.DigestRow failedDigest = new CognitionJdbcRepository.DigestRow(200L,
                    "经前情绪变化", DigestStatus.FAILED, null, null, null, null, "fixed-v1", null,
                    CognitionException.GENERATION_FAILED, null, 1, Instant.now());
            when(repository.findDigest(USER_ID, 200L, false)).thenReturn(Optional.of(failedDigest));

            DigestTaskView result = service.createDigestTask(USER_ID,
                    new DigestTaskCreateRequest(TriggerType.USER_REQUEST, List.of("clue_1"), null));

            assertThat(result.status()).isEqualTo(DigestTaskStatus.FAILED);
            assertThat(result.digestStatus()).isEqualTo(DigestStatus.FAILED);
            assertThat(result.failureCode()).isEqualTo(CognitionException.GENERATION_FAILED);
            verify(repository).failDigest(USER_ID, 200L, CognitionException.GENERATION_FAILED);
            verify(repository).markTaskFailed(USER_ID, 100L, 200L, CognitionException.GENERATION_FAILED);
            // Section 12 V1: source clues stay PROCESSING, nothing else is created
            verify(repository).updateClueStatus(USER_ID, List.of(1L), ClueStatus.PROCESSING);
            verify(repository, never()).updateClueStatus(eq(USER_ID), any(), eq(ClueStatus.PENDING));
            verify(repository, never()).insertTopic(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
                    anyInt(), anyInt(), anyInt(), anyInt());
            verify(repository, never()).insertAction(anyLong(), anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        void retryOfNonFailedTaskReturnsStateConflict() {
            when(repository.findTaskForUpdate(USER_ID, 100L)).thenReturn(Optional.of(
                    taskRow(100L, 200L, DigestTaskStatus.SUCCEEDED, 1, null)));

            CognitionException ex = assertThrows(CognitionException.class,
                    () -> service.retryTask(USER_ID, "task_100"));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.STATE_CONFLICT);
            assertThat(ex.currentStatus()).isEqualTo("SUCCEEDED");
            verify(repository, never()).markTaskRunning(anyLong(), anyLong(), anyBoolean());
            verify(generator, never()).generate(any(), any());
        }

        @Test
        void retryReusesOriginalDigestAndEvidence() {
            when(repository.findTaskForUpdate(USER_ID, 100L)).thenReturn(Optional.of(
                    taskRow(100L, 200L, DigestTaskStatus.FAILED, 0, CognitionException.GENERATION_FAILED)));
            when(repository.findDigest(USER_ID, 200L, true))
                    .thenReturn(Optional.of(digestRow(200L, DigestStatus.FAILED, 1)));
            when(repository.findDigestClues(USER_ID, 200L)).thenReturn(List.of(
                    clueRow(1L, ClueIntent.RELATED, ClueStatus.PROCESSING),
                    clueRow(2L, ClueIntent.RELATED, ClueStatus.PROCESSING)));
            when(generator.generate(any(), any())).thenReturn(
                    new DigestGenerator.GeneratedDigest("经前情绪变化", "共同点", "可能联系", "仍不确定",
                            "记录一次", "fixed-v1"));
            when(repository.findTask(USER_ID, 100L)).thenReturn(Optional.of(
                    taskRow(100L, 200L, DigestTaskStatus.SUCCEEDED, 1, null)));
            when(repository.findDigest(USER_ID, 200L, false))
                    .thenReturn(Optional.of(digestRow(200L, DigestStatus.READY, 2)));

            DigestTaskView result = service.retryTask(USER_ID, "task_100");

            assertThat(result.status()).isEqualTo(DigestTaskStatus.SUCCEEDED);
            assertThat(result.digestId()).isEqualTo("digest_200");
            assertThat(result.digestStatus()).isEqualTo(DigestStatus.READY);
            assertThat(result.retryCount()).isEqualTo(1);
            assertThat(result.failureCode()).isNull();
            verify(repository).markTaskRunning(USER_ID, 100L, true);
            verify(repository).markDigestProcessing(USER_ID, 200L);
            verify(repository).completeDigest(eq(USER_ID), eq(200L), any(), any(), any(), any());
            // no duplicate digest / evidence / clue membership
            verify(repository, never()).insertTask(anyLong(), any(), any());
            verify(repository, never()).insertDigest(anyLong(), any(), any());
            verify(repository, never()).insertEvidence(anyLong(), any(), any(), any(), any(), any());
            verify(repository, never()).linkDigestClues(anyLong(), anyLong(), any());
        }

        @Test
        void secondRetryAfterSuccessConflictsWithoutSideEffects() {
            when(repository.findTaskForUpdate(USER_ID, 100L)).thenReturn(
                    Optional.of(taskRow(100L, 200L, DigestTaskStatus.FAILED, 0, CognitionException.GENERATION_FAILED)),
                    Optional.of(taskRow(100L, 200L, DigestTaskStatus.SUCCEEDED, 1, null)));
            when(repository.findDigest(USER_ID, 200L, true))
                    .thenReturn(Optional.of(digestRow(200L, DigestStatus.FAILED, 1)));
            when(repository.findDigestClues(USER_ID, 200L))
                    .thenReturn(List.of(clueRow(1L, ClueIntent.RELATED, ClueStatus.PROCESSING)));
            when(generator.generate(any(), any())).thenReturn(
                    new DigestGenerator.GeneratedDigest("经前情绪变化", "共同点", "可能联系", "仍不确定",
                            "记录一次", "fixed-v1"));
            when(repository.findTask(USER_ID, 100L)).thenReturn(Optional.of(
                    taskRow(100L, 200L, DigestTaskStatus.SUCCEEDED, 1, null)));
            when(repository.findDigest(USER_ID, 200L, false))
                    .thenReturn(Optional.of(digestRow(200L, DigestStatus.READY, 2)));

            service.retryTask(USER_ID, "task_100");
            CognitionException ex = assertThrows(CognitionException.class,
                    () -> service.retryTask(USER_ID, "task_100"));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.STATE_CONFLICT);
            verify(generator, times(1)).generate(any(), any());
            verify(repository, never()).insertDigest(anyLong(), any(), any());
            verify(repository, never()).insertEvidence(anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        void duplicateFeedbackStillConflicts() {
            CognitionJdbcRepository.ActionRow action = new CognitionJdbcRepository.ActionRow(9L, 5L, "记录一次",
                    "补充时间和程度", ActionType.RECORD_BODY, ActionStatus.PENDING, null, null, Instant.now());
            when(repository.findAction(USER_ID, 9L, true)).thenReturn(Optional.of(action));
            when(repository.findFeedbackByAction(USER_ID, 9L)).thenReturn(Optional.of(
                    new CognitionJdbcRepository.FeedbackRow(30L, 9L, 5L, ActionFeedbackResult.OCCURRED, null,
                            Instant.now(), Instant.now(), 40L)));

            CognitionException ex = assertThrows(CognitionException.class, () -> service.submitFeedback(
                    USER_ID, "action_9", new FeedbackRequest("topic_5", ActionFeedbackResult.OCCURRED, null, null)));

            assertThat(ex.errorCode()).isEqualTo(CognitionException.STATE_CONFLICT);
            verify(repository, never()).insertFeedback(anyLong(), anyLong(), anyLong(), any(), any(), any());
        }

        private CognitionJdbcRepository.TaskRow taskRow(long id, Long digestId, DigestTaskStatus status,
                                                        int retryCount, String failureCode) {
            return new CognitionJdbcRepository.TaskRow(id, digestId, status, TriggerType.USER_REQUEST,
                    "fixed-v1", retryCount, failureCode, Instant.now());
        }
    }

    // ---------- section 7.3 / TC-12~16: action feedback ----------

    @Nested
    class ActionFeedbackRules {

        @Test
        void tc12OccurredCompletesActionCreatesEvidenceAndRefreshesTopic() {
            stubPendingAction();
            stubCurrentTopic(Maturity.CLUE);
            CognitionJdbcRepository.FeedbackRow saved = new CognitionJdbcRepository.FeedbackRow(30L, 9L, 5L,
                    ActionFeedbackResult.OCCURRED, "今天下午出现过轻微情绪低落",
                    Instant.parse("2026-08-13T10:20:00Z"), Instant.now(), 40L);
            when(repository.findFeedbackByAction(USER_ID, 9L)).thenReturn(Optional.empty(), Optional.of(saved));
            when(repository.insertFeedback(eq(USER_ID), eq(9L), eq(5L), eq(ActionFeedbackResult.OCCURRED),
                    any(), any())).thenReturn(30L);
            when(repository.insertEvidence(eq(USER_ID), eq(EvidenceSourceType.ACTION_FEEDBACK), eq("feedback_30"),
                    eq(FactLevel.SELF_REPORTED), any(), any())).thenReturn(40L);
            when(repository.findTopicEvidence(USER_ID, 5L)).thenReturn(List.of(
                    clueEvidence(1L), evidenceRow(40L, EvidenceSourceType.ACTION_FEEDBACK,
                            ActionFeedbackResult.OCCURRED, Instant.parse("2026-08-13T10:20:00Z"))));
            when(repository.findTopic(USER_ID, 5L, false)).thenReturn(Optional.of(topicRow(5L, 3)));

            FeedbackResultView result = service.submitFeedback(USER_ID, "action_9",
                    new FeedbackRequest("topic_5", ActionFeedbackResult.OCCURRED, "今天下午出现过轻微情绪低落",
                            Instant.parse("2026-08-13T10:20:00Z")));

            assertThat(result.feedback().id()).isEqualTo("feedback_30");
            assertThat(result.feedback().evidenceId()).isEqualTo("evidence_40");
            assertThat(result.actionStatus()).isEqualTo(ActionStatus.COMPLETED);
            assertThat(result.topicVersion()).isEqualTo(3);
            assertThat(result.refreshRequired()).isTrue();
            verify(repository).linkTopicEvidence(USER_ID, 5L, List.of(40L));
            verify(repository).updateActionStatus(USER_ID, 9L, ActionStatus.COMPLETED);
            verify(repository).updateTopicAfterFeedback(eq(USER_ID), eq(5L),
                    argThat(text -> text != null && text.contains("出现过")),
                    eq(Maturity.INSUFFICIENT), eq(2), eq(1), eq(1), eq(1));
        }

        @Test
        void tc13NotOccurredCreatesObservationEvidenceWithoutEndingTopic() {
            stubPendingAction();
            stubCurrentTopic(Maturity.CLUE);
            CognitionJdbcRepository.FeedbackRow saved = new CognitionJdbcRepository.FeedbackRow(31L, 9L, 5L,
                    ActionFeedbackResult.NOT_OCCURRED, null, Instant.now(), Instant.now(), 41L);
            when(repository.findFeedbackByAction(USER_ID, 9L)).thenReturn(Optional.empty(), Optional.of(saved));
            when(repository.insertFeedback(eq(USER_ID), eq(9L), eq(5L), eq(ActionFeedbackResult.NOT_OCCURRED),
                    any(), any())).thenReturn(31L);
            when(repository.insertEvidence(eq(USER_ID), eq(EvidenceSourceType.ACTION_FEEDBACK), eq("feedback_31"),
                    eq(FactLevel.OBSERVED), any(), any())).thenReturn(41L);
            when(repository.findTopicEvidence(USER_ID, 5L)).thenReturn(List.of(
                    clueEvidence(1L), evidenceRow(41L, EvidenceSourceType.ACTION_FEEDBACK,
                            ActionFeedbackResult.NOT_OCCURRED, Instant.now())));
            when(repository.findTopic(USER_ID, 5L, false)).thenReturn(Optional.of(topicRow(5L, 3)));

            FeedbackResultView result = service.submitFeedback(USER_ID, "action_9",
                    new FeedbackRequest("topic_5", ActionFeedbackResult.NOT_OCCURRED, null, null));

            // NOT_OCCURRED is a valid observation: evidence created, action completed,
            // but nothing closes or ends the topic
            assertThat(result.feedback().evidenceId()).isEqualTo("evidence_41");
            assertThat(result.actionStatus()).isEqualTo(ActionStatus.COMPLETED);
            verify(repository).insertEvidence(eq(USER_ID), eq(EvidenceSourceType.ACTION_FEEDBACK), any(),
                    eq(FactLevel.OBSERVED), any(), any());
            verify(repository).updateTopicAfterFeedback(eq(USER_ID), eq(5L),
                    argThat(text -> text != null && text.contains("没有出现")),
                    eq(Maturity.INSUFFICIENT), eq(2), eq(1), eq(1), eq(1));
        }

        @Test
        void tc14UncertainKeepsEvidenceWithoutRaisingMaturity() {
            stubPendingAction();
            stubCurrentTopic(Maturity.CLUE);
            CognitionJdbcRepository.FeedbackRow saved = new CognitionJdbcRepository.FeedbackRow(32L, 9L, 5L,
                    ActionFeedbackResult.UNCERTAIN, null, Instant.now(), Instant.now(), 42L);
            when(repository.findFeedbackByAction(USER_ID, 9L)).thenReturn(Optional.empty(), Optional.of(saved));
            when(repository.insertFeedback(eq(USER_ID), eq(9L), eq(5L), eq(ActionFeedbackResult.UNCERTAIN),
                    any(), any())).thenReturn(32L);
            when(repository.insertEvidence(eq(USER_ID), eq(EvidenceSourceType.ACTION_FEEDBACK), eq("feedback_32"),
                    eq(FactLevel.OBSERVED), any(), any())).thenReturn(42L);
            when(repository.findTopicEvidence(USER_ID, 5L)).thenReturn(List.of(
                    clueEvidence(1L), evidenceRow(42L, EvidenceSourceType.ACTION_FEEDBACK,
                            ActionFeedbackResult.UNCERTAIN, Instant.now())));
            when(repository.findTopic(USER_ID, 5L, false)).thenReturn(Optional.of(topicRow(5L, 3)));

            FeedbackResultView result = service.submitFeedback(USER_ID, "action_9",
                    new FeedbackRequest("topic_5", ActionFeedbackResult.UNCERTAIN, null, null));

            assertThat(result.feedback().evidenceId()).isEqualTo("evidence_42");
            assertThat(result.actionStatus()).isEqualTo(ActionStatus.COMPLETED);
            // UNCERTAIN evidence is saved but excluded from maturity: stays CLUE
            verify(repository).updateTopicAfterFeedback(eq(USER_ID), eq(5L),
                    argThat(text -> text != null && text.contains("不确定")),
                    eq(Maturity.CLUE), eq(2), eq(1), eq(1), eq(1));
        }

        @Test
        void tc15SkippedHasNoEvidenceAndNoCounterIncrease() {
            stubPendingAction();
            stubCurrentTopic(Maturity.CLUE);
            CognitionJdbcRepository.FeedbackRow saved = new CognitionJdbcRepository.FeedbackRow(33L, 9L, 5L,
                    ActionFeedbackResult.SKIPPED, null, Instant.now(), Instant.now(), null);
            when(repository.findFeedbackByAction(USER_ID, 9L)).thenReturn(Optional.empty(), Optional.of(saved));
            when(repository.insertFeedback(eq(USER_ID), eq(9L), eq(5L), eq(ActionFeedbackResult.SKIPPED),
                    any(), any())).thenReturn(33L);
            when(repository.findTopicEvidence(USER_ID, 5L)).thenReturn(List.of(clueEvidence(1L)));
            when(repository.findTopic(USER_ID, 5L, false)).thenReturn(Optional.of(topicRow(5L, 2)));

            FeedbackResultView result = service.submitFeedback(USER_ID, "action_9",
                    new FeedbackRequest("topic_5", ActionFeedbackResult.SKIPPED, null, null));

            assertThat(result.feedback().evidenceId()).isNull();
            assertThat(result.actionStatus()).isEqualTo(ActionStatus.SKIPPED);
            verify(repository, never()).insertEvidence(anyLong(), any(), any(), any(), any(), any());
            verify(repository, never()).linkTopicEvidence(anyLong(), anyLong(), any());
            // stageUnderstanding stays (null keeps current text); counters unchanged
            verify(repository).updateTopicAfterFeedback(eq(USER_ID), eq(5L), isNull(),
                    eq(Maturity.CLUE), eq(1), eq(1), eq(0), eq(1));
            // occurredAt defaults to submission time when the frontend omits it
            ArgumentCaptor<Instant> occurredAt = ArgumentCaptor.forClass(Instant.class);
            verify(repository).insertFeedback(eq(USER_ID), eq(9L), eq(5L), eq(ActionFeedbackResult.SKIPPED),
                    any(), occurredAt.capture());
            assertThat(occurredAt.getValue()).isNotNull();
        }

        @Test
        void feedbackAcrossTwoCyclesRaisesMaturity() {
            stubPendingAction();
            stubCurrentTopic(Maturity.CLUE);
            CognitionJdbcRepository.FeedbackRow saved = new CognitionJdbcRepository.FeedbackRow(34L, 9L, 5L,
                    ActionFeedbackResult.OCCURRED, null, Instant.parse("2026-07-11T04:00:00Z"),
                    Instant.now(), 44L);
            when(repository.findFeedbackByAction(USER_ID, 9L)).thenReturn(Optional.empty(), Optional.of(saved));
            when(repository.insertFeedback(eq(USER_ID), eq(9L), eq(5L), eq(ActionFeedbackResult.OCCURRED),
                    any(), any())).thenReturn(34L);
            when(repository.insertEvidence(eq(USER_ID), eq(EvidenceSourceType.ACTION_FEEDBACK), eq("feedback_34"),
                    eq(FactLevel.SELF_REPORTED), any(), any())).thenReturn(44L);
            when(repository.findTopicEvidence(USER_ID, 5L)).thenReturn(List.of(
                    clueEvidence(1L),
                    evidenceRow(43L, EvidenceSourceType.ACTION_FEEDBACK, ActionFeedbackResult.OCCURRED,
                            Instant.parse("2026-06-10T04:00:00Z")),
                    evidenceRow(44L, EvidenceSourceType.ACTION_FEEDBACK, ActionFeedbackResult.OCCURRED,
                            Instant.parse("2026-07-11T04:00:00Z"))));
            when(repository.findTopic(USER_ID, 5L, false)).thenReturn(Optional.of(topicRow(5L, 3)));

            service.submitFeedback(USER_ID, "action_9",
                    new FeedbackRequest("topic_5", ActionFeedbackResult.OCCURRED, null,
                            Instant.parse("2026-07-11T04:00:00Z")));

            verify(repository).updateTopicAfterFeedback(eq(USER_ID), eq(5L), any(),
                    eq(Maturity.REPEATED_PATTERN), eq(3), eq(1), eq(2), eq(3));
        }

        @Test
        void singleNotOccurredNeverDowngradesMaturity() {
            stubPendingAction();
            stubCurrentTopic(Maturity.REPEATED_PATTERN);
            CognitionJdbcRepository.FeedbackRow saved = new CognitionJdbcRepository.FeedbackRow(35L, 9L, 5L,
                    ActionFeedbackResult.NOT_OCCURRED, null, Instant.now(), Instant.now(), 45L);
            when(repository.findFeedbackByAction(USER_ID, 9L)).thenReturn(Optional.empty(), Optional.of(saved));
            when(repository.insertFeedback(eq(USER_ID), eq(9L), eq(5L), eq(ActionFeedbackResult.NOT_OCCURRED),
                    any(), any())).thenReturn(35L);
            when(repository.insertEvidence(eq(USER_ID), eq(EvidenceSourceType.ACTION_FEEDBACK), eq("feedback_35"),
                    eq(FactLevel.OBSERVED), any(), any())).thenReturn(45L);
            when(repository.findTopicEvidence(USER_ID, 5L)).thenReturn(List.of(
                    clueEvidence(1L), evidenceRow(45L, EvidenceSourceType.ACTION_FEEDBACK,
                            ActionFeedbackResult.NOT_OCCURRED, Instant.now())));
            when(repository.findTopic(USER_ID, 5L, false)).thenReturn(Optional.of(topicRow(5L, 3)));

            service.submitFeedback(USER_ID, "action_9",
                    new FeedbackRequest("topic_5", ActionFeedbackResult.NOT_OCCURRED, null, null));

            verify(repository).updateTopicAfterFeedback(eq(USER_ID), eq(5L), any(),
                    eq(Maturity.REPEATED_PATTERN), eq(2), eq(1), eq(1), eq(1));
        }

        private void stubPendingAction() {
            when(repository.findAction(USER_ID, 9L, true)).thenReturn(Optional.of(
                    new CognitionJdbcRepository.ActionRow(9L, 5L, "记录一次相关身体变化", "补充出现时间和程度",
                            ActionType.RECORD_BODY, ActionStatus.PENDING, null, null, Instant.now())));
        }

        private void stubCurrentTopic(Maturity maturity) {
            when(repository.findTopic(USER_ID, 5L, true)).thenReturn(Optional.of(
                    new CognitionJdbcRepository.TopicRow(5L, 3L, "经前情绪变化", "MOOD", maturity,
                            UserProgress.OBSERVING, RiskStatus.NONE, "阶段理解", "[]", "[]",
                            1, 1, 0, 1, 9L, Instant.now(), 1, Instant.now())));
        }

        private CognitionJdbcRepository.TopicRow topicRow(long id, int version) {
            return new CognitionJdbcRepository.TopicRow(id, 3L, "经前情绪变化", "MOOD", Maturity.CLUE,
                    UserProgress.OBSERVING, RiskStatus.NONE, "阶段理解", "[]", "[]",
                    1, 1, 0, 1, 9L, Instant.now(), version, Instant.now());
        }
    }

    // ---------- fixtures ----------

    private ClueRow clueRow(long id, ClueIntent intent, ClueStatus status) {
        return new ClueRow(id, ClueType.ARTICLE_HIGHLIGHT, intent, RelationType.CURRENT, HelpRequestType.OBSERVE,
                "1024", "文章", 100, "选中文字", null, null, null, CycleRelation.UNKNOWN, null, null,
                ClueSource.KNOWLEDGE_ARTICLE, status, null, "经前情绪变化", "和我有关",
                Instant.now(), Instant.now());
    }

    private DigestRow digestRow(long id, DigestStatus status, int version) {
        return new DigestRow(id, "经前情绪变化", status, "共同点", "可能联系", "仍不确定", "记录一次",
                "fixed-v1", Instant.now(), null, null, version, Instant.now());
    }

    private CognitionJdbcRepository.EvidenceRow evidenceRow(long id, EvidenceSourceType sourceType,
                                                            ActionFeedbackResult feedbackResult, Instant occurredAt) {
        return new CognitionJdbcRepository.EvidenceRow(id, sourceType, "src_" + id, FactLevel.SELF_REPORTED,
                "摘要", occurredAt, Instant.now(), true, null, null, null, feedbackResult);
    }

    private CognitionJdbcRepository.EvidenceRow clueEvidence(long id) {
        return evidenceRow(id, EvidenceSourceType.CLUE, null, Instant.now());
    }
}
