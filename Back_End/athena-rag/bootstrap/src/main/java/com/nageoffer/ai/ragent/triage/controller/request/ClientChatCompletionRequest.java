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

package com.nageoffer.ai.ragent.triage.controller.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端文本补全代理请求。
 *
 * <p>该请求用于替代客户端直接持有 API Key 的模式，
 * 让文本类大模型请求统一收口到后端。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientChatCompletionRequest {

    /**
     * 前端发送的消息序列。
     */
    @Builder.Default
    @NotEmpty(message = "messages 不能为空")
    private List<ClientChatMessage> messages = new ArrayList<>();

    /**
     * 是否希望模型尽量返回 JSON。
     */
    private Boolean jsonMode;

    /**
     * 前端兼容字段，当前由后端接管模型选择，因此仅保留不强依赖。
     */
    private String model;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientChatMessage {

        /**
         * system / user / assistant
         */
        private String role;

        /**
         * 文本内容。
         */
        private String content;
    }
}
