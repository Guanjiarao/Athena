

package com.nageoffer.ai.ragent.user.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "CurrentUserVO返回对象")
public class CurrentUserVO {

@Schema(description = "userId")
    private String userId;

@Schema(description = "username")
    private String username;

@Schema(description = "role")
    private String role;

@Schema(description = "avatar")
    private String avatar;
}
