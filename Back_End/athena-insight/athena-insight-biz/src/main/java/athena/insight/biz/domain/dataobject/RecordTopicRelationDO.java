package athena.insight.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_record_topic_relation")
public class RecordTopicRelationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Byte modeType;

    private Integer recordItemId;

    private String recordValuePattern;

    private Long topicId;

    private BigDecimal weight;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
