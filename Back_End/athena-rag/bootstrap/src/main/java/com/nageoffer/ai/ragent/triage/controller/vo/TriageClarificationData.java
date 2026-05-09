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

package com.nageoffer.ai.ragent.triage.controller.vo;

import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 追问动作对应的数据载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "追问动作对应的数据载荷。")
public class TriageClarificationData {

    @Schema(description = "sessionId")
    private String sessionId;

    @Builder.Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    @Builder.Default
    @Schema(description = "当前仍待补齐的槽位")
    private List<SlotCode> pendingSlots = new ArrayList<>();

    @Schema(description = "结构化追问规划")
    private QuestionPlan questionPlan;

    @Schema(description = "followUpQuestion")
    private String followUpQuestion;
}
