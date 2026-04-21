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

package com.nageoffer.ai.ragent.triage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * triage 模块专属 AI 配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "triage.ai")
public class TriageAiProperties {

    /**
     * triage 文本任务默认模型。
     */
    private String textModel;

    /**
     * triage 报告生成默认模型。
     */
    private String reportModel;

    /**
     * triage 视觉分析默认模型。
     */
    private String visionModel = "qwen-vl-max";

    /**
     * triage 会话窗口使用的文本温度。
     */
    private Double textTemperature = 0.2D;

    /**
     * triage 报告生成温度。
     */
    private Double reportTemperature = 0.2D;
}
