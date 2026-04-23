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

package com.nageoffer.ai.ragent.rag.controller;





import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.controller.request.AthenaAskRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AthenaAskVO;
import com.nageoffer.ai.ragent.rag.service.AthenaRagAskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向 Athena 的 RAG 问答接口。
 *
 * @deprecated 该接口为 Athena 早期从 `athena-ground` 侧接入 `athena-rag` 的过渡接口，
 * 当前主链路已切换为通过网关直接调用 RAG V3 通用问答接口，不再继续扩展。
 */
@Deprecated
@RestController
@RequiredArgsConstructor
@Tag(name = "RAG接口")
public class AthenaRagAskController {

    private final AthenaRagAskService athenaRagAskService;

    @PostMapping("/athena/rag/ask")
    @Operation(summary = "接口操作")
    public Result<AthenaAskVO> ask(@RequestBody AthenaAskRequest request) {
        return Results.success(athenaRagAskService.ask(request));
    }
}
