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

package com.nageoffer.ai.ragent.rag.service;

import com.nageoffer.ai.ragent.rag.controller.request.AthenaAskRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AthenaAskVO;

/**
 * 面向 Athena 的问答服务。
 *
 * @deprecated 该服务接口对应 Athena 早期从 `athena-ground` 进入 `athena-rag` 的直连接入方案，
 * 当前已改为通过网关直接调用 RAG V3 通用问答链路，不再作为后续主方向继续演进。
 */
@Deprecated
public interface AthenaRagAskService {

    /**
     * 执行一次面向 Athena 的知识问答
     *
     * @param request 问答请求
     * @return 问答结果
     */
    AthenaAskVO ask(AthenaAskRequest request);
}
