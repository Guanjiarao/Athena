package athena.cognition.biz.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Contract-aligned models (cognition-contract-v1.md sections 4 and 5).
 * Enum names must stay identical to the Android frontend.
 */
public final class CognitionModels {

    private CognitionModels() {
    }

    // ---------- Section 5.1 clue enums ----------

    public enum ClueType { ARTICLE_HIGHLIGHT, USER_QUESTION, BODY_RECORD, ACTION_FEEDBACK }

    public enum ClueIntent { RELATED, QUESTION, KNOWLEDGE_ONLY }

    public enum RelationType { CURRENT, PAST, OBSERVE, KNOWLEDGE_ONLY }

    public enum HelpRequestType { OBSERVE, KNOWLEDGE, ATTENTION, SAVE_ONLY }

    public enum QuestionType { IS_COMMON, POSSIBLE_CAUSES, SELF_CARE, PROFESSIONAL_HELP, CUSTOM }

    public enum CycleRelation { BEFORE_PERIOD, DURING_PERIOD, AFTER_PERIOD, NO_RELATION, UNKNOWN }

    public enum ClueStatus { PENDING, PROCESSING, ORGANIZED, DISMISSED }

    public enum ClueSource { KNOWLEDGE_ARTICLE }

    // ---------- Section 5.2 digest / topic / action enums ----------

    public enum DigestStatus { PROCESSING, READY, ACCEPTED, KEPT_AS_KNOWLEDGE, REJECTED, FAILED, EXPIRED }

    public enum DigestDecision { ACCEPT_AS_TOPIC, KEEP_AS_KNOWLEDGE, REJECT }

    public enum Maturity { CLUE, INSUFFICIENT, EARLY_LINK, REPEATED_PATTERN, RELATIVELY_STABLE }

    public enum UserProgress { PENDING_CONFIRMATION, FOLLOWING, OBSERVING, PAUSED, ARCHIVED }

    public enum RiskStatus { NONE, WATCH, PROFESSIONAL_HELP }

    public enum ActionType { RECORD_BODY, RECORD_MOOD, RECORD_SLEEP, READ_CONTENT, ANSWER_QUESTION, CONFIRM_STATUS }

    public enum ActionStatus { PENDING, COMPLETED, SKIPPED, EXPIRED }

    public enum ActionFeedbackResult { OCCURRED, NOT_OCCURRED, UNCERTAIN, SKIPPED }

    public enum HomeSummaryState {
        EMPTY, BUILDING_BASELINE, DIGEST_PROCESSING, DIGEST_READY, OBSERVING,
        ACTION_COMPLETED, DIGEST_KEPT_AS_KNOWLEDGE, DIGEST_REJECTED, DIGEST_FAILED
    }

    // ---------- Section 4.4 / 4.7 support enums ----------

    public enum EvidenceSourceType { CLUE, BODY_RECORD, ACTION_FEEDBACK, DEVICE }

    public enum FactLevel { KNOWLEDGE, QUESTION, SELF_REPORTED, OBSERVED }

    public enum DigestTaskStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

    public enum TriggerType { RULE_THRESHOLD, USER_REQUEST, RETRY }

    /** view parameter of GET /clues (section 8.3) */
    public enum ClueListView { PENDING, ORGANIZED, QUESTIONS, ALL }

    // ---------- Clue (section 4.1) ----------

    public record ClueCreateRequest(
            @NotNull ClueType type,
            @NotNull ClueIntent intent,
            RelationType relationType,
            HelpRequestType helpRequestType,
            @Size(max = 128) String articleId,
            @Size(max = 255) String articleTitle,
            Integer articleType,
            @Size(max = 4000) String selectedText,
            QuestionType questionType,
            @Size(max = 1000) String questionText,
            Instant occurredAt,
            CycleRelation cycleRelation,
            Integer severity,
            Boolean resolved,
            ClueSource source,
            @Size(max = 128) String suggestedTopicId,
            @Size(max = 255) String suggestedTopicTitle,
            @Size(max = 64) String originalLabel
    ) {
    }

