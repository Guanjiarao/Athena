package athena.cognition.biz.service;

import athena.cognition.biz.bodyrecord.BodyRecordEvidenceProvider;
import athena.cognition.biz.bodyrecord.BodyRecordEvidenceProvider.ConfirmedBodyRecord;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionIds;
import athena.cognition.biz.domain.CognitionModels.*;
import athena.cognition.biz.domain.CognitionStateMachine;
import athena.cognition.biz.domain.MaturityCalculator;
import athena.cognition.biz.domain.TopicDomainClassifier;
import athena.cognition.biz.generator.DigestGenerator;
import athena.cognition.biz.repository.CognitionJdbcRepository;
import athena.cognition.biz.repository.CognitionJdbcRepository.ActionRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.ClueRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.DigestRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.EvidenceRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.FeedbackRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.TaskRow;
import athena.cognition.biz.repository.CognitionJdbcRepository.TopicRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Contract business logic: three marker save rules (section 6), state flows
 * (section 7) and the HTTP-facing aggregates (section 8).
 */
@Service
public class CognitionService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int INBOX_FIRST_PAGE = 20;
    /** Section 10.1 RULE_1: at least 3 valid RELATED article clues per candidate topic. */
    private static final int RULE_1_MIN_CLUES = 3;
    /** Section 10.1 RULE_2: at least 2 valid RELATED article clues plus 1 confirmed body record. */
    private static final int RULE_2_MIN_CLUES = 2;

    private final CognitionJdbcRepository repository;
    private final DigestGenerator generator;
    private final BodyRecordEvidenceProvider bodyRecordEvidenceProvider;
    private final ObjectMapper objectMapper;

    public CognitionService(CognitionJdbcRepository repository, DigestGenerator generator,
                            BodyRecordEvidenceProvider bodyRecordEvidenceProvider, ObjectMapper objectMapper) {
        this.repository = repository;
        this.generator = generator;
        this.bodyRecordEvidenceProvider = bodyRecordEvidenceProvider;
        this.objectMapper = objectMapper;
    }

    public record PagedResult<T>(List<T> items, long total) {
    }

    // ---------- section 6: three marker save rules ----------

    @Transactional
    public ClueCreateView createClue(long userId, ClueCreateRequest request) {
        validateClue(request);

        ClueIntent intent = request.intent();
        ClueStatus status;
        HelpRequestType helpRequestType = request.helpRequestType();
        if (intent == ClueIntent.QUESTION) {
            // 6.2 question: PENDING, never counts into topic thresholds or knownFacts
            status = ClueStatus.PENDING;
            if (helpRequestType == null) helpRequestType = HelpRequestType.KNOWLEDGE;
        } else if (intent == ClueIntent.KNOWLEDGE_ONLY || request.relationType() == RelationType.KNOWLEDGE_ONLY) {
            // 6.1 / 6.3 knowledge only: ORGANIZED, never enters body topic thresholds
            intent = ClueIntent.KNOWLEDGE_ONLY;
            status = ClueStatus.ORGANIZED;
            if (helpRequestType == null) helpRequestType = HelpRequestType.SAVE_ONLY;
        } else {
            // 6.1 related: CURRENT / PAST / OBSERVE stay PENDING
            status = ClueStatus.PENDING;
            if (helpRequestType == null) helpRequestType = HelpRequestType.OBSERVE;
        }
        CycleRelation cycleRelation = request.cycleRelation() == null ? CycleRelation.UNKNOWN : request.cycleRelation();

        long id = repository.insertClue(userId, request, intent, status, helpRequestType, cycleRelation);
        ClueRow saved = repository.findClue(userId, id).orElseThrow(CognitionException::notFound);
        return new ClueCreateView(toClueView(saved), maybeAutoTrigger(userId, saved));
    }

    /**
     * Section 10.1 automatic thresholds, checked after a RELATED clue is saved.
     * QUESTION and KNOWLEDGE_ONLY clues never reach this point. RULE_2 stays
     * inert until the daily_record provider (P3-3) confirms real body records.
     */
    private DigestTaskTriggerView maybeAutoTrigger(long userId, ClueRow saved) {
        if (saved.intent() != ClueIntent.RELATED || saved.status() != ClueStatus.PENDING) {
            return DigestTaskTriggerView.notTriggered();
        }
        String candidateTopicId = blank(saved.suggestedTopicId()) ? null : saved.suggestedTopicId();
        String candidateTitle = blank(saved.suggestedTopicTitle()) ? null : saved.suggestedTopicTitle().trim();
        if (candidateTopicId == null && candidateTitle == null) {
            // Section 10.1: no deterministic candidate group, no automatic merge
            return DigestTaskTriggerView.notTriggered();
        }

        List<ClueRow> group = repository.findPendingRelatedCluesForCandidate(userId, candidateTopicId, candidateTitle);
        List<ConfirmedBodyRecord> bodyRecords = List.of();
        boolean rule1 = group.size() >= RULE_1_MIN_CLUES;
        if (!rule1 && group.size() >= RULE_2_MIN_CLUES) {
            bodyRecords = bodyRecordEvidenceProvider.findConfirmedBodyRecords(userId, candidateTopicId, candidateTitle);
        }
        boolean rule2 = !rule1 && !bodyRecords.isEmpty();
        if (!rule1 && !rule2) {
            return DigestTaskTriggerView.notTriggered();
        }

        List<Long> clueIds = group.stream().map(ClueRow::id).toList();
        if (repository.hasOpenDigestForClues(userId, clueIds)) {
            // Section 10.1: at most one open digest per candidate topic
            return DigestTaskTriggerView.notTriggered();
        }

        DigestTaskView task = organize(userId, TriggerType.RULE_THRESHOLD, group, bodyRecords, candidateTitle);
        return new DigestTaskTriggerView(true, task.taskId(), task.digestId(), task.status());
    }

    private void validateClue(ClueCreateRequest request) {
        if (request.type() != ClueType.ARTICLE_HIGHLIGHT) {
            throw CognitionException.invalidArgument("本轮只支持文章线索");
        }
        if (request.source() != null && request.source() != ClueSource.KNOWLEDGE_ARTICLE) {
            // section 13.6: square content never enters the loop
            throw CognitionException.invalidArgument("来源不合法");
        }
        if (blank(request.selectedText())) throw CognitionException.invalidArgument("selectedText 不能为空");
        if (blank(request.articleTitle())) throw CognitionException.invalidArgument("articleTitle 不能为空");
        if (blank(request.originalLabel())) throw CognitionException.invalidArgument("originalLabel 不能为空");
        if (request.severity() != null && (request.severity() < 0 || request.severity() > 10)) {
            throw CognitionException.invalidArgument("severity 只能在 0 到 10 之间");
        }
        if (request.intent() == ClueIntent.QUESTION) {
            if (request.questionType() == null && blank(request.questionText())) {
                throw CognitionException.invalidArgument("疑问线索需要问题类型或问题内容");
            }
        } else if (request.intent() == ClueIntent.RELATED
                && request.relationType() != RelationType.CURRENT
                && request.relationType() != RelationType.PAST
                && request.relationType() != RelationType.OBSERVE
                && request.relationType() != RelationType.KNOWLEDGE_ONLY) {
            throw CognitionException.invalidArgument("relationType 不合法");
        }
    }

    /** Section 8.3 */
    public PagedResult<ClueView> listClues(long userId, ClueListView view, ClueIntent intent, ClueStatus status,
                                           String articleId, int page, int pageSize) {
        int size = pageSize(pageSize);
        int offset = offset(page, size);
        List<ClueView> items = repository.listClues(userId, view, intent, status, articleId, offset, size)
                .stream().map(this::toClueView).toList();
        long total = repository.countClues(userId, view, intent, status, articleId);
        return new PagedResult<>(items, total);
    }

    // ---------- section 6.4 / 8.5: revoke ----------

    @Transactional
    public String deleteClue(long userId, String clueExternalId) {
        long clueId = CognitionIds.parse(CognitionIds.CLUE, clueExternalId);
        ClueRow clue = repository.findClue(userId, clueId).orElseThrow(CognitionException::notFound);
        if (repository.isClueUsedInDigest(userId, clueId)) {
            throw CognitionException.clueInUse(clueExternalId);
        }
        boolean revocable = clue.status() == ClueStatus.PENDING
                || (clue.intent() == ClueIntent.KNOWLEDGE_ONLY && clue.status() == ClueStatus.ORGANIZED);
        if (!revocable) {
            throw CognitionException.stateConflict("当前状态的线索不能撤销", clueExternalId, clue.status().name());
        }
        repository.logicalDeleteClue(userId, clueId);
        return clueExternalId;
    }

    // ---------- section 8.6 / 7.1: digest task ----------

    @Transactional
    public DigestTaskView createDigestTask(long userId, DigestTaskCreateRequest request) {
        TriggerType triggerType = request.triggerType() == null ? TriggerType.USER_REQUEST : request.triggerType();

        List<ClueRow> entryClues = List.of();
        if (request.clueIds() != null && !request.clueIds().isEmpty()) {
            List<Long> entryIds = parseIds(CognitionIds.CLUE, request.clueIds());
            entryClues = repository.findClues(userId, entryIds);
            if (entryClues.size() != entryIds.size()) throw CognitionException.notFound();
        }

        // Section 8.6: expand from the entry clue to the whole candidate group
        ClueRow entry = entryClues.isEmpty() ? null : entryClues.get(0);
        String candidateTopicId = entry != null ? entry.suggestedTopicId() : null;
        String candidateTitle = entry != null ? entry.suggestedTopicTitle() : request.suggestedTitle();
        List<ClueRow> group = repository.findPendingRelatedCluesForCandidate(userId, candidateTopicId, candidateTitle);

        // Entry clues without a candidate group can still be organized on explicit user request
        List<ClueRow> clues = new ArrayList<>(group);
        for (ClueRow clue : entryClues) {
            if (clue.intent() == ClueIntent.RELATED && clue.status() == ClueStatus.PENDING
                    && clues.stream().noneMatch(existing -> existing.id() == clue.id())) {
                clues.add(clue);
            }
        }
        if (clues.isEmpty()) throw CognitionException.noValidEvidence();

        List<Long> clueIds = clues.stream().map(ClueRow::id).toList();
        if (repository.hasOpenDigestForClues(userId, clueIds)) throw CognitionException.taskRunning();

        String title = !blank(request.suggestedTitle()) ? request.suggestedTitle().trim()
                : (candidateTitle != null && !candidateTitle.isBlank() ? candidateTitle.trim() : null);

        return organize(userId, triggerType, clues, List.of(), title);
    }

    /**
     * Shared task + digest pipeline (section 7.1): task record, PROCESSING
     * digest, clue membership, clue status flip, evidence, then the fixed
     * generator. Used by both user-requested tasks and automatic thresholds.
     */
    private DigestTaskView organize(long userId, TriggerType triggerType, List<ClueRow> clues,
                                    List<ConfirmedBodyRecord> bodyRecords, String title) {
        List<Long> clueIds = clues.stream().map(ClueRow::id).toList();
        long taskId = repository.insertTask(userId, triggerType, DigestGenerator.FIXED_VERSION);
        repository.markTaskRunning(userId, taskId, false);
        long digestId = repository.insertDigest(userId,
                title != null ? title : "一项值得继续观察的身体线索", DigestGenerator.FIXED_VERSION);
        repository.linkDigestClues(userId, digestId, clueIds);
        repository.updateClueStatus(userId, clueIds, ClueStatus.PROCESSING);

        List<Long> evidenceIds = createClueEvidence(userId, clues);
        for (ConfirmedBodyRecord record : bodyRecords) {
            // Section 4.8: evidence references the real daily_record id, no clue copy
            long evidenceId = repository.insertEvidence(userId, EvidenceSourceType.BODY_RECORD,
                    record.dailyRecordId(), FactLevel.SELF_REPORTED, record.summary(), record.occurredAt());
            evidenceIds.add(evidenceId);
        }
        repository.linkDigestEvidence(userId, digestId, evidenceIds);

        runGeneration(userId, taskId, digestId, clues, title);
        return toTaskView(userId, repository.findTask(userId, taskId).orElseThrow(CognitionException::notFound));
    }

    /**
     * Section 12: retry keeps the same digest, evidence and source clues; the
     * task row is locked so concurrent retries cannot double-run. Only FAILED
     * tasks are retryable; a succeeded retry makes any further retry a
     * COGNITION_STATE_CONFLICT with no side effects.
     */
    @Transactional
    public DigestTaskView retryTask(long userId, String taskExternalId) {
        long taskId = CognitionIds.parse(CognitionIds.TASK, taskExternalId);
        TaskRow task = repository.findTaskForUpdate(userId, taskId).orElseThrow(CognitionException::notFound);
        if (task.status() != DigestTaskStatus.FAILED) {
            throw CognitionException.stateConflict("只有失败任务可以重试", taskExternalId, task.status().name());
        }
        if (task.digestId() == null) throw CognitionException.notFound();
        DigestRow digest = repository.findDigest(userId, task.digestId(), true).orElseThrow(CognitionException::notFound);
        List<ClueRow> clues = repository.findDigestClues(userId, digest.id());

        repository.markTaskRunning(userId, taskId, true);
        repository.markDigestProcessing(userId, digest.id());
        runGeneration(userId, taskId, digest.id(), clues, digest.title());
        return toTaskView(userId, repository.findTask(userId, taskId).orElseThrow(CognitionException::notFound));
    }

    public DigestTaskView getTask(long userId, String taskExternalId) {
        long taskId = CognitionIds.parse(CognitionIds.TASK, taskExternalId);
        return toTaskView(userId, repository.findTask(userId, taskId).orElseThrow(CognitionException::notFound));
    }

    private void runGeneration(long userId, long taskId, long digestId, List<ClueRow> clues, String suggestedTitle) {
        try {
            DigestGenerator.GeneratedDigest generated = generator.generate(
                    clues.stream().map(this::toClueView).toList(), suggestedTitle);
            if (!blank(generated.title())) {
                repository.updateDigestTitle(userId, digestId, generated.title());
            }
            repository.completeDigest(userId, digestId, generated.commonPoint(), generated.possibleRelation(),
                    generated.uncertainty(), generated.suggestedAction());
            repository.markTaskSucceeded(userId, taskId, digestId);
        } catch (RuntimeException ex) {
            // Section 12: clues stay PROCESSING, retry keeps the same evidence
            repository.failDigest(userId, digestId, CognitionException.GENERATION_FAILED);
            repository.markTaskFailed(userId, taskId, digestId, CognitionException.GENERATION_FAILED);
        }
    }

    /** Section 4.4: clue evidence. QUESTION clues are excluded by the caller. */
    private List<Long> createClueEvidence(long userId, List<ClueRow> clues) {
        List<Long> evidenceIds = new ArrayList<>();
        for (ClueRow clue : clues) {
            FactLevel factLevel = clue.intent() == ClueIntent.QUESTION ? FactLevel.QUESTION
                    : (clue.relationType() == RelationType.CURRENT || clue.relationType() == RelationType.PAST)
                    ? FactLevel.SELF_REPORTED : FactLevel.OBSERVED;
            String summary = clue.selectedText();
            if (summary != null && summary.length() > 1000) summary = summary.substring(0, 1000);
            long evidenceId = repository.insertEvidence(userId, EvidenceSourceType.CLUE,
                    CognitionIds.of(CognitionIds.CLUE, clue.id()), factLevel, summary, clue.occurredAt());
            evidenceIds.add(evidenceId);
        }
        return evidenceIds;
    }

    // ---------- section 8.7: digests ----------

    public DigestView getDigest(long userId, String digestExternalId) {
        long digestId = CognitionIds.parse(CognitionIds.DIGEST, digestExternalId);
        DigestRow digest = repository.findDigest(userId, digestId, false).orElseThrow(CognitionException::notFound);
        return toDigestView(userId, digest);
    }

    public PagedResult<DigestView> listDigests(long userId, DigestStatus status, int page, int pageSize) {
        int size = pageSize(pageSize);
        int offset = offset(page, size);
        List<DigestView> items = repository.listDigests(userId, status, offset, size).stream()
                .map(row -> toDigestView(userId, row)).toList();
        long total = repository.countDigests(userId, status);
        return new PagedResult<>(items, total);
    }

    // ---------- section 7.2 / 8.8: digest decision ----------

    @Transactional
    public DigestDecisionView decideDigest(long userId, String digestExternalId, DigestDecisionRequest request) {
        long digestId = CognitionIds.parse(CognitionIds.DIGEST, digestExternalId);
        DigestRow digest = repository.findDigest(userId, digestId, true).orElseThrow(CognitionException::notFound);
        if (request.clientVersion() != null && request.clientVersion() != digest.version()) {
            throw CognitionException.versionConflict(digestExternalId, String.valueOf(digest.version()));
        }
        CognitionStateMachine.requireReadyDigest(digestExternalId, digest.status());
        repository.insertDecisionLog(userId, digestId, request.decision(), request.reason(), request.clientVersion());

        List<ClueRow> clues = repository.findDigestClues(userId, digestId);
        List<Long> clueIds = clues.stream().map(ClueRow::id).toList();
        TopicView topic = null;
        ActionView action = null;
        DigestStatus newStatus;

        switch (request.decision()) {
            case ACCEPT_AS_TOPIC -> {
                List<EvidenceRow> evidence = retireDeadBodyRecordEvidence(userId,
                        repository.findDigestEvidence(userId, digestId));
                long topicId = createTopicFromDigest(userId, digest, clues, evidence);
                repository.linkTopicEvidence(userId, topicId, evidence.stream().map(EvidenceRow::id).toList());
                long actionId = repository.insertAction(userId, topicId, "记录一次相关身体变化",
                        digest.suggestedAction() != null ? digest.suggestedAction() : "补充出现时间和程度。",
                        ActionType.RECORD_BODY, null, writeJson(defaultFeedbackOptions()));
                repository.updateTopicNextAction(userId, topicId, actionId);
                repository.decideDigest(userId, digestId, DigestStatus.ACCEPTED);
                repository.updateClueStatus(userId, clueIds, ClueStatus.ORGANIZED);
                newStatus = DigestStatus.ACCEPTED;
                topic = toTopicView(userId, repository.findTopic(userId, topicId, false).orElseThrow());
                action = toActionView(repository.findAction(userId, actionId, false).orElseThrow());
            }
            case KEEP_AS_KNOWLEDGE -> {
                repository.decideDigest(userId, digestId, DigestStatus.KEPT_AS_KNOWLEDGE);
                repository.updateClueStatus(userId, clueIds, ClueStatus.ORGANIZED);
                newStatus = DigestStatus.KEPT_AS_KNOWLEDGE;
            }
            case REJECT -> {
                repository.decideDigest(userId, digestId, DigestStatus.REJECTED);
                repository.updateClueStatus(userId, clueIds, ClueStatus.DISMISSED);
                newStatus = DigestStatus.REJECTED;
            }
            default -> throw CognitionException.invalidArgument("未知草稿决定");
        }

        int newVersion = repository.findDigest(userId, digestId, false).orElseThrow().version();
        return new DigestDecisionView(new DecidedDigestView(digestExternalId, newStatus, newVersion), topic, action);
    }

    private long createTopicFromDigest(long userId, DigestRow digest, List<ClueRow> clues, List<EvidenceRow> evidence) {
        // Section 6.1: only CURRENT / PAST self reports may enter knownFacts
        List<String> knownFacts = new ArrayList<>();
        if (clues.stream().anyMatch(clue -> clue.relationType() == RelationType.CURRENT)) {
            knownFacts.add("用户确认现在出现过与该线索类似的情况");
        }
        if (clues.stream().anyMatch(clue -> clue.relationType() == RelationType.PAST)) {
            knownFacts.add("用户确认以前出现过与该线索类似的情况");
        }
        List<String> openQuestions = digest.uncertainty() == null ? List.of() : List.of(digest.uncertainty());

        int articleClueCount = (int) evidence.stream().filter(e -> e.sourceType() == EvidenceSourceType.CLUE).count();
        int bodyRecordCount = (int) evidence.stream().filter(e -> e.sourceType() == EvidenceSourceType.BODY_RECORD).count();
        int cycleCount = distinctMonths(evidence);

        // Section 10.2: maturity is computed from the real evidence, not demoed
        Maturity maturity = MaturityCalculator.calculate(toFacts(evidence));
        // Section 4.3: deterministic keyword domain inference, no model involved
        String domain = TopicDomainClassifier.classify(digest.title(), clueTexts(clues));

        return repository.insertTopic(userId, digest.id(), digest.title(), domain, maturity,
                digest.possibleRelation() != null ? digest.possibleRelation() : "这些线索仍需继续观察。",
                writeJson(knownFacts), writeJson(openQuestions),
                evidence.size(), articleClueCount, bodyRecordCount, cycleCount);
    }

    private static List<MaturityCalculator.EvidenceFact> toFacts(List<EvidenceRow> evidence) {
        return evidence.stream().map(e -> new MaturityCalculator.EvidenceFact(
                e.sourceType(), e.factLevel(), e.feedbackResult(), e.occurredAt())).toList();
    }

    /**
     * Section 4.8.5: BODY_RECORD evidence is re-validated against athena-record
     * at recompute moments (digest accept, action feedback). Evidence whose
     * record no longer exists is retired (active=0, never physically deleted)
     * and excluded from counters, thresholds and maturity. Degradation fails
     * open inside the provider: an outage keeps evidence alive.
     */
    private List<EvidenceRow> retireDeadBodyRecordEvidence(long userId, List<EvidenceRow> evidence) {
        List<EvidenceRow> bodyRecords = evidence.stream()
                .filter(e -> e.sourceType() == EvidenceSourceType.BODY_RECORD).toList();
        if (bodyRecords.isEmpty()) return evidence;
        Set<String> alive = bodyRecordEvidenceProvider.filterExistingRecordIds(userId,
                bodyRecords.stream().map(EvidenceRow::sourceId).toList());
        List<Long> deadIds = bodyRecords.stream().filter(e -> !alive.contains(e.sourceId()))
                .map(EvidenceRow::id).toList();
        if (deadIds.isEmpty()) return evidence;
        repository.deactivateEvidence(userId, deadIds);
        return evidence.stream().filter(e -> !deadIds.contains(e.id())).toList();
    }

    private static String clueTexts(List<ClueRow> clues) {
        StringBuilder texts = new StringBuilder();
        for (ClueRow clue : clues) {
            if (clue.suggestedTopicTitle() != null) texts.append(clue.suggestedTopicTitle()).append('\n');
            if (clue.selectedText() != null) texts.append(clue.selectedText()).append('\n');
        }
        return texts.toString();
    }

    private static int distinctMonths(List<EvidenceRow> evidence) {
        long months = evidence.stream().map(EvidenceRow::occurredAt).filter(Objects::nonNull)
                .map(at -> YearMonth.from(at.atOffset(ZoneOffset.ofHours(8)))).distinct().count();
        if (months > 0) return (int) months;
        return evidence.isEmpty() ? 0 : 1;
    }

    // ---------- section 8.9: topics ----------

    public PagedResult<TopicView> listTopics(long userId, int page, int pageSize) {
        int size = pageSize(pageSize);
        int offset = offset(page, size);
        List<TopicView> items = repository.listTopics(userId, offset, size).stream()
                .map(row -> toTopicView(userId, row)).toList();
        return new PagedResult<>(items, repository.countTopics(userId));
    }

    public TopicDetailView getTopic(long userId, String topicExternalId) {
        long topicId = CognitionIds.parse(CognitionIds.TOPIC, topicExternalId);
        TopicRow topic = repository.findTopic(userId, topicId, false).orElseThrow(CognitionException::notFound);

        DigestRow sourceDigest = repository.findDigest(userId, topic.sourceDigestId(), false).orElse(null);
        List<EvidenceRow> evidenceRows = repository.findTopicEvidence(userId, topicId);
        ActionRow nextAction = topic.nextActionId() == null ? null
                : repository.findAction(userId, topic.nextActionId(), false).orElse(null);
        List<FeedbackRow> feedback = repository.findRecentFeedback(userId, topicId, 5);

        List<RelatedArticleView> relatedArticles = evidenceRows.stream()
                .filter(e -> e.articleId() != null)
                .map(e -> new RelatedArticleView(e.articleId(), e.articleTitle(), e.articleType()))
                .distinct().toList();
        String recentChange = feedback.isEmpty()
                ? "主题已建立，正在等待第一次行动反馈。"
                : "最近收到一次行动反馈，阶段理解已更新。";

        return new TopicDetailView(
                toTopicView(userId, topic, evidenceRows),
                sourceDigest == null ? null : new SourceDigestRef(
                        CognitionIds.of(CognitionIds.DIGEST, sourceDigest.id()), sourceDigest.possibleRelation()),
                evidenceRows.stream().map(this::toEvidenceView).toList(),
                nextAction == null ? null : toActionView(nextAction),
                relatedArticles,
                feedback.stream().map(this::toFeedbackView).toList(),
                recentChange);
    }

    // ---------- section 7.3 / 8.10: action feedback ----------

    @Transactional
    public FeedbackResultView submitFeedback(long userId, String actionExternalId, FeedbackRequest request) {
        long actionId = CognitionIds.parse(CognitionIds.ACTION, actionExternalId);
        long requestTopicId = CognitionIds.parse(CognitionIds.TOPIC, request.topicId());
        ActionRow action = repository.findAction(userId, actionId, true).orElseThrow(CognitionException::notFound);
        if (action.topicId() != requestTopicId) throw CognitionException.notFound();
        if (repository.findFeedbackByAction(userId, actionId).isPresent()) {
            throw CognitionException.stateConflict("这项行动已经反馈过", actionExternalId, action.status().name());
        }
        if (action.status() != ActionStatus.PENDING) {
            throw CognitionException.stateConflict("当前行动不能提交反馈", actionExternalId, action.status().name());
        }

        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        boolean skipped = request.result() == ActionFeedbackResult.SKIPPED;
        long feedbackId = repository.insertFeedback(userId, actionId, action.topicId(), request.result(),
                request.note(), occurredAt);

        Long evidenceId = null;
        if (!skipped) {
            // Section 7.3: OCCURRED is a self report; NOT_OCCURRED / UNCERTAIN are
            // valid observations (they neither end the topic nor raise maturity)
            FactLevel factLevel = request.result() == ActionFeedbackResult.OCCURRED
                    ? FactLevel.SELF_REPORTED : FactLevel.OBSERVED;
            String summary = !blank(request.note()) ? request.note() : feedbackSummary(request.result());
            evidenceId = repository.insertEvidence(userId, EvidenceSourceType.ACTION_FEEDBACK,
                    CognitionIds.of(CognitionIds.FEEDBACK, feedbackId), factLevel, summary, occurredAt);
            repository.updateFeedbackEvidence(userId, feedbackId, evidenceId);
            repository.linkTopicEvidence(userId, action.topicId(), List.of(evidenceId));
        }

        ActionStatus actionStatus = skipped ? ActionStatus.SKIPPED : ActionStatus.COMPLETED;
        repository.updateActionStatus(userId, actionId, actionStatus);

        // Section 7.3: recompute counters from the linked evidence (recompute, not
        // blind increment, so counters stay correct however evidence evolved) and
        // refresh stage understanding with a simple per-result text rule
        List<EvidenceRow> topicEvidence = retireDeadBodyRecordEvidence(userId,
                repository.findTopicEvidence(userId, action.topicId()));
        int evidenceCount = topicEvidence.size();
        int articleClueCount = (int) topicEvidence.stream()
                .filter(e -> e.sourceType() == EvidenceSourceType.CLUE).count();
        int bodyRecordCount = (int) topicEvidence.stream()
                .filter(e -> e.sourceType() == EvidenceSourceType.BODY_RECORD
                        || e.sourceType() == EvidenceSourceType.ACTION_FEEDBACK).count();
        // Section 10.2: feedback may raise maturity but never lowers it; riskStatus
        // stays an independent axis and is not touched here
        TopicRow topic = repository.findTopic(userId, action.topicId(), true)
                .orElseThrow(CognitionException::notFound);
        Maturity maturity = MaturityCalculator.higherOf(topic.maturity(),
                MaturityCalculator.calculate(toFacts(topicEvidence)));
        repository.updateTopicAfterFeedback(userId, action.topicId(),
                skipped ? null : stageUnderstandingAfter(request.result()), maturity,
                evidenceCount, articleClueCount, bodyRecordCount, distinctMonths(topicEvidence));

        FeedbackRow saved = repository.findFeedbackByAction(userId, actionId).orElseThrow();
        int topicVersion = repository.findTopic(userId, action.topicId(), false).orElseThrow().version();
        return new FeedbackResultView(toFeedbackView(saved), actionStatus, topicVersion, true);
    }

    private static String feedbackSummary(ActionFeedbackResult result) {
        return switch (result) {
            case OCCURRED -> "用户反馈相关变化出现过";
            case NOT_OCCURRED -> "用户反馈相关变化没有出现";
            case UNCERTAIN -> "用户反馈不确定是否出现";
            default -> "行动反馈记录";
        };
    }

    private static String stageUnderstandingAfter(ActionFeedbackResult result) {
        return switch (result) {
            case OCCURRED -> "最近一次反馈确认相关变化出现过，继续观察它是否会重复。";
            case NOT_OCCURRED -> "最近一次反馈相关变化没有出现，这也是一次有效观察，继续观察。";
            case UNCERTAIN -> "最近一次反馈不确定是否出现，仍需继续观察。";
            default -> "继续观察。";
        };
    }

    // ---------- section 8.4: inbox aggregate ----------

    public InboxView getInbox(long userId) {
        List<ClueView> pendingClues = repository.listClues(userId, ClueListView.PENDING, null, null, null, 0, INBOX_FIRST_PAGE)
                .stream().map(this::toClueView).toList();
        long pendingTotal = repository.countClues(userId, ClueListView.PENDING, null, null, null);

        DigestView activeDigest = repository.findActiveDigest(userId).map(row -> toDigestView(userId, row)).orElse(null);

        List<TopicView> topics = repository.listTopics(userId, 0, INBOX_FIRST_PAGE).stream()
                .map(row -> toTopicView(userId, row)).toList();
        long topicTotal = repository.countTopics(userId);

        List<DigestView> knowledgeDigests = repository.listDigests(userId, DigestStatus.KEPT_AS_KNOWLEDGE, 0, INBOX_FIRST_PAGE)
                .stream().map(row -> toDigestView(userId, row)).toList();
        long knowledgeDigestTotal = repository.countDigests(userId, DigestStatus.KEPT_AS_KNOWLEDGE);

        List<ClueView> knowledgeClues = repository.listClues(userId, null, ClueIntent.KNOWLEDGE_ONLY, ClueStatus.ORGANIZED, null, 0, INBOX_FIRST_PAGE)
                .stream().map(this::toClueView).toList();
        long knowledgeClueTotal = repository.countClues(userId, null, ClueIntent.KNOWLEDGE_ONLY, ClueStatus.ORGANIZED, null);

        List<ClueView> questions = repository.listClues(userId, ClueListView.QUESTIONS, null, null, null, 0, INBOX_FIRST_PAGE)
                .stream().map(this::toClueView).toList();
        long questionTotal = repository.countClues(userId, ClueListView.QUESTIONS, null, null, null);

        InboxCounts counts = new InboxCounts(pendingTotal, topicTotal,
                knowledgeDigestTotal + knowledgeClueTotal, questionTotal);
        boolean hasMore = pendingClues.size() < pendingTotal || topics.size() < topicTotal
                || knowledgeDigests.size() < knowledgeDigestTotal || knowledgeClues.size() < knowledgeClueTotal
                || questions.size() < questionTotal;
        return new InboxView(pendingClues, activeDigest, topics, knowledgeDigests, knowledgeClues, questions,
                counts, hasMore);
    }

    // ---------- section 8.11: home aggregate (full 9-state selection rules) ----------

    /**
     * Short-term feedback states (DIGEST_KEPT_AS_KNOWLEDGE / DIGEST_REJECTED)
     * only show while the decision is recent; older decisions fall back to
     * EMPTY so they never become the permanent home state (section 8.11).
     */
    private static final java.time.Duration DECISION_FEEDBACK_WINDOW = java.time.Duration.ofHours(24);

    public HomeView getHome(long userId) {
        int pendingDigests = (int) repository.countPendingDigests(userId);
        int failedTasks = repository.countFailedTasks(userId);
        TopicRow topicRow = repository.findPrimaryTopic(userId).orElse(null);

        HomeSummaryState state;
        String headline;
        if (repository.hasDigestWithStatus(userId, DigestStatus.READY)) {
            state = HomeSummaryState.DIGEST_READY;
            headline = "有一份整理草稿等待确认";
        } else if (repository.hasProcessingTask(userId)) {
            state = HomeSummaryState.DIGEST_PROCESSING;
            headline = "Athena 正在整理你的身体线索";
        } else if (failedTasks > 0 && repository.findLatestTask(userId)
                .map(task -> task.status() == DigestTaskStatus.FAILED).orElse(false)) {
            // "most recent generation failed and nothing of higher priority exists"
            state = HomeSummaryState.DIGEST_FAILED;
            headline = "最近一次整理没有完成，可以稍后重试";
        } else if (topicRow != null) {
            boolean actionCompleted = topicRow.nextActionId() != null
                    && repository.findAction(userId, topicRow.nextActionId(), false)
                    .map(action -> action.status() == ActionStatus.COMPLETED).orElse(false);
            state = actionCompleted ? HomeSummaryState.ACTION_COMPLETED : HomeSummaryState.OBSERVING;
            headline = actionCompleted ? "这次观察已完成，阶段理解已更新" : "Athena 正在理解你的身体变化";
        } else if (repository.countClues(userId, ClueListView.PENDING, null, null, null) > 0) {
            state = HomeSummaryState.BUILDING_BASELINE;
            headline = "Athena 正在积累你的身体线索";
        } else {
            // Reached only with no topic, no open digest and no pending clue:
            // the latest decision may show as a short-term feedback state
            var latestDecision = repository.findLatestDecision(userId);
            boolean recentDecision = latestDecision.isPresent()
                    && latestDecision.get().createdAt() != null
                    && latestDecision.get().createdAt().isAfter(Instant.now().minus(DECISION_FEEDBACK_WINDOW));
            if (recentDecision && latestDecision.get().decision() == DigestDecision.KEEP_AS_KNOWLEDGE) {
                state = HomeSummaryState.DIGEST_KEPT_AS_KNOWLEDGE;
                headline = "这次整理已保存为知识";
            } else if (recentDecision && latestDecision.get().decision() == DigestDecision.REJECT) {
                state = HomeSummaryState.DIGEST_REJECTED;
                headline = "这次整理已按你的反馈撤下";
            } else {
                state = HomeSummaryState.EMPTY;
                headline = "还没有可展示的身体认知摘要";
            }
        }

        HomeInsight insight = null;
        HomeTopic homeTopic = null;
        ActionView nextAction = null;
        if (topicRow != null) {
            List<String> openQuestions = readStringList(topicRow.openQuestionsJson());
            insight = new HomeInsight(topicRow.title(), topicRow.stageUnderstanding(), topicRow.evidenceCount(),
                    openQuestions.isEmpty() ? null : openQuestions.get(0));
            homeTopic = new HomeTopic(CognitionIds.of(CognitionIds.TOPIC, topicRow.id()), topicRow.title(),
                    topicRow.maturity(), topicRow.userProgress(), topicRow.riskStatus(), topicRow.evidenceCount(),
                    topicRow.cycleCount(),
                    topicRow.nextActionId() == null ? null : CognitionIds.of(CognitionIds.ACTION, topicRow.nextActionId()));
            if (topicRow.nextActionId() != null) {
                nextAction = repository.findAction(userId, topicRow.nextActionId(), false)
                        .filter(action -> action.status() == ActionStatus.PENDING)
                        .map(this::toActionView).orElse(null);
            }
        }
        return new HomeView(Instant.now(), state, headline, insight, homeTopic, pendingDigests, nextAction, failedTasks);
    }

    // ---------- view mapping ----------

    private ClueView toClueView(ClueRow row) {
        return new ClueView(CognitionIds.of(CognitionIds.CLUE, row.id()), row.type(), row.intent(), row.relationType(),
                row.helpRequestType(), row.articleId(), row.articleTitle(), row.articleType(), row.selectedText(),
                row.questionType(), row.questionText(), row.occurredAt(), row.cycleRelation(), row.severity(),
                row.resolved(), row.source(), row.status(), row.suggestedTopicId(), row.suggestedTopicTitle(),
                row.originalLabel(), row.createdAt(), row.updatedAt());
    }

    private DigestView toDigestView(long userId, DigestRow row) {
        List<EvidenceRow> evidence = repository.findDigestEvidence(userId, row.id());
        List<String> clueIds = repository.findDigestClueIds(userId, row.id()).stream()
                .map(id -> CognitionIds.of(CognitionIds.CLUE, id)).toList();
        return new DigestView(CognitionIds.of(CognitionIds.DIGEST, row.id()), row.title(), row.status(),
                row.commonPoint(), row.possibleRelation(), row.uncertainty(), row.suggestedAction(),
                evidence.stream().map(e -> CognitionIds.of(CognitionIds.EVIDENCE, e.id())).toList(),
                clueIds, evidence.stream().map(this::toEvidenceView).toList(),
                row.generatorVersion(), row.generatedAt(), row.failureCode(), row.expiresAt(), row.version());
    }

    private TopicView toTopicView(long userId, TopicRow row) {
        return toTopicView(userId, row, repository.findTopicEvidence(userId, row.id()));
    }

    private TopicView toTopicView(long userId, TopicRow row, List<EvidenceRow> evidence) {
        return new TopicView(CognitionIds.of(CognitionIds.TOPIC, row.id()),
                CognitionIds.of(CognitionIds.DIGEST, row.sourceDigestId()), row.title(), row.domain(), row.maturity(),
                row.userProgress(), row.riskStatus(), row.stageUnderstanding(),
                readStringList(row.knownFactsJson()), readStringList(row.openQuestionsJson()),
                evidence.stream().map(e -> CognitionIds.of(CognitionIds.EVIDENCE, e.id())).toList(),
                row.evidenceCount(), row.articleClueCount(), row.bodyRecordCount(), row.cycleCount(),
                row.nextActionId() == null ? null : CognitionIds.of(CognitionIds.ACTION, row.nextActionId()),
                row.lastUpdatedAt(), row.version());
    }

    private ActionView toActionView(ActionRow row) {
        return new ActionView(CognitionIds.of(CognitionIds.ACTION, row.id()),
                CognitionIds.of(CognitionIds.TOPIC, row.topicId()), row.title(), row.description(), row.actionType(),
                row.status(), row.dueAt(), readFeedbackOptions(row.feedbackOptionsJson()), row.createdAt());
    }

    private FeedbackView toFeedbackView(FeedbackRow row) {
        return new FeedbackView(CognitionIds.of(CognitionIds.FEEDBACK, row.id()),
                CognitionIds.of(CognitionIds.ACTION, row.actionId()),
                CognitionIds.of(CognitionIds.TOPIC, row.topicId()), row.result(), row.note(), row.occurredAt(),
                row.createdAt(),
                row.evidenceId() == null ? null : CognitionIds.of(CognitionIds.EVIDENCE, row.evidenceId()));
    }

    private EvidenceView toEvidenceView(EvidenceRow row) {
        return new EvidenceView(CognitionIds.of(CognitionIds.EVIDENCE, row.id()), row.sourceType(), row.sourceId(),
                row.factLevel(), row.summary(), row.occurredAt(), row.linkedAt(), row.active(),
                row.articleId(), row.articleTitle(), row.articleType());
    }

    private DigestTaskView toTaskView(long userId, TaskRow row) {
        DigestStatus digestStatus = row.digestId() == null ? null
                : repository.findDigest(userId, row.digestId(), false).map(DigestRow::status).orElse(null);
        return new DigestTaskView(CognitionIds.of(CognitionIds.TASK, row.id()),
                row.digestId() == null ? null : CognitionIds.of(CognitionIds.DIGEST, row.digestId()),
                row.status(), digestStatus, row.triggerType(), row.retryCount(), row.failureCode());
    }

    // ---------- small helpers ----------

    private List<ActionFeedbackResult> defaultFeedbackOptions() {
        return List.of(ActionFeedbackResult.OCCURRED, ActionFeedbackResult.NOT_OCCURRED,
                ActionFeedbackResult.UNCERTAIN, ActionFeedbackResult.SKIPPED);
    }

    private List<Long> parseIds(String prefix, List<String> externalIds) {
        List<Long> result = new ArrayList<>(new LinkedHashSet<>(
                externalIds.stream().map(id -> CognitionIds.parse(prefix, id)).toList()));
        if (result.size() != externalIds.size()) throw CognitionException.invalidArgument("ID 不能重复");
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize json", ex);
        }
    }

    private List<String> readStringList(String json) {
        if (blank(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<ActionFeedbackResult> readFeedbackOptions(String json) {
        if (blank(json)) return defaultFeedbackOptions();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return defaultFeedbackOptions();
        }
    }

    private int pageSize(int requested) {
        if (requested <= 0) return 20;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private int offset(int page, int size) {
        return (Math.max(page, 1) - 1) * size;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
