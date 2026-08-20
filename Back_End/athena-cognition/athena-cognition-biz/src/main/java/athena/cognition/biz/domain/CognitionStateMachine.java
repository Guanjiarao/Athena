package athena.cognition.biz.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static athena.cognition.biz.domain.CognitionModels.DigestStatus;
import static athena.cognition.biz.domain.CognitionModels.UserProgress;

/**
 * Formal state transitions (contract section 7).
 */
public final class CognitionStateMachine {

    private static final Map<UserProgress, Set<UserProgress>> TOPIC_TRANSITIONS = Map.of(
            UserProgress.FOLLOWING, EnumSet.of(UserProgress.OBSERVING, UserProgress.PAUSED, UserProgress.ARCHIVED),
            UserProgress.OBSERVING, EnumSet.of(UserProgress.FOLLOWING, UserProgress.PAUSED, UserProgress.ARCHIVED),
            UserProgress.PAUSED, EnumSet.of(UserProgress.FOLLOWING, UserProgress.OBSERVING, UserProgress.ARCHIVED),
            UserProgress.PENDING_CONFIRMATION, EnumSet.of(UserProgress.FOLLOWING),
            UserProgress.ARCHIVED, EnumSet.noneOf(UserProgress.class)
    );

    private CognitionStateMachine() {
    }

    /** Section 7.2: only a READY digest can be decided, and only once. */
    public static void requireReadyDigest(String digestId, DigestStatus status) {
        if (status != DigestStatus.READY) {
            throw CognitionException.stateConflict("草稿已经处理，请刷新后重试", digestId, status.name());
        }
    }

    public static void requireTopicTransition(UserProgress from, UserProgress to) {
        if (from == to) {
            return;
        }
        if (!TOPIC_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw CognitionException.stateConflict("主题状态不能这样变更", null, from.name());
        }
    }
}
