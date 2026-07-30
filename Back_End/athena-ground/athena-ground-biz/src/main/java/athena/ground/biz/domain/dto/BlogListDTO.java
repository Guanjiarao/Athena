package athena.ground.biz.domain.dto;

import athena.athenaframework.DTO.UserDTO;
import lombok.Data;

/**
 * 广场博客列表返回DTO
 */
@Data
public class BlogListDTO {
    // 博客/笔记id
    private Long blogId;
    // 类型（视频/图文等）
    private Byte type;
    // 封面地址
    private String coverUrl;
    // 标题
    private String title;
    // 点赞数
    private Long likeTotal;
    // 审核状态
    private Byte status;
    // 审核备注
    private String reviewRemark;

    private UserDTO userDTO;

    private Integer channelId;

    private String channelName;
}
