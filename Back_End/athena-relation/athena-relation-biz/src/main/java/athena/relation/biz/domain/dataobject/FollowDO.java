package athena.relation.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_follow")
public class FollowDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long followUserId;

    private LocalDateTime createTime;
}
