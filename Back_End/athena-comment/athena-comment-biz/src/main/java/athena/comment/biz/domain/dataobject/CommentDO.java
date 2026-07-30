package athena.comment.biz.domain.dataobject;


import java.time.LocalDateTime;

public class CommentDO {
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

    private Boolean isTop;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long firstReplyCommentId;

    private Long heat;

    public CommentDO() {
    }

    public CommentDO(Long id, Long noteId, Long userId, Boolean isContentEmpty, String imageUrl, Integer level, Long replyTotal, Long likeTotal, Long parentId, Long replyCommentId, Long replyUserId, Boolean isTop, LocalDateTime createTime, LocalDateTime updateTime, Long firstReplyCommentId, Long heat) {
        this.id = id;
        this.noteId = noteId;
        this.userId = userId;
        this.isContentEmpty = isContentEmpty;
        this.imageUrl = imageUrl;
        this.level = level;
        this.replyTotal = replyTotal;
        this.likeTotal = likeTotal;
        this.parentId = parentId;
        this.replyCommentId = replyCommentId;
        this.replyUserId = replyUserId;
        this.isTop = isTop;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.firstReplyCommentId = firstReplyCommentId;
        this.heat = heat;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Boolean getIsContentEmpty() {
        return isContentEmpty;
    }

    public void setIsContentEmpty(Boolean isContentEmpty) {
        this.isContentEmpty = isContentEmpty;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Long getReplyTotal() {
        return replyTotal;
    }

    public void setReplyTotal(Long replyTotal) {
        this.replyTotal = replyTotal;
    }

    public Long getLikeTotal() {
        return likeTotal;
    }

    public void setLikeTotal(Long likeTotal) {
        this.likeTotal = likeTotal;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getReplyCommentId() {
        return replyCommentId;
    }

    public void setReplyCommentId(Long replyCommentId) {
        this.replyCommentId = replyCommentId;
    }

    public Long getReplyUserId() {
        return replyUserId;
    }

    public void setReplyUserId(Long replyUserId) {
        this.replyUserId = replyUserId;
    }

    public Boolean getIsTop() {
        return isTop;
    }

    public void setIsTop(Boolean isTop) {
        this.isTop = isTop;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Long getFirstReplyCommentId() {
        return firstReplyCommentId;
    }

    public void setFirstReplyCommentId(Long firstReplyCommentId) {
        this.firstReplyCommentId = firstReplyCommentId;
    }

    public Long getHeat() {
        return heat;
    }

    public void setHeat(Long heat) {
        this.heat = heat;
    }

    public String toString() {
        return "CommentDO{id = " + id + ", noteId = " + noteId + ", userId = " + userId + ", isContentEmpty = " + isContentEmpty + ", imageUrl = " + imageUrl + ", level = " + level + ", replyTotal = " + replyTotal + ", likeTotal = " + likeTotal + ", parentId = " + parentId + ", replyCommentId = " + replyCommentId + ", replyUserId = " + replyUserId + ", isTop = " + isTop + ", createTime = " + createTime + ", updateTime = " + updateTime + ", firstReplyCommentId = " + firstReplyCommentId + ", heat = " + heat + "}";
    }
}