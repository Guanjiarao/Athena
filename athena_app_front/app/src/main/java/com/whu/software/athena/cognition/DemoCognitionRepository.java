package com.whu.software.athena.cognition;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.whu.software.athena.cognition.CognitionDemoScenario.DemoState;
import com.whu.software.athena.cognition.CognitionModels.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SharedPreferences + Gson demo implementation. It is not a health inference engine. */
public final class DemoCognitionRepository implements CognitionRepository {

    private static final String PREFS = "athena_cognition_demo_v1";
    private static final String STATE = "state";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private DemoState state;

    public DemoCognitionRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        state = load();
    }

    @Override
    public synchronized void createClue(ClueCreateRequest request, Callback<Clue> callback) {
        if (request == null || request.clueType == null) {
            callback.onError("线索内容不完整");
            return;
        }
        if (request.clueType == ClueType.ARTICLE_MARK
                && (request.markIntent == null || blank(request.articleId) || blank(request.excerpt))) {
            callback.onError("文章线索缺少标记或来源");
            return;
        }
        Clue clue = gson.fromJson(gson.toJson(request), Clue.class);
        clue.clueId = state.nextId++;
        clue.status = ClueStatus.PENDING;
        clue.createdAt = now();
        if (clue.occurredAt == null) clue.occurredAt = clue.createdAt;
        state.clues.add(clue);
        save();
        callback.onSuccess(clue);
    }

    @Override
    public synchronized void listClues(ClueSection section, Callback<List<Clue>> callback) {
        List<Clue> result = new ArrayList<>();
        for (Clue clue : state.clues) {
            boolean include;
            if (section == ClueSection.QUESTIONS) {
                include = clue.markIntent == MarkIntent.QUESTION && clue.status != ClueStatus.WITHDRAWN;
            } else if (section == ClueSection.ORGANIZED) {
                include = clue.status == ClueStatus.ORGANIZED || clue.status == ClueStatus.KNOWLEDGE_ONLY;
            } else {
                include = (clue.status == ClueStatus.PENDING || clue.status == ClueStatus.IN_DIGEST)
                        && clue.markIntent != MarkIntent.QUESTION;
            }
            if (include) result.add(clue);
        }
        callback.onSuccess(result);
    }

    @Override
    public synchronized void createDigestTask(List<Long> clueIds, Callback<DigestTask> callback) {
        if (clueIds == null || clueIds.isEmpty() || new HashSet<>(clueIds).size() != clueIds.size()) {
            callback.onError("请选择不重复的待整理线索");
            return;
        }
        List<Clue> clues = new ArrayList<>();
        for (Long id : clueIds) {
            Clue clue = findClue(id == null ? -1 : id);
            if (clue == null || clue.status != ClueStatus.PENDING) {
                callback.onError("部分线索不存在或已经整理");
                return;
            }
            clues.add(clue);
        }

        DigestTask task = new DigestTask();
        task.digestTaskId = state.nextId++;
        task.status = DigestTaskStatus.RUNNING;
        task.generatorType = "FIXED_V1";
        task.attemptCount = 1;
        task.createdAt = now();
        state.tasks.add(task);
        for (Clue clue : clues) clue.status = ClueStatus.IN_DIGEST;

        Digest digest = fixedDigest(task.digestTaskId, clues);
        state.digests.add(digest);
        task.status = DigestTaskStatus.SUCCEEDED;
        task.digestId = digest.digestId;
        task.finishedAt = now();
        save();
        callback.onSuccess(task);
    }

    @Override
    public synchronized void retryDigestTask(long taskId, Callback<DigestTask> callback) {
        DigestTask task = findTask(taskId);
        if (task == null) callback.onError("整理任务不存在");
        else if (task.status != DigestTaskStatus.FAILED) callback.onError("只有失败任务可以重试");
        else callback.onError("演示任务缺少原始输入，请重新选择线索");
    }

    @Override
    public synchronized void getDigest(long digestId, Callback<Digest> callback) {
        Digest digest = findDigest(digestId);
        if (digest == null) callback.onError("整理草稿不存在"); else callback.onSuccess(digest);
    }

    @Override
    public synchronized void listPendingDigests(Callback<List<Digest>> callback) {
        List<Digest> result = new ArrayList<>();
        for (Digest digest : state.digests) {
            if (digest.status == DigestStatus.PENDING_CONFIRMATION) result.add(digest);
        }
        callback.onSuccess(result);
    }

    @Override
    public synchronized void decideDigest(long digestId, DigestDecision decision, String reasonCode,
                                          Callback<DigestDecisionResult> callback) {
        Digest digest = findDigest(digestId);
        if (digest == null) { callback.onError("整理草稿不存在"); return; }
        if (!CognitionStateMachine.canDecide(digest.status)) { callback.onError("这份草稿已经处理"); return; }

        DigestDecisionResult result = new DigestDecisionResult();
        result.digestId = digestId;
        result.decision = decision;
        result.decidedAt = now();
        if (decision == DigestDecision.ACCEPT_TOPIC) {
            digest.status = DigestStatus.ACCEPTED;
            Topic topic = topicFrom(digest);
            state.topics.add(topic);
            result.topicId = topic.topicId;
            result.actionId = topic.actions.get(0).actionId;
            for (Evidence evidence : digest.evidence) {
                Clue clue = findClue(evidence.clueId);
                if (clue != null) clue.status = ClueStatus.ORGANIZED;
            }
        } else if (decision == DigestDecision.SAVE_KNOWLEDGE) {
            digest.status = DigestStatus.SAVED_KNOWLEDGE;
            setDigestClues(digest, ClueStatus.KNOWLEDGE_ONLY);
        } else {
            digest.status = DigestStatus.REJECTED;
            setDigestClues(digest, ClueStatus.REJECTED);
        }
        result.digestStatus = digest.status;
        save();
        callback.onSuccess(result);
    }

    @Override
    public synchronized void listTopics(Callback<List<Topic>> callback) {
        callback.onSuccess(new ArrayList<>(state.topics));
    }

    @Override
    public synchronized void getTopic(long topicId, Callback<Topic> callback) {
        Topic topic = findTopic(topicId);
        if (topic == null) callback.onError("认知主题不存在"); else callback.onSuccess(topic);
    }

    @Override
    public synchronized void updateTopicProgress(long topicId, TopicProgress progress, Callback<Topic> callback) {
        Topic topic = findTopic(topicId);
        if (topic == null) { callback.onError("认知主题不存在"); return; }
        if (!CognitionStateMachine.canMoveTopic(topic.progress, progress)) {
            callback.onError("主题状态不能这样变更");
            return;
        }
        topic.progress = progress;
        appendVersion(topic, "USER_PROGRESS_CHANGED");
        topic.updatedAt = now();
        save();
        callback.onSuccess(topic);
    }

    @Override
    public synchronized void submitFeedback(long actionId, FeedbackAccuracy accuracy, boolean completed, String note,
                                            Callback<Feedback> callback) {
        Action action = findAction(actionId);
        if (action == null) { callback.onError("行动不存在"); return; }
        if (action.status != ActionStatus.PENDING) { callback.onError("这项行动已经反馈过"); return; }
        Feedback feedback = new Feedback();
        feedback.feedbackId = state.nextId++;
        feedback.actionId = actionId;
        feedback.topicId = action.topicId;
        feedback.accuracy = accuracy;
        feedback.completed = completed;
        feedback.note = note;
        feedback.createdAt = now();
        action.status = completed ? ActionStatus.COMPLETED : ActionStatus.SKIPPED;
        action.completedAt = feedback.createdAt;
        Topic topic = findTopic(action.topicId);
        if (topic != null) {
            topic.feedback.add(feedback);
            appendVersion(topic, "ACTION_FEEDBACK");
            topic.updatedAt = now();
        }
        state.feedback.add(feedback);
        save();
        callback.onSuccess(feedback);
    }

    @Override
    public synchronized void getHome(Callback<Home> callback) {
        callback.onSuccess(CognitionHomeMapper.map(state));
    }

    public synchronized void resetDemo() {
        state = CognitionDemoScenario.initialState();
        save();
    }

    public synchronized String exportDemoJson() { return gson.toJson(state); }

    public synchronized void clearDemo() {
        state = new DemoState();
        state.nextId = 100;
        save();
    }

    private Digest fixedDigest(long taskId, List<Clue> clues) {
        Digest digest = new Digest();
        digest.digestId = state.nextId++;
        digest.digestTaskId = taskId;
        digest.status = DigestStatus.PENDING_CONFIRMATION;
        digest.title = "一项值得继续观察的身体线索";
        digest.commonPoint = "你保存的这些内容都在表达想进一步了解或观察。";
        digest.possibleLink = "这些输入可以作为后续观察的起点，但目前只存在初步联系。";
        digest.uncertainty = "仅凭文章标记或疑问，不能确认你出现了相同情况，也不能说明原因或形成诊断。";
        digest.suggestedAction = "接下来 7 天完成一次相关身体记录。";
        digest.generatorType = "FIXED_V1";
        digest.generatorVersion = "fixed-demo-v1.0";
        digest.version = 1;
        digest.createdAt = now();
        for (Clue clue : clues) {
            Evidence evidence = new Evidence();
            evidence.evidenceId = state.nextId++;
            evidence.clueId = clue.clueId;
            evidence.evidenceLevel = clue.clueType == ClueType.BODY_RECORD ? "HIGH" : "LOW";
            evidence.evidenceRole = clue.markIntent == MarkIntent.QUESTION ? "QUESTION_CONTEXT" : "OBSERVATION_CONTEXT";
            digest.evidence.add(evidence);
        }
        return digest;
    }

    private Topic topicFrom(Digest digest) {
        Topic topic = new Topic();
        topic.topicId = state.nextId++;
        topic.sourceDigestId = digest.digestId;
        topic.title = digest.title;
        topic.summary = digest.possibleLink;
        topic.uncertainty = digest.uncertainty;
        topic.maturity = CognitionMaturity.CLUE;
        topic.progress = TopicProgress.FOLLOWING;
        topic.riskStatus = RiskStatus.NONE;
        topic.currentVersion = 0;
        topic.evidence.addAll(digest.evidence);
        topic.createdAt = now();
        topic.updatedAt = topic.createdAt;
        Action action = new Action();
        action.actionId = state.nextId++;
        action.topicId = topic.topicId;
        action.title = "完成一次观察记录";
        action.instruction = digest.suggestedAction;
        action.status = ActionStatus.PENDING;
        action.dueAt = Instant.now().plus(7, ChronoUnit.DAYS).toString();
        topic.actions.add(action);
        appendVersion(topic, "DIGEST_ACCEPTED");
        return topic;
    }

    private void appendVersion(Topic topic, String reason) {
        TopicVersion version = new TopicVersion();
        version.version = ++topic.currentVersion;
        version.summary = topic.summary;
        version.uncertainty = topic.uncertainty;
        version.maturity = topic.maturity;
        version.progress = topic.progress;
        version.riskStatus = topic.riskStatus;
        version.changeReason = reason;
        version.createdAt = now();
        topic.history.add(0, version);
    }

    private void setDigestClues(Digest digest, ClueStatus status) {
        for (Evidence evidence : digest.evidence) {
            Clue clue = findClue(evidence.clueId);
            if (clue != null) clue.status = status;
        }
    }

    private Clue findClue(long id) { for (Clue value : state.clues) if (value.clueId == id) return value; return null; }
    private DigestTask findTask(long id) { for (DigestTask value : state.tasks) if (value.digestTaskId == id) return value; return null; }
    private Digest findDigest(long id) { for (Digest value : state.digests) if (value.digestId == id) return value; return null; }
    private Topic findTopic(long id) { for (Topic value : state.topics) if (value.topicId == id) return value; return null; }
    private Action findAction(long id) { for (Topic topic : state.topics) for (Action value : topic.actions) if (value.actionId == id) return value; return null; }

    private DemoState load() {
        String json = preferences.getString(STATE, null);
        if (json == null) return CognitionDemoScenario.initialState();
        try {
            DemoState loaded = gson.fromJson(json, DemoState.class);
            return loaded == null ? CognitionDemoScenario.initialState() : loaded;
        } catch (RuntimeException ignored) {
            return CognitionDemoScenario.initialState();
        }
    }

    private void save() { preferences.edit().putString(STATE, gson.toJson(state)).apply(); }
    private static String now() { return Instant.now().toString(); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
