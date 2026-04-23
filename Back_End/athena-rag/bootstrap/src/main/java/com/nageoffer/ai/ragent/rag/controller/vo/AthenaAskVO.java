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

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Athena 问答响应。
 *
 * @deprecated 该响应对象仅对应 Athena 早期过渡问答接口，
 * 当前主链路已切换为通过网关直接调用 RAG V3 通用问答接口。
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Athena 问答响应。")
public class AthenaAskVO {

    /**
     * 回答内容
     */
@Schema(description = "Athena 问答响应。")
    private String answer;

    /**
     * 实际生效年龄
     */
    @Schema(description = "实际生效年龄")
    private Integer resolvedAge;

    /**
     * 命中的知识库编码列表
     */
    @Schema(description = "命中的知识库编码列表")
    private List<String> kbCodes;

    /**
     * 引用笔记列表
     */
    @Schema(description = "引用笔记列表")
    private List<AthenaNoteReferenceVO> references;
}
