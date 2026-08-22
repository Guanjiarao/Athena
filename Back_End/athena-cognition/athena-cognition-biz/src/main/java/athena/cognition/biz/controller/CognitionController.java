package athena.cognition.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionModels.*;
import athena.cognition.biz.service.CognitionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contract HTTP endpoints (cognition-contract-v1.md section 8). No mandatory
 * Idempotency-Key header: idempotency is backed by unique constraints and the
 * decision log instead (section 11).
 */
@RestController
@RequestMapping("/athena/cognition")
public class CognitionController {

    private final CognitionService service;

    public CognitionController(CognitionService service) {
        this.service = service;
    }

    /** 8.2 create clue */
    @PostMapping("/clues")
    public Result<ClueCreateView> createClue(@Valid @RequestBody ClueCreateRequest request) {
        return Result.ok(service.createClue(userId(), request));
    }

    /** 8.3 paged clue list: view is optional so intent/status can filter precisely */
    @GetMapping("/clues")
    public Result<List<ClueView>> listClues(@RequestParam(required = false) ClueListView view,
                                            @RequestParam(required = false) ClueIntent intent,
                                            @RequestParam(required = false) ClueStatus status,
                                            @RequestParam(required = false) String articleId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        CognitionService.PagedResult<ClueView> result =
                service.listClues(userId(), view, intent, status, articleId, page, pageSize);
        return Result.ok(result.items(), result.total());
    }

    /** 8.4 inbox aggregate */
    @GetMapping("/inbox")
    public Result<InboxView> getInbox() {
        return Result.ok(service.getInbox(userId()));
    }

    /** 8.5 revoke clue */
    @DeleteMapping("/clues/{clueId}")
    public Result<String> deleteClue(@PathVariable String clueId) {
        return Result.ok(service.deleteClue(userId(), clueId));
    }

    /** 8.6 user requested digest task */
    @PostMapping("/digest-tasks")
    public Result<DigestTaskView> createDigestTask(@Valid @RequestBody DigestTaskCreateRequest request) {
        return Result.ok(service.createDigestTask(userId(), request));
    }

    @GetMapping("/digest-tasks/{taskId}")
    public Result<DigestTaskView> getDigestTask(@PathVariable String taskId) {
        return Result.ok(service.getTask(userId(), taskId));
    }

    /** section 12 retry */
    @PostMapping("/digest-tasks/{taskId}/retry")
    public Result<DigestTaskView> retryDigestTask(@PathVariable String taskId) {
        return Result.ok(service.retryTask(userId(), taskId));
    }

    /** 8.7 digest list and detail */
    @GetMapping("/digests")
    public Result<List<DigestView>> listDigests(@RequestParam(required = false) DigestStatus status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        CognitionService.PagedResult<DigestView> result = service.listDigests(userId(), status, page, pageSize);
        return Result.ok(result.items(), result.total());
    }

    @GetMapping("/digests/{digestId}")
    public Result<DigestView> getDigest(@PathVariable String digestId) {
        return Result.ok(service.getDigest(userId(), digestId));
    }

    /** 8.8 digest decision */
    @PostMapping("/digests/{digestId}/decision")
    public Result<DigestDecisionView> decideDigest(@PathVariable String digestId,
                                                   @Valid @RequestBody DigestDecisionRequest request) {
        return Result.ok(service.decideDigest(userId(), digestId, request));
    }

    /** 8.9 topic list and detail */
    @GetMapping("/topics")
    public Result<List<TopicView>> listTopics(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        CognitionService.PagedResult<TopicView> result = service.listTopics(userId(), page, pageSize);
        return Result.ok(result.items(), result.total());
    }

    @GetMapping("/topics/{topicId}")
    public Result<TopicDetailView> getTopic(@PathVariable String topicId) {
        return Result.ok(service.getTopic(userId(), topicId));
    }

    /** 8.10 action feedback */
    @PostMapping("/actions/{actionId}/feedback")
    public Result<FeedbackResultView> submitFeedback(@PathVariable String actionId,
                                                     @Valid @RequestBody FeedbackRequest request) {
        return Result.ok(service.submitFeedback(userId(), actionId, request));
    }

    /** 8.11 home aggregate */
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
