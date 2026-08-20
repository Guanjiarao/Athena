package athena.cognition.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionModels.*;
import athena.cognition.biz.service.CognitionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/athena/cognition")
public class CognitionController {

    private final CognitionService service;

    public CognitionController(CognitionService service) {
        this.service = service;
    }

    @PostMapping("/clues")
    public Result<ClueView> createClue(@RequestHeader("Idempotency-Key") String key,
                                       @Valid @RequestBody ClueCreateRequest request) {
        return Result.ok(service.createClue(userId(), key, request));
    }

    @GetMapping("/clues")
    public Result<CursorPage<ClueView>> listClues(@RequestParam(defaultValue = "PENDING") ClueSection section,
                                                  @RequestParam(required = false) Long cursor,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(service.listClues(userId(), section, cursor, limit));
    }

    @PostMapping("/digest-tasks")
    public Result<DigestTaskView> createDigestTask(@RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody DigestTaskCreateRequest request) {
        return Result.ok(service.createDigestTask(userId(), key, request));
    }

    @GetMapping("/digest-tasks/{taskId}")
    public Result<DigestTaskView> getDigestTask(@PathVariable long taskId) {
        return Result.ok(service.getTask(userId(), taskId));
    }

    @PostMapping("/digest-tasks/{taskId}/retry")
    public Result<DigestTaskView> retryDigestTask(@PathVariable long taskId,
                                                  @RequestHeader("Idempotency-Key") String key) {
        return Result.ok(service.retryTask(userId(), taskId, key));
    }

    @GetMapping("/digests/{digestId}")
    public Result<DigestView> getDigest(@PathVariable long digestId) {
        return Result.ok(service.getDigest(userId(), digestId));
    }

    @GetMapping("/digests")
    public Result<CursorPage<DigestView>> listDigests(
            @RequestParam(defaultValue = "PENDING_CONFIRMATION") DigestStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(service.listDigests(userId(), status, cursor, limit));
    }

    @PostMapping("/digests/{digestId}/decisions")
    public Result<DigestDecisionView> decideDigest(@PathVariable long digestId,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody DigestDecisionRequest request) {
        return Result.ok(service.decideDigest(userId(), digestId, key, request));
    }

    @GetMapping("/topics")
    public Result<CursorPage<TopicView>> listTopics(@RequestParam(required = false) TopicProgress progress,
                                                    @RequestParam(required = false) Long cursor,
                                                    @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(service.listTopics(userId(), progress, cursor, limit));
    }

    @GetMapping("/topics/{topicId}")
    public Result<TopicView> getTopic(@PathVariable long topicId) {
        return Result.ok(service.getTopic(userId(), topicId));
    }

    @PatchMapping("/topics/{topicId}/progress")
    public Result<TopicView> updateTopicProgress(@PathVariable long topicId,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody TopicProgressRequest request) {
        return Result.ok(service.updateTopicProgress(userId(), topicId, key, request));
    }

    @PostMapping("/actions/{actionId}/feedback")
    public Result<FeedbackView> submitFeedback(@PathVariable long actionId,
                                               @RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody FeedbackRequest request) {
        return Result.ok(service.submitFeedback(userId(), actionId, key, request));
    }

    @GetMapping("/home")
    public Result<HomeView> getHome() {
        return Result.ok(service.getHome(userId()));
    }

    private long userId() {
        Long userId = UserIdHolder.getUserId();
        if (userId == null || userId <= 0) throw CognitionException.unauthenticated();
        return userId;
    }
}
