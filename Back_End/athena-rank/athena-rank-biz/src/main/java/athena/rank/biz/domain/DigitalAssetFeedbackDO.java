package athena.rank.biz.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_digital_asset_feedback")
public class DigitalAssetFeedbackDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String conversationId;

    private String messageId;

    private Integer vote;

    private String reason;

    private String comment;

    private String ragMessageRole;

    private String ragMessageContent;

    private String ragThinkingContent;

    private Integer ragThinkingDuration;

    private LocalDateTime ragMessageCreateTime;

    private String auditStatus;

    private Long auditUserId;

    private LocalDateTime auditTime;

    private String auditRemark;

    private Integer assetScore;

    private Long assetRecordId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
