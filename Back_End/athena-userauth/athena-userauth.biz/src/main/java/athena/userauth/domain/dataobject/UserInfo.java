package athena.userauth.domain.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


@NoArgsConstructor
@AllArgsConstructor
@Data
@TableName("tb_user_info")
public class UserInfo {
    @TableId("user_id")
    private Long userId;

    private String city;

    private String introduction;

    private Integer fansTotal;

    private Integer followingTotal;

    private Byte gender;

    private LocalDate birthday;

    private Integer credits;

    private Byte level;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long contentTotal;

    private Long likeTotal;

    private Long collectTotal;


}