    public record ClueView(
            String id,
            ClueType type,
            ClueIntent intent,
            RelationType relationType,
            HelpRequestType helpRequestType,
            String articleId,
            String articleTitle,
            Integer articleType,
            String selectedText,
            QuestionType questionType,
            String questionText,
            Instant occurredAt,
            CycleRelation cycleRelation,
            Integer severity,
            Boolean resolved,
            ClueSource source,
            ClueStatus status,
            String suggestedTopicId,
            String suggestedTopicTitle,
            String originalLabel,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** digestTask block inside the create-clue response (section 8.2) */
    public record DigestTaskTriggerView(
            boolean triggered,
            String taskId,
            String digestId,
            DigestTaskStatus status
    ) {
        public static DigestTaskTriggerView notTriggered() {
            return new DigestTaskTriggerView(false, null, null, null);
        }
    }

    /**
     * @param agentTask the graph-workflow Agent task spawned for a RELATED clue, so the
     *                  frontend can poll it directly instead of guessing from the task list;
     *                  null for QUESTION / KNOWLEDGE_ONLY clues or when task creation failed
     */
    public record ClueCreateView(ClueView clue, DigestTaskTriggerView digestTask,
                                 CognitionGraphModels.AgentTaskView agentTask) {
    }

    // ---------- Evidence (section 4.4) ----------

    public record EvidenceView(
            String id,
            EvidenceSourceType sourceType,
            String sourceId,
            FactLevel factLevel,
            String summary,
            Instant occurredAt,
            Instant linkedAt,
            Boolean active,
            String articleId,
            String articleTitle,
            Integer articleType
    ) {
    }

    // ---------- Digest task (section 4.7, 8.6) ----------

    public record DigestTaskCreateRequest(
            TriggerType triggerType,
            @Size(max = 20) List<@NotNull String> clueIds,
            @Size(max = 120) String suggestedTitle
    ) {
    }

    public record DigestTaskView(
            String taskId,
            String digestId,
            DigestTaskStatus status,
            DigestStatus digestStatus,
            TriggerType triggerType,
            Integer retryCount,
            String failureCode
    ) {
    }

    // ---------- Digest (section 4.2, 8.7) ----------

    public record DigestView(
            String id,
            String title,
            DigestStatus status,
            String commonPoint,
            String possibleRelation,
            String uncertainty,
            String suggestedAction,
            List<String> evidenceIds,
            List<String> sourceClueIds,
            List<EvidenceView> evidence,
            String generatorVersion,
            Instant generatedAt,
            String failureCode,
            Instant expiresAt,
            Integer version
    ) {
    }

    // ---------- Digest decision (section 7.2, 8.8) ----------

    public record DigestDecisionRequest(
            @NotNull DigestDecision decision,
            @Size(max = 255) String reason,
            Integer clientVersion
    ) {
    }

    public record DecidedDigestView(String id, DigestStatus status, Integer version) {
    }

    public record DigestDecisionView(
            DecidedDigestView digest,
            TopicView topic,
            ActionView action
    ) {
    }

    // ---------- Topic (section 4.3, 8.9) ----------

    public record TopicView(
            String id,
            String sourceDigestId,
            String title,
            String domain,
            Maturity maturity,
            UserProgress userProgress,
            RiskStatus riskStatus,
            String stageUnderstanding,
            List<String> knownFacts,
            List<String> openQuestions,
            List<String> evidenceIds,
            Integer evidenceCount,
            Integer articleClueCount,
            Integer bodyRecordCount,
            Integer cycleCount,
            String nextActionId,
            Instant lastUpdatedAt,
            Integer version
    ) {
    }

    public record SourceDigestRef(String id, String possibleRelation) {
    }

    public record RelatedArticleView(String articleId, String articleTitle, Integer articleType) {
    }

    public record TopicDetailView(
            TopicView topic,
            SourceDigestRef sourceDigest,
            List<EvidenceView> evidence,
            ActionView nextAction,
            List<RelatedArticleView> relatedArticles,
            List<FeedbackView> recentFeedback,
            String recentChange
    ) {
    }

    // ---------- Action (section 4.5) ----------

    public record ActionView(
            String id,
            String topicId,
            String title,
            String description,
            ActionType actionType,
            ActionStatus status,
            Instant dueAt,
            List<ActionFeedbackResult> feedbackOptions,
            Instant createdAt
    ) {
    }

    // ---------- Action feedback (section 4.6, 8.10) ----------

    public record FeedbackRequest(
            @NotNull String topicId,
            @NotNull ActionFeedbackResult result,
            @Size(max = 1000) String note,
            Instant occurredAt
    ) {
    }

    public record FeedbackView(
            String id,
            String actionId,
            String topicId,
            ActionFeedbackResult result,
            String note,
            Instant occurredAt,
            Instant createdAt,
            String evidenceId
    ) {
    }

    public record FeedbackResultView(
            FeedbackView feedback,
            ActionStatus actionStatus,
            Integer topicVersion,
            boolean refreshRequired
    ) {
    }

    // ---------- Inbox aggregate (section 8.4) ----------

    public record InboxCounts(
            long pending,
            long organizedTopics,
            long organizedKnowledge,
            long questions
    ) {
    }

    public record InboxView(
            List<ClueView> pendingClues,
            DigestView activeDigest,
            List<TopicView> topics,
            List<DigestView> knowledgeDigests,
            List<ClueView> knowledgeClues,
            List<ClueView> questions,
            InboxCounts counts,
            boolean hasMore
    ) {
    }

    // ---------- Home aggregate (section 8.11) ----------

    public record HomeInsight(String title, String body, Integer evidenceCount, String uncertainty) {
    }

    public record HomeTopic(
            String id,
            String title,
            Maturity maturity,
            UserProgress userProgress,
            RiskStatus riskStatus,
            Integer evidenceCount,
            Integer cycleCount,
            String nextActionId
    ) {
    }

    public record HomeView(
            Instant asOf,
            HomeSummaryState summaryState,
            String headline,
            HomeInsight latestInsight,
            HomeTopic activeTopic,
            int pendingDigestCount,
            ActionView nextAction,
            int failedTaskCount
    ) {
    }

    // ---------- Error body (section 12) ----------

    public record ErrorBody(String errorCode, String objectId, String currentStatus) {
        public static ErrorBody of(String errorCode) {
            return new ErrorBody(errorCode, null, null);
        }
    }
}
