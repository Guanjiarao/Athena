package com.whu.software.athena.cognition;

import com.whu.software.athena.cognition.CognitionModels.DigestStatus;
import com.whu.software.athena.cognition.CognitionModels.TopicProgress;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Mirrors server rules for demo consistency only. The HTTP backend remains authoritative. */
public final class CognitionStateMachine {

    private static final Map<TopicProgress, Set<TopicProgress>> TOPIC = new HashMap<>();

    static {
        TOPIC.put(TopicProgress.FOLLOWING, EnumSet.of(TopicProgress.OBSERVING, TopicProgress.PAUSED, TopicProgress.ARCHIVED));
        TOPIC.put(TopicProgress.OBSERVING, EnumSet.of(TopicProgress.FOLLOWING, TopicProgress.PAUSED, TopicProgress.ARCHIVED));
        TOPIC.put(TopicProgress.PAUSED, EnumSet.of(TopicProgress.FOLLOWING, TopicProgress.OBSERVING, TopicProgress.ARCHIVED));
        TOPIC.put(TopicProgress.PENDING_CONFIRMATION, EnumSet.of(TopicProgress.FOLLOWING));
        TOPIC.put(TopicProgress.ARCHIVED, EnumSet.noneOf(TopicProgress.class));
    }

    private CognitionStateMachine() {}

    public static boolean canDecide(DigestStatus status) {
        return status == DigestStatus.PENDING_CONFIRMATION;
    }

    public static boolean canMoveTopic(TopicProgress from, TopicProgress to) {
        return from == to || (TOPIC.containsKey(from) && TOPIC.get(from).contains(to));
    }
}
