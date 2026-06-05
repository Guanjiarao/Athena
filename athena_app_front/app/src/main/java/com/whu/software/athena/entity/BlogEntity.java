package com.whu.software.athena.entity;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 博客实体类 - 使用 @SerializedName 映射后端最新字段
 * 保留旧的 Getter 方法以兼容现有 Adapter
 */
public class BlogEntity implements Serializable {

    // 列表接口返回的新字段
    @SerializedName("blogId")
    private int blogId;              // 文章ID (列表接口最新字段)

    @SerializedName("noteId")
    private int noteId;              // 文章ID (后端旧字段，兼容保留)

    @SerializedName("id")
    private int id;                  // 详情接口返回的ID字段

    @SerializedName("coverUrl")
    private String coverUrl;         // 封面图 (后端新字段)

    @SerializedName(value = "liked", alternate = {"likeTotal", "like_number", "likeCount"})
    private int liked;            // 点赞数 (后端新字段)

    @SerializedName(value = "collectTotal", alternate = {"collect_number", "collectCount"})
    private int collectTotal;        // 收藏数 (后端新字段)

    @SerializedName("title")
    private String title;            // 标题

    /**
     * 详情配图 URL：后端为逗号分隔字符串，如 "url1,url2,url3"。
     * 与 {@link #coverUrl} 合并为完整图集见 {@link #mergeCoverAndCommaDetailImages(String, String)}。
     */
    @SerializedName("imgUrls")
    private String imgUrlsStr;

    @SerializedName("content")
    private String content;          // 正文内容

    @SerializedName("comments")
    private int comments;            // 评论数 (后端新字段)

    @SerializedName("type")
    private int type;                // 帖子类型：1=图文，2=视频

    @SerializedName("videoUrl")
    private String videoUrl;         // 视频链接（type=2 时有值）

    // ── 详情接口返回的用户信息（与评论接口 userDTO 结构对齐）──────────────
    /** 详情接口返回的完整用户对象，字段名与评论接口保持一致 */
    @SerializedName("userDTO")
    private AuthorDTO userDTO;

    /** 部分后端版本直接把用户信息平铺在博客对象里，以下字段做兜底 */
    @SerializedName("nickName")
    private String nickName;

    @SerializedName("icon")
    private String authorIcon;

    @SerializedName("userName")
    private String userName;

    // ── 内嵌用户信息类（与评论 UserDTO 结构相同）────────────────────────
    public static class AuthorDTO implements Serializable {
        @SerializedName("userId")
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
    }

    public AuthorDTO getUserDTO() { return userDTO; }
    public String getAuthorIcon() {
        if (userDTO != null && userDTO.getIcon() != null && !userDTO.getIcon().isEmpty()) {
            return userDTO.getIcon();
        }
        return authorIcon;
    }

    // 无参构造函数
    public BlogEntity() {
    }

    // ========== 新字段的 Getter/Setter ==========

    public int getBlogId() {
        return blogId;
    }

    public void setBlogId(int blogId) {
        this.blogId = blogId;
    }

    public int getNoteId() {
        return noteId;
    }

