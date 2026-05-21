

package com.nageoffer.ai.ragent.triage.question;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.response.TriageReplyPromptSupport;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapReasonType;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.QuestionNeed;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.rule.SlotRuleDefinition;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.rule.TriageSlotRuleService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuestionPlanningSupport {
    private static final String SLOT_SCORING_SYSTEM_PROMPT = """
你是医疗分诊系统的冷启动槽位规则学习器。

系统架构说明：
- 常见症状的追问规则优先来自 PostgreSQL，并缓存在 Redis。
- 只有当 DB/Redis 没有命中可追问规则，或现有规则都已问完时，才会调用你。
- 你的结果会用于本轮选择下一个槽位，并把高置信槽位学习进 Redis，供后续相同 signal 复用。

你的任务：
1. 在候选槽位中评估哪些信息对当前症状最值得继续追问。
2. 不要重复选择已收集、已回答、最近问过或用户刚刚回答过的槽位。
3. 如果当前信息已经足够，或继续追问收益很低，应建议 generate_report。
4. 如果继续追问，优先选择对风险分层、危险信号排查、关键诊断分流最有价值的槽位。

评分标准（0-100分）：
- 90-100分：当前症状下高度必要，优先学习为规则并立即追问
- 70-89分：重要，适合学习为该 signal 的常用规则
- 50-69分：有一定帮助，但不应优先于高价值槽位
- 30-49分：价值有限，通常不应追问
- 0-29分：无关、重复、已回答或不建议追问

输出格式（严格 JSON）：
{
  "scores": [
    {
      "slot": "槽位代码",
      "score": 分数(0-100),
      "reason": "评分理由（一句话，说明为什么该槽位适合或不适合当前 signal）"
    }
  ],
  "recommendation": "continue" 或 "generate_report",
  "rationale": "整体推荐理由"
}

硬性要求：
- 只能评价候选槽位列表中出现的 slot。
- 对已收集、已回答、最近问过、当前轮刚回答的槽位必须给低分。
- 如果连续兜底次数 ≥ 3 次，强烈倾向 generate_report，避免追问死循环。
- 如果连续兜底次数 ≥ 4 次，除非有明确高危风险需要确认，否则必须 generate_report。
- 不要为了凑轮次而追问。
- 不要输出 JSON 以外的内容。
""";

    private static final int SLOT_SCORE_THRESHOLD = 30;
    private static final int MAX_EMERGENCY_SLOTS = 1;

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

    private TriageSlotRuleService triageSlotRuleService;

    public QuestionPlanningSupport() {
    }

    @Autowired(required = false)
    public void setTriageSlotRuleService(TriageSlotRuleService triageSlotRuleService) {
        this.triageSlotRuleService = triageSlotRuleService;
    }

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

        addDatabaseBackedSignalRules(gaps, context, slotState);
        log.info("[QuestionPlanSupport] 常规规则仅使用 DB/Redis，命中数量: {}", gaps.size());
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

    private void addDatabaseBackedSignalRules(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        if (triageSlotRuleService == null) {
            log.debug("[QuestionPlanSupport] triageSlotRuleService 未注入，跳过 DB/Redis 槽位规则");
            return;
        }
        List<String> signals = collectRuleSignals(context, slotState);
        if (signals.isEmpty()) {
            log.info("[QuestionPlanSupport] 未收集到可查询 DB/Redis 槽位规则的 signal");
            return;
        }
        log.info("[QuestionPlanSupport] 查询 DB/Redis 槽位规则 signals={}", signals);
        for (String signal : signals) {
            List<SlotRuleDefinition> rules = triageSlotRuleService.getRulesBySignal(signal);
            if (rules == null || rules.isEmpty()) {
                continue;
            }
            if (context.getActivatedSemanticSignals() == null) {
                context.setActivatedSemanticSignals(new HashSet<>());
            }
            context.getActivatedSemanticSignals().add(signal);
            for (SlotRuleDefinition rule : rules) {
                if (rule == null || rule.getSlot() == null) {
                    continue;
                }
                addIfMissing(
                        gaps,
                        context,
                        slotState,
                        rule.getSlot(),
                        rule.getGapType() == null ? QuestionGapType.FOLLOW_UP_REQUIRED : rule.getGapType(),
                        rule.getSource() == null ? QuestionGapSource.PATTERN : rule.getSource(),
                        rule.getPriority() == null ? 70 : rule.getPriority(),
                        rule.getReason() == null || rule.getReason().isBlank()
                                ? signal + " 场景需要补充 " + rule.getSlot()
                                : rule.getReason()
                );
            }
        }
    }

    private void appendRuleOptions(TriageContext context, List<SlotRuleDefinition> rules) {
        if (context == null || rules == null || rules.isEmpty()) {
            return;
        }
        List<TriageClarificationData.QuestionOption> ruleOptions = rules.stream()
                .filter(rule -> rule != null && rule.getOptions() != null && !rule.getOptions().isEmpty())
                .flatMap(rule -> rule.getOptions().stream())
                .filter(option -> option != null && option.getTargetSlot() != null)
                .toList();
        if (ruleOptions.isEmpty()) {
            return;
        }
        if (context.getGeneratedOptions() == null) {
            context.setGeneratedOptions(new ArrayList<>());
        }
        context.getGeneratedOptions().addAll(ruleOptions);
        log.info("[QuestionPlanSupport] 已追加 DB/Redis 规则选项, count={}, totalGeneratedOptions={}",
                ruleOptions.size(), context.getGeneratedOptions().size());
    }

    private List<String> collectRuleSignals(TriageContext context, SlotState slotState) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        if (context != null && context.getActivatedSemanticSignals() != null) {
            signals.addAll(context.getActivatedSemanticSignals().stream().filter(StrUtil::isNotBlank).toList());
        }
        String primarySymptom = slotValue(slotState, SlotCode.PRIMARY_SYMPTOM);
        String symptom = slotValue(slotState, SlotCode.SYMPTOM);
        if (StrUtil.isNotBlank(primarySymptom)) {
            signals.add(primarySymptom.trim());
        }
        if (StrUtil.isNotBlank(symptom)) {
            signals.add(symptom.trim());
        }
        if (context != null && context.getFinalPrimaryComplaint() != null && !context.getFinalPrimaryComplaint().isBlank()) {
            signals.add(context.getFinalPrimaryComplaint().trim());
        }
        if (context != null && context.getExtractedSymptoms() != null) {
            context.getExtractedSymptoms().stream()
                    .filter(symptomValue -> symptomValue != null && StrUtil.isNotBlank(symptomValue.getName()))
                    .map(symptomValue -> symptomValue.getName().trim())
                    .forEach(signals::add);
        }
        return new ArrayList<>(signals);
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

    private String slotValue(SlotState slotState, SlotCode slotCode) { SlotValue slotValue = slotState == null ? null : slotState.get(slotCode); return slotValue == null ? null : slotValue.getValue(); }

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

    /**
     * 使用 LLM 智能选择紧急槽位（方案6）
     * 当 candidateGaps 为空时调用，LLM 评估每个候选槽位的重要性
     * 如果所有槽位分数都低于阈值，返回空计划（触发报告生成）
     */
    QuestionPlan selectEmergencySlotByLLM(TriageContext context, TriageModelGateway modelGateway, int consecutiveFallbackCount) {
        log.warn("[QuestionPlanSupport] 启动 LLM 智能兜底槽位选择（方案6），连续兜底次数: {}", consecutiveFallbackCount);

        // 1. 收集候选槽位
        List<SlotCode> candidateSlots = collectCandidateSlots(context);
        if (candidateSlots.isEmpty()) {
            log.warn("[QuestionPlanSupport] 没有可用的候选槽位，返回空计划");
            return QuestionPlan.builder()
                .nextSlotsToAsk(List.of())
                .priorityReason("所有兜底槽位都已填充，建议生成报告")
                .build();
        }

        log.info("[QuestionPlanSupport] 收集到 {} 个候选槽位: {}", candidateSlots.size(), candidateSlots);

        // 2. 构建 LLM 评分提示词
        String userPrompt = buildSlotScoringPrompt(context, candidateSlots, consecutiveFallbackCount);

        // 3. 调用 LLM 进行评分
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(SLOT_SCORING_SYSTEM_PROMPT));
            messages.add(ChatMessage.user(userPrompt));

            String response = modelGateway.chatWithReportModel(messages, 0.3D, 0.5D, 800);
            log.info("[QuestionPlanSupport] LLM 槽位评分响应: {}", response);

            // 4. 解析 LLM 响应
            SlotScoringResult scoringResult = parseSlotScoringResponse(response);

            if (scoringResult == null) {
                log.error("[QuestionPlanSupport] LLM 响应解析失败，使用默认兜底");
                return fallbackToDefaultSlot(context, candidateSlots);
            }

            log.info("[QuestionPlanSupport] LLM 推荐: {}, 理由: {}",
                scoringResult.getRecommendation(), scoringResult.getRationale());

            // 5. 根据 LLM 推荐决策
            if ("generate_report".equals(scoringResult.getRecommendation())) {
                log.info("[QuestionPlanSupport] LLM 建议生成报告，返回空计划");
                return QuestionPlan.builder()
                    .nextSlotsToAsk(List.of())
                    .priorityReason("LLM 评估：" + scoringResult.getRationale())
                    .build();
            }

            // 6. 选择得分最高的槽位
            SlotScore topScore = scoringResult.getScores().stream()
                .max(Comparator.comparingInt(SlotScore::getScore))
                .orElse(null);

            if (topScore == null || topScore.getScore() < SLOT_SCORE_THRESHOLD) {
                log.info("[QuestionPlanSupport] 最高分槽位 {} 分数 {} 低于阈值 {}，返回空计划",
                    topScore == null ? "null" : topScore.getSlot(),
                    topScore == null ? 0 : topScore.getScore(),
                    SLOT_SCORE_THRESHOLD);
                return QuestionPlan.builder()
                    .nextSlotsToAsk(List.of())
                    .priorityReason("所有候选槽位分数都低于阈值，建议生成报告")
                    .build();
            }

            // 7. 验证槽位是否有有效的问题模板（Phase 1 优化）
            if (!hasValidQuestionTemplate(topScore.getSlot())) {
                log.warn("[QuestionPlanSupport] LLM 选择的槽位 {} 没有有效的问题模板（会触发通用兜底话术），尝试选择次优槽位或生成报告", topScore.getSlot());

                // 尝试找到有有效模板的次优槽位
                SlotScore validSlot = scoringResult.getScores().stream()
                    .filter(score -> score.getScore() >= SLOT_SCORE_THRESHOLD)
                    .filter(score -> hasValidQuestionTemplate(score.getSlot()))
                    .max(Comparator.comparingInt(SlotScore::getScore))
                    .orElse(null);

                if (validSlot != null) {
                    log.info("[QuestionPlanSupport] 找到有效模板的次优槽位 {} (分数: {})", validSlot.getSlot(), validSlot.getScore());
                    return QuestionPlan.builder()
                        .nextSlotsToAsk(List.of(validSlot.getSlot()))
                        .priorityReason("LLM 智能选择（已验证问题模板）：" + validSlot.getReason() + "（分数：" + validSlot.getScore() + "）")
                        .build();
                } else {
                    log.warn("[QuestionPlanSupport] 所有高分槽位都没有有效的问题模板，建议生成报告");
                    return QuestionPlan.builder()
                        .nextSlotsToAsk(List.of())
                        .priorityReason("候选槽位缺少有效问题模板，避免触发通用兜底话术，建议生成报告")
                        .build();
                }
            }

            log.info("[QuestionPlanSupport] 选择槽位 {} (分数: {}, 理由: {})",
                topScore.getSlot(), topScore.getScore(), topScore.getReason());

            cacheHighConfidenceSlotScores(context, scoringResult);

            return QuestionPlan.builder()
                .nextSlotsToAsk(List.of(topScore.getSlot()))
                .priorityReason("LLM 智能选择：" + topScore.getReason() + "（分数：" + topScore.getScore() + "）")
                .build();

        } catch (Exception e) {
            log.error("[QuestionPlanSupport] LLM 槽位评分失败", e);
            return fallbackToDefaultSlot(context, candidateSlots);
        }
    }

    private void cacheHighConfidenceSlotScores(TriageContext context, SlotScoringResult scoringResult) {
        if (triageSlotRuleService == null || scoringResult == null || scoringResult.getScores() == null || scoringResult.getScores().isEmpty()) {
            return;
        }
        String signal = inferPrimarySignalForLearning(context);
        if (StrUtil.isBlank(signal)) {
            log.info("[QuestionPlanSupport] 无法推断学习规则的 signal，跳过缓存高置信槽位");
            return;
        }
        List<SlotRuleDefinition> learnedRules = scoringResult.getScores().stream()
                .filter(score -> score != null && score.getSlot() != null)
                .map(score -> SlotRuleDefinition.builder()
                        .signal(signal)
                        .slot(score.getSlot())
                        .gapType(QuestionGapType.FOLLOW_UP_REQUIRED)
                        .source(QuestionGapSource.PATTERN)
                        .priority(score.getScore())
                        .reason(StrUtil.blankToDefault(score.getReason(), signal + " 场景由 LLM 评分得到的追问槽位。"))
                        .confidence(score.getScore() / 100.0D)
                        .options(buildLearnedRuleOptions(score.getSlot()))
                        .build())
                .toList();
        triageSlotRuleService.saveLearnedRules(signal, learnedRules);
    }

    private List<TriageClarificationData.QuestionOption> buildLearnedRuleOptions(SlotCode slot) {
        if (slot == null) {
            return List.of();
        }
        return switch (slot) {
            case PAIN_SEVERITY -> List.of(
                    option("轻微", "mild", slot),
                    option("中等", "moderate", slot),
                    option("严重", "severe", slot),
                    option("难以忍受", "unbearable", slot),
                    option("其他", "other", slot)
            );
            case PAIN_CHARACTER -> List.of(
                    option("刺痛", "sharp", slot),
                    option("钝痛", "dull", slot),
                    option("胀痛", "distending", slot),
                    option("放射痛/窜痛", "radiating", slot),
                    option("其他", "other", slot)
            );
            case ASSOCIATED_SYMPTOMS -> List.of(
                    option("麻木或无力", "numbness_weakness", slot),
                    option("放射到腿/臀部", "radiating_leg_hip", slot),
                    option("排尿异常", "urinary_abnormality", slot),
                    option("发热或寒战", "fever_chills", slot),
                    option("其他", "other", slot)
            );
            case ONSET_TIME -> List.of(
                    option("突然开始", "sudden", slot),
                    option("逐渐出现", "gradual", slot),
                    option("活动/劳累后出现", "after_activity", slot),
                    option("反复发作", "recurrent", slot),
                    option("其他", "other", slot)
            );
            case FEVER_PRESENCE -> List.of(
                    option("有发热", "yes", slot),
                    option("没有发热", "no", slot),
                    option("发冷/寒战", "chills", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );
            case DYSPNEA_PRESENCE -> List.of(
                    option("有呼吸困难", "yes", slot),
                    option("没有呼吸困难", "no", slot),
                    option("活动后气短", "exertional", slot),
                    option("胸闷伴气短", "chest_tightness", slot),
                    option("其他", "other", slot)
            );
            case DIAGNOSIS_HISTORY -> List.of(
                    option("有相关既往病史", "yes", slot),
                    option("没有相关病史", "no", slot),
                    option("曾经类似发作", "similar_before", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );
            case MEDICATION_HISTORY -> List.of(
                    option("还没用药", "none", slot),
                    option("用过止痛药", "painkiller", slot),
                    option("用药后有缓解", "relieved", slot),
                    option("用药后无缓解", "not_relieved", slot),
                    option("其他", "other", slot)
            );
            case AGE -> List.of(
                    option("18岁以下", "under_18", slot),
                    option("18-40岁", "18_40", slot),
                    option("41-60岁", "41_60", slot),
                    option("60岁以上", "over_60", slot),
                    option("其他", "other", slot)
            );
            case BODY_PART -> List.of(
                    option("左侧", "left", slot),
                    option("右侧", "right", slot),
                    option("双侧", "both", slot),
                    option("中间/正中", "middle", slot),
                    option("其他", "other", slot)
            );
            default -> List.of(
                    option("有", "yes", slot),
                    option("没有", "no", slot),
                    option("轻微", "mild", slot),
                    option("明显", "obvious", slot),
                    option("其他", "other", slot)
            );
        };
    }

    private TriageClarificationData.QuestionOption option(String label, String value, SlotCode targetSlot) {
        return TriageClarificationData.QuestionOption.builder()
                .label(label)
                .value(value)
                .targetSlot(targetSlot)
                .build();
    }

    private String inferPrimarySignalForLearning(TriageContext context) {
        if (context == null) {
            return null;
        }
        if (StrUtil.isNotBlank(context.getFinalPrimaryComplaint())) {
            return context.getFinalPrimaryComplaint().trim();
        }
        String primarySymptom = slotValue(context.getSlotState(), SlotCode.PRIMARY_SYMPTOM);
        if (StrUtil.isNotBlank(primarySymptom)) {
            return primarySymptom.trim();
        }
        String symptom = slotValue(context.getSlotState(), SlotCode.SYMPTOM);
        if (StrUtil.isNotBlank(symptom)) {
            return symptom.trim();
        }
        if (context.getExtractedSymptoms() != null) {
            return context.getExtractedSymptoms().stream()
                    .filter(symptomValue -> symptomValue != null && StrUtil.isNotBlank(symptomValue.getName()))
                    .map(symptomValue -> symptomValue.getName().trim())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 检查槽位是否有有效的问题模板（Phase 1 优化）
     *
     * @param slotCode 槽位代码
     * @return true 如果有有效的问题模板，false 如果只有通用兜底话术
     */
    private boolean hasValidQuestionTemplate(SlotCode slotCode) {
        String prompt = TriageReplyPromptSupport.promptForSlot(slotCode);
        // 通用兜底话术表示没有有效的问题模板
        return prompt != null && !prompt.equals("能再详细说说这方面的情况吗？");
    }

    /**
     * 收集候选槽位（16个兜底槽位，排除已填充的）
     */
    private List<SlotCode> collectCandidateSlots(TriageContext context) {
        List<SlotCode> allFallbackSlots = List.of(
            // 时间维度
            SlotCode.DURATION, SlotCode.ONSET_TIME,

            // 疼痛维度
            SlotCode.PAIN_SEVERITY, SlotCode.PAIN_CHARACTER, SlotCode.BODY_PART,

            // 伴随症状
            SlotCode.FEVER_PRESENCE, SlotCode.NAUSEA_PRESENCE, SlotCode.VOMITING_PRESENCE,
            SlotCode.DIARRHEA_PRESENCE, SlotCode.COUGH_PRESENCE, SlotCode.DYSPNEA_PRESENCE,

            // 病史
            SlotCode.DIAGNOSIS_HISTORY, SlotCode.MEDICATION_HISTORY,

            // 其他
            SlotCode.ASSOCIATED_SYMPTOMS, SlotCode.AGE
        );

        SlotState slotState = context.getSlotState();
        if (slotState == null) {
            return allFallbackSlots;
        }

        Set<SlotCode> answeredSlots = context.getAnsweredSlots() == null ? Set.of() : new HashSet<>(context.getAnsweredSlots());
        Set<SlotCode> recentlyAskedSlots = context.getLastAskedSlots() == null ? Set.of() : new HashSet<>(context.getLastAskedSlots());

        // 过滤掉已填充、当前轮已回答、最近已问过的槽位，避免 LLM 兜底重复提问。
        return allFallbackSlots.stream()
            .filter(slot -> !answeredSlots.contains(slot))
            .filter(slot -> !recentlyAskedSlots.contains(slot))
            .filter(slot -> {
                SlotValue slotValue = slotState.get(slot);
                return slotValue == null
                    || slotValue.getStatus() == null
                    || slotValue.getStatus() == SlotStatus.UNKNOWN;
            })
            .collect(Collectors.toList());
    }

    /**
     * 构建槽位评分提示词
     */
    private String buildSlotScoringPrompt(TriageContext context, List<SlotCode> candidateSlots, int consecutiveFallbackCount) {
        StringBuilder prompt = new StringBuilder();

        // 1. 当前症状描述
        prompt.append("【当前症状描述】\n");
        prompt.append("用户输入：").append(context.getUserInput() == null ? "无" : context.getUserInput()).append("\n\n");

        // 2. 已提取的结构化症状
        prompt.append("【已提取的结构化症状】\n");
        if (context.getExtractedSymptoms() == null || context.getExtractedSymptoms().isEmpty()) {
            prompt.append("暂无\n");
        } else {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                prompt.append("- 症状：").append(symptom.getName() == null ? "未知" : symptom.getName()).append("\n");
                if (symptom.getBodyPart() != null) prompt.append("  部位：").append(symptom.getBodyPart()).append("\n");
                if (symptom.getDuration() != null) prompt.append("  持续时间：").append(symptom.getDuration()).append("\n");
                if (symptom.getSeverity() != null) prompt.append("  程度：").append(symptom.getSeverity()).append("\n");
            }
        }
        prompt.append("\n");

        // 3. 已填充的槽位
        prompt.append("【已收集的信息】\n");
        SlotState slotState = context.getSlotState();
        if (slotState == null || slotState.getSlots() == null || slotState.getSlots().isEmpty()) {
            prompt.append("暂无\n");
        } else {
            for (Map.Entry<SlotCode, SlotValue> entry : slotState.getSlots().entrySet()) {
                SlotValue value = entry.getValue();
                if (value != null && value.getStatus() != null && value.getStatus() != SlotStatus.UNKNOWN) {
                    prompt.append("- ").append(getSlotDescription(entry.getKey()))
                        .append("：").append(value.getValue() == null ? "未知" : value.getValue())
                        .append("（状态：").append(value.getStatus()).append("）\n");
                }
            }
        }
        prompt.append("\n");

        // 4. 风险评估
        prompt.append("【当前风险评估】\n");
        RiskLevel riskLevel = context.getRiskAssessment();
        if (riskLevel != null) {
            prompt.append("风险等级：").append(riskLevel.getLevel()).append("\n");
            prompt.append("风险分数：").append(riskLevel.getScore()).append("\n");
            if (riskLevel.getEvidence() != null) {
                prompt.append("依据：").append(riskLevel.getEvidence()).append("\n");
            }
        } else {
            prompt.append("暂未评估\n");
        }
        prompt.append("\n");

        // 5. 连续兜底次数信息
        prompt.append("【连续兜底次数】\n");
        prompt.append("这是第 ").append(consecutiveFallbackCount).append(" 次连续触发 LLM 兜底机制。\n");
        if (consecutiveFallbackCount >= 3) {
            prompt.append("⚠️ 警告：连续兜底次数较多，说明当前症状信息可能已经足够，建议考虑生成报告。\n");
        }
        prompt.append("\n");

        // 6. 候选槽位列表
        prompt.append("【候选问题槽位】\n");
        for (SlotCode slot : candidateSlots) {
            prompt.append("- ").append(slot.name()).append("：").append(getSlotDescription(slot)).append("\n");
        }
        prompt.append("\n");

        prompt.append("请根据以上信息，评估每个候选槽位的重要性（0-100分），并给出是否继续询问的建议。");

        return prompt.toString();
    }

    /**
     * 获取槽位的中文描述
     */
    private String getSlotDescription(SlotCode slot) {
        return switch (slot) {
            case DURATION -> "持续时间";
            case ONSET_TIME -> "发作时间";
            case PAIN_SEVERITY -> "疼痛程度";
            case PAIN_CHARACTER -> "疼痛性质";
            case BODY_PART -> "身体部位";
            case FEVER_PRESENCE -> "是否发热";
            case NAUSEA_PRESENCE -> "是否恶心";
            case VOMITING_PRESENCE -> "是否呕吐";
            case DIARRHEA_PRESENCE -> "是否腹泻";
            case COUGH_PRESENCE -> "是否咳嗽";
            case DYSPNEA_PRESENCE -> "是否呼吸困难";
            case DIAGNOSIS_HISTORY -> "既往病史";
            case MEDICATION_HISTORY -> "用药史";
            case ASSOCIATED_SYMPTOMS -> "伴随症状";
            case AGE -> "年龄";
            default -> slot.name();
        };
    }

    /**
     * 解析 LLM 槽位评分响应
     */
    private SlotScoringResult parseSlotScoringResponse(String response) {
        if (response == null || response.isBlank()) {
            log.error("[QuestionPlanSupport] LLM 响应为空");
            return null;
        }

        try {
            // 提取 JSON（处理可能的 ```json``` 包装）
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            // 解析 JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonStr);

            // 解析 scores
            List<SlotScore> scores = new ArrayList<>();
            JsonNode scoresNode = root.get("scores");
            if (scoresNode != null && scoresNode.isArray()) {
                for (JsonNode scoreNode : scoresNode) {
                    String slotName = scoreNode.get("slot").asText();
                    int score = scoreNode.get("score").asInt();
                    String reason = scoreNode.get("reason").asText();

                    try {
                        SlotCode slotCode = SlotCode.valueOf(slotName);
                        scores.add(SlotScore.builder()
                            .slot(slotCode)
                            .score(score)
                            .reason(reason)
                            .build());
                    } catch (IllegalArgumentException e) {
                        log.warn("[QuestionPlanSupport] 无法识别槽位代码: {}", slotName);
                    }
                }
            }

            // 解析 recommendation 和 rationale
            String recommendation = root.get("recommendation").asText();
            String rationale = root.get("rationale").asText();

            return SlotScoringResult.builder()
                .scores(scores)
                .recommendation(recommendation)
                .rationale(rationale)
                .build();

        } catch (Exception e) {
            log.error("[QuestionPlanSupport] 解析 LLM 响应失败", e);
            return null;
        }
    }

    /**
     * 默认兜底：选择第一个未填充的槽位
     */
    private QuestionPlan fallbackToDefaultSlot(TriageContext context, List<SlotCode> candidateSlots) {
        log.warn("[QuestionPlanSupport] LLM 评分失败，使用默认兜底策略");

        if (candidateSlots.isEmpty()) {
            return QuestionPlan.builder()
                .nextSlotsToAsk(List.of())
                .priorityReason("没有可用的候选槽位")
                .build();
        }

        SlotCode firstSlot = candidateSlots.get(0);
        log.info("[QuestionPlanSupport] 默认选择第一个槽位: {}", firstSlot);

        return QuestionPlan.builder()
            .nextSlotsToAsk(List.of(firstSlot))
            .priorityReason("默认兜底：选择 " + getSlotDescription(firstSlot))
            .build();
    }

    private record RiskGapRule(RiskSignalType riskSignalType, RiskGapSpec gapSpec) {
    }

    private record RiskGapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) {
    }

    @Data
    @Builder
    public static class SlotScoringResult {
        private List<SlotScore> scores;
        private String recommendation; // "continue" or "generate_report"
        private String rationale;
    }

    @Data
    @Builder
    public static class SlotScore {
        private SlotCode slot;
        private int score;
        private String reason;
    }
}
