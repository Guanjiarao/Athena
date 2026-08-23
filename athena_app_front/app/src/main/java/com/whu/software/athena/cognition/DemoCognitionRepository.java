package com.whu.software.athena.cognition;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.whu.software.athena.cognition.CognitionModels.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit offline showcase using exactly the same DTOs as the HTTP implementation. */
public final class DemoCognitionRepository implements CognitionRepository {
    private static final String PREFS = "athena_cognition_contract_demo";
    private static final String STATE = "state";
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private DemoState state;

    public DemoCognitionRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        state = load();
    }

    @Override public synchronized void createClue(ClueCreateRequest request, Callback<ClueCreateResult> callback) {
        Clue clue = new Clue();
        clue.id = id("clue_"); clue.type = request.type; clue.intent = request.intent;
        clue.relationType = request.relationType; clue.helpRequestType = request.helpRequestType;
        clue.articleId = request.articleId; clue.articleTitle = request.articleTitle; clue.articleType = request.articleType;
        clue.selectedText = request.selectedText; clue.questionType = request.questionType; clue.questionText = request.questionText;
        clue.occurredAt = request.occurredAt; clue.cycleRelation = request.cycleRelation; clue.severity = request.severity;
        clue.resolved = request.resolved; clue.source = request.source; clue.suggestedTopicId = request.suggestedTopicId;
        clue.suggestedTopicTitle = request.suggestedTopicTitle; clue.originalLabel = request.originalLabel;
        clue.status = ClueStatus.PENDING; clue.createdAt = now(); clue.updatedAt = clue.createdAt;
        state.clues.add(0, clue); save();
        ClueCreateResult result = new ClueCreateResult(); result.clue = clue; result.digestTask = new DigestTaskTrigger();
        callback.onSuccess(result);
    }

    @Override public synchronized void deleteClue(String clueId, Callback<String> callback) {
        for (Clue clue : state.clues) {
            if (clue.id.equals(clueId) && clue.status == ClueStatus.PROCESSING) {
                callback.onError("这条线索已经进入整理草稿，不能撤销"); return;
            }
        }
        state.clues.removeIf(item -> item.id.equals(clueId)); save(); callback.onSuccess(clueId);
    }

    @Override public synchronized void getInbox(Callback<Inbox> callback) {
        Inbox inbox = new Inbox();
        for (Clue clue : state.clues) {
            if (clue.intent == ClueIntent.QUESTION) inbox.questions.add(clue);
            else if (clue.intent == ClueIntent.KNOWLEDGE_ONLY) inbox.knowledgeClues.add(clue);
            else if (clue.status == ClueStatus.PENDING) inbox.pendingClues.add(clue);
        }
        inbox.topics.addAll(state.topics); inbox.knowledgeDigests.addAll(state.knowledgeDigests);
        inbox.activeDigest = firstReadyDigest(); inbox.counts.pending = inbox.pendingClues.size();
        inbox.counts.organizedTopics = inbox.topics.size(); inbox.counts.organizedKnowledge = inbox.knowledgeClues.size() + inbox.knowledgeDigests.size();
        inbox.counts.questions = inbox.questions.size(); callback.onSuccess(inbox);
    }

    @Override public synchronized void listClues(ClueListView view, int page, int pageSize, Callback<Page<Clue>> callback) {
        List<Clue> matches = new ArrayList<>();
        for (Clue clue : state.clues) {
            boolean include = view == ClueListView.ALL
                    || view == ClueListView.PENDING && clue.intent == ClueIntent.RELATED && clue.status == ClueStatus.PENDING
                    || view == ClueListView.QUESTIONS && clue.intent == ClueIntent.QUESTION
                    || view == ClueListView.ORGANIZED && clue.status == ClueStatus.ORGANIZED;
            if (include) matches.add(clue);
        }
        callback.onSuccess(page(matches, page, pageSize));
    }

    @Override public synchronized void createDigestTask(List<String> clueIds, Callback<DigestTask> callback) {
        if (clueIds == null || clueIds.isEmpty()) { callback.onError("还没有可整理的相关线索，请先标记“和我有关”"); return; }
        Digest digest = new Digest(); digest.id = id("digest_"); digest.title = "近期身体线索整理";
        digest.status = DigestStatus.READY; digest.commonPoint = "这些线索都来自你主动保存的内容，可能值得放在一起继续观察。";
        digest.possibleRelation = "它们可能在时间或感受上有关联，但当前证据不足以确认因果。";
        digest.uncertainty = "还需要更多连续记录，才能判断这种变化是否重复出现。";
        digest.suggestedAction = "接下来记录一次当天的身体感受和发生时间。";
        digest.sourceClueIds.addAll(clueIds); digest.generatorVersion = "fixed-v1"; digest.generatedAt = now(); digest.version = 1;
        for (Clue clue : state.clues) if (clueIds.contains(clue.id)) { clue.status = ClueStatus.PROCESSING; digest.evidence.add(evidence(clue)); }
        state.digests.add(0, digest); save();
        DigestTask task = new DigestTask(); task.taskId = id("task_"); task.digestId = digest.id;
        task.status = DigestTaskStatus.SUCCEEDED; task.digestStatus = DigestStatus.READY; task.triggerType = TriggerType.USER_REQUEST;
        callback.onSuccess(task);
    }

    @Override public void retryDigestTask(String taskId, Callback<DigestTask> callback) { callback.onError("演示数据中没有失败任务"); }

    @Override public void getDigestTask(String taskId, Callback<DigestTask> callback) { callback.onError("演示数据中的整理任务已同步完成"); }

    @Override public synchronized void getDigest(String digestId, Callback<Digest> callback) {
        Digest value = findDigest(digestId); if (value == null) callback.onError("这份草稿不存在或已被删除"); else callback.onSuccess(value);
    }

    @Override public synchronized void listReadyDigests(int page, int pageSize, Callback<Page<Digest>> callback) {
        List<Digest> values = new ArrayList<>(); for (Digest item : state.digests) if (item.status == DigestStatus.READY) values.add(item);
        callback.onSuccess(page(values, page, pageSize));
    }

    @Override public synchronized void decideDigest(String digestId, DigestDecision decision, String reason, int clientVersion, Callback<DigestDecisionResult> callback) {
        Digest digest = findDigest(digestId); if (digest == null) { callback.onError("这份草稿不存在或已被删除"); return; }
        if (digest.status != DigestStatus.READY || digest.version != clientVersion) { callback.onError("内容已更新，请刷新后重试"); return; }
        DigestDecisionResult result = new DigestDecisionResult(); result.digest = new DecidedDigest(); result.digest.id = digest.id; result.digest.version = ++digest.version;
        if (decision == DigestDecision.ACCEPT_AS_TOPIC) {
            digest.status = DigestStatus.ACCEPTED; Topic topic = new Topic(); topic.id = id("topic_"); topic.sourceDigestId = digest.id;
            topic.title = digest.title; topic.domain = TopicDomain.OTHER; topic.maturity = Maturity.EARLY_LINK; topic.userProgress = UserProgress.OBSERVING;
            topic.riskStatus = RiskStatus.NONE; topic.stageUnderstanding = digest.commonPoint; topic.evidenceCount = digest.evidence.size(); topic.version = 1; topic.lastUpdatedAt = now();
            Action action = new Action(); action.id = id("action_"); action.topicId = topic.id; action.title = "完成一次身体记录"; action.description = digest.suggestedAction;
            action.actionType = ActionType.RECORD_BODY; action.status = ActionStatus.PENDING; action.feedbackOptions.add(ActionFeedbackResult.OCCURRED);
            action.feedbackOptions.add(ActionFeedbackResult.NOT_OCCURRED); action.feedbackOptions.add(ActionFeedbackResult.UNCERTAIN); action.feedbackOptions.add(ActionFeedbackResult.SKIPPED);
            topic.nextActionId = action.id; state.topics.add(0, topic); state.actions.add(0, action); result.topic = topic; result.action = action;
            for (Clue clue : state.clues) if (digest.sourceClueIds.contains(clue.id)) clue.status = ClueStatus.ORGANIZED;
        } else if (decision == DigestDecision.KEEP_AS_KNOWLEDGE) { digest.status = DigestStatus.KEPT_AS_KNOWLEDGE; state.knowledgeDigests.add(digest); }
        else digest.status = DigestStatus.REJECTED;
        result.digest.status = digest.status; save(); callback.onSuccess(result);
    }

    @Override public synchronized void listTopics(int page, int pageSize, Callback<Page<Topic>> callback) { callback.onSuccess(page(state.topics, page, pageSize)); }

    @Override public synchronized void getTopic(String topicId, Callback<TopicDetail> callback) {
        Topic topic = findTopic(topicId); if (topic == null) { callback.onError("这个主题不存在或已被删除"); return; }
        TopicDetail detail = new TopicDetail(); detail.topic = topic; Digest digest = findDigest(topic.sourceDigestId);
        if (digest != null) { detail.evidence.addAll(digest.evidence); detail.sourceDigest = new SourceDigestRef(); detail.sourceDigest.id = digest.id; detail.sourceDigest.possibleRelation = digest.possibleRelation; }
        detail.nextAction = findAction(topic.nextActionId); detail.recentFeedback.addAll(state.feedback); detail.recentChange = "由你确认的整理草稿形成"; callback.onSuccess(detail);
    }

    @Override public synchronized void submitFeedback(String actionId, String topicId, ActionFeedbackResult result, String note, String occurredAt, Callback<FeedbackResult> callback) {
        Action action = findAction(actionId); if (action == null) { callback.onError("这个行动不存在或已被删除"); return; }
        if (action.status != ActionStatus.PENDING) { callback.onError("这个行动已经反馈，请刷新查看"); return; }
        Feedback feedback = new Feedback(); feedback.id = id("feedback_"); feedback.actionId = actionId; feedback.topicId = topicId;
        feedback.result = result; feedback.note = note; feedback.occurredAt = occurredAt == null ? now() : occurredAt; feedback.createdAt = now();
        if (result != ActionFeedbackResult.SKIPPED) feedback.evidenceId = id("evidence_");
        action.status = result == ActionFeedbackResult.SKIPPED ? ActionStatus.SKIPPED : ActionStatus.COMPLETED; state.feedback.add(0, feedback);
        Topic topic = findTopic(topicId); if (topic != null) { topic.version++; topic.lastUpdatedAt = now(); }
        save(); FeedbackResult value = new FeedbackResult(); value.feedback = feedback; value.actionStatus = action.status; value.topicVersion = topic == null ? 0 : topic.version; value.refreshRequired = true; callback.onSuccess(value);
    }

    @Override public synchronized void getHome(Callback<Home> callback) {
        Home home = new Home(); home.asOf = now(); home.failedTaskCount = 0; int pending = 0; for (Digest d : state.digests) if (d.status == DigestStatus.READY) pending++;
        home.pendingDigestCount = pending; Topic topic = state.topics.isEmpty() ? null : state.topics.get(0);
        if (pending > 0) { home.summaryState = HomeSummaryState.DIGEST_READY; home.headline = "有一份整理草稿等待你确认"; }
        else if (topic != null) { home.summaryState = HomeSummaryState.OBSERVING; home.headline = "继续观察你的身体线索"; home.activeTopic = toHomeTopic(topic); home.nextAction = findAction(topic.nextActionId); }
        else if (!state.clues.isEmpty()) { home.summaryState = HomeSummaryState.BUILDING_BASELINE; home.headline = "正在积累你的身体线索"; }
        else { home.summaryState = HomeSummaryState.EMPTY; home.headline = "从一条身体线索开始"; }
        callback.onSuccess(home);
    }

    public synchronized void resetDemo() { state = seededState(); save(); }
    public synchronized void clearDemo() { state = new DemoState(); save(); }
    public synchronized String exportDemoJson() { return gson.toJson(state); }

    private DemoState load() {
        String value = preferences.getString(STATE, null);
        return value == null ? seededState() : gson.fromJson(value, DemoState.class);
    }
    private void save() { preferences.edit().putString(STATE, gson.toJson(state)).apply(); }
    private String id(String prefix) { return prefix + state.nextId++; }
    private static String now() { return Instant.now().toString(); }
    private Digest findDigest(String id) { for (Digest v : state.digests) if (v.id.equals(id)) return v; return null; }
    private Topic findTopic(String id) { for (Topic v : state.topics) if (v.id.equals(id)) return v; return null; }
    private Action findAction(String id) { if (id != null) for (Action v : state.actions) if (v.id.equals(id)) return v; return null; }
    private Digest firstReadyDigest() { for (Digest v : state.digests) if (v.status == DigestStatus.READY) return v; return null; }
    private Evidence evidence(Clue clue) { Evidence e = new Evidence(); e.id = id("evidence_"); e.sourceType = EvidenceSourceType.CLUE; e.sourceId = clue.id; e.factLevel = clue.intent == ClueIntent.QUESTION ? FactLevel.QUESTION : FactLevel.SELF_REPORTED; e.summary = clue.selectedText; e.articleId = clue.articleId; e.articleTitle = clue.articleTitle; e.articleType = clue.articleType; e.active = true; return e; }
    private HomeTopic toHomeTopic(Topic topic) { HomeTopic h = new HomeTopic(); h.id = topic.id; h.title = topic.title; h.maturity = topic.maturity; h.userProgress = topic.userProgress; h.riskStatus = topic.riskStatus; h.evidenceCount = topic.evidenceCount; h.cycleCount = topic.cycleCount; h.nextActionId = topic.nextActionId; return h; }
    private static <T> Page<T> page(List<T> all, int page, int size) { Page<T> out = new Page<>(); out.total = all.size(); int from = Math.max(0, (page - 1) * size); int to = Math.min(all.size(), from + size); if (from < to) out.data.addAll(all.subList(from, to)); return out; }

    private DemoState seededState() {
        DemoState demo = new DemoState();
        String timestamp = now();

        Clue sleep = demoClue("clue_demo_sleep", ClueIntent.RELATED, ClueStatus.PENDING,
                "最近几天入睡时间变晚，第二天更容易疲惫。", "睡眠与身体节律", timestamp);
        Clue mood = demoClue("clue_demo_mood", ClueIntent.RELATED, ClueStatus.PENDING,
                "下午精神容易下降，但休息后会有所缓解。", "理解疲劳与情绪变化", timestamp);
        Clue question = demoClue("clue_demo_question", ClueIntent.QUESTION, ClueStatus.PENDING,
                "偶尔睡不好是常见情况吗？", "如何改善睡眠", timestamp);
        question.type = ClueType.USER_QUESTION;
        question.questionType = QuestionType.IS_COMMON;
        question.questionText = question.selectedText;
        Clue knowledge = demoClue("clue_demo_knowledge", ClueIntent.KNOWLEDGE_ONLY, ClueStatus.ORGANIZED,
                "规律作息和减少睡前刺激有助于建立稳定睡眠节律。", "睡眠自我照护", timestamp);
        knowledge.relationType = RelationType.KNOWLEDGE_ONLY;
        demo.clues.add(sleep); demo.clues.add(mood); demo.clues.add(question); demo.clues.add(knowledge);

        Digest ready = new Digest();
        ready.id = "digest_demo_ready"; ready.title = "睡眠与白天精力的近期线索"; ready.status = DigestStatus.READY;
        ready.commonPoint = "两条记录都提到了休息状态与白天精力变化。";
        ready.possibleRelation = "作息变化可能与白天疲惫同时出现，但目前只能说明时间上有关联。";
        ready.uncertainty = "记录次数还少，也缺少连续的入睡时间和清醒感受。";
        ready.suggestedAction = "连续三天记录入睡时间和第二天上午的精神状态。";
        ready.generatorVersion = "fixed-v1"; ready.generatedAt = timestamp; ready.version = 1;
        ready.sourceClueIds.add(sleep.id); ready.sourceClueIds.add(mood.id);
        ready.evidence.add(demoEvidence("evidence_demo_sleep", sleep));
        ready.evidence.add(demoEvidence("evidence_demo_mood", mood));
        demo.digests.add(ready);

        Digest accepted = new Digest();
        accepted.id = "digest_demo_accepted"; accepted.title = "经期前后的睡眠变化"; accepted.status = DigestStatus.ACCEPTED;
        accepted.commonPoint = "已有多次记录显示，经期前几天更容易晚睡或夜间醒来。";
        accepted.possibleRelation = "这种变化可能与周期阶段同时出现，但还不能确认稳定规律。";
        accepted.uncertainty = "仍需要覆盖更多周期并排除压力和作息变化。";
        accepted.suggestedAction = "下一周期继续记录睡眠和周期阶段。";
        accepted.generatorVersion = "fixed-v1"; accepted.generatedAt = timestamp; accepted.version = 2;
        accepted.evidence.add(demoEvidence("evidence_demo_cycle", sleep));
        demo.digests.add(accepted);

        Topic topic = new Topic();
        topic.id = "topic_demo_cycle_sleep"; topic.sourceDigestId = accepted.id; topic.title = "周期与睡眠变化";
        topic.domain = TopicDomain.CYCLE; topic.maturity = Maturity.EARLY_LINK; topic.userProgress = UserProgress.OBSERVING;
        topic.riskStatus = RiskStatus.NONE; topic.stageUnderstanding = accepted.commonPoint;
        topic.knownFacts.add("你记录过几次经期前睡眠变浅"); topic.openQuestions.add("这种变化是否会在下一个周期再次出现？");
        topic.evidenceIds.add("evidence_demo_cycle"); topic.evidenceCount = 1; topic.articleClueCount = 1; topic.cycleCount = 1;
        topic.nextActionId = "action_demo_sleep_record"; topic.lastUpdatedAt = timestamp; topic.version = 1;
        demo.topics.add(topic);

        Action action = new Action();
        action.id = topic.nextActionId; action.topicId = topic.id; action.title = "记录今晚的睡眠";
        action.description = "明早补充入睡时间、夜间是否醒来和醒来后的精神状态。";
        action.actionType = ActionType.RECORD_SLEEP; action.status = ActionStatus.PENDING; action.createdAt = timestamp;
        action.feedbackOptions.add(ActionFeedbackResult.OCCURRED); action.feedbackOptions.add(ActionFeedbackResult.NOT_OCCURRED);
        action.feedbackOptions.add(ActionFeedbackResult.UNCERTAIN); action.feedbackOptions.add(ActionFeedbackResult.SKIPPED);
        demo.actions.add(action);
        demo.nextId = 2001;
        return demo;
    }

    private static Clue demoClue(String id, ClueIntent intent, ClueStatus status,
                                 String selectedText, String articleTitle, String timestamp) {
        Clue clue = new Clue(); clue.id = id; clue.type = ClueType.ARTICLE_HIGHLIGHT; clue.intent = intent;
        clue.relationType = intent == ClueIntent.KNOWLEDGE_ONLY ? RelationType.KNOWLEDGE_ONLY : RelationType.OBSERVE;
        clue.helpRequestType = intent == ClueIntent.KNOWLEDGE_ONLY ? HelpRequestType.SAVE_ONLY : HelpRequestType.OBSERVE;
        clue.articleId = "demo_article_sleep"; clue.articleTitle = articleTitle; clue.articleType = 100;
        clue.selectedText = selectedText; clue.occurredAt = timestamp; clue.cycleRelation = CycleRelation.UNKNOWN;
        clue.source = ClueSource.KNOWLEDGE_ARTICLE; clue.status = status; clue.suggestedTopicTitle = "睡眠与精力";
        clue.originalLabel = intent == ClueIntent.QUESTION ? "我有疑问" : intent == ClueIntent.KNOWLEDGE_ONLY ? "保存为知识" : "和我有关";
        clue.createdAt = timestamp; clue.updatedAt = timestamp; return clue;
    }

    private static Evidence demoEvidence(String id, Clue clue) {
        Evidence evidence = new Evidence(); evidence.id = id; evidence.sourceType = EvidenceSourceType.CLUE;
        evidence.sourceId = clue.id; evidence.factLevel = FactLevel.SELF_REPORTED; evidence.summary = clue.selectedText;
        evidence.occurredAt = clue.occurredAt; evidence.linkedAt = clue.createdAt; evidence.active = true;
        evidence.articleId = clue.articleId; evidence.articleTitle = clue.articleTitle; evidence.articleType = clue.articleType;
        return evidence;
    }

    private static class DemoState {
        long nextId = 1001;
        List<Clue> clues = new ArrayList<>(); List<Digest> digests = new ArrayList<>(); List<Digest> knowledgeDigests = new ArrayList<>();
        List<Topic> topics = new ArrayList<>(); List<Action> actions = new ArrayList<>(); List<Feedback> feedback = new ArrayList<>();
    }
}
