

package com.nageoffer.ai.ragent.user.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "用户视图对象")
public class UserVO {

@Schema(description = "id")
    private String id;
@Schema(description = "username")
    private String username;
@Schema(description = "role")
    private String role;
@Schema(description = "avatar")
    private String avatar;
@Schema(description = "createTime")
    private Date createTime;
@Schema(description = "updateTime")
    private Date updateTime;
}
