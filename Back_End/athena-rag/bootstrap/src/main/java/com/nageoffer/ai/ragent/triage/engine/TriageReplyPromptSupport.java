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

package com.nageoffer.ai.ragent.triage.engine;

import com.nageoffer.ai.ragent.triage.model.SlotCode;

final class TriageReplyPromptSupport {

    private TriageReplyPromptSupport() {
    }

    static String promptForSlot(SlotCode slotCode) {
        if (slotCode == null) {
            return null;
        }
        return switch (slotCode) {
            case PRIMARY_SYMPTOM -> "目前最明显的不适主要是什么";
            case DURATION -> "这种不适是从什么时候开始的";
            case BODY_PART -> "具体是哪个位置不舒服";
            case PAIN_CHARACTER -> "这种疼是绞痛、隐痛、刺痛，还是持续疼";
            case PAIN_SEVERITY -> "疼痛大概是轻度、中度还是重度";
            case FEVER_PRESENCE -> "有没有发热";
            case TEMPERATURE -> "如果量过体温，体温大概是多少";
            case NAUSEA_PRESENCE -> "有没有恶心";
            case VOMITING_PRESENCE -> "有没有呕吐或者吐过";
            case DYSPNEA_PRESENCE -> "有没有胸闷、气短或呼吸费力";
            case BLEEDING_PRESENCE -> "有没有出血";
            case PREGNANCY_STATUS -> "目前是否怀孕或存在妊娠可能";
            case SEIZURE_PRESENCE -> "有没有出现抽搐或惊厥";
            case DIARRHEA_PRESENCE -> "有没有拉肚子或腹泻";
        };
    }

    static String promptForField(String field) {
        return switch (field) {
            case "腹痛位置", "疼痛部位" -> "肚子具体是哪个位置不舒服";
            case "疼痛性质" -> "这种疼是绞痛、隐痛、刺痛，还是持续疼";
            case "是否伴随发热" -> "有没有发热";
            case "主要症状", "主诉症状" -> "目前最明显的不适主要是什么";
            case "持续时间" -> "这种不适是从什么时候开始的";
            case "是否伴随恶心或呕吐" -> "有没有恶心、想吐或者已经吐过";
            case "是否伴随恶心" -> "有没有恶心";
            case "是否伴随呕吐" -> "有没有呕吐或者吐过";
            case "是否伴随呼吸困难" -> "有没有胸闷、气短或呼吸费力";
            case "体温" -> "如果量过体温，体温大概是多少";
            case "是否伴随出血" -> "有没有出血";
            case "是否妊娠" -> "目前是否怀孕或存在妊娠可能";
            case "是否存在抽搐" -> "有没有出现抽搐或惊厥";
            case "是否伴随腹泻" -> "有没有拉肚子或腹泻";
            default -> field;
        };
    }
}
