

package com.nageoffer.ai.ragent.user.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户更新请求
 */
@Data
@Schema(description = "用户更新请求")
public class UserUpdateRequest {

    /**
     * 用户名
     */
@Schema(description = "用户更新请求")
    private String username;

    /**
     * 新密码（可选）
     */
    @Schema(description = "新密码（可选）")
    private String password;

    /**
     * 角色（admin/user）
     */
    @Schema(description = "角色（admin/user）")
    private String role;

    /**
     * 头像地址
     */
    @Schema(description = "头像地址")
    private String avatar;
}
