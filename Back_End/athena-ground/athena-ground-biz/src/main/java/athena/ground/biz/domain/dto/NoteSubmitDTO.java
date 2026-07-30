package athena.ground.biz.domain.dto;

import lombok.Data;
import java.util.List;

/**
 * 上传笔记的请求参数DTO
 */
@Data
public class NoteSubmitDTO {
    // 用户ID
    private Long userId;
    // 标题
    private String title;
    // 话题ID
    private Long topicId;
    // 话题名称
    private String topicName;
    // 是否置顶（true/false）
    private Boolean isTop;
    // 笔记类型（1-图文，2-视频等,不区分年龄段）,（3、4）表示0到12岁，(5,6)表示12～22岁，(7,8)表示(22～55)，(9，10)表示55+
    private Byte type;
    // 封面URL
    private String coverUrl;
    // 图片URL列表（多个用逗号分隔）
    private List<String> imgUrls;
    // 视频URL
    private String videoUrl;
    // 可见性（1-公开，2-仅自己可见）
    private Byte visible;
    //文本
    private String content;

    private Integer channelId;
    private String channelName;


}