package com.whu.software.athena.cognition;

import com.whu.software.athena.cognition.CognitionDemoScenario.DemoState;
import com.whu.software.athena.cognition.CognitionModels.*;

import java.time.Instant;

/** Selects exactly one priority for the local demo, using the same ordering as the server contract. */
public final class CognitionHomeMapper {

    private CognitionHomeMapper() {}

    public static Home map(DemoState state) {
        Home home = new Home();
        home.generatedAt = Instant.now().toString();
        for (Digest digest : state.digests) {
            if (digest.status == DigestStatus.PENDING_CONFIRMATION) home.pendingDigestCount++;
        }
        for (DigestTask task : state.tasks) {
            if (task.status == DigestTaskStatus.FAILED) home.failedTaskCount++;
        }

        for (Topic topic : state.topics) {
            if (topic.progress == TopicProgress.FOLLOWING || topic.progress == TopicProgress.OBSERVING) {
                if (home.primaryTopic == null || riskPriority(topic.riskStatus) < riskPriority(home.primaryTopic.riskStatus)) {
                    home.primaryTopic = topic;
                }
            }
        }
        if (home.primaryTopic != null) {
            for (Action action : home.primaryTopic.actions) {
                if (action.status == ActionStatus.PENDING) {
                    home.nextAction = action;
                    break;
                }
            }
        }

        if (home.primaryTopic != null && home.primaryTopic.riskStatus != RiskStatus.NONE) {
            home.mode = HomeMode.NOTICE;
            home.headline = "有一项变化值得继续留意";
            home.summary = home.primaryTopic.summary;
        } else if (home.primaryTopic != null || home.pendingDigestCount > 0) {
            home.mode = HomeMode.OBSERVE;
            home.headline = home.primaryTopic == null
                    ? "有一份整理草稿等待确认"
                    : "正在观察：" + home.primaryTopic.title;
            home.summary = home.primaryTopic == null
                    ? "确认之前，它不会成为你的身体结论。"
                    : home.primaryTopic.summary;
        } else {
            home.mode = HomeMode.CALM;
            home.headline = "今天没有需要特别处理的变化";
            home.summary = "你可以继续按自己的节奏记录身体变化。";
        }
        return home;
    }

    private static int riskPriority(RiskStatus status) {
        if (status == RiskStatus.PROFESSIONAL_HELP) return 1;
        if (status == RiskStatus.WATCH) return 2;
        return 3;
    }
}
