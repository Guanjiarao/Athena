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

package com.nageoffer.ai.ragent.triage.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 就医助手统一分析响应。
 *
 * <p>按照前端约定，接口始终返回统一四元组：
 * action + data + message + riskLevel。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageAnalyzeResponse {

    /**
     * 编排器动作。
     */
    private String action;

    /**
     * 动作对应的结构化数据载荷。
     */
    private Object data;

    /**
     * 前端可直接展示的摘要消息。
     */
    private String message;

    /**
     * 风险等级；未评估时为 0。
     */
    private Integer riskLevel;
}
