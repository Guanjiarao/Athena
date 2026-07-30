package athena.comment.biz.domain.dataobject;

import java.time.LocalDateTime;

public class CommentAllDO {
    private Long id;

    private Long noteId;

    private Long userId;

    private Boolean isContentEmpty;

    private String imageUrl;

    private Integer level;

    private Long replyTotal;

    private Long likeTotal;

    private Long parentId;

    private Long replyCommentId;

    private Long replyUserId;

    private Byte isTop;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long firstReplyCommentId;

    private Long heat;
    private String content;
}
