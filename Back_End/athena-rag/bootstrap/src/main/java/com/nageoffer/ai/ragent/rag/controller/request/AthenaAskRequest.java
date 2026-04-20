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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Athena 问答请求。
 *
 * @deprecated 该请求对象仅服务于 Athena 早期 `athena-ground -> athena-rag` 过渡接口，
 * 当前问答主链路已切换到经网关访问的 RAG V3 通用接口。
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AthenaAskRequest {

    /**
     * 用户问题
     */
    private String question;

    /**
     * 用户年龄，可为空
     */
    private Integer age;
}
