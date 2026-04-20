package athena.insight.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_user_feature_snapshot")
public class UserFeatureSnapshotDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String baseFeatureJson;

    private String behaviorFeatureJson;

    private String healthFeatureJson;

    private LocalDateTime generatedAt;

    private Integer featureVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
