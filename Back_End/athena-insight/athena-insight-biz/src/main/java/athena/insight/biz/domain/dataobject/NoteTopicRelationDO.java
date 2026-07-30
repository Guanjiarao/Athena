package athena.insight.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_note_topic_relation")
public class NoteTopicRelationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private Long topicId;

    private BigDecimal weight;

    private Byte sourceType;

    private LocalDateTime createTime;
}
