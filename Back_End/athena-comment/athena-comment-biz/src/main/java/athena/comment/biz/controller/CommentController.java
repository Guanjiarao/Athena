package athena.comment.biz.controller;

import athena.athenaframework.result.Result;
import athena.comment.biz.domain.vo.PublishCommentVO;
import athena.comment.biz.service.CommentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/athena/comment")
public class CommentController {

    @Resource
    private CommentService commentService;

    // ========== 原有接口保持不变 ==========
    @GetMapping("/listPage")
    public Result commentListPage(
            @RequestParam("blogId") Long blogId,
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize) {
        return commentService.commentListPage(blogId, pageNum, pageSize);
    }

    @PostMapping("/publish")
    public Result publishComment(@RequestBody PublishCommentVO publishCommentVO) {
        return commentService.publishComment(publishCommentVO);
    }

    @GetMapping("/extend")
    public Result extendComment(
            @RequestParam("commentId") Long commentId,
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize) {
        return commentService.extendComment(commentId, pageNum, pageSize);
    }

    @GetMapping("/commentLike")
    public Result commentLike(@RequestParam("commentId") Long commentId)
    {
        return commentService.commentLike(commentId);
    }

    // ========== 新增：查询当前用户是否点赞该评论 ==========
    @GetMapping("/isCommentLike")
    public Result isCommentLike(@RequestParam("commentId") Long commentId) {
        return commentService.isCommentLike(commentId);
    }

    @DeleteMapping("/by-note")
    public Result deleteByNoteId(@RequestParam("noteId") Long noteId) {
        return commentService.deleteByNoteId(noteId);
    }
}