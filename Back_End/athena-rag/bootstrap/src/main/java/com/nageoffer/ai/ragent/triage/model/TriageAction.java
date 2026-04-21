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

/**
 * 就医助手编排器的下一步动作枚举。
 *
 * <p>该枚举是前后端之间最核心的控制信号之一：
 * 前端只需要关心当前动作是什么，而不需要知道后端内部到底调用了几个 Worker、
 * 使用了什么 Prompt、是否做过规则校验。</p>
 */
public enum TriageAction {

    /**
     * 信息还不够，必须先追问。
     */
    ASK_CLARIFICATION,

    /**
     * 发现高风险信号，必须优先阻断并给出就医警示。
     */
    TRIGGER_WARNING,

    /**
     * 信息已齐备且风险可控，可以进入病历摘要 / 分诊报告生成阶段。
     */
    GENERATE_REPORT
}
