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



import io.swagger.v3.oas.annotations.media.Schema;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 高风险阻断动作对应的数据载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "高风险阻断动作对应的数据载荷。")
public class TriageWarningData {

@Schema(description = "sessionId")
    private String sessionId;

@Schema(description = "riskAssessment")
    private RiskLevel riskAssessment;

    @Builder.Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

@Schema(description = "warningText")
    private String warningText;

@Schema(description = "emergencyGuidance")
    private String emergencyGuidance;
}
