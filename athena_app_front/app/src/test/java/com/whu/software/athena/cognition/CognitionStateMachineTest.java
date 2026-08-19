package com.whu.software.athena.cognition;

import org.junit.Test;

import static com.whu.software.athena.cognition.CognitionModels.TopicProgress.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CognitionStateMachineTest {
    @Test public void onlyPendingDigestCanBeDecided() {
        assertTrue(CognitionStateMachine.canDecide(CognitionModels.DigestStatus.PENDING_CONFIRMATION));
        assertFalse(CognitionStateMachine.canDecide(CognitionModels.DigestStatus.ACCEPTED));
        assertFalse(CognitionStateMachine.canDecide(CognitionModels.DigestStatus.REJECTED));
    }

    @Test public void archivedTopicCannotBeReopenedImplicitly() {
        assertTrue(CognitionStateMachine.canMoveTopic(FOLLOWING, PAUSED));
        assertTrue(CognitionStateMachine.canMoveTopic(PAUSED, OBSERVING));
        assertFalse(CognitionStateMachine.canMoveTopic(ARCHIVED, FOLLOWING));
    }
}
