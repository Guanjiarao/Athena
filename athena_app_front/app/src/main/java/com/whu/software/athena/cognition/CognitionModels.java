package com.whu.software.athena.cognition;

import java.util.ArrayList;
import java.util.List;

/** DTOs shared by the demo and HTTP repositories. Field names match Cognition Contract V1. */
public final class CognitionModels {

    private CognitionModels() {}

    public enum ClueType { ARTICLE_MARK, BODY_RECORD, CYCLE_RECORD, DEVICE_RECORD }
    public enum MarkIntent { RELATED, QUESTION, KNOWLEDGE }
    public enum RelationDetail { CURRENT, HISTORICAL, UNCERTAIN_OBSERVE, KNOWLEDGE_ONLY }
    public enum ClueStatus { PENDING, IN_DIGEST, ORGANIZED, KNOWLEDGE_ONLY, REJECTED, WITHDRAWN }
    public enum ClueSection { PENDING, ORGANIZED, QUESTIONS }
    public enum DigestTaskStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
    public enum DigestStatus { PENDING_CONFIRMATION, ACCEPTED, SAVED_KNOWLEDGE, REJECTED }
    public enum DigestDecision { ACCEPT_TOPIC, SAVE_KNOWLEDGE, REJECT }
    public enum CognitionMaturity { CLUE, INSUFFICIENT, EARLY_LINK, REPEATED_PATTERN, RELATIVELY_STABLE }
    public enum TopicProgress { PENDING_CONFIRMATION, FOLLOWING, OBSERVING, PAUSED, ARCHIVED }
    public enum RiskStatus { NONE, WATCH, PROFESSIONAL_HELP }
    public enum ActionStatus { PENDING, COMPLETED, SKIPPED, CANCELLED }
    public enum FeedbackAccuracy { ACCURATE, INACCURATE, DID_NOT_HAPPEN, NOT_SURE }
    public enum HomeMode { CALM, OBSERVE, NOTICE }

    public static class ClueCreateRequest {
        public ClueType clueType;
        public MarkIntent markIntent;
        public RelationDetail relationDetail;
        public String desiredHelp;
        public String articleId;
        public String articleTitle;
        public String sourceName;
        public String excerpt;
        public String questionType;
        public String questionText;
        public Long bodyRecordId;
        public String occurredAt;
    }

    public static class Clue {
        public long clueId;
        public ClueType clueType;
        public MarkIntent markIntent;
        public RelationDetail relationDetail;
        public String desiredHelp;
        public String articleId;
        public String articleTitle;
        public String sourceName;
        public String excerpt;
        public String questionType;
        public String questionText;
        public Long bodyRecordId;
        public String occurredAt;
        public ClueStatus status;
        public String createdAt;
    }

    public static class DigestTask {
        public long digestTaskId;
        public DigestTaskStatus status;
        public String generatorType;
        public int attemptCount;
        public String failureCode;
        public Long digestId;
        public String createdAt;
        public String finishedAt;
    }

    public static class Evidence {
        public long evidenceId;
        public long clueId;
        public String evidenceLevel;
        public String evidenceRole;
    }

    public static class Digest {
        public long digestId;
        public long digestTaskId;
        public DigestStatus status;
        public String title;
        public String commonPoint;
        public String possibleLink;
        public String uncertainty;
        public String suggestedAction;
        public List<Evidence> evidence = new ArrayList<>();
        public String generatorType;
        public String generatorVersion;
        public int version;
        public String createdAt;
    }

    public static class DigestDecisionResult {
        public long digestId;
        public DigestDecision decision;
        public DigestStatus digestStatus;
        public Long topicId;
        public Long actionId;
        public String decidedAt;
    }

    public static class Action {
        public long actionId;
        public long topicId;
        public String title;
        public String instruction;
        public ActionStatus status;
        public String dueAt;
        public String completedAt;
    }

    public static class Feedback {
        public long feedbackId;
        public long actionId;
        public long topicId;
        public FeedbackAccuracy accuracy;
        public boolean completed;
        public String note;
        public String createdAt;
    }

    public static class TopicVersion {
        public int version;
        public String summary;
        public String uncertainty;
        public CognitionMaturity maturity;
        public TopicProgress progress;
        public RiskStatus riskStatus;
        public String changeReason;
        public String createdAt;
    }

    public static class Topic {
        public long topicId;
        public long sourceDigestId;
        public String title;
        public String summary;
        public String uncertainty;
        public CognitionMaturity maturity;
        public TopicProgress progress;
        public RiskStatus riskStatus;
        public int currentVersion;
        public List<Evidence> evidence = new ArrayList<>();
        public List<Action> actions = new ArrayList<>();
        public List<Feedback> feedback = new ArrayList<>();
        public List<TopicVersion> history = new ArrayList<>();
        public String createdAt;
        public String updatedAt;
    }

    public static class Home {
        public HomeMode mode;
        public String headline;
        public String summary;
        public Topic primaryTopic;
        public int pendingDigestCount;
        public Action nextAction;
        public int failedTaskCount;
        public String generatedAt;
    }

    public static class CursorPage<T> {
        public List<T> items = new ArrayList<>();
        public Long nextCursor;
    }
}
