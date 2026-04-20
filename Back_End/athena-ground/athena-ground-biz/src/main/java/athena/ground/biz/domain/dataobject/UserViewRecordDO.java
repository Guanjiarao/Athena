package athena.ground.biz.domain.dataobject;

import java.time.LocalDateTime;

/**
 * 用户浏览记录 DO，对应表 user_view_record
 */
public class UserViewRecordDO {

    private Long id;
    private Long userId;
    private Long noteId;
    private LocalDateTime firstViewTime;
    private LocalDateTime lastViewTime;
    private Integer viewCount;
    private Integer duration;

    public UserViewRecordDO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public LocalDateTime getFirstViewTime() {
        return firstViewTime;
    }

    public void setFirstViewTime(LocalDateTime firstViewTime) {
        this.firstViewTime = firstViewTime;
    }

    public LocalDateTime getLastViewTime() {
        return lastViewTime;
    }

    public void setLastViewTime(LocalDateTime lastViewTime) {
        this.lastViewTime = lastViewTime;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
