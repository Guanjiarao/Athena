package com.whu.software.athena.cognition;

import com.whu.software.athena.cognition.CognitionDemoScenario.DemoState;
import com.whu.software.athena.cognition.CognitionModels.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CognitionHomeMapperTest {
    @Test public void emptyStateIsCalmWithoutInventingInsight() {
        Home home = CognitionHomeMapper.map(new DemoState());
        assertEquals(HomeMode.CALM, home.mode);
        assertNull(home.primaryTopic);
    }

    @Test public void pendingDigestIsObservationNotRiskAlert() {
        DemoState state = new DemoState();
        Digest digest = new Digest();
        digest.status = DigestStatus.PENDING_CONFIRMATION;
        state.digests.add(digest);
        Home home = CognitionHomeMapper.map(state);
        assertEquals(HomeMode.OBSERVE, home.mode);
        assertEquals(1, home.pendingDigestCount);
    }
}
