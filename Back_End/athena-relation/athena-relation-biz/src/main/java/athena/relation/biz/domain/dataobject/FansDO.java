package athena.relation.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_fans")
public class FansDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long fansUserId;

    private LocalDateTime createTime;
}
