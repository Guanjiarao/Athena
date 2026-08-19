package athena.cognition.biz.service;

import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionModels.*;
import athena.cognition.biz.generator.DigestGenerator;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.repository.CognitionJdbcRepository.DigestRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CognitionServiceTest {

    @Mock
    private CognitionJdbcRepository repository;
    @Mock
    private DigestGenerator generator;

    private CognitionService service;

    @BeforeEach
    void setUp() {
        service = new CognitionService(repository, generator, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void savingKnowledgeNeverCreatesTopicOrAction() {
        long userId = 7L;
        long digestId = 9L;
        DigestRow digest = digest(digestId, DigestStatus.PENDING_CONFIRMATION);
        when(repository.findIdempotency(eq(userId), anyString(), eq("key-1"))).thenReturn(Optional.empty());
        when(repository.findDigest(userId, digestId, true)).thenReturn(Optional.of(digest));

        DigestDecisionView result = service.decideDigest(userId, digestId, "key-1",
                new DigestDecisionRequest(DigestDecision.SAVE_KNOWLEDGE, null));

        assertThat(result.topicId()).isNull();
        assertThat(result.actionId()).isNull();
        assertThat(result.digestStatus()).isEqualTo(DigestStatus.SAVED_KNOWLEDGE);
        verify(repository).saveDigestAsKnowledge(userId, digest, null);
        verify(repository, never()).acceptDigest(anyLong(), any());
    }

    @Test
    void decidedDigestCannotBeDecidedAgainWithAnotherKey() {
        long userId = 7L;
        long digestId = 9L;
        when(repository.findIdempotency(eq(userId), anyString(), eq("another-key"))).thenReturn(Optional.empty());
        when(repository.findDigest(userId, digestId, true)).thenReturn(Optional.of(digest(digestId, DigestStatus.ACCEPTED)));

        assertThrows(CognitionException.class, () -> service.decideDigest(userId, digestId, "another-key",
                new DigestDecisionRequest(DigestDecision.REJECT, null)));

        verify(repository, never()).rejectDigest(anyLong(), any(), any());
    }

    @Test
    void digestTaskRejectsClueIdsNotOwnedByCurrentUser() {
        long userId = 7L;
        when(repository.findIdempotency(eq(userId), anyString(), eq("key-2"))).thenReturn(Optional.empty());
        when(repository.findClues(userId, List.of(1L, 2L))).thenReturn(List.of(clue(1L)));

        assertThrows(CognitionException.class, () -> service.createDigestTask(userId, "key-2",
                new DigestTaskCreateRequest(List.of(1L, 2L))));

        verify(repository, never()).insertTask(anyLong());
    }

    private DigestRow digest(long id, DigestStatus status) {
        return new DigestRow(id, 4, status, "主题", "共同点", "可能联系", "仍不确定", "记录一次",
                GeneratorType.FIXED_V1, "fixed-v1.0", 1, Instant.now(), null);
    }

    private ClueView clue(long id) {
        return new ClueView(id, ClueType.ARTICLE_MARK, MarkIntent.RELATED, RelationDetail.UNCERTAIN_OBSERVE,
                "OBSERVE", "a-1", "文章", "reviewed", "摘录", null, null, null,
                Instant.now(), ClueStatus.PENDING, Instant.now());
    }
}
