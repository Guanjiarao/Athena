

package com.nageoffer.ai.ragent.user.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LoginVO返回对象")
public class LoginVO {

@Schema(description = "userId")
    private String userId;

@Schema(description = "role")
    private String role;

@Schema(description = "token")
    private String token;

@Schema(description = "avatar")
    private String avatar;
}
