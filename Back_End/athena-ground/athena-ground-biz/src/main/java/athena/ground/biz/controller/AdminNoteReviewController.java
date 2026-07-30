package athena.ground.biz.controller;

import athena.athenaframework.result.Result;
import athena.ground.biz.domain.dto.NoteApproveDTO;
import athena.ground.biz.domain.dto.NoteRejectDTO;
import athena.ground.biz.service.NoteReviewService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/athena/admin/blog/review")
public class AdminNoteReviewController {

    @Resource
    private NoteReviewService noteReviewService;

    @GetMapping("/pending")
    public Result getPendingList(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                 @RequestParam(required = false) Byte type,
                                 @RequestParam(required = false) Integer channelId) {
        return noteReviewService.getPendingList(pageNum, pageSize, type, channelId);
    }

    @GetMapping("/detail")
    public Result getReviewDetail(@RequestParam Long noteId) {
        return noteReviewService.getReviewDetail(noteId);
    }

    @PostMapping("/approve")
    public Result approve(@RequestBody NoteApproveDTO request) {
        return noteReviewService.approve(request);
    }

    @PostMapping("/reject")
    public Result reject(@RequestBody NoteRejectDTO request) {
        return noteReviewService.reject(request);
    }
}
