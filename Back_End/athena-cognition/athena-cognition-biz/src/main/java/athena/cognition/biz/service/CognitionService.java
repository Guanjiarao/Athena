package athena.cognition.biz.service;

import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionModels.*;
import athena.cognition.biz.domain.CognitionStateMachine;
import athena.cognition.biz.generator.DigestGenerator;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.repository.CognitionJdbcRepository.DigestRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.IdempotencyRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.TaskRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.TopicRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;

@Service
public class CognitionService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CognitionJdbcRepository repository;
    private final DigestGenerator generator;
    private final ObjectMapper objectMapper;

    public CognitionService(CognitionJdbcRepository repository, DigestGenerator generator, ObjectMapper objectMapper) {
        this.repository = repository;
        this.generator = generator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClueView createClue(long userId, String key, ClueCreateRequest request) {
        validateClue(request);
        String hash = hash(request);
        ClueView previous = replay(userId, "CREATE_CLUE", key, hash,
                id -> repository.findClue(userId, id).orElseThrow(CognitionException::notFound));
        if (previous != null) return previous;

        long id = repository.insertClue(userId, request);
        repository.insertIdempotency(userId, "CREATE_CLUE", requireKey(key), hash, "CLUE", id);
        return repository.findClue(userId, id).orElseThrow(CognitionException::notFound);
    }

    public CursorPage<ClueView> listClues(long userId, ClueSection section, Long cursor, int limit) {
        List<ClueView> items = repository.listClues(userId, section, cursor, pageSize(limit) + 1);
        return page(items, ClueView::clueId, limit);
    }

    @Transactional
    public DigestTaskView createDigestTask(long userId, String key, DigestTaskCreateRequest request) {
        List<Long> clueIds = distinctClueIds(request.clueIds());
        String hash = hash(request);
        DigestTaskView previous = replay(userId, "CREATE_DIGEST_TASK", key, hash, id -> getTask(userId, id));
        if (previous != null) return previous;

        List<ClueView> clues = repository.findClues(userId, clueIds);
        if (clues.size() != clueIds.size()) throw CognitionException.notFound();
        if (clues.stream().anyMatch(clue -> clue.status() != ClueStatus.PENDING)) {
            throw CognitionException.conflict("COGNITION_INVALID_STATE_TRANSITION", "只有待整理线索可以提交整理");
        }

        long taskId = repository.insertTask(userId);
        repository.linkTaskClues(userId, taskId, clueIds);
        runGeneration(userId, taskId, clues);
        repository.insertIdempotency(userId, "CREATE_DIGEST_TASK", requireKey(key), hash, "DIGEST_TASK", taskId);
        return getTask(userId, taskId);
    }

    public DigestTaskView getTask(long userId, long taskId) {
        return toTaskView(repository.findTask(userId, taskId).orElseThrow(CognitionException::notFound));
    }

    @Transactional
    public DigestTaskView retryTask(long userId, long taskId, String key) {
        String hash = hash(List.of(taskId));
        DigestTaskView previous = replay(userId, "RETRY_DIGEST_TASK", key, hash, id -> getTask(userId, id));
        if (previous != null) return previous;

        TaskRow task = repository.findTask(userId, taskId).orElseThrow(CognitionException::notFound);
        if (task.status() != DigestTaskStatus.FAILED) {
            throw CognitionException.conflict("COGNITION_TASK_NOT_RETRYABLE", "只有失败任务可以重试");
        }
        List<ClueView> clues = repository.findTaskClues(userId, taskId);
        runGeneration(userId, taskId, clues);
        repository.insertIdempotency(userId, "RETRY_DIGEST_TASK", requireKey(key), hash, "DIGEST_TASK", taskId);
        return getTask(userId, taskId);
    }

    public DigestView getDigest(long userId, long digestId) {
        return toDigestView(userId, repository.findDigest(userId, digestId, false).orElseThrow(CognitionException::notFound));
    }

    public CursorPage<DigestView> listDigests(long userId, DigestStatus status, Long cursor, int limit) {
        List<DigestView> items = repository.listDigests(userId, status, cursor, pageSize(limit) + 1).stream()
                .map(row -> toDigestView(userId, row)).toList();
        return page(items, DigestView::digestId, limit);
    }

    @Transactional
    public DigestDecisionView decideDigest(long userId, long digestId, String key, DigestDecisionRequest request) {
        String hash = hash(request);
        IdempotencyRow prior = checkReplay(userId, "DECIDE_DIGEST_" + digestId, key, hash);
        if (prior != null) return currentDecision(userId, digestId, request.decision());

        DigestRow digest = repository.findDigest(userId, digestId, true).orElseThrow(CognitionException::notFound);
        CognitionStateMachine.requirePendingDigest(digest.status());
        Long topicId = null;
        Long actionId = null;
        DigestStatus status;
        switch (request.decision()) {
            case ACCEPT_TOPIC -> {
                actionId = repository.acceptDigest(userId, digest);
                topicId = repository.findTopicByDigest(userId, digestId).orElseThrow().id();
                status = DigestStatus.ACCEPTED;
            }
            case SAVE_KNOWLEDGE -> {
                repository.saveDigestAsKnowledge(userId, digest, request.reasonCode());
                status = DigestStatus.SAVED_KNOWLEDGE;
            }
            case REJECT -> {
                repository.rejectDigest(userId, digest, request.reasonCode());
                status = DigestStatus.REJECTED;
            }
            default -> throw CognitionException.badRequest("未知草稿决定");
        }
        repository.insertIdempotency(userId, "DECIDE_DIGEST_" + digestId, requireKey(key), hash, "DIGEST", digestId);
        return new DigestDecisionView(digestId, request.decision(), status, topicId, actionId, Instant.now());
    }

    public CursorPage<TopicView> listTopics(long userId, TopicProgress progress, Long cursor, int limit) {
        List<TopicView> items = repository.listTopics(userId, progress, cursor, pageSize(limit) + 1).stream()
                .map(row -> toTopicView(userId, row)).toList();
        return page(items, TopicView::topicId, limit);
    }

    public TopicView getTopic(long userId, long topicId) {
        return toTopicView(userId, repository.findTopic(userId, topicId, false).orElseThrow(CognitionException::notFound));
    }

    @Transactional
    public TopicView updateTopicProgress(long userId, long topicId, String key, TopicProgressRequest request) {
        String hash = hash(request);
        TopicView previous = replay(userId, "UPDATE_TOPIC_PROGRESS_" + topicId, key, hash, id -> getTopic(userId, id));
        if (previous != null) return previous;

        TopicRow topic = repository.findTopic(userId, topicId, true).orElseThrow(CognitionException::notFound);
        CognitionStateMachine.requireTopicTransition(topic.progress(), request.progress());
        repository.updateTopicProgress(userId, topic, request.progress());
        repository.insertIdempotency(userId, "UPDATE_TOPIC_PROGRESS_" + topicId, requireKey(key), hash, "TOPIC", topicId);
        return getTopic(userId, topicId);
    }

    @Transactional
    public FeedbackView submitFeedback(long userId, long actionId, String key, FeedbackRequest request) {
        String hash = hash(request);
        FeedbackView previous = replay(userId, "SUBMIT_ACTION_FEEDBACK_" + actionId, key, hash,
                ignored -> repository.findFeedbackByAction(userId, actionId).orElseThrow(CognitionException::notFound));
        if (previous != null) return previous;

        ActionView action = repository.findAction(userId, actionId, true).orElseThrow(CognitionException::notFound);
        if (repository.findFeedbackByAction(userId, actionId).isPresent()) {
            throw CognitionException.conflict("COGNITION_FEEDBACK_ALREADY_SUBMITTED", "这项行动已经反馈过");
        }
        if (action.status() != ActionStatus.PENDING) {
            throw CognitionException.conflict("COGNITION_INVALID_STATE_TRANSITION", "当前行动不能提交反馈");
        }
        long feedbackId = repository.insertFeedback(userId, action, request);
        repository.insertIdempotency(userId, "SUBMIT_ACTION_FEEDBACK_" + actionId, requireKey(key), hash, "FEEDBACK", feedbackId);
        return repository.findFeedbackByAction(userId, actionId).orElseThrow(CognitionException::notFound);
    }

    public HomeView getHome(long userId) {
        TopicRow topicRow = repository.findPrimaryTopic(userId).orElse(null);
        TopicView topic = topicRow == null ? null : toTopicView(userId, topicRow);
        ActionView action = repository.findNextAction(userId).orElse(null);
        int pendingDigests = repository.countPendingDigests(userId);
        int failedTasks = repository.countFailedTasks(userId);

        HomeMode mode = HomeMode.CALM;
        String headline = "今天没有需要特别处理的变化";
        String summary = "你可以继续按自己的节奏记录身体变化。";
        if (topic != null && topic.riskStatus() != RiskStatus.NONE) {
            mode = HomeMode.NOTICE;
            headline = "有一项变化值得继续留意";
            summary = topic.summary();
        } else if (topic != null || pendingDigests > 0) {
            mode = HomeMode.OBSERVE;
            headline = topic == null ? "有一份整理草稿等待确认" : "正在观察：" + topic.title();
            summary = topic == null ? "确认之前，它不会成为你的身体结论。" : topic.summary();
        }
        return new HomeView(mode, headline, summary, topic, pendingDigests, action, failedTasks, Instant.now());
    }

    private void runGeneration(long userId, long taskId, List<ClueView> clues) {
        repository.markTaskRunning(userId, taskId);
        try {
            DigestGenerator.GeneratedDigest generated = generator.generate(clues);
            long digestId = repository.insertDigest(userId, taskId, generated);
            repository.insertEvidence(userId, digestId, clues);
            repository.markTaskSucceeded(userId, taskId);
        } catch (RuntimeException ex) {
            repository.markTaskFailed(userId, taskId, "GENERATION_FAILED", "整理暂时失败，可以稍后重试");
        }
    }

    private void validateClue(ClueCreateRequest request) {
        if (request.clueType() == ClueType.ARTICLE_MARK) {
            if (request.markIntent() == null || blank(request.articleId()) || blank(request.articleTitle())
                    || blank(request.sourceName()) || blank(request.excerpt())) {
                throw CognitionException.badRequest("文章线索缺少标记类型、来源或原文");
            }
            if (request.markIntent() == MarkIntent.QUESTION && blank(request.questionType()) && blank(request.questionText())) {
                throw CognitionException.badRequest("疑问线索需要问题类型或问题内容");
            }
        }
        if (request.clueType() == ClueType.BODY_RECORD && request.bodyRecordId() == null) {
            throw CognitionException.badRequest("身体记录线索缺少记录 ID");
        }
    }

    private List<Long> distinctClueIds(List<Long> clueIds) {
        List<Long> result = new ArrayList<>(new LinkedHashSet<>(clueIds));
        if (result.size() != clueIds.size()) throw CognitionException.badRequest("线索 ID 不能重复");
        if (result.stream().anyMatch(id -> id == null || id <= 0)) throw CognitionException.badRequest("线索 ID 无效");
        return result;
    }

    private DigestDecisionView currentDecision(long userId, long digestId, DigestDecision decision) {
        DigestRow digest = repository.findDigest(userId, digestId, false).orElseThrow(CognitionException::notFound);
        TopicRow topic = repository.findTopicByDigest(userId, digestId).orElse(null);
        ActionView action = topic == null ? null : repository.findFirstTopicAction(userId, topic.id()).orElse(null);
        return new DigestDecisionView(digestId, decision, digest.status(), topic == null ? null : topic.id(),
                action == null ? null : action.actionId(), digest.decidedAt());
    }

    private DigestTaskView toTaskView(TaskRow row) {
        return new DigestTaskView(row.id(), row.status(), row.generatorType(), row.attemptCount(), row.failureCode(),
                row.digestId(), row.createdAt(), row.finishedAt());
    }

    private DigestView toDigestView(long userId, DigestRow row) {
        return new DigestView(row.id(), row.taskId(), row.status(), row.title(), row.commonPoint(), row.possibleLink(),
                row.uncertainty(), row.suggestedAction(), repository.findDigestEvidence(userId, row.id()), row.generatorType(),
                row.generatorVersion(), row.version(), row.createdAt());
    }

    private TopicView toTopicView(long userId, TopicRow row) {
        return new TopicView(row.id(), row.sourceDigestId(), row.title(), row.summary(), row.uncertainty(), row.maturity(),
                row.progress(), row.riskStatus(), row.currentVersion(), repository.findDigestEvidence(userId, row.sourceDigestId()),
                repository.findTopicActions(userId, row.id()), repository.findTopicFeedback(userId, row.id()),
                repository.findTopicVersions(userId, row.id()), row.createdAt(), row.updatedAt());
    }

    private <T> T replay(long userId, String operation, String key, String hash, LongFunction<T> loader) {
        IdempotencyRow row = checkReplay(userId, operation, key, hash);
        return row == null ? null : loader.apply(Objects.requireNonNull(row.resourceId()));
    }

    private IdempotencyRow checkReplay(long userId, String operation, String key, String hash) {
        String validatedKey = requireKey(key);
        IdempotencyRow row = repository.findIdempotency(userId, operation, validatedKey).orElse(null);
        if (row != null && !row.requestHash().equals(hash)) {
            throw CognitionException.conflict("COGNITION_IDEMPOTENCY_CONFLICT", "幂等键已用于不同请求");
        }
        return row;
    }

    private String requireKey(String key) {
        if (blank(key) || key.length() > 64 || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw CognitionException.badRequest("Idempotency-Key 必须是 1 到 64 个可见 ASCII 字符");
        }
        return key;
    }

    private String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("cannot hash request", ex);
        }
    }

    private int pageSize(int requested) {
        if (requested <= 0) return 20;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> CursorPage<T> page(List<T> source, java.util.function.ToLongFunction<T> id, int requestedLimit) {
        int limit = requestedLimit <= 0 ? 20 : Math.min(requestedLimit, MAX_PAGE_SIZE);
        if (source.size() <= limit) return new CursorPage<>(source, null);
        List<T> items = source.subList(0, limit);
        return new CursorPage<>(items, id.applyAsLong(items.get(items.size() - 1)));
    }
}
