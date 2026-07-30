package athena.ground.biz.domain.dataobject;

import java.time.LocalDateTime;

public class NoteBasicDO {
    private Long noteId;

    private Long userId;

    private String title;

    private String coverUrl;

    private Byte type;

    private Byte status;

    private String reviewRemark;

    private LocalDateTime reviewTime;

    private Long reviewerId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer channelId;

    private String channelName;

    public NoteBasicDO() {
    }

    public NoteBasicDO(Long noteId, Long userId, String title, String coverUrl, Byte type, Byte status,
                       String reviewRemark, LocalDateTime reviewTime, Long reviewerId,
                       LocalDateTime createTime, LocalDateTime updateTime, Integer channelId, String channelName) {
        this.noteId = noteId;
        this.userId = userId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.type = type;
        this.status = status;
        this.reviewRemark = reviewRemark;
        this.reviewTime = reviewTime;
        this.reviewerId = reviewerId;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.channelId = channelId;
        this.channelName = channelName;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Byte getType() {
        return type;
    }

    public void setType(Byte type) {
        this.type = type;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public String getReviewRemark() {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark) {
        this.reviewRemark = reviewRemark;
    }

    public LocalDateTime getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(LocalDateTime reviewTime) {
        this.reviewTime = reviewTime;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
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

    public Integer getChannelId() {
        return channelId;
    }

    public void setChannelId(Integer channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    @Override
    public String toString() {
        return "NoteBasicDO{" +
                "noteId=" + noteId +
                ", userId=" + userId +
                ", title='" + title + '\'' +
                ", coverUrl='" + coverUrl + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", reviewRemark='" + reviewRemark + '\'' +
                ", reviewTime=" + reviewTime +
                ", reviewerId=" + reviewerId +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", channelId=" + channelId +
                ", channelName='" + channelName + '\'' +
                '}';
    }
}
