package athena.ground.biz.domain.dto;

import athena.athenaframework.DTO.UserDTO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客详情返回DTO（包含所有字段）
 */
@Data
public class BlogDetailDTO {
    private Long id;                // 博客ID（对应blog_id）
    private String title;           // 标题

    private UserDTO userDTO;        // 用户信息
    private Long topicId;           // 话题ID
    private String topicName;       // 话题名称
    private Boolean isTop;          // 是否置顶
    private Byte type;              // 类型（视频/图文）
    private Long likeTotal;         // 点赞数
    private Long commentTotal;      // 评论数
    private Long collectTotal;      // 收藏数
    private String imgUrls;         // 图片地址
    private String videoUrl;        // 视频地址
    private Byte visible;           // 可见性
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
    private Byte status;            // 审核状态
    private String reviewRemark;    // 审核备注
    private LocalDateTime reviewTime; // 审核时间
    private Long reviewerId;        // 审核人ID
    private String content;         // 正文
    private Integer channelId;
    private String channelName;
}
