package athena.cognition.biz.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CognitionModels {

    private CognitionModels() {
    }

    public enum ClueType { ARTICLE_MARK, BODY_RECORD, CYCLE_RECORD, DEVICE_RECORD }
    public enum MarkIntent { RELATED, QUESTION, KNOWLEDGE }
    public enum RelationDetail { CURRENT, HISTORICAL, UNCERTAIN_OBSERVE, KNOWLEDGE_ONLY }
    public enum ClueStatus { PENDING, IN_DIGEST, ORGANIZED, KNOWLEDGE_ONLY, REJECTED, WITHDRAWN }
    public enum ClueSection { PENDING, ORGANIZED, QUESTIONS }
    public enum DigestTaskStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
    public enum GeneratorType { FIXED_V1, AGENT_V1 }
    public enum DigestStatus { PENDING_CONFIRMATION, ACCEPTED, SAVED_KNOWLEDGE, REJECTED }
    public enum DigestDecision { ACCEPT_TOPIC, SAVE_KNOWLEDGE, REJECT }
    public enum CognitionMaturity { CLUE, INSUFFICIENT, EARLY_LINK, REPEATED_PATTERN, RELATIVELY_STABLE }
    public enum TopicProgress { PENDING_CONFIRMATION, FOLLOWING, OBSERVING, PAUSED, ARCHIVED }
    public enum RiskStatus { NONE, WATCH, PROFESSIONAL_HELP }
    public enum ActionStatus { PENDING, COMPLETED, SKIPPED, CANCELLED }
    public enum FeedbackAccuracy { ACCURATE, INACCURATE, DID_NOT_HAPPEN, NOT_SURE }
    public enum HomeMode { CALM, OBSERVE, NOTICE }

    public record ClueCreateRequest(
            @NotNull ClueType clueType,
            MarkIntent markIntent,
            RelationDetail relationDetail,
            @Size(max = 32) String desiredHelp,
            @Size(max = 128) String articleId,
            @Size(max = 255) String articleTitle,
            @Size(max = 128) String sourceName,
            @Size(max = 4000) String excerpt,
            @Size(max = 64) String questionType,
            @Size(max = 1000) String questionText,
            Long bodyRecordId,
            @NotNull Instant occurredAt
    ) {
    }

    public record ClueView(
            long clueId,
            ClueType clueType,
            MarkIntent markIntent,
            RelationDetail relationDetail,
            String desiredHelp,
            String articleId,
            String articleTitle,
            String sourceName,
            String excerpt,
            String questionType,
            String questionText,
            Long bodyRecordId,
            Instant occurredAt,
            ClueStatus status,
            Instant createdAt
    ) {
    }

    public record DigestTaskCreateRequest(@NotEmpty @Size(max = 20) List<@NotNull Long> clueIds) {
    }

    public record DigestTaskView(
            long digestTaskId,
            DigestTaskStatus status,
            GeneratorType generatorType,
            int attemptCount,
            String failureCode,
            Long digestId,
            Instant createdAt,
            Instant finishedAt
    ) {
    }

    public record EvidenceView(long evidenceId, long clueId, String evidenceLevel, String evidenceRole) {
    }

    public record DigestView(
            long digestId,
            long digestTaskId,
            DigestStatus status,
            String title,
            String commonPoint,
            String possibleLink,
            String uncertainty,
            String suggestedAction,
            List<EvidenceView> evidence,
            GeneratorType generatorType,
            String generatorVersion,
            int version,
            Instant createdAt
    ) {
    }

    public record DigestDecisionRequest(@NotNull DigestDecision decision, @Size(max = 64) String reasonCode) {
    }

    public record DigestDecisionView(
            long digestId,
            DigestDecision decision,
            DigestStatus digestStatus,
            Long topicId,
            Long actionId,
            Instant decidedAt
    ) {
    }

    public record TopicProgressRequest(@NotNull TopicProgress progress) {
    }

    public record ActionView(
            long actionId,
            long topicId,
            String title,
            String instruction,
            ActionStatus status,
            Instant dueAt,
            Instant completedAt
    ) {
    }

    public record FeedbackRequest(
            @NotNull FeedbackAccuracy accuracy,
            boolean completed,
            @Size(max = 1000) String note
    ) {
    }

    public record FeedbackView(
            long feedbackId,
            long actionId,
            long topicId,
            FeedbackAccuracy accuracy,
            boolean completed,
            Instant createdAt
    ) {
    }

    public record TopicVersionView(
            int version,
            String summary,
            String uncertainty,
            CognitionMaturity maturity,
            TopicProgress progress,
            RiskStatus riskStatus,
            String changeReason,
            Instant createdAt
    ) {
    }

    public record TopicView(
            long topicId,
            long sourceDigestId,
            String title,
            String summary,
            String uncertainty,
            CognitionMaturity maturity,
            TopicProgress progress,
            RiskStatus riskStatus,
            int currentVersion,
            List<EvidenceView> evidence,
            List<ActionView> actions,
            List<FeedbackView> feedback,
            List<TopicVersionView> history,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record HomeView(
            HomeMode mode,
            String headline,
            String summary,
            TopicView primaryTopic,
            int pendingDigestCount,
            ActionView nextAction,
            int failedTaskCount,
            Instant generatedAt
    ) {
    }

    public record CursorPage<T>(List<T> items, Long nextCursor) {
    }
}
