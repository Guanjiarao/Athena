package com.whu.software.athena.cognition;

import java.util.ArrayList;
import java.util.List;

/** DTOs that mirror Back_End/athena-cognition/openapi-v1.yaml. */
public final class CognitionModels {
    private CognitionModels() {}

    public enum ClueType { ARTICLE_HIGHLIGHT, USER_QUESTION, BODY_RECORD, ACTION_FEEDBACK }
    public enum ClueIntent { RELATED, QUESTION, KNOWLEDGE_ONLY }
    public enum RelationType { CURRENT, PAST, OBSERVE, KNOWLEDGE_ONLY }
    public enum HelpRequestType { OBSERVE, KNOWLEDGE, ATTENTION, SAVE_ONLY }
    public enum QuestionType { IS_COMMON, POSSIBLE_CAUSES, SELF_CARE, PROFESSIONAL_HELP, CUSTOM }
    public enum CycleRelation { BEFORE_PERIOD, DURING_PERIOD, AFTER_PERIOD, NO_RELATION, UNKNOWN }
    public enum ClueStatus { PENDING, PROCESSING, ORGANIZED, DISMISSED }
    public enum ClueSource { KNOWLEDGE_ARTICLE }
    public enum ClueListView { PENDING, ORGANIZED, QUESTIONS, ALL }
    public enum DigestStatus { PROCESSING, READY, ACCEPTED, KEPT_AS_KNOWLEDGE, REJECTED, FAILED, EXPIRED }
    public enum DigestDecision { ACCEPT_AS_TOPIC, KEEP_AS_KNOWLEDGE, REJECT }
    public enum DigestTaskStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
    public enum TriggerType { RULE_THRESHOLD, USER_REQUEST, RETRY }
    public enum Maturity { CLUE, INSUFFICIENT, EARLY_LINK, REPEATED_PATTERN, RELATIVELY_STABLE }
    public enum UserProgress { PENDING_CONFIRMATION, FOLLOWING, OBSERVING, PAUSED, ARCHIVED }
    public enum RiskStatus { NONE, WATCH, PROFESSIONAL_HELP }
    public enum TopicDomain { MOOD, CYCLE, SLEEP, SYMPTOM, SEXUAL_HEALTH, OTHER }
    public enum ActionType { RECORD_BODY, RECORD_MOOD, RECORD_SLEEP, READ_CONTENT, ANSWER_QUESTION, CONFIRM_STATUS }
    public enum ActionStatus { PENDING, COMPLETED, SKIPPED, EXPIRED }
    public enum ActionFeedbackResult { OCCURRED, NOT_OCCURRED, UNCERTAIN, SKIPPED }
    public enum EvidenceSourceType { CLUE, BODY_RECORD, ACTION_FEEDBACK, DEVICE }
    public enum FactLevel { KNOWLEDGE, QUESTION, SELF_REPORTED, OBSERVED }
    public enum HomeSummaryState { EMPTY, BUILDING_BASELINE, DIGEST_PROCESSING, DIGEST_READY, OBSERVING, ACTION_COMPLETED, DIGEST_KEPT_AS_KNOWLEDGE, DIGEST_REJECTED, DIGEST_FAILED }

    public static class ErrorBody {
        public String errorCode;
        public String objectId;
        public String currentStatus;
    }

    public static class Page<T> {
        public List<T> data = new ArrayList<>();
        public long total;
    }

    public static class ClueCreateRequest {
        public ClueType type;
        public ClueIntent intent;
        public RelationType relationType;
        public HelpRequestType helpRequestType;
        public String articleId;
        public String articleTitle;
        public Integer articleType;
        public String selectedText;
        public QuestionType questionType;
        public String questionText;
        public String occurredAt;
        public CycleRelation cycleRelation;
        public Integer severity;
        public Boolean resolved;
        public ClueSource source;
        public String suggestedTopicId;
        public String suggestedTopicTitle;
        public String originalLabel;
    }

    public static class Clue {
        public String id;
        public ClueType type;
        public ClueIntent intent;
        public RelationType relationType;
        public HelpRequestType helpRequestType;
        public String articleId;
        public String articleTitle;
        public Integer articleType;
        public String selectedText;
        public QuestionType questionType;
        public String questionText;
        public String occurredAt;
        public CycleRelation cycleRelation;
        public Integer severity;
        public Boolean resolved;
        public ClueSource source;
        public ClueStatus status;
        public String suggestedTopicId;
        public String suggestedTopicTitle;
        public String originalLabel;
        public String createdAt;
        public String updatedAt;
    }

    public static class DigestTaskTrigger {
        public boolean triggered;
        public String taskId;
        public String digestId;
        public DigestTaskStatus status;
    }

