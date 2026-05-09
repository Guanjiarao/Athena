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

package com.nageoffer.ai.ragent.triage.worker;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
public class RiskHeuristicHelper {

    static final String HARD_GUARDRAIL_TAG = HeuristicGovernanceTags.KEEP_GUARDRAIL;
    static final String LEGACY_FALLBACK_TAG = HeuristicGovernanceTags.LEGACY_FALLBACK;

    private final LegacyRiskFallback legacyRiskFallback;
    private final RiskTextSnapshotBuilder riskTextSnapshotBuilder;

    public RiskHeuristicHelper(LegacyRiskFallback legacyRiskFallback,
                               RiskTextSnapshotBuilder riskTextSnapshotBuilder) {
        this.legacyRiskFallback = legacyRiskFallback;
        this.riskTextSnapshotBuilder = riskTextSnapshotBuilder;
    }

    public RiskHeuristicHelper() {
        this(new LegacyRiskFallback(), new RiskTextSnapshotBuilder());
    }

    public boolean shouldFastTrackHighRisk(TriageContext context) {
        RiskLevel riskLevel = hardRedFlagFallback(context);
        return riskLevel != null && Boolean.TRUE.equals(riskLevel.getShouldInterrupt());
    }

    public RiskLevel hardRedFlagFallback(TriageContext context) {
        Supplier<String> riskTextSnapshot = lazyRiskTextSnapshot(context);
        List<String> evidence = new ArrayList<>();
        List<String> riskHints = new ArrayList<>();

        boolean severeBleeding = hasRiskSignal(context, RiskSignalType.BLEEDING)
                || hasResolvedYes(context, SlotCode.BLEEDING_PRESENCE)
                || containsAny(riskTextSnapshot.get(), List.of("大出血", "大量出血", "止不住出血", "血流不停", "一直在流血", "出血很多", "流了很多血"));
        boolean dyspnea = hasRiskSignal(context, RiskSignalType.DYSPNEA)
                || hasResolvedYes(context, SlotCode.DYSPNEA_PRESENCE)
                || containsAny(riskTextSnapshot.get(), List.of("呼吸困难", "喘不过气", "喘不上来", "上不来气", "透不过气", "胸闷得喘不上来"));
        boolean seizure = hasRiskSignal(context, RiskSignalType.SEIZURE)
                || hasResolvedYes(context, SlotCode.SEIZURE_PRESENCE)
                || containsAny(riskTextSnapshot.get(), List.of("抽搐", "惊厥", "抽过去了", "全身发抖抽动"));
        boolean consciousnessRisk = hasRiskSignal(context, RiskSignalType.ALTERED_CONSCIOUSNESS)
                || containsAny(riskTextSnapshot.get(), List.of("意识不清", "昏迷", "晕厥", "人不清醒", "叫不醒", "说胡话", "整个人迷糊了", "迷糊了", "没什么反应", "反应很差"));
        boolean chestPain = hasRiskSignal(context, RiskSignalType.CHEST_PAIN)
                || hasResolvedPrimarySymptom(context, "胸痛")
                || containsAny(riskTextSnapshot.get(), List.of("胸痛", "胸口痛", "心口痛", "胸前压着痛"));
        boolean pregnancyBleeding = hasRiskSignal(context, RiskSignalType.PREGNANCY_RELATED_BLEEDING)
                || (hasResolvedYes(context, SlotCode.PREGNANCY_STATUS) && hasResolvedYes(context, SlotCode.BLEEDING_PRESENCE))
                || (containsAny(riskTextSnapshot.get(), List.of("怀孕", "妊娠")) && containsAny(riskTextSnapshot.get(), List.of("出血", "见红", "流血")));

        if (severeBleeding) {
            evidence.add("存在明显大量出血信号。");
            riskHints.add("BLEEDING");
        }
        if (dyspnea) {
            evidence.add("存在呼吸困难等急危重红旗表现。");
            riskHints.add("DYSPNEA");
        }
        if (seizure) {
            evidence.add("存在抽搐/惊厥等急危重红旗表现。");
            riskHints.add("SEIZURE");
        }
        if (consciousnessRisk) {
            evidence.add("存在意识障碍相关红旗表现。");
            riskHints.add("CONSCIOUSNESS");
        }
        if (chestPain && dyspnea) {
            evidence.add("胸痛合并呼吸困难属于高危急症组合。");
            riskHints.add("CHEST_PAIN_WITH_DYSPNEA");
        }
        if (pregnancyBleeding) {
            evidence.add("妊娠相关出血属于高危场景。");
            riskHints.add("PREGNANCY_BLEEDING");
        }
        if (evidence.isEmpty()) {
            return null;
        }
        return RiskLevel.builder()
                .level(4)
                .score(95D)
                .evidence(String.join("；", evidence))
                .rationale("命中硬红旗规则，应直接中断并提示尽快线下就医。")
                .shouldInterrupt(Boolean.TRUE)
                .needsMoreInfo(Boolean.FALSE)
                .riskHints(riskHints)
                .build()
                .normalize();
    }

    public RiskLevel legacyRiskFallback(TriageContext context) {
        return legacyRiskFallback.evaluate(context, lazyRiskTextSnapshot(context));
    }

    public RiskLevel heuristicRiskFallback(TriageContext context) {
        RiskLevel hardRedFlag = hardRedFlagFallback(context);
        if (hardRedFlag != null) {
            return hardRedFlag;
        }
        return legacyRiskFallback(context);
    }

    private Supplier<String> lazyRiskTextSnapshot(TriageContext context) {
        return new Supplier<>() {
            private String cached;

            @Override
            public String get() {
                if (cached == null) {
                    cached = riskTextSnapshotBuilder.build(context);
                }
                return cached;
            }
        };
    }

    private boolean hasRiskSignal(TriageContext context, RiskSignalType riskSignalType) {
        return context.getRiskSignalState() != null
                && context.getRiskSignalState().stream().anyMatch(each -> each != null
                && each.getType() == riskSignalType
                && each.getAssertion() == AssertionStatus.PRESENT);
    }

    private boolean hasResolvedYes(TriageContext context, SlotCode slotCode) {
        if (context == null || slotCode == null) {
            return false;
        }
        SlotState slotState = context.getSlotState();
        if (slotState == null) {
            return false;
        }
        SlotValue slotValue = slotState.get(slotCode);
        return slotValue != null && isResolved(slotValue.getStatus()) && "YES".equalsIgnoreCase(slotValue.getValue());
    }

    private boolean hasResolvedPrimarySymptom(TriageContext context, String expectedValue) {
        if (context == null || StrUtil.isBlank(expectedValue) || context.getSlotState() == null) {
            return false;
        }
        SlotValue slotValue = context.getSlotState().get(SlotCode.PRIMARY_SYMPTOM);
        return slotValue != null && isResolved(slotValue.getStatus()) && expectedValue.equals(slotValue.getValue());
    }

    private boolean isResolved(SlotStatus slotStatus) {
        return slotStatus == SlotStatus.FILLED
                || slotStatus == SlotStatus.NEGATED
                || slotStatus == SlotStatus.CORRECTED
                || slotStatus == SlotStatus.INFERRED;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (StrUtil.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (StrUtil.isNotBlank(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
