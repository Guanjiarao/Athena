package athena.insight.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_user_insight")
public class UserInsightDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String healthFocusJson;

    private String contentFocusJson;

    private String riskTagsJson;

    private String recommendationReasonsJson;

    private LocalDateTime generatedAt;

    private Integer insightVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
