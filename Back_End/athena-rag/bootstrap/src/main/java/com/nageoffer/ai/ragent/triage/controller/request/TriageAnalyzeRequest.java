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

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 就医助手分析请求。
 *
 * <p>sessionId 可由前端带入，用于串联一轮问诊上下文；
 * 如果前端不传，后端会自动生成。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageAnalyzeRequest {

    /**
     * 会话 ID，可为空。
     */
    private String sessionId;

    /**
     * 用户原始输入。
     */
    @NotBlank(message = "userInput 不能为空")
    private String userInput;
}
