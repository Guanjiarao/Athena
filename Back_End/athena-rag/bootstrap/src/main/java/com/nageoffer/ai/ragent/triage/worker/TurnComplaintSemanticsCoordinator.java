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

import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.ArrayList;

public class TurnComplaintSemanticsCoordinator {
    private final ComplaintFallbackResolver complaintFallbackResolver;
    private final AnsweredSlotFlowCollector answeredSlotFlowCollector;
    private final AnsweredSlotSemanticsAssembler answeredSlotSemanticsAssembler;

    public TurnComplaintSemanticsCoordinator(ComplaintFallbackResolver complaintFallbackResolver,
                                             AnsweredSlotFlowCollector answeredSlotFlowCollector,
                                             AnsweredSlotSemanticsAssembler answeredSlotSemanticsAssembler) {
        this.complaintFallbackResolver = complaintFallbackResolver;
        this.answeredSlotFlowCollector = answeredSlotFlowCollector;
        this.answeredSlotSemanticsAssembler = answeredSlotSemanticsAssembler;
    }

    public void enrich(TriageContext context, TurnUnderstanding understanding, String text) {
        if (understanding == null) {
            return;
        }
        if (understanding.getAnsweredSlots() == null) understanding.setAnsweredSlots(new ArrayList<>());
        if (understanding.getCorrections() == null) understanding.setCorrections(new ArrayList<>());
        if (understanding.getRiskSignals() == null) understanding.setRiskSignals(new ArrayList<>());

        ComplaintUnderstanding explicitComplaint = explicitComplaint(text);
        if (isBlankComplaint(understanding.getPrimaryComplaint())) {
            understanding.setPrimaryComplaint(firstNonBlankComplaint(explicitComplaint, complaintFromContext(context)));
        }

        answeredSlotFlowCollector.collectInto(context, understanding, text);
        answeredSlotSemanticsAssembler.apply(context, understanding, text, explicitComplaint);
    }

    private ComplaintUnderstanding explicitComplaint(String text) {
        String value = complaintFallbackResolver.resolvePrimaryComplaint(text);
        if (blank(value)) value = complaintFallbackResolver.resolveWeakSymptomWithBodyCue(text);
        return blank(value) ? null : ComplaintUnderstanding.builder().value(value).confidence(0.8D).evidence(text).build();
    }

    private ComplaintUnderstanding complaintFromContext(TriageContext context) {
        if (context == null) {
            return null;
        }
        SlotValue primarySymptom = context.getSlotState() == null ? null : context.getSlotState().get(SlotCode.PRIMARY_SYMPTOM);
        if (primarySymptom != null && !blank(primarySymptom.getValue())) {
            return ComplaintUnderstanding.builder()
                    .value(primarySymptom.getValue().trim())
                    .confidence(0.7D)
                    .evidence(primarySymptom.getEvidence())
                    .build();
        }
        if (!blank(context.getFinalPrimaryComplaint())) {
            return ComplaintUnderstanding.builder()
                    .value(context.getFinalPrimaryComplaint().trim())
                    .confidence(0.7D)
                    .evidence("context.finalPrimaryComplaint")
                    .build();
        }
        return null;
    }

    private ComplaintUnderstanding firstNonBlankComplaint(ComplaintUnderstanding first, ComplaintUnderstanding second) {
        return isBlankComplaint(first) ? second : first;
    }

    private boolean isBlankComplaint(ComplaintUnderstanding complaint) {
        return complaint == null || blank(complaint.getValue());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
