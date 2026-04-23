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

import java.util.Date;

/**
 * 关键词映射视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "关键词映射视图对象")
public class QueryTermMappingVO {

@Schema(description = "id")
    private String id;
@Schema(description = "sourceTerm")
    private String sourceTerm;
@Schema(description = "targetTerm")
    private String targetTerm;
@Schema(description = "matchType")
    private Integer matchType;
@Schema(description = "priority")
    private Integer priority;
@Schema(description = "enabled")
    private Boolean enabled;
@Schema(description = "remark")
    private String remark;
@Schema(description = "createTime")
    private Date createTime;
@Schema(description = "updateTime")
    private Date updateTime;
}
