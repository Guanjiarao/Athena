package athena.cognition.biz.service;

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

    private CognitionService service;

    @BeforeEach
    void setUp() {
        service = new CognitionService(repository, generator, new ObjectMapper().findAndRegisterModules());
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
            verify(repository, never()).insertTopic(anyLong(), anyLong(), any(), any(), any(), any(), any(),
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
}
