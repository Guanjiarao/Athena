package athena.athenaframework.DTO;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * 用户基本信息表 DTO
 * 对应数据库表：tb_user
 * </p>
 *
 * @author YourName
 * @since 2026-01-23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    /**
     * 主键
     */

    private Long userId;

    /**
     * 昵称，默认是用户id
     */
    private String nickName;

    /**
     * 人物头像
     */
    private String icon;

    /**
     * 用户优先级，0或1，0代表普通用户，1代表管理员
     */
    private Boolean priority;



}