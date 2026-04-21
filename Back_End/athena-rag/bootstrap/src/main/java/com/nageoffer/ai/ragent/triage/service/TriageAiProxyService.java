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

package com.nageoffer.ai.ragent.triage.service;

import com.nageoffer.ai.ragent.triage.controller.request.ClientChatCompletionRequest;
import com.nageoffer.ai.ragent.triage.controller.request.VisionAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.ClientChatCompletionResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.VisionAnalyzeResponse;

/**
 * 面向前端的 AI 代理服务。
 *
 * <p>用于彻底下线前端 API Key，把文本和视觉类模型调用统一收口到后端。</p>
 */
public interface TriageAiProxyService {

    /**
     * 代理文本补全能力。
     */
    ClientChatCompletionResponse complete(ClientChatCompletionRequest request);

    /**
     * 代理视觉分析能力。
     */
    VisionAnalyzeResponse analyzeVision(VisionAnalyzeRequest request);
}
