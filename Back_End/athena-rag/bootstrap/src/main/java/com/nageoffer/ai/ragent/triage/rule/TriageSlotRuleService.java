

package com.nageoffer.ai.ragent.triage.rule;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.rule.dao.TriageSlotRuleDO;
import com.nageoffer.ai.ragent.triage.rule.dao.TriageSlotRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 按 signal 读取/缓存 triage 追问槽位规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageSlotRuleService {

    private static final TypeReference<List<SlotRuleDefinition>> RULE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<TriageClarificationData.QuestionOption>> OPTION_LIST_TYPE = new TypeReference<>() {
    };

    private final TriageSlotRuleMapper triageSlotRuleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TriageSlotRuleProperties triageSlotRuleProperties;

    public List<SlotRuleDefinition> getRulesBySignal(String signal) {
        if (StrUtil.isBlank(signal)) {
            return List.of();
        }
        String normalizedSignal = signal.trim();
        String cacheKey = buildSignalKey(normalizedSignal);
        List<SlotRuleDefinition> cachedRules = readFromCache(cacheKey, normalizedSignal);
        if (cachedRules != null) {
            return cachedRules;
        }

        List<SlotRuleDefinition> dbRules = loadFromDatabase(normalizedSignal);
        writeToCache(cacheKey, normalizedSignal, dbRules);
        return dbRules;
    }

    public void saveLearnedRules(String signal, List<SlotRuleDefinition> rules) {
        if (StrUtil.isBlank(signal) || rules == null || rules.isEmpty()) {
            return;
        }
        String normalizedSignal = signal.trim();
        double minConfidence = safeMinConfidence();
        List<SlotRuleDefinition> acceptedRules = rules.stream()
                .filter(rule -> rule != null && rule.getSlot() != null)
                .filter(rule -> rule.getConfidence() != null && rule.getConfidence() > minConfidence)
                .map(rule -> normalizeRule(normalizedSignal, rule))
                .sorted(Comparator.comparing(SlotRuleDefinition::getPriority, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (acceptedRules.isEmpty()) {
            log.info("[TriageSlotRuleService] signal={} 没有置信度大于 {} 的可缓存规则", normalizedSignal, minConfidence);
            return;
        }

        List<SlotRuleDefinition> existingRules = getRulesBySignal(normalizedSignal);
        List<SlotRuleDefinition> mergedRules = mergeRulesBySlot(existingRules, acceptedRules);
        writeToCache(buildSignalKey(normalizedSignal), normalizedSignal, mergedRules);
        log.info("[TriageSlotRuleService] 已合并并缓存 LLM 学习规则, signal={}, acceptedRules={}, mergedRules={}",
                normalizedSignal, acceptedRules, mergedRules);
    }

    private List<SlotRuleDefinition> readFromCache(String cacheKey, String signal) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isBlank(payload)) {
                return null;
            }
            List<SlotRuleDefinition> rules = objectMapper.readValue(payload, RULE_LIST_TYPE);
            log.info("[TriageSlotRuleService] 命中 Redis 槽位规则缓存, signal={}, count={}", signal, rules == null ? 0 : rules.size());
            return rules == null ? List.of() : rules;
        } catch (Exception ex) {
            log.warn("[TriageSlotRuleService] 读取 Redis 槽位规则失败, signal={}", signal, ex);
            return null;
        }
    }

    private void writeToCache(String cacheKey, String signal, List<SlotRuleDefinition> rules) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(rules == null ? List.of() : rules),
                    Duration.ofMinutes(safeTtlMinutes())
            );
            log.info("[TriageSlotRuleService] 写入 Redis 槽位规则缓存, signal={}, count={}, ttlMinutes={}",
                    signal, rules == null ? 0 : rules.size(), safeTtlMinutes());
        } catch (Exception ex) {
            log.warn("[TriageSlotRuleService] 写入 Redis 槽位规则失败, signal={}", signal, ex);
        }
    }

    private List<SlotRuleDefinition> loadFromDatabase(String signal) {
        double minConfidence = safeMinConfidence();
        List<TriageSlotRuleDO> records = triageSlotRuleMapper.selectList(new LambdaQueryWrapper<TriageSlotRuleDO>()
                .eq(TriageSlotRuleDO::getSignal, signal)
                .eq(TriageSlotRuleDO::getEnabled, 1)
                .eq(TriageSlotRuleDO::getDeleted, 0)
                .gt(TriageSlotRuleDO::getConfidence, minConfidence)
                .orderByDesc(TriageSlotRuleDO::getPriority));
        if (records == null || records.isEmpty()) {
            log.info("[TriageSlotRuleService] DB 未找到可用槽位规则, signal={}, minConfidence={}", signal, minConfidence);
            return List.of();
        }

        List<SlotRuleDefinition> result = new ArrayList<>();
        for (TriageSlotRuleDO record : records) {
            SlotRuleDefinition rule = toRule(record);
            if (rule != null) {
                result.add(rule);
            }
        }
        log.info("[TriageSlotRuleService] DB 加载槽位规则, signal={}, count={}", signal, result.size());
        return result;
    }

    private SlotRuleDefinition toRule(TriageSlotRuleDO record) {
        try {
            SlotCode slot = SlotCode.valueOf(record.getSlotCode());
            QuestionGapType gapType = StrUtil.isBlank(record.getGapType())
                    ? QuestionGapType.FOLLOW_UP_REQUIRED
                    : QuestionGapType.valueOf(record.getGapType());
            QuestionGapSource source = StrUtil.isBlank(record.getSource())
                    ? QuestionGapSource.PATTERN
                    : QuestionGapSource.valueOf(record.getSource());
            return SlotRuleDefinition.builder()
                    .signal(record.getSignal())
                    .slot(slot)
                    .gapType(gapType)
                    .source(source)
                    .priority(record.getPriority())
                    .reason(record.getReason())
                    .confidence(record.getConfidence())
                    .options(parseOptions(record))
                    .build();
        } catch (Exception ex) {
            log.warn("[TriageSlotRuleService] 跳过非法槽位规则记录, id={}, signal={}, slotCode={}",
                    record.getId(), record.getSignal(), record.getSlotCode(), ex);
            return null;
        }
    }

    private SlotRuleDefinition normalizeRule(String signal, SlotRuleDefinition rule) {
        return SlotRuleDefinition.builder()
                .signal(signal)
                .slot(rule.getSlot())
                .gapType(rule.getGapType() == null ? QuestionGapType.FOLLOW_UP_REQUIRED : rule.getGapType())
                .source(rule.getSource() == null ? QuestionGapSource.PATTERN : rule.getSource())
                .priority(rule.getPriority() == null ? 70 : rule.getPriority())
                .reason(StrUtil.blankToDefault(rule.getReason(), signal + " 场景由 LLM 学习得到的追问槽位。"))
                .confidence(rule.getConfidence())
                .options(rule.getOptions())
                .build();
    }

    private List<SlotRuleDefinition> mergeRulesBySlot(List<SlotRuleDefinition> existingRules, List<SlotRuleDefinition> learnedRules) {
        List<SlotRuleDefinition> merged = new ArrayList<>();
        if (existingRules != null) {
            merged.addAll(existingRules.stream().filter(rule -> rule != null && rule.getSlot() != null).toList());
        }
        if (learnedRules != null) {
            for (SlotRuleDefinition learnedRule : learnedRules) {
                if (learnedRule == null || learnedRule.getSlot() == null) {
                    continue;
                }
                int existingIndex = -1;
                for (int i = 0; i < merged.size(); i++) {
                    if (merged.get(i).getSlot() == learnedRule.getSlot()) {
                        existingIndex = i;
                        break;
                    }
                }
                if (existingIndex < 0) {
                    merged.add(learnedRule);
                    continue;
                }
                SlotRuleDefinition existingRule = merged.get(existingIndex);
                SlotRuleDefinition replacement = shouldReplaceRule(existingRule, learnedRule)
                        ? preserveOptions(learnedRule, existingRule)
                        : preserveOptions(existingRule, learnedRule);
                merged.set(existingIndex, replacement);
            }
        }
        return merged.stream()
                .sorted(Comparator.comparing(SlotRuleDefinition::getPriority, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean shouldReplaceRule(SlotRuleDefinition existingRule, SlotRuleDefinition learnedRule) {
        double existingConfidence = existingRule.getConfidence() == null ? 0D : existingRule.getConfidence();
        double learnedConfidence = learnedRule.getConfidence() == null ? 0D : learnedRule.getConfidence();
        int existingPriority = existingRule.getPriority() == null ? 0 : existingRule.getPriority();
        int learnedPriority = learnedRule.getPriority() == null ? 0 : learnedRule.getPriority();
        return learnedConfidence > existingConfidence || learnedPriority > existingPriority;
    }

    private SlotRuleDefinition preserveOptions(SlotRuleDefinition preferredRule, SlotRuleDefinition fallbackRule) {
        List<TriageClarificationData.QuestionOption> options = preferredRule.getOptions();
        if ((options == null || options.isEmpty()) && fallbackRule != null) {
            options = fallbackRule.getOptions();
        }
        return SlotRuleDefinition.builder()
                .signal(preferredRule.getSignal())
                .slot(preferredRule.getSlot())
                .gapType(preferredRule.getGapType())
                .source(preferredRule.getSource())
                .priority(preferredRule.getPriority())
                .reason(preferredRule.getReason())
                .confidence(preferredRule.getConfidence())
                .options(options)
                .build();
    }

    private List<TriageClarificationData.QuestionOption> parseOptions(TriageSlotRuleDO record) {
        if (record == null || StrUtil.isBlank(record.getOptionsJson())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(record.getOptionsJson(), OPTION_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("[TriageSlotRuleService] 解析槽位规则 optionsJson 失败, id={}, signal={}, slotCode={}",
                    record.getId(), record.getSignal(), record.getSlotCode(), ex);
            return List.of();
        }
    }

    private String buildSignalKey(String signal) {
        return StrUtil.blankToDefault(triageSlotRuleProperties.getKeyPrefix(), "triage:slot-rule:signal:") + signal;
    }

    private long safeTtlMinutes() {
        Long ttlMinutes = triageSlotRuleProperties.getTtlMinutes();
        return ttlMinutes == null || ttlMinutes <= 0 ? 1440L : ttlMinutes;
    }

    private double safeMinConfidence() {
        Double minConfidence = triageSlotRuleProperties.getMinConfidence();
        return minConfidence == null ? 0.6D : minConfidence;
    }
}
