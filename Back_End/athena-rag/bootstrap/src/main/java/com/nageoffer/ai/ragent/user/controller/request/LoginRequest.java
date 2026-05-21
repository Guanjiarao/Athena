

package com.nageoffer.ai.ragent.user.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "LoginRequest请求参数")
public class LoginRequest {

@Schema(description = "username")
    private String username;

@Schema(description = "password")
    private String password;
}
