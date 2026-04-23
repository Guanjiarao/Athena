/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
