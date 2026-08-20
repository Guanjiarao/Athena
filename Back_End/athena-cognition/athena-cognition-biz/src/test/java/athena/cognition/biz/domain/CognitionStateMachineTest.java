package athena.cognition.biz.domain;

import org.junit.jupiter.api.Test;

import static athena.cognition.biz.domain.CognitionModels.DigestStatus.ACCEPTED;
import static athena.cognition.biz.domain.CognitionModels.DigestStatus.READY;
import static athena.cognition.biz.domain.CognitionModels.UserProgress.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CognitionStateMachineTest {

    @Test
    void onlyReadyDigestCanBeDecided() {
        assertDoesNotThrow(() -> CognitionStateMachine.requireReadyDigest("digest_1", READY));
        assertThrows(CognitionException.class,
                () -> CognitionStateMachine.requireReadyDigest("digest_1", ACCEPTED));
    }

    @Test
    void topicProgressUsesExplicitAllowedTransitions() {
        assertDoesNotThrow(() -> CognitionStateMachine.requireTopicTransition(FOLLOWING, OBSERVING));
        assertDoesNotThrow(() -> CognitionStateMachine.requireTopicTransition(OBSERVING, PAUSED));
        assertDoesNotThrow(() -> CognitionStateMachine.requireTopicTransition(PAUSED, FOLLOWING));
        assertDoesNotThrow(() -> CognitionStateMachine.requireTopicTransition(FOLLOWING, ARCHIVED));
        assertThrows(CognitionException.class, () -> CognitionStateMachine.requireTopicTransition(ARCHIVED, FOLLOWING));
    }
}
