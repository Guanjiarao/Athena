

package com.nageoffer.ai.ragent.user.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 修改密码请求
 */
@Data
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {

    /**
     * 当前密码
     */
@Schema(description = "修改密码请求")
    private String currentPassword;

    /**
     * 新密码
     */
    @Schema(description = "新密码")
    private String newPassword;
}
