package athena.comment.biz.domain.dto;

import athena.athenaframework.DTO.UserDTO;

import java.time.LocalDateTime;


public class CommentBasicDTO {


    /**
     * 评论 ID
     */
    private Long commentId;

    private Long noteId;

    private UserDTO userDTO;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论内容
     */
    private String imageUrl;

    /**
     * 发布时间
     */
    private LocalDateTime createTime;

    /**
     * 被点赞数
     */
    private Long likeTotal;

    /**
     * 二级评论总数
     */
    private Long replyTotal;


    private Boolean isTop;

    private Long heat;

    /**
     * 最早回复的评论
     */
    private ChildCommentDTO firstReplyComment;



    public CommentBasicDTO() {
    }

    public CommentBasicDTO(Long commentId, Long noteId, UserDTO userDTO, String content, String imageUrl, LocalDateTime createTime, Long likeTotal, Long replyTotal, Boolean isTop, Long heat, ChildCommentDTO firstReplyComment) {
        this.commentId = commentId;
        this.noteId = noteId;
        this.userDTO = userDTO;
        this.content = content;
        this.imageUrl = imageUrl;
        this.createTime = createTime;
        this.likeTotal = likeTotal;
        this.replyTotal = replyTotal;
        this.isTop = isTop;
        this.heat = heat;
        this.firstReplyComment = firstReplyComment;
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
     * @return noteId
     */
    public Long getNoteId() {
        return noteId;
    }

    /**
     * 设置
     * @param noteId
     */
    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    /**
     * 获取
     * @return userDTO
     */
    public UserDTO getUserDTO() {
        return userDTO;
    }

    /**
     * 设置
     * @param userDTO
     */
    public void setUserDTO(UserDTO userDTO) {
        this.userDTO = userDTO;
    }

    /**
     * 获取
     * @return content
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置
     * @param content
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取
     * @return imageUrl
     */
    public String getImageUrl() {
        return imageUrl;
    }

    /**
     * 设置
     * @param imageUrl
     */
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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
     * @return likeTotal
     */
    public Long getLikeTotal() {
        return likeTotal;
    }

    /**
     * 设置
     * @param likeTotal
     */
    public void setLikeTotal(Long likeTotal) {
        this.likeTotal = likeTotal;
    }

    /**
     * 获取
     * @return replyTotal
     */
    public Long getReplyTotal() {
        return replyTotal;
    }

    /**
     * 设置
     * @param replyTotal
     */
    public void setReplyTotal(Long replyTotal) {
        this.replyTotal = replyTotal;
    }

    /**
     * 获取
     * @return firstReplyComment
     */
    public ChildCommentDTO getFirstReplyComment() {
        return firstReplyComment;
    }

    /**
     * 设置
     * @param firstReplyComment
     */
    public void setFirstReplyComment(ChildCommentDTO firstReplyComment) {
        this.firstReplyComment = firstReplyComment;
    }

    /**
     * 获取
     * @return isTop
     */
    public Boolean getIsTop() {
        return isTop;
    }

    /**
     * 设置
     * @param isTop
     */
    public void setIsTop(Boolean isTop) {
        this.isTop = isTop;
    }

    /**
     * 获取
     * @return heat
     */
    public Long getHeat() {
        return heat;
    }

    /**
     * 设置
     * @param heat
     */
    public void setHeat(Long heat) {
        this.heat = heat;
    }

    public String toString() {
        return "CommentBasicDTO{commentId = " + commentId + ", noteId = " + noteId + ", userDTO = " + userDTO + ", content = " + content + ", imageUrl = " + imageUrl + ", createTime = " + createTime + ", likeTotal = " + likeTotal + ", replyTotal = " + replyTotal + ", firstReplyComment = " + firstReplyComment + ", isTop = " + isTop + ", heat = " + heat + "}";
    }
}



