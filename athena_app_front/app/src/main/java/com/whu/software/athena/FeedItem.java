package com.whu.software.athena;

import java.io.Serializable;
import java.util.List;

/**
 * 统一的内容实体类，支持视频和图文两种类型
 */
public class FeedItem implements Serializable {
    
    // 内容类型常量
    public static final int TYPE_VIDEO = 0;  // 视频类型
    public static final int TYPE_NOTE = 1;   // 图文类型
    
    public int id;
    public int type;  // 0=视频, 1=图文
    public String title;
    public String content;  // 正文内容（图文专用）
    public String username;
    public String userAvatar;
    public int likeCount;
    public int commentCount;
    public int collectCount;
    public int coverHeight;  // 封面高度（用于瀑布流）
    
    // 视频专用字段
    public String videoUrl;
    
    // 图文专用字段
    public List<String> imageUrls;  // 多图
    public List<String> tags;  // 标签
    public String publishTime;  // 发布时间
    
    public FeedItem() {
    }
    
    // 创建视频类型的构造函数
    public static FeedItem createVideo(int id, String title, String username, 
                                      int likeCount, int commentCount, 
                                      int collectCount, String videoUrl) {
        FeedItem item = new FeedItem();
        item.id = id;
        item.type = TYPE_VIDEO;
        item.title = title;
        item.username = username;
        item.likeCount = likeCount;
        item.commentCount = commentCount;
        item.collectCount = collectCount;
        item.videoUrl = videoUrl;
        item.coverHeight = 150 + (int)(Math.random() * 100);
        return item;
    }
    
    // 创建图文类型的构造函数
    public static FeedItem createNote(int id, String title, String content,
                                     String username, int likeCount, 
                                     int commentCount, int collectCount,
                                     List<String> imageUrls, List<String> tags,
                                     String publishTime) {
        FeedItem item = new FeedItem();
        item.id = id;
        item.type = TYPE_NOTE;
        item.title = title;
        item.content = content;
        item.username = username;
        item.likeCount = likeCount;
        item.commentCount = commentCount;
        item.collectCount = collectCount;
        item.imageUrls = imageUrls;
        item.tags = tags;
        item.publishTime = publishTime;
        item.coverHeight = 150 + (int)(Math.random() * 100);
        return item;
    }
    
    public boolean isVideo() {
        return type == TYPE_VIDEO;
    }
    
    public boolean isNote() {
        return type == TYPE_NOTE;
    }
}
