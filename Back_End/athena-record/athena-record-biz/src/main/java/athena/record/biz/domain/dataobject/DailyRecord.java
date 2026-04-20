package athena.record.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("daily_record")
public class DailyRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate recordDate; // 推荐使用 LocalDate 处理日期
    private Integer modeType;     // 1经期 2备孕 3怀孕
    private Integer recordItemId;
    private String recordValue;
}