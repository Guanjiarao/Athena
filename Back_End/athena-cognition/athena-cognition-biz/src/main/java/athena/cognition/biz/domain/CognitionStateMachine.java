package athena.cognition.biz.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static athena.cognition.biz.domain.CognitionModels.DigestStatus;
import static athena.cognition.biz.domain.CognitionModels.TopicProgress;

public final class CognitionStateMachine {

    private static final Map<TopicProgress, Set<TopicProgress>> TOPIC_TRANSITIONS = Map.of(
            TopicProgress.FOLLOWING, EnumSet.of(TopicProgress.OBSERVING, TopicProgress.PAUSED, TopicProgress.ARCHIVED),
            TopicProgress.OBSERVING, EnumSet.of(TopicProgress.FOLLOWING, TopicProgress.PAUSED, TopicProgress.ARCHIVED),
            TopicProgress.PAUSED, EnumSet.of(TopicProgress.FOLLOWING, TopicProgress.OBSERVING, TopicProgress.ARCHIVED),
            TopicProgress.PENDING_CONFIRMATION, EnumSet.of(TopicProgress.FOLLOWING),
            TopicProgress.ARCHIVED, EnumSet.noneOf(TopicProgress.class)
    );

    private CognitionStateMachine() {
    }

    public static void requirePendingDigest(DigestStatus status) {
        if (status != DigestStatus.PENDING_CONFIRMATION) {
            throw CognitionException.conflict("COGNITION_DIGEST_ALREADY_DECIDED", "该整理草稿已经处理");
        }
    }

    public static void requireTopicTransition(TopicProgress from, TopicProgress to) {
        if (from == to) {
            return;
        }
        if (!TOPIC_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw CognitionException.conflict("COGNITION_INVALID_STATE_TRANSITION", "主题状态不能这样变更");
        }
    }
}
