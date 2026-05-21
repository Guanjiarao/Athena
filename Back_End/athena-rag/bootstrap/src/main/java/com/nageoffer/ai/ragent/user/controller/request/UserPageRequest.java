

package com.nageoffer.ai.ragent.user.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 用户分页查询请求
 */
@Data
@Schema(description = "用户分页查询请求")
public class UserPageRequest extends Page {

    /**
     * 关键词（支持匹配用户名/角色）
     */
@Schema(description = "用户分页查询请求")
    private String keyword;
}
