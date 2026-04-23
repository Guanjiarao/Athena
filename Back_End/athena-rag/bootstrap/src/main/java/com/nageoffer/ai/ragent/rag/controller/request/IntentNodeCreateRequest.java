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

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "IntentNodeCreateRequest请求参数")
public class IntentNodeCreateRequest {

@Schema(description = "kbId")
    private String kbId;
@Schema(description = "intentCode")
    private String intentCode;
@Schema(description = "name")
    private String name;
    /**
     * 0=DOMAIN,1=CATEGORY,2=TOPIC
     */
    @Schema(description = "0=DOMAIN,1=CATEGORY,2=TOPIC")
    private Integer level;
@Schema(description = "parentCode")
    private String parentCode;
@Schema(description = "description")
    private String description;
@Schema(description = "examples")
    private List<String> examples;
@Schema(description = "mcpToolId")
    private String mcpToolId;
@Schema(description = "topK")
    private Integer topK;
@Schema(description = "kind")
    private Integer kind;
@Schema(description = "sortOrder")
    private Integer sortOrder;
@Schema(description = "enabled")
    private Integer enabled;

    /**
     * 短规则片段（可选）
     */
    @Schema(description = "短规则片段（可选）")
    private String promptSnippet;

    /**
     * 场景用的完整 Prompt 模板（可选）
     */
    @Schema(description = "场景用的完整 Prompt 模板（可选）")
    private String promptTemplate;

    /**
     * 参数提取提示词模板（MCP模式专属）
     */
    @Schema(description = "参数提取提示词模板（MCP模式专属）")
    private String paramPromptTemplate;
}
