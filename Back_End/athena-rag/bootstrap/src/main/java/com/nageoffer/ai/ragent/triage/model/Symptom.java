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

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个症状的结构化表达。
 *
 * <p>该对象不是诊断结论，而是“用户已经明确表达出来的信息”。
 * 语义解析 Worker 会尽量把自然语言中的零散描述抽取成统一字段，
 * 方便后续 SOP 校验器和风险分级器复用。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Symptom {

    /**
     * 规范化后的症状名称，例如“腹痛”“发热”“头晕”。
     */
    private String name;

    /**
     * 症状对应的部位，例如“右下腹”“胸前区”“头顶部”。
     */
    private String bodyPart;

    /**
     * 用户描述的持续时间，例如“2小时”“3天”“昨晚开始”。
     */
    private String duration;

    /**
     * 症状强度，用于帮助判断危险程度，例如“轻微”“剧烈”“难忍”。
     */
    private String severity;

    /**
     * 对疼痛 / 不适性质的描述，例如“绞痛”“刺痛”“持续性”。
     */
    @Builder.Default
    private List<String> characteristics = new ArrayList<>();

    /**
     * 同时伴随出现的其他症状。
     */
    @Builder.Default
    private List<String> accompanyingSymptoms = new ArrayList<>();
}
