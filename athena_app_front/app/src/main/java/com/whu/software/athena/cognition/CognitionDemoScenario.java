package com.whu.software.athena.cognition;

import com.whu.software.athena.cognition.CognitionModels.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Deterministic demo seed. Seed clues are presentation data, never a formal generation threshold. */
public final class CognitionDemoScenario {

    private CognitionDemoScenario() {}

    public static DemoState initialState() {
        DemoState state = new DemoState();
        state.nextId = 100;
        state.clues.add(articleClue(state.nextId++, MarkIntent.RELATED,
                "为什么经期前情绪容易变化", "激素变化可能影响情绪，但每个人的体验不同。", null));
        state.clues.add(articleClue(state.nextId++, MarkIntent.QUESTION,
                "睡眠与情绪记录指南", "连续记录有助于观察时间上的联系。", "这和周期有关系吗？"));
        Clue confirmed = bodyClue(state.nextId++);
        state.clues.add(confirmed);
        state.topics.add(sampleTopic(state, confirmed, false));
        state.topics.add(sampleTopic(state, confirmed, true));
        return state;
    }

    private static Clue bodyClue(long id) {
        Clue clue = new Clue();
        clue.clueId = id;
        clue.clueType = ClueType.BODY_RECORD;
        clue.relationDetail = RelationDetail.CURRENT;
        clue.desiredHelp = "OBSERVE";
        clue.excerpt = "昨晚睡眠约 6 小时，下午感到疲惫，情绪比平时更容易波动。";
        clue.status = ClueStatus.ORGANIZED;
        clue.occurredAt = Instant.now().minusSeconds(86400).toString();
        clue.createdAt = clue.occurredAt;
        return clue;
    }

    private static Topic sampleTopic(DemoState state, Clue clue, boolean archived) {
        Topic topic = new Topic();
        topic.topicId = state.nextId++;
        topic.title = archived ? "周期规律" : "睡眠与情绪变化";
        topic.summary = archived
                ? "最近的周期记录仍在个人常见范围内，目前没有需要特别处理的变化。"
                : "较短睡眠后的情绪变化值得继续观察，但目前只有一次确认记录。";
        topic.uncertainty = archived
                ? "记录周期数量仍有限，暂时不能称为稳定规律。"
                : "一次记录不能说明睡眠是情绪变化的原因，也不能排除其他影响。";
        topic.maturity = archived ? CognitionMaturity.INSUFFICIENT : CognitionMaturity.EARLY_LINK;
        topic.progress = archived ? TopicProgress.ARCHIVED : TopicProgress.OBSERVING;
        topic.riskStatus = RiskStatus.NONE;
        Evidence evidence = new Evidence();
        evidence.evidenceId = state.nextId++;
        evidence.clueId = clue.clueId;
        evidence.evidenceLevel = "HIGH";
        evidence.evidenceRole = "CONFIRMED_BODY_RECORD";
        topic.evidence.add(evidence);
        if (!archived) {
            Action action = new Action();
            action.actionId = state.nextId++;
            action.topicId = topic.topicId;
            action.title = "记录一次睡眠和情绪";
            action.instruction = "今晚记录睡眠时长，并在明天下午选择一次情绪状态。";
            action.status = ActionStatus.PENDING;
            action.dueAt = Instant.now().plusSeconds(7 * 86400).toString();
            topic.actions.add(action);
        }
        TopicVersion first = new TopicVersion();
        first.version = 1;
        first.summary = topic.summary;
        first.uncertainty = topic.uncertainty;
        first.maturity = CognitionMaturity.CLUE;
        first.progress = archived ? TopicProgress.FOLLOWING : TopicProgress.FOLLOWING;
        first.riskStatus = RiskStatus.NONE;
        first.changeReason = "DIGEST_ACCEPTED";
        first.createdAt = Instant.now().minusSeconds(172800).toString();
        topic.history.add(first);
        TopicVersion current = new TopicVersion();
        current.version = 2;
        current.summary = topic.summary;
        current.uncertainty = topic.uncertainty;
        current.maturity = topic.maturity;
        current.progress = topic.progress;
        current.riskStatus = topic.riskStatus;
        current.changeReason = archived ? "USER_PROGRESS_CHANGED" : "ACTION_FEEDBACK";
        current.createdAt = Instant.now().minusSeconds(86400).toString();
        topic.history.add(0, current);
        topic.currentVersion = 2;
        topic.createdAt = first.createdAt;
        topic.updatedAt = current.createdAt;
        return topic;
    }

    private static Clue articleClue(long id, MarkIntent intent, String title, String excerpt, String question) {
        Clue clue = new Clue();
        clue.clueId = id;
        clue.clueType = ClueType.ARTICLE_MARK;
        clue.markIntent = intent;
        clue.relationDetail = RelationDetail.UNCERTAIN_OBSERVE;
        clue.desiredHelp = "OBSERVE";
        clue.articleId = "demo-" + id;
        clue.articleTitle = title;
        clue.sourceName = "Athena 审核内容";
        clue.excerpt = excerpt;
        clue.questionText = question;
        clue.questionType = question == null ? null : "POSSIBLE_CAUSES";
        clue.status = ClueStatus.PENDING;
        clue.occurredAt = Instant.now().toString();
        clue.createdAt = clue.occurredAt;
        return clue;
    }

    public static class DemoState {
        public long nextId;
        public List<Clue> clues = new ArrayList<>();
        public List<DigestTask> tasks = new ArrayList<>();
        public List<Digest> digests = new ArrayList<>();
        public List<Topic> topics = new ArrayList<>();
        public List<Feedback> feedback = new ArrayList<>();
    }
}
