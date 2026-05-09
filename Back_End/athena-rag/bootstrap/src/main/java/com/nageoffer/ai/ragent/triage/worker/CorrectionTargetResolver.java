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

import com.nageoffer.ai.ragent.triage.model.AnsweredSlotUnderstanding;
import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.CorrectionTarget;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CorrectionTargetResolver {
    private final SlotAnswerInferenceHelper slotAnswerInferenceHelper;

    public CorrectionTargetResolver(SlotAnswerInferenceHelper slotAnswerInferenceHelper) {
        this.slotAnswerInferenceHelper = slotAnswerInferenceHelper;
    }

    public ResolvedCorrection resolve(TriageContext context,
                                      CorrectionPhraseParser.ParsedCorrectionPhrase phrase,
                                      ComplaintUnderstanding explicitComplaint,
                                      String latestTurn) {
        if (phrase == null) {
            return null;
        }
        ResolvedCorrection fromSlotState = resolveFromSlotState(context == null ? null : context.getSlotState(), phrase, latestTurn);
        if (fromSlotState != null) {
            return fromSlotState;
        }
        ResolvedCorrection fromAskedSlots = resolveFromAskedSlots(context, phrase);
        if (fromAskedSlots != null) {
            return fromAskedSlots;
        }
        if (explicitComplaint != null && !blank(explicitComplaint.getValue())) {
            return new ResolvedCorrection(
                    CorrectionTarget.PRIMARY_COMPLAINT,
                    SlotCode.PRIMARY_SYMPTOM,
                    phrase.rejectValue(),
                    trim(explicitComplaint.getValue()),
                    phrase.evidence());
        }
        return new ResolvedCorrection(
                CorrectionTarget.UNKNOWN,
                null,
                phrase.rejectValue(),
                phrase.confirmValue(),
                phrase.evidence());
    }

    private ResolvedCorrection resolveFromSlotState(SlotState slotState,
                                                    CorrectionPhraseParser.ParsedCorrectionPhrase phrase,
                                                    String latestTurn) {
        if (slotState == null || slotState.getSlots() == null) {
            return null;
        }
        SlotCode matchedSlot = null;
        String confirmValue = null;
        for (Map.Entry<SlotCode, SlotValue> entry : slotState.getSlots().entrySet()) {
            SlotCode slot = entry.getKey();
            SlotValue slotValue = entry.getValue();
            if (slot == null || slotValue == null || blank(slotValue.getValue())) {
                continue;
            }
            if (!matchesExistingValue(slot, slotValue.getValue(), phrase.rejectValue(), latestTurn)) {
                continue;
            }
            String candidate = normalizeSlotValue(slot, phrase.confirmValue());
            if (blank(candidate)) {
                candidate = trim(phrase.confirmValue());
            }
            if (matchedSlot != null && matchedSlot != slot) {
                return unresolved(phrase);
            }
            matchedSlot = slot;
            confirmValue = candidate;
        }
        if (matchedSlot == null || blank(confirmValue)) {
            return null;
        }
        return new ResolvedCorrection(
                matchedSlot == SlotCode.PRIMARY_SYMPTOM ? CorrectionTarget.PRIMARY_COMPLAINT : CorrectionTarget.SLOT_VALUE,
                matchedSlot,
                phrase.rejectValue(),
                confirmValue,
                phrase.evidence());
    }

    private ResolvedCorrection resolveFromAskedSlots(TriageContext context,
                                                     CorrectionPhraseParser.ParsedCorrectionPhrase phrase) {
        List<SlotCode> candidates = new ArrayList<>();
        if (context != null && context.getLastAskedSlots() != null) {
            candidates.addAll(context.getLastAskedSlots());
        }
        if (context != null && context.getPendingSlots() != null) {
            for (SlotCode slot : context.getPendingSlots()) {
                if (!candidates.contains(slot)) {
                    candidates.add(slot);
                }
            }
        }
        SlotCode matchedSlot = null;
        String confirmValue = null;
        for (SlotCode slot : candidates) {
            AnsweredSlotUnderstanding inferred = slotAnswerInferenceHelper.infer(slot, phrase.confirmValue());
            if (inferred == null) {
                continue;
            }
            if (matchedSlot != null && matchedSlot != slot) {
                return unresolved(phrase);
            }
            matchedSlot = slot;
            confirmValue = normalizeAnsweredValue(inferred, phrase.confirmValue());
        }
        if (matchedSlot == null || blank(confirmValue)) {
            return null;
        }
        return new ResolvedCorrection(
                matchedSlot == SlotCode.PRIMARY_SYMPTOM ? CorrectionTarget.PRIMARY_COMPLAINT : CorrectionTarget.SLOT_VALUE,
                matchedSlot,
                phrase.rejectValue(),
                confirmValue,
                phrase.evidence());
    }

    private boolean matchesExistingValue(SlotCode slot,
                                         String existing,
                                         String reject,
                                         String latestTurn) {
        String normalizedExisting = normalizeSlotValue(slot, existing);
        String normalizedReject = normalizeSlotValue(slot, reject);
        return (!blank(normalizedExisting) && !blank(normalizedReject) && normalizedExisting.equals(normalizedReject))
                || (blank(reject) && !blank(existing) && !blank(latestTurn) && latestTurn.contains(existing));
    }

    private String normalizeSlotValue(SlotCode slot, String value) {
        if (blank(value)) {
            return null;
        }
        return normalizeAnsweredValue(slotAnswerInferenceHelper.infer(slot, value), value);
    }

    private String normalizeAnsweredValue(AnsweredSlotUnderstanding inferred, String fallback) {
        if (inferred != null && !blank(inferred.getNormalizedValue())) {
            return inferred.getNormalizedValue().trim();
        }
        return trim(fallback);
    }

    private ResolvedCorrection unresolved(CorrectionPhraseParser.ParsedCorrectionPhrase phrase) {
        return new ResolvedCorrection(
                CorrectionTarget.UNKNOWN,
                null,
                phrase.rejectValue(),
                phrase.confirmValue(),
                phrase.evidence());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return blank(value) ? null : value.trim();
    }

    public record ResolvedCorrection(CorrectionTarget target,
                                     SlotCode slot,
                                     String rejectValue,
                                     String confirmValue,
                                     String evidence) {
    }
}
