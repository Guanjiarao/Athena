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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SemanticParserSupport {

    static final Pattern DURATION_PATTERN = Pattern.compile(
            "((?:从)?(?:今天凌晨|昨天夜里|昨天晚上|今天早上|今天上午|今天中午|今天下午|今天晚上|昨晚|前天|昨天|刚才|刚刚)(?:开始)?(?:到现在|至今|一直这样)?|(?:从)?\\d+\\s*(?:分钟|小时|天|日|周|个月|年)(?:前开始|了|左右|多)?|最近|今早开始|刚开始|一整天|好几天)"
    );
    static final Pattern SPANNED_DURATION_PATTERN = Pattern.compile(
            "((?:从)?(?:今天凌晨|昨天夜里|昨天晚上|今天早上|今天上午|今天中午|今天下午|今天晚上|昨晚|前天|昨天|刚才|刚刚).{0,12}?(?:到现在|至今|一直这样))"
    );

    static final List<String> CHARACTERISTIC_KEYWORDS = List.of(
            "绞痛", "刺痛", "胀痛", "隐痛", "灼痛", "钝痛", "阵发性", "撕裂样", "压榨样"
    );

    static final Map<String, List<String>> SYMPTOM_KEYWORDS = createSymptomKeywords();
    static final Map<String, List<String>> BODY_PART_KEYWORDS = createBodyPartKeywords();
    static final Map<String, List<String>> NEGATED_SYMPTOM_PATTERNS = createNegatedSymptomPatterns();

    private SemanticParserSupport() {
    }

    static String extractDuration(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        Matcher spannedMatcher = SPANNED_DURATION_PATTERN.matcher(text);
        if (spannedMatcher.find()) {
            String matched = spannedMatcher.group(1).trim();
            return normalizeSpannedDuration(matched);
        }
        Matcher directMatcher = DURATION_PATTERN.matcher(text);
        if (directMatcher.find()) {
            return directMatcher.group(1).trim();
        }
        return null;
    }

    static boolean containsAny(String text, List<String> keywords) {
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

    static boolean isSymptomNegated(String userInput, String symptomName) {
        if (StrUtil.isBlank(userInput) || StrUtil.isBlank(symptomName)) {
            return false;
        }
        List<String> patterns = NEGATED_SYMPTOM_PATTERNS.get(symptomName.trim());
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (userInput.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSpannedDuration(String matched) {
        String normalized = matched
                .replace("从", "")
                .replace("发烧", "")
                .replace("发热", "")
                .replace("还在", "")
                .replace("肚子疼", "")
                .replace("腹痛", "")
                .replace("还", "")
                .replace("一直", "");
        normalized = normalized.replaceAll("\\s+", "").trim();
        return normalized.isBlank() ? matched : normalized;
    }

    private static Map<String, List<String>> createSymptomKeywords() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("腹痛", List.of("腹痛", "肚子痛", "肚子疼", "胃痛", "胃疼", "小腹痛", "下腹痛"));
        result.put("发热", List.of("发热", "发烧", "体温高", "烧得"));
        result.put("头痛", List.of("头痛", "脑袋痛", "头疼"));
        result.put("胸痛", List.of("胸痛", "胸口痛", "心口痛"));
        result.put("恶心", List.of("恶心", "想吐"));
        result.put("呕吐", List.of("呕吐", "吐了", "吐出来"));
        result.put("腹泻", List.of("腹泻", "拉肚子", "便稀"));
        result.put("头晕", List.of("头晕", "眩晕", "昏昏沉"));
        result.put("呼吸困难", List.of("呼吸困难", "喘不过气", "气短"));
        result.put("阴道出血", List.of("阴道出血", "出血", "见红", "流血"));
        return result;
    }

    private static Map<String, List<String>> createBodyPartKeywords() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("右下腹", List.of("右下腹", "右下角", "右下腹痛", "肚脐的右下角", "右边下", "右边肚子", "右边小腹"));
        result.put("左下腹", List.of("左下腹", "左下角", "左下腹痛", "肚脐的左下角", "左边下"));
        result.put("下腹部", List.of("下腹", "小腹"));
        result.put("上腹部", List.of("上腹", "胃部", "胃那里"));
        result.put("脐周", List.of("肚脐周围", "肚脐"));
        result.put("胸前区", List.of("胸口", "胸前", "心口"));
        result.put("头顶部", List.of("头顶"));
        result.put("太阳穴", List.of("太阳穴"));
        return result;
    }

    private static Map<String, List<String>> createNegatedSymptomPatterns() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("发热", List.of("没有发热", "没发热", "无发热", "没有发烧", "没发烧", "无发烧", "没有明显发热", "未发热", "未发烧", "不发热"));
        result.put("呕吐", List.of("没有呕吐", "没呕吐", "无呕吐", "没有吐", "没吐", "未吐", "不吐"));
        result.put("腹泻", List.of("没有腹泻", "没腹泻", "无腹泻", "没有拉肚子", "没拉肚子", "不拉肚子", "大便不稀", "没便稀", "不便稀"));
        result.put("恶心", List.of("没有恶心", "没恶心", "无恶心"));
        result.put("呼吸困难", List.of("没有呼吸困难", "没呼吸困难", "无呼吸困难", "没有气短", "没气短", "没有喘不过气", "没喘不过气", "没有喘不上来气", "没喘不上来气", "没有上不来气", "没上不来气", "没有透不过气", "没透不过气"));
        result.put("阴道出血", List.of("没出血", "没有出血", "无出血", "没见红", "没有见红", "无见红", "没流血", "没有流血", "不流血"));
        return result;
    }
}
