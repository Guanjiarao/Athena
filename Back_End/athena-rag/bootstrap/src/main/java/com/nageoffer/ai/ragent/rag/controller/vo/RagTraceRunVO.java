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
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * RAG Trace 运行记录
 */
@Data
@Builder
@Schema(description = "RAG Trace 运行记录")
public class RagTraceRunVO {

@Schema(description = "traceId")
    private String traceId;

@Schema(description = "traceName")
    private String traceName;

@Schema(description = "entryMethod")
    private String entryMethod;

@Schema(description = "conversationId")
    private String conversationId;

@Schema(description = "taskId")
    private String taskId;

@Schema(description = "userId")
    private String userId;

@Schema(description = "username")
    private String username;

@Schema(description = "status")
    private String status;

@Schema(description = "errorMessage")
    private String errorMessage;

@Schema(description = "durationMs")
    private Long durationMs;

@Schema(description = "startTime")
    private Date startTime;

@Schema(description = "endTime")
    private Date endTime;
}
