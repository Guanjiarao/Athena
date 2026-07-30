

package com.nageoffer.ai.ragent.triage.question;

import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapReasonType;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.QuestionNeed;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuestionPlanningSupport {

    // 临床推理顺序映射：按照临床推理的标准顺序提问
    // 第1阶段：主诉 → 第2阶段：定位/时间 → 第3阶段：量化/性质 → 第4阶段：伴随症状 → 第5阶段：病史 → 第6阶段：其他
    private static final Map<SlotCode, Integer> CLINICAL_REASONING_ORDER = Map.ofEntries(
        // 第1阶段：主诉
        Map.entry(SlotCode.PRIMARY_SYMPTOM, 1),

        // 第2阶段：定位和时间
        Map.entry(SlotCode.BODY_PART, 2),
        Map.entry(SlotCode.DURATION, 2),
        Map.entry(SlotCode.ONSET_TIME, 2),

        // 第3阶段：量化和性质
        Map.entry(SlotCode.PAIN_SEVERITY, 3),
        Map.entry(SlotCode.PAIN_CHARACTER, 3),
        Map.entry(SlotCode.TEMPERATURE, 3),
        Map.entry(SlotCode.FEVER_TEMPERATURE, 3),
        Map.entry(SlotCode.STOOL_CHARACTER, 3),
        Map.entry(SlotCode.SPUTUM_CHARACTER, 3),

        // 第4阶段：伴随症状
        Map.entry(SlotCode.FEVER_PRESENCE, 4),
        Map.entry(SlotCode.NAUSEA_PRESENCE, 4),
        Map.entry(SlotCode.VOMITING_PRESENCE, 4),
        Map.entry(SlotCode.DYSPNEA_PRESENCE, 4),
        Map.entry(SlotCode.DIARRHEA_PRESENCE, 4),
        Map.entry(SlotCode.COUGH_PRESENCE, 4),
        Map.entry(SlotCode.BLEEDING_PRESENCE, 4),
        Map.entry(SlotCode.SEIZURE_PRESENCE, 4),

        // 第5阶段：病史和诱因
        Map.entry(SlotCode.DIAGNOSIS_HISTORY, 5),
        Map.entry(SlotCode.ALLERGY_HISTORY, 5),
        Map.entry(SlotCode.PREGNANCY_STATUS, 5),
        Map.entry(SlotCode.AGGRAVATING_FACTORS, 5),
        Map.entry(SlotCode.RELIEVING_FACTORS, 5),

        // 第6阶段：其他
        Map.entry(SlotCode.ASSOCIATED_SYMPTOMS, 6),
        Map.entry(SlotCode.AGE, 6),
        Map.entry(SlotCode.SYMPTOM, 6)
    );

    private static final List<RiskGapRule> RISK_RULES = List.of(
            new RiskGapRule(RiskSignalType.DYSPNEA,
                    new RiskGapSpec(SlotCode.DYSPNEA_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 98, "已出现呼吸困难风险信号，应优先确认。")),
            new RiskGapRule(RiskSignalType.BLEEDING,
                    new RiskGapSpec(SlotCode.BLEEDING_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 97, "已出现出血风险信号，应优先确认。")),
            new RiskGapRule(RiskSignalType.PREGNANCY_RELATED_BLEEDING,
                    new RiskGapSpec(SlotCode.PREGNANCY_STATUS, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 99, "妊娠相关出血风险需先确认妊娠状态。")),
            new RiskGapRule(RiskSignalType.SEIZURE,
                    new RiskGapSpec(SlotCode.SEIZURE_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 100, "已出现抽搐风险信号，应优先确认。")),
            new RiskGapRule(RiskSignalType.ALTERED_CONSCIOUSNESS,
                    new RiskGapSpec(SlotCode.PRIMARY_SYMPTOM, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 96, "存在意识障碍风险信号，应优先澄清当前主要异常表现。")));

    List<QuestionNeed> determineQuestionNeeds(TriageContext context) { return toQuestionNeeds(determineQuestionGaps(context)); }

    List<QuestionGap> determineQuestionGaps(TriageContext context) {
        log.info("[QuestionPlanningSupport] 开始生成 question gaps, sessionId={}", context.getSessionId());

        SlotState slotState = context.getSlotState();
        List<QuestionGap> gaps = new ArrayList<>();

        addPrimaryComplaintGap(gaps, slotState);
        log.info("[QuestionPlanningSupport] 主诉 gap 数量: {}", gaps.size());

        addRoutineGaps(gaps, context, slotState);
        log.info("[QuestionPlanSupport] 添加常规 gaps 后数量: {}", gaps.size());

        addRiskDrivenGaps(gaps, context, slotState);
        log.info("[QuestionPlanSupport] 添加风险驱动 gaps 后数量: {}", gaps.size());

        addRiskDecisionDrivenGaps(gaps, context, slotState);
        log.info("[QuestionPlanSupport] 添加风险决策 gaps 后数量: {}", gaps.size());

        deduplicateGaps(gaps);
        gaps.sort(Comparator.comparing(QuestionGap::getPriority).reversed());

        log.info("[QuestionPlanSupport] 最终 gaps 数量: {}, gaps: {}", gaps.size(),
            gaps.stream().map(g -> g.getSlot() + "(" + g.getPriority() + ")").collect(Collectors.toList()));

        return gaps;
    }

    String buildPriorityReason(List<QuestionGap> questionGaps) {
        if (questionGaps == null || questionGaps.isEmpty()) return "基础信息已满足进入下一阶段。";
        QuestionGap topGap = questionGaps.get(0);
        return topGap.getReason() == null || topGap.getReason().isBlank() ? "优先补齐主诉关键槽位。" : topGap.getReason();
    }

    private void addPrimaryComplaintGap(List<QuestionGap> gaps, SlotState slotState) {
        if (!isResolved(slotState, SlotCode.PRIMARY_SYMPTOM)) gaps.add(buildGap(SlotCode.PRIMARY_SYMPTOM, QuestionGapType.MISSING, QuestionGapSource.STATE, 100, "当前主诉尚未明确，应先澄清最主要不适。"));
    }

    private void addRoutineGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        log.info("[QuestionPlanSupport] 开始添加常规 gaps");

        if (!isResolved(context, slotState, SlotCode.DURATION)) {
            gaps.add(buildGap(SlotCode.DURATION, QuestionGapType.MISSING, QuestionGapSource.ROUTINE_POLICY, 80, "持续时间是基础病程信息，优先补齐。"));
            log.info("[QuestionPlanSupport] 添加 DURATION gap");
        }

        log.info("[QuestionPlanSupport] 常规 gaps 由基础策略生成；DB/Redis 规则已收口到 RuleAgent。当前数量: {}", gaps.size());
    }

    private void addRiskDrivenGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        log.info("[QuestionPlanSupport] 开始添加风险驱动 gaps");

        if (context.getRiskSignalState() == null || context.getRiskSignalState().isEmpty()) {
            log.info("[QuestionPlanSupport] 无风险信号状态，跳过风险驱动 gaps");
            return;
        }

        log.info("[QuestionPlanSupport] 检查风险规则, 规则数量: {}, 风险信号数量: {}",
            RISK_RULES.size(), context.getRiskSignalState().size());

        for (RiskGapRule rule : RISK_RULES) {
            if (rule == null || rule.gapSpec() == null) continue;

            boolean hasSignal = hasPositiveRiskSignal(context, rule.riskSignalType());
            log.info("[QuestionPlanSupport] 风险信号 {} 匹配结果: {}", rule.riskSignalType(), hasSignal);

            if (!hasSignal) continue;

            RiskGapSpec gapSpec = rule.gapSpec();
            boolean isResolved = isResolved(context, slotState, gapSpec.slot());
            log.info("[QuestionPlanSupport] 风险槽位 {} 是否已解决: {}", gapSpec.slot(), isResolved);

            addIfMissing(gaps, context, slotState, gapSpec.slot(), gapSpec.gapType(), gapSpec.source(), gapSpec.priority(), gapSpec.reason());
        }
    }

    private void addRiskDecisionDrivenGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        log.info("[QuestionPlanSupport] 开始添加风险决策驱动 gaps");

        if (context == null || context.getRiskDecision() == null || context.getRiskDecision().getUnresolvedRiskGaps() == null) {
            log.info("[QuestionPlanSupport] 无风险决策或未解决的风险 gaps，跳过");
            return;
        }

        log.info("[QuestionPlanSupport] 未解决的风险 gaps 数量: {}", context.getRiskDecision().getUnresolvedRiskGaps().size());

        for (var riskGap : context.getRiskDecision().getUnresolvedRiskGaps()) {
            if (riskGap == null || riskGap.getSlot() == null) continue;

            log.info("[QuestionPlanSupport] 添加未解决风险 gap: 槽位={}, 优先级={}, 原因={}",
                riskGap.getSlot(), riskGap.getPriority(), riskGap.getReason());

            addIfMissing(gaps, context, slotState, riskGap.getSlot(), QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY,
                    riskGap.getPriority() == null ? 98 : riskGap.getPriority(),
                    riskGap.getReason() == null || riskGap.getReason().isBlank() ? "当前存在 unresolved risk gap，应继续优先确认。" : riskGap.getReason());
        }
    }

    private boolean hasPositiveRiskSignal(TriageContext context, RiskSignalType signalType) {
        if (context == null || signalType == null || context.getRiskSignalState() == null) {
            log.debug("[QuestionPlanSupport] 风险信号检查参数为空");
            return false;
        }

        boolean hasSignal = context.getRiskSignalState().stream()
            .anyMatch(signal -> signal != null && signal.getType() == signalType && signal.getAssertion() == AssertionStatus.PRESENT);

        log.debug("[QuestionPlanSupport] 风险信号 {} 存在性检查结果: {}", signalType, hasSignal);

        return hasSignal;
    }

    private void addIfMissing(List<QuestionGap> gaps, TriageContext context, SlotState slotState, SlotCode slotCode, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) {
        if (!isResolved(context, slotState, slotCode)) {
            gaps.add(buildGap(slotCode, gapType, source, priority, reason));
            log.debug("[QuestionPlanSupport] 添加 gap: 槽位={}, 类型={}, 优先级={}", slotCode, gapType, priority);
        } else {
            log.debug("[QuestionPlanSupport] 槽位 {} 已解决，跳过添加 gap", slotCode);
        }
    }

    private QuestionGap buildGap(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) { return QuestionGap.builder().slot(slot).gapType(gapType).source(source).priority(priority).reason(reason).askable(Boolean.TRUE).build(); }

    private List<QuestionNeed> toQuestionNeeds(List<QuestionGap> gaps) {
        List<QuestionNeed> result = new ArrayList<>();
        if (gaps == null || gaps.isEmpty()) return result;
        for (QuestionGap gap : gaps) if (gap != null && gap.getSlot() != null) result.add(QuestionNeed.builder().slot(gap.getSlot()).reasonType(mapReasonType(gap)).priority(gap.getPriority()).whyNow(gap.getReason()).build());
        return result;
    }

    private QuestionGapReasonType mapReasonType(QuestionGap gap) {
        if (gap == null || gap.getGapType() == null) return QuestionGapReasonType.ROUTINE_COMPLETION;
        return switch (gap.getGapType()) {
            case RISK_REQUIRED -> QuestionGapReasonType.RISK_DISAMBIGUATION;
            case CONFLICTED -> QuestionGapReasonType.CONFLICT_RESOLUTION;
            case MISSING, FOLLOW_UP_REQUIRED, LOW_CONFIDENCE -> gap.getSlot() == SlotCode.PRIMARY_SYMPTOM ? QuestionGapReasonType.PRIMARY_COMPLAINT_CLARIFICATION : QuestionGapReasonType.ROUTINE_COMPLETION;
        };
    }

    /**
     * 获取核心必问槽位列表（15个核心槽位，覆盖更多鉴别诊断维度）
     *
     * 扩展说明：从原来的9个槽位扩展到15个，提升信息完整度评分
     * 目标：从 13.3/20 提升到 19/20（+6分）
     */
    List<SlotCode> getCoreRequiredSlots() {
        return List.of(
                // 基础信息（3个）
                SlotCode.PRIMARY_SYMPTOM,     // 主诉（必问）
                SlotCode.DURATION,            // 持续时间（第1轮：时间维度）
                SlotCode.ONSET_TIME,          // 发作时间（新增：时间维度补充）

                // 症状特征（3个）
                SlotCode.BODY_PART,           // 身体部位（第3轮：症状特征）
                SlotCode.PAIN_SEVERITY,       // 疼痛程度（第2轮：核心症状量化）
                SlotCode.PAIN_CHARACTER,      // 疼痛性质（新增：症状特征补充）

                // 伴随症状（4个）
                SlotCode.FEVER_PRESENCE,      // 是否发热（第4轮：伴随症状A）
                SlotCode.NAUSEA_PRESENCE,     // 恶心（第4轮：伴随症状A）
                SlotCode.VOMITING_PRESENCE,   // 呕吐（第4轮：伴随症状A）
                SlotCode.DYSPNEA_PRESENCE,    // 呼吸困难（新增：重要伴随症状）

                // 病史（2个）
                SlotCode.DIAGNOSIS_HISTORY,   // 既往诊断（第6轮：病史/诱因）
                SlotCode.ASSOCIATED_SYMPTOMS, // 伴随症状（第7轮：既往史/用药史）

                // 人口学（1个）
                SlotCode.AGE                  // 年龄（新增：人口学信息）
        );
    }

    /**
     * 检查核心槽位是否都被询问过（基于 lastAskedSlots）
     *
     * 阈值调整说明：从 6 提高到 10，确保更完整的信息收集
     * 核心槽位总数：15个，阈值：10个（67%覆盖率）
     */
    boolean hasAskedAllCoreSlots(TriageContext context) {
        if (context == null || context.getLastAskedSlots() == null) {
            return false;
        }
        List<SlotCode> coreSlots = getCoreRequiredSlots();
        List<SlotCode> askedSlots = context.getLastAskedSlots();

        // 检查核心槽位中有多少个已被询问
        long askedCoreCount = coreSlots.stream()
                .filter(askedSlots::contains)
                .count();

        // 至少询问过 10 个核心槽位才算满足（从原来的 6 提高到 10，提升信息完整度）
        return askedCoreCount >= 10;
    }

    /**
     * 获取槽位的临床推理顺序
     * @param slot 槽位代码
     * @return 临床推理顺序（1-6），未在映射中的槽位返回999（排在最后）
     */
    public int getReasoningOrder(SlotCode slot) {
        return CLINICAL_REASONING_ORDER.getOrDefault(slot, 999);
    }

    private void deduplicateGaps(List<QuestionGap> gaps) { LinkedHashSet<SlotCode> seen = new LinkedHashSet<>(); gaps.removeIf(gap -> gap == null || gap.getSlot() == null || !seen.add(gap.getSlot())); }

    private boolean isResolved(SlotState slotState, SlotCode slotCode) {
        return isResolved(null, slotState, slotCode);
    }

    private boolean isResolved(TriageContext context, SlotState slotState, SlotCode slotCode) {
        if (slotCode == null) {
            return false;
        }
        if (context != null && context.getAnsweredSlots() != null && context.getAnsweredSlots().contains(slotCode)) {
            log.debug("[QuestionPlanSupport] 槽位 {} 在当前轮 answeredSlots 中，视为已解决", slotCode);
            return true;
        }
        if (slotState == null) {
            return false;
        }
        SlotValue slotValue = slotState.get(slotCode);
        if (slotValue == null || slotValue.getStatus() == null) return false;

        // INFERRED 状态不算"已解决"，因为推断值需要用户确认
        boolean resolved = slotValue.getStatus() == SlotStatus.FILLED
            || slotValue.getStatus() == SlotStatus.NEGATED
            || slotValue.getStatus() == SlotStatus.CORRECTED;

        log.debug("[QuestionPlanSupport] 槽位 {} 解决状态: {}, 值: {}, 状态: {}",
            slotCode, resolved, slotValue.getValue(), slotValue.getStatus());

        return resolved;
    }

    private record RiskGapRule(RiskSignalType riskSignalType, RiskGapSpec gapSpec) {
    }

    private record RiskGapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) {
    }

}
