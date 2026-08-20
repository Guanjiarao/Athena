package com.whu.software.athena.cognition;

import com.whu.software.athena.cognition.CognitionModels.*;

import java.util.List;

/** UI-facing boundary. Implementations own persistence/networking; screens own no business rules. */
public interface CognitionRepository {

    interface Callback<T> {
        void onSuccess(T value);
        void onError(String safeMessage);
    }

    void createClue(ClueCreateRequest request, Callback<Clue> callback);
    void listClues(ClueSection section, Callback<List<Clue>> callback);
    void createDigestTask(List<Long> clueIds, Callback<DigestTask> callback);
    void retryDigestTask(long taskId, Callback<DigestTask> callback);
    void getDigest(long digestId, Callback<Digest> callback);
    void listPendingDigests(Callback<List<Digest>> callback);
    void decideDigest(long digestId, DigestDecision decision, String reasonCode,
                      Callback<DigestDecisionResult> callback);
    void listTopics(Callback<List<Topic>> callback);
    void getTopic(long topicId, Callback<Topic> callback);
    void updateTopicProgress(long topicId, TopicProgress progress, Callback<Topic> callback);
    void submitFeedback(long actionId, FeedbackAccuracy accuracy, boolean completed, String note,
                        Callback<Feedback> callback);
    void getHome(Callback<Home> callback);
}
