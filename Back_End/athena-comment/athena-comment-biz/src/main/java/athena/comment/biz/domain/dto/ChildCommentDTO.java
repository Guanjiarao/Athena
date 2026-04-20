package athena.comment.biz.domain.dto;

import athena.athenaframework.DTO.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
public class ChildCommentDTO {
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

    private Long parentId;

    private Long replyCommentId;

    private Long replyUserId;

    private String replyUserName;

    private Long heat;

    public ChildCommentDTO() {
    }

    public ChildCommentDTO(Long commentId, Long noteId, UserDTO userDTO, String content, String imageUrl, LocalDateTime createTime, Long likeTotal, Long parentId, Long replyCommentId, Long replyUserId, String replyUserName, Long heat) {
        this.commentId = commentId;
        this.noteId = noteId;
        this.userDTO = userDTO;
        this.content = content;
        this.imageUrl = imageUrl;
        this.createTime = createTime;
        this.likeTotal = likeTotal;
        this.parentId = parentId;
        this.replyCommentId = replyCommentId;
        this.replyUserId = replyUserId;
        this.replyUserName = replyUserName;
        this.heat = heat;
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
     * @return parentId
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 设置
     * @param parentId
     */
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    /**
     * 获取
     * @return replyCommentId
     */
    public Long getReplyCommentId() {
        return replyCommentId;
    }

    /**
     * 设置
     * @param replyCommentId
     */
    public void setReplyCommentId(Long replyCommentId) {
        this.replyCommentId = replyCommentId;
    }

    /**
     * 获取
     * @return replyUserId
     */
    public Long getReplyUserId() {
        return replyUserId;
    }

    /**
     * 设置
     * @param replyUserId
     */
    public void setReplyUserId(Long replyUserId) {
        this.replyUserId = replyUserId;
    }

    /**
     * 获取
     * @return replyUserName
     */
    public String getReplyUserName() {
        return replyUserName;
    }

    /**
     * 设置
     * @param replyUserName
     */
    public void setReplyUserName(String replyUserName) {
        this.replyUserName = replyUserName;
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
        return "ChildCommentDTO{commentId = " + commentId + ", noteId = " + noteId + ", userDTO = " + userDTO + ", content = " + content + ", imageUrl = " + imageUrl + ", createTime = " + createTime + ", likeTotal = " + likeTotal + ", parentId = " + parentId + ", replyCommentId = " + replyCommentId + ", replyUserId = " + replyUserId + ", replyUserName = " + replyUserName + ", heat = " + heat + "}";
    }
}
