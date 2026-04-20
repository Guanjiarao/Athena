package athena.ground.biz.domain.dataobject;


import java.time.LocalDateTime;

public class NoteContentDO {
    private Long contentId;

    private Long noteId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String content;



    public Long getContentId() {
        return contentId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getupdateTime() {
        return updateTime;
    }

    public void setupdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}