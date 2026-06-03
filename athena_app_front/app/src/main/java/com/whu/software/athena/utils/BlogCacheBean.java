package com.whu.software.athena.utils;

public class BlogCacheBean {
    private String userName;   // 作者名字
    private String blogId;     // 文章ID
    private String title;      // 标题
    private String imageUrl;   // 封面图片URL
    private String imageId;    // 封面图片ID
    private int likeNumber;    // 点赞数
    private int height;        // 瀑布流中图片的高度（px）
    private int type;          // 帖子类型：1=图文，2=视频
    private String videoUrl;   // 视频链接（type=2 时有值）
    private String userId;     // 作者用户ID

    // 旧构造函数（兼容现有调用点）
    public BlogCacheBean(String userName, String blogId, String title, String imageUrl, String imageId, int likeNumber) {
        this.userName = userName;
        this.blogId = blogId;
        this.title = title;
        this.imageUrl = imageUrl;
        this.imageId = imageId;
        this.likeNumber = likeNumber;
        this.height = 0;
        this.type = 1; // 默认图文
        this.videoUrl = "";
    }

    // Getter 和 Setter 方法
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getBlogId() {
        return blogId;
    }

    public void setBlogId(String blogId) {
        this.blogId = blogId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public int getLikeNumber() {
        return likeNumber;
    }

    public void setLikeNumber(int likeNumber) {
        this.likeNumber = likeNumber;
    }
    public int getHeight() {
        return height;
    }

    // 新增：height的setter
    public void setHeight(int height) {
        this.height = height;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}