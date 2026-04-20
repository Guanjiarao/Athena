package athena.userauth.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserAll {
    private String phone;

    private String nickName;

    private String icon;

    private Boolean priority;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long userId;

    private String city;

    private String introduction;

    private Integer fansTotal;

    private Integer followingTotal;

    private Byte gender;

    private LocalDate birthday;

    private Integer credits;

    private Byte level;


    private String contentTotal;

    private String likeTotal;

    private String collectTotal;
}
