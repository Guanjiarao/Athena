package com.whu.software.athena.entity;

public class TimelineEntity {
    private String imageUrl;
    private int drawableResId; // 0 表示无本地资源，使用 imageUrl；非 0 优先使用本地图片
    private String title;
    private String description;
    private String timeLabel;

    /** 使用网络 URL 的构造函数 */
    public TimelineEntity(String imageUrl, String title, String description, String timeLabel) {
        this.imageUrl = imageUrl;
        this.drawableResId = 0;
        this.title = title;
        this.description = description;
        this.timeLabel = timeLabel;
    }

    /** 使用本地 drawable 资源的构造函数 */
    public TimelineEntity(int drawableResId, String title, String description, String timeLabel) {
        this.imageUrl = null;
        this.drawableResId = drawableResId;
        this.title = title;
        this.description = description;
        this.timeLabel = timeLabel;
    }

    public String getImageUrl() { return imageUrl; }
    public int getDrawableResId() { return drawableResId; }
    public boolean hasLocalDrawable() { return drawableResId != 0; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTimeLabel() { return timeLabel; }
}
