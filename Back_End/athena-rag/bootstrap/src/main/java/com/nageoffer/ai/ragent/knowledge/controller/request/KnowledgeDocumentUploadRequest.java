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

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "KnowledgeDocumentUploadRequest请求参数")
public class KnowledgeDocumentUploadRequest {

    /**
     * 来源类型：file / url
     */
    @Schema(description = "来源类型：file / url")
    private String sourceType;

    /**
     * 来源位置（URL）
     */
    @Schema(description = "来源位置（URL）")
    private String sourceLocation;

    /**
     * 是否开启定时拉取
     */
    @Schema(description = "是否开启定时拉取")
    private Boolean scheduleEnabled;

    /**
     * 定时表达式（cron）
     */
    @Schema(description = "定时表达式（cron）")
    private String scheduleCron;

    /**
     * 处理模式：chunk / pipeline
     * - chunk: 使用分块策略直接分块
     * - pipeline: 使用数据通道进行清洗处理
     */
    @Schema(description = "处理模式：chunk / pipeline")
    private String processMode;

    /**
     * 分块策略：fixed_size / structure_aware
     * 仅在 processMode=chunk 时有效
     */
    @Schema(description = "分块策略：fixed_size / structure_aware")
    private String chunkStrategy;

    /**
     * 分块参数JSON，processMode=chunk 时必传
     * 如 {"chunkSize":512,"overlapSize":128} 或 {"targetChars":1400,"maxChars":1800,"minChars":600,"overlapChars":0}
     */
    @Schema(description = "分块参数JSON，processMode=chunk 时必传")
    private String chunkConfig;

    /**
     * 数据通道（Pipeline）ID
     * 仅在 processMode=pipeline 时有效
     */
    @Schema(description = "数据通道（Pipeline）ID")
    private String pipelineId;
}
