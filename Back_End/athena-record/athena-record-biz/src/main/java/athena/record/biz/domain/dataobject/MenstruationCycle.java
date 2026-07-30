package athena.record.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("menstruation_cycle")
public class MenstruationCycle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer durationDays;

    private Integer cycleLength;

    private Integer isPredicted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
