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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Athena 引用笔记。
 *
 * @deprecated 该对象仅服务于 Athena 早期过渡问答接口，
 * 当前主链路已切换为通过网关直接调用 RAG V3 通用问答接口。
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AthenaNoteReferenceVO {

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 笔记标题
     */
    private String title;

    /**
     * 片段内容
     */
    private String snippet;

    /**
     * 相关性得分
     */
    private Float score;
}
