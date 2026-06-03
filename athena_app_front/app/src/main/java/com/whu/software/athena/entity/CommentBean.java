package com.whu.software.athena.entity;

import com.google.gson.annotations.SerializedName;

public class CommentBean {

    @SerializedName("commentId")
    private long commentId;

    @SerializedName("noteId")
    private long noteId;

    @SerializedName("userDTO")
    private UserDTO userDTO;

    @SerializedName("content")
    private String content;

    @SerializedName("imageUrl")
    private String imageUrl;

    @SerializedName("createTime")
    private String createTime;

    @SerializedName("likeTotal")
    private int likeTotal;

    @SerializedName("replyTotal")
    private int replyTotal;

    @SerializedName("isTop")
    private boolean isTop;

    @SerializedName("heat")
    private int heat;

    // only present on reply items
    @SerializedName("parentId")
    private long parentId;

    @SerializedName("replyCommentId")
    private long replyCommentId;

    @SerializedName("replyUserId")
    private long replyUserId;

    @SerializedName("replyUserName")
    private String replyUserName;

    @SerializedName("firstReplyComment")
    private CommentBean firstReplyComment;

    // ── 本地临时对象工厂（发布成功后乐观插入用）─────────────────────
    /**
     * 构造一条用于前端乐观插入的临时评论对象。
     *
     * @param content         评论内容
     * @param nickName        当前用户昵称
     * @param replyCommentId  被回复的评论 ID，0 表示主评论
     */
    public static CommentBean createLocal(String content, String nickName, long replyCommentId) {
        String time = new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date());
        UserDTO user = new UserDTO();
        user.setNickName(nickName != null ? nickName : "我");
        return new CommentBean(content, replyCommentId, time, user);
    }

    /** 用于 createLocal 的内部构造函数 */
    private CommentBean(String content, long replyCommentId, String createTime, UserDTO userDTO) {
        this.content        = content;
        this.replyCommentId = replyCommentId;
        this.createTime     = createTime;
        this.userDTO        = userDTO;
    }

    /** Gson 反序列化用的无参构造 */
    private CommentBean() {}

    // ── Getters ──────────────────────────────────────────────────

    public long getCommentId() { return commentId; }
    public long getNoteId() { return noteId; }
    public UserDTO getUserDTO() { return userDTO; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public String getCreateTime() { return createTime; }
    public int getLikeTotal() { return likeTotal; }
    public int getReplyTotal() { return replyTotal; }
    public boolean isTop() { return isTop; }
    public int getHeat() { return heat; }
    public long getParentId() { return parentId; }
    public long getReplyCommentId() { return replyCommentId; }
    public long getReplyUserId() { return replyUserId; }
    public String getReplyUserName() { return replyUserName; }
    public CommentBean getFirstReplyComment() { return firstReplyComment; }

    // ── Inner class: UserDTO ─────────────────────────────────────

    public static class UserDTO {
        @SerializedName("id")
        private long id;

        @SerializedName("nickName")
        private String nickName;

        @SerializedName("icon")
        private String icon;

        @SerializedName("priority")
        private boolean priority;

        public long getId() { return id; }
        public String getNickName() { return nickName; }
        public String getIcon() { return icon; }
        public boolean isPriority() { return priority; }
        public void setNickName(String n) { this.nickName = n; }
    }
}