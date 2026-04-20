package athena.insight.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_note_feature")
public class NoteFeatureDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private Byte type;

    private Long authorId;

    private String title;

    private String coverUrl;

    private Integer channelId;

    private Byte status;

    private String topicFeatureJson;

    private BigDecimal qualityScore;

    private BigDecimal hotScore;

    private Integer featureVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
