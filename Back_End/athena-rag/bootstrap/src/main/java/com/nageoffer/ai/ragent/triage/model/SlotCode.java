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
 * Supported triage slot codes for the refactored dual-agent flow.
 */
public enum SlotCode {

    PRIMARY_SYMPTOM,

    DURATION,

    BODY_PART,

    PAIN_CHARACTER,

    PAIN_SEVERITY,

    FEVER_PRESENCE,

    TEMPERATURE,

    NAUSEA_PRESENCE,

    VOMITING_PRESENCE,

    DYSPNEA_PRESENCE,

    BLEEDING_PRESENCE,

    PREGNANCY_STATUS,

    SEIZURE_PRESENCE,

    DIARRHEA_PRESENCE
}
