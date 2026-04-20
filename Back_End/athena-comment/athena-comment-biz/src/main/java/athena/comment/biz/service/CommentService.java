package athena.comment.biz.service;

import athena.athenaframework.result.Result;
import athena.comment.biz.domain.vo.PublishCommentVO;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
public interface CommentService {
    public Result commentListPage(Long blogId,Long pageNum,Long pageSize);

    public Result publishComment(@RequestBody PublishCommentVO publishCommentVO);

    public Result extendComment(Long commentId,Long pageNum,Long pageSize);

    // 点赞/取消点赞方法
    Result commentLike(Long commentId);

    Result isCommentLike(Long commentId);
}
