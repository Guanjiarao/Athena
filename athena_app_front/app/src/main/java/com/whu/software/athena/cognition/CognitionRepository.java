package com.whu.software.athena.cognition;

import com.whu.software.athena.cognition.CognitionModels.*;
import java.util.List;

/** UI-facing boundary for the deployed Cognition Contract V1. */
public interface CognitionRepository {
    interface Callback<T> {
        void onSuccess(T value);
        void onError(String safeMessage);
    }

    void createClue(ClueCreateRequest request, Callback<ClueCreateResult> callback);
    void deleteClue(String clueId, Callback<String> callback);
    void getInbox(Callback<Inbox> callback);
    void listClues(ClueListView view, int page, int pageSize, Callback<Page<Clue>> callback);
    void createDigestTask(List<String> clueIds, Callback<DigestTask> callback);
    void getDigestTask(String taskId, Callback<DigestTask> callback);
    void retryDigestTask(String taskId, Callback<DigestTask> callback);
    void getDigest(String digestId, Callback<Digest> callback);
    void listReadyDigests(int page, int pageSize, Callback<Page<Digest>> callback);
    void decideDigest(String digestId, DigestDecision decision, String reason, int clientVersion,
                      Callback<DigestDecisionResult> callback);
    void listTopics(int page, int pageSize, Callback<Page<Topic>> callback);
    void getTopic(String topicId, Callback<TopicDetail> callback);
    void submitFeedback(String actionId, String topicId, ActionFeedbackResult result,
                        String note, String occurredAt, Callback<FeedbackResult> callback);
    void getHome(Callback<Home> callback);
}