    public static class ClueCreateResult {
        public Clue clue;
        public DigestTaskTrigger digestTask;
    }

    public static class Evidence {
        public String id;
        public EvidenceSourceType sourceType;
        public String sourceId;
        public FactLevel factLevel;
        public String summary;
        public String occurredAt;
        public String linkedAt;
        public boolean active;
        public String articleId;
        public String articleTitle;
        public Integer articleType;
    }

    public static class DigestTaskRequest {
        public TriggerType triggerType = TriggerType.USER_REQUEST;
        public List<String> clueIds = new ArrayList<>();
        public String suggestedTitle;
    }

    public static class DigestTask {
        public String taskId;
        public String digestId;
        public DigestTaskStatus status;
        public DigestStatus digestStatus;
        public TriggerType triggerType;
        public int retryCount;
        public String failureCode;
    }

    public static class Digest {
        public String id;
        public String title;
        public DigestStatus status;
        public String commonPoint;
        public String possibleRelation;
        public String uncertainty;
        public String suggestedAction;
        public List<String> evidenceIds = new ArrayList<>();
        public List<String> sourceClueIds = new ArrayList<>();
        public List<Evidence> evidence = new ArrayList<>();
        public String generatorVersion;
        public String generatedAt;
        public String failureCode;
        public String expiresAt;
        public int version;
    }

    public static class DecidedDigest {
        public String id;
        public DigestStatus status;
        public int version;
    }

    public static class DigestDecisionResult {
        public DecidedDigest digest;
        public Topic topic;
        public Action action;
    }

    public static class Topic {
        public String id;
        public String sourceDigestId;
        public String title;
        public TopicDomain domain;
        public Maturity maturity;
        public UserProgress userProgress;
        public RiskStatus riskStatus;
        public String stageUnderstanding;
        public List<String> knownFacts = new ArrayList<>();
        public List<String> openQuestions = new ArrayList<>();
        public List<String> evidenceIds = new ArrayList<>();
        public int evidenceCount;
        public int articleClueCount;
        public int bodyRecordCount;
        public int cycleCount;
        public String nextActionId;
        public String lastUpdatedAt;
        public int version;
    }

    public static class SourceDigestRef {
        public String id;
        public String possibleRelation;
    }

    public static class RelatedArticle {
        public String articleId;
        public String articleTitle;
        public Integer articleType;
    }

    public static class TopicDetail {
        public Topic topic;
        public SourceDigestRef sourceDigest;
        public List<Evidence> evidence = new ArrayList<>();
        public Action nextAction;
        public List<RelatedArticle> relatedArticles = new ArrayList<>();
        public List<Feedback> recentFeedback = new ArrayList<>();
        public String recentChange;
    }

    public static class Action {
        public String id;
        public String topicId;
        public String title;
        public String description;
        public ActionType actionType;
        public ActionStatus status;
        public String dueAt;
        public List<ActionFeedbackResult> feedbackOptions = new ArrayList<>();
        public String createdAt;
    }

    public static class Feedback {
        public String id;
        public String actionId;
        public String topicId;
        public ActionFeedbackResult result;
        public String note;
        public String occurredAt;
        public String createdAt;
        public String evidenceId;
    }

    public static class FeedbackResult {
        public Feedback feedback;
        public ActionStatus actionStatus;
        public int topicVersion;
        public boolean refreshRequired;
    }

    public static class InboxCounts {
        public long pending;
        public long organizedTopics;
        public long organizedKnowledge;
        public long questions;
    }

    public static class Inbox {
        public List<Clue> pendingClues = new ArrayList<>();
        public Digest activeDigest;
        public List<Topic> topics = new ArrayList<>();
        public List<Digest> knowledgeDigests = new ArrayList<>();
        public List<Clue> knowledgeClues = new ArrayList<>();
        public List<Clue> questions = new ArrayList<>();
        public InboxCounts counts = new InboxCounts();
        public boolean hasMore;
    }

    public static class HomeInsight {
        public String title;
        public String body;
        public int evidenceCount;
        public String uncertainty;
    }

    public static class HomeTopic {
        public String id;
        public String title;
        public Maturity maturity;
        public UserProgress userProgress;
        public RiskStatus riskStatus;
        public int evidenceCount;
        public int cycleCount;
        public String nextActionId;
    }

    public static class Home {
        public String asOf;
        public HomeSummaryState summaryState;
        public String headline;
        public HomeInsight latestInsight;
        public HomeTopic activeTopic;
        public int pendingDigestCount;
        public Action nextAction;
        public int failedTaskCount;
    }
}
