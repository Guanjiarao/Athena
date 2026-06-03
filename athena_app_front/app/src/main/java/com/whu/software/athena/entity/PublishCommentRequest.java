package com.whu.software.athena.entity;

import com.google.gson.annotations.SerializedName;

public class PublishCommentRequest {

    @SerializedName("blogId")
    private int blogId;

    @SerializedName("content")
    private String content;

    @SerializedName("imageUrl")
    private String imageUrl;

    /** 0 = 直接评论文章；非 0 = 被回复的评论 ID */
    @SerializedName("replyCommentId")
    private long replyCommentId;

    /** 被回复用户的 userId；直接评论时传 0 */
    @SerializedName("replyUserId")
    private long replyUserId;

    /** 被回复用户的昵称；直接评论时传空串 */
    @SerializedName("replyUserName")
    private String replyUserName;

    /**
     * 所属根评论的 ID（楼层概念）。
     * <ul>
     *   <li>直接评论文章 → 0</li>
     *   <li>回复主评论   → 主评论的 commentId</li>
     *   <li>回复子评论   → 继承子评论的 parentId（即最顶层主评论的 ID）</li>
     * </ul>
     */
    @SerializedName("parentId")
    private long parentId;

    public PublishCommentRequest() {}

    // ── Getters / Setters ─────────────────────────────────────────

    public int getBlogId() { return blogId; }
    public void setBlogId(int blogId) { this.blogId = blogId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public long getReplyCommentId() { return replyCommentId; }
    public void setReplyCommentId(long replyCommentId) { this.replyCommentId = replyCommentId; }

    public long getReplyUserId() { return replyUserId; }
    public void setReplyUserId(long replyUserId) { this.replyUserId = replyUserId; }

    public String getReplyUserName() { return replyUserName; }
    public void setReplyUserName(String replyUserName) { this.replyUserName = replyUserName; }

    public long getParentId() { return parentId; }
    public void setParentId(long parentId) { this.parentId = parentId; }
}
