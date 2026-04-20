package athena.insight.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_topic")
public class TopicDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String topicCode;

    private String topicName;

    private Long parentId;

    private Byte topicType;

    private Byte status;

    private Integer sort;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
