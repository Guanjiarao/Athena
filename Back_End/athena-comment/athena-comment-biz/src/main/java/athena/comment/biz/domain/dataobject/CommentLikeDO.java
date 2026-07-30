package athena.comment.biz.domain.dataobject;


import java.time.LocalDateTime;

public class CommentLikeDO {
    private Long id;

    private Long userId;

    private Long commentId;

    private LocalDateTime createTime;

    private Integer status;

    public CommentLikeDO() {
    }

    public CommentLikeDO(Long id, Long userId, Long commentId, LocalDateTime createTime, Integer status) {
        this.id = id;
        this.userId = userId;
        this.commentId = commentId;
        this.createTime = createTime;
        this.status = status;
    }

    /**
     * 获取
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置
     * @param id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取
     * @return userId
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置
     * @param userId
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取
     * @return commentId
     */
    public Long getCommentId() {
        return commentId;
    }

    /**
     * 设置
     * @param commentId
     */
    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    /**
     * 获取
     * @return createTime
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置
     * @param createTime
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取
     * @return status
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置
     * @param status
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    public String toString() {
        return "CommentLikeDO{id = " + id + ", userId = " + userId + ", commentId = " + commentId + ", createTime = " + createTime + ", status = " + status + "}";
    }
}