    public void setNoteId(int noteId) {
        this.noteId = noteId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public int getLiked() {
        return liked;
    }

    public void setLiked(int liked) {
        this.liked = liked;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImgUrlsStr() {
        return imgUrlsStr;
    }

    public void setImgUrlsStr(String imgUrlsStr) {
        this.imgUrlsStr = imgUrlsStr;
    }

    /**
     * 解析逗号分隔的详情图字段（不含封面）。
     */
    public static List<String> parseCommaSeparatedImgUrls(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return out;
        }
        for (String u : raw.split(",")) {
            String cleanUrl = u.replace("\"", "").replace("[", "").replace("]", "").trim();
            if (!cleanUrl.isEmpty() && !"null".equalsIgnoreCase(cleanUrl) && !out.contains(cleanUrl)) {
                out.add(cleanUrl);
            }
        }
        return out;
    }

    /**
     * 组装完整图集：封面第一张，再追加详情区 URL（逗号分隔），与封面去重。
     */
    public static List<String> mergeCoverAndCommaDetailImages(String coverUrl, String imgUrlsRaw) {
        List<String> finalImages = new ArrayList<>();
        if (coverUrl != null && !coverUrl.trim().isEmpty() && !"null".equalsIgnoreCase(coverUrl.trim())) {
            finalImages.add(coverUrl.trim());
        }
        if (imgUrlsRaw != null && !imgUrlsRaw.trim().isEmpty() && !"null".equalsIgnoreCase(imgUrlsRaw.trim())) {
            for (String u : imgUrlsRaw.split(",")) {
                String cleanUrl = u.replace("\"", "").replace("[", "").replace("]", "").trim();
                if (!cleanUrl.isEmpty() && !finalImages.contains(cleanUrl)) {
                    finalImages.add(cleanUrl);
                }
            }
        }
        return finalImages;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getComments() {
        return comments;
    }

    public void setComments(int comments) {
        this.comments = comments;
    }

    public int getCollectTotal() {
        return collectTotal;
    }

    public void setCollectTotal(int collectTotal) {
        this.collectTotal = collectTotal;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Long getUserId() {
        if (userDTO != null && userDTO.getId() != 0) {
            return userDTO.getId();
        }
        return null;
    }

    // ========== 兼容旧代码的 Getter 方法（映射到新字段）==========

    /**
     * 兼容方法：返回文章ID（优先 blogId，其次 noteId，最后 id）
     */
    public String getBlog_id() {
        if (blogId != 0) {
            return String.valueOf(blogId);
        }
        if (noteId != 0) {
            return String.valueOf(noteId);
        }
        return String.valueOf(id);
    }

    /**
     * 兼容方法：返回封面图（映射到 coverUrl）
     */
    public String getImage_url() {
        return coverUrl;
    }

    /**
     * 兼容方法：返回点赞数（映射到 liked）
     */
    public int getLike_number() {
        return liked;
    }

    /**
     * 兼容方法：返回收藏数（映射到 collectTotal）
     */
    public int getCollect_number() {
        return collectTotal;
    }

    /**
     * 仅解析详情字段 {@link #imgUrlsStr}（逗号分隔），不含封面。
     * 详情页完整图集请使用 {@link #mergeCoverAndCommaDetailImages(String, String)}。
     */
    public List<String> getParsedImgUrls() {
        return parseCommaSeparatedImgUrls(imgUrlsStr);
    }

    /**
     * 兼容旧调用方：与 {@link #getParsedImgUrls()} 相同（详情区 URL 列表）。
     */
    public List<String> getPhoto() {
        return getParsedImgUrls();
    }

    /**
     * 返回作者昵称。优先级：
     *   1. userDTO.nickName（详情接口嵌套对象）
     *   2. nickName（平铺字段）
     *   3. userName（备用平铺字段）
     *   4. 兜底 "未知用户"
     */
    public String getUser_name() {
        android.util.Log.d("DETAIL_DEBUG", "[BlogEntity.getUser_name] userDTO=" + userDTO
                + ", userDTO.nickName=" + (userDTO != null ? userDTO.getNickName() : "N/A")
                + ", nickName=" + nickName
                + ", userName=" + userName);
        if (userDTO != null && userDTO.getNickName() != null && !userDTO.getNickName().isEmpty()) {
            android.util.Log.d("DETAIL_DEBUG", "[BlogEntity.getUser_name] → 返回 userDTO.nickName=\"" + userDTO.getNickName() + "\"");
            return userDTO.getNickName();
        }
        if (nickName != null && !nickName.isEmpty()) {
            android.util.Log.d("DETAIL_DEBUG", "[BlogEntity.getUser_name] → 返回平铺 nickName=\"" + nickName + "\"");
            return nickName;
        }
        if (userName != null && !userName.isEmpty()) {
            android.util.Log.d("DETAIL_DEBUG", "[BlogEntity.getUser_name] → 返回平铺 userName=\"" + userName + "\"");
            return userName;
        }
        android.util.Log.w("DETAIL_DEBUG", "[BlogEntity.getUser_name] → ⚠️ 三个字段均为空，返回兜底\"未知用户\"");
        return "未知用户";
    }

    /**
     * 兼容方法：返回图片ID（优先 blogId，其次 noteId，最后 id）
     */
    public String getImage_id() {
        if (blogId != 0) {
            return "img_" + blogId;
        }
        if (noteId != 0) {
            return "img_" + noteId;
        }
        return "img_" + id;
    }

    @Override
    public String toString() {
        return "BlogEntity{" +
                "blogId=" + blogId +
                ", noteId=" + noteId +
                ", id=" + id +
                ", coverUrl='" + coverUrl + '\'' +
                ", liked=" + liked +
                ", collectTotal=" + collectTotal +
                ", title='" + title + '\'' +
                ", imgUrlsStr='" + imgUrlsStr + '\'' +
                ", content='" + content + '\'' +
                ", comments=" + comments +
                '}';
    }
}
