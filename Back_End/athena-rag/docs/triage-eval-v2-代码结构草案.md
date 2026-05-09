# Triage Eval v2 代码结构草案

> 配套：
>
> - `docs/triage-eval-schema-v2-升级清单.md`
> - `docs/triage-eval-schema-v2-开发任务清单.md`

## 1. 目标

这份草案不是最终实现代码，而是给开发同学一个可直接照着改的结构蓝图。

重点覆盖 3 个核心类：

1. `TriageEvalCase`
2. `TriageEvalNormalizer`
3. `TriageRegressionJudge`

并明确：

- regression 与 extended 继续共用同一套底座
- 不推翻现有 eval 框架
- 在兼容旧 case 的前提下扩展 v2 能力

---

# 2. `TriageEvalCase` v2 草案

## 2.1 设计原则

- 保留现有顶层结构：`turns + context + expected + forbidden`
- 在 `Expected` 中增量扩展结构化断言块
- 旧字段继续兼容，但部分会降级

## 2.2 目标结构

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageEvalCase {

    private String id;
    private String category;
    private String priority;

    @Builder.Default
    private List<Turn> turns = new ArrayList<>();

    private EvalContext context;
    private Expected expected;

    @Builder.Default
    private List<String> forbidden = new ArrayList<>();

    private Boolean strictCompatibilityFacts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Expected {
        @Builder.Default
        private Map<String, String> slotValues = new LinkedHashMap<>();

        @Builder.Default
        private Map<String, String> finalSlotValues = new LinkedHashMap<>();

        @Builder.Default
        private Map<String, String> slotStatuses = new LinkedHashMap<>();

        @Builder.Default
        private List<String> answeredSlotsContains = new ArrayList<>();

        @Builder.Default
        private List<String> pendingSlotsNotContains = new ArrayList<>();

        private String nextAction;
        private String riskLevelAtLeast;
        private QuestionPlanExpectation questionPlan;
        private Boolean mustAcknowledgeInsufficient;

        // v1 legacy
        @Builder.Default
        private Map<String, String> factModifiers = new LinkedHashMap<>();

        @Builder.Default
        private List<FactPolarityHint> factPolarityHints = new ArrayList<>();

        @Builder.Default
        private List<String> riskHintsContains = new ArrayList<>();

        // v2 structured blocks
        private UnderstandingExpectation understanding;
        private ReducerExpectation reducer;
        private PlannerExpectation planner;
        private RiskDecisionExpectation riskDecision;
        private HistoryExpectation history;
    }
}
```

## 2.3 新增子结构建议

### `UnderstandingExpectation`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class UnderstandingExpectation {
    private String intent;
    private String primaryComplaint;

    @Builder.Default
    private List<String> answeredSlotsContains = new ArrayList<>();

    @Builder.Default
    private List<String> riskSignalsContains = new ArrayList<>();

    @Builder.Default
    private List<String> correctionsContains = new ArrayList<>();
}
```

### `ReducerExpectation`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class ReducerExpectation {
    @Builder.Default
    private Map<String, String> reducedSlotValues = new LinkedHashMap<>();

    @Builder.Default
    private List<String> answeredSlotsContains = new ArrayList<>();

    @Builder.Default
    private List<String> pendingSlotsNotContains = new ArrayList<>();

    @Builder.Default
    private List<String> riskSignalsContains = new ArrayList<>();

    private Integer correctionCountAtLeast;
}
```

### `PlannerExpectation`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class PlannerExpectation {
    @Builder.Default
    private List<String> candidateGapsContains = new ArrayList<>();

    @Builder.Default
    private List<String> selectedGapsContains = new ArrayList<>();

    @Builder.Default
    private List<String> suppressedGapsContains = new ArrayList<>();

    @Builder.Default
    private List<String> askabilityDecisionsContains = new ArrayList<>();

    @Builder.Default
    private List<String> mustNotSelectGaps = new ArrayList<>();
}
```

### `RiskDecisionExpectation`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class RiskDecisionExpectation {
    private String decisionType;
    private Boolean shouldInterrupt;
    private Boolean needsMoreInfo;

    @Builder.Default
    private List<String> confirmedRiskGapsContains = new ArrayList<>();

    @Builder.Default
    private List<String> suspectedRiskGapsContains = new ArrayList<>();

    @Builder.Default
    private List<String> unresolvedRiskGapsContains = new ArrayList<>();
}
```

### `HistoryExpectation`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class HistoryExpectation {
    private Integer turnUnderstandingCountAtLeast;
    private Integer riskDecisionHistoryCountAtLeast;
    private String finalPrimarySymptomMustPersist;
}
```

## 2.4 兼容性要求

- regression / extended 两个 case 文件继续共用 `TriageEvalCase`
- 所有新增字段都必须是 optional
- 旧 case 不做改动也能正常反序列化

---

# 3. `TriageEvalNormalizer` v2 草案

## 3.1 设计原则

当前 normalizer 主要输出：

- facts
- slotValues
- answered / pending
- riskLevel
- nextAction
- questionPlan
- finalReply

v2 必须继续保留这些，同时增加新主链观测结果。

## 3.2 目标结构

```java
public class TriageEvalNormalizer {

    public NormalizedEvalResult normalize(String caseId, TriageContext context) {
        TriageContext safeContext = context == null ? new TriageContext() : context;
        safeContext.ensureCollections();

        return NormalizedEvalResult.builder()
                .caseId(caseId)
                .facts(new ArrayList<>(safeContext.getFactHistory()))
                .slotValues(normalizeSlotValues(safeContext))
                .answeredSlots(toNames(safeContext.getAnsweredSlots()))
                .lastAskedSlots(toNames(safeContext.getLastAskedSlots()))
                .pendingSlots(toNames(safeContext.getPendingSlots()))
                .riskLevel(normalizeRiskLevel(safeContext.getRiskAssessment()))
                .riskScore(...)
                .nextAction(...)
                .questionPlan(normalizeQuestionPlan(...))
                .finalReply(safeContext.getFinalReply())

                // v2
                .understanding(normalizeUnderstanding(safeContext))
                .reducer(normalizeReducer(safeContext))
                .planner(normalizePlanner(safeContext))
                .riskDecision(normalizeRiskDecision(safeContext))
                .history(normalizeHistory(safeContext))
                .build();
    }
}
```

## 3.3 新增 snapshot 建议

### `UnderstandingSnapshot`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class UnderstandingSnapshot {
    private String intent;
    private String primaryComplaint;

    @Builder.Default
    private List<String> answeredSlots = new ArrayList<>();

    @Builder.Default
    private List<String> riskSignals = new ArrayList<>();

    @Builder.Default
    private List<String> corrections = new ArrayList<>();
}
```

### `ReducerSnapshot`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class ReducerSnapshot {
    @Builder.Default
    private Map<String, SlotSnapshot> reducedSlotValues = new LinkedHashMap<>();

    @Builder.Default
    private List<String> answeredSlots = new ArrayList<>();

    @Builder.Default
    private List<String> pendingCandidates = new ArrayList<>();

    @Builder.Default
    private List<String> accumulatedRiskSignals = new ArrayList<>();

    private Integer correctionCount;
}
```

### `PlannerSnapshot`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class PlannerSnapshot {
    @Builder.Default
    private List<String> candidateGaps = new ArrayList<>();

    @Builder.Default
    private List<String> selectedGaps = new ArrayList<>();

    @Builder.Default
    private List<String> suppressedGaps = new ArrayList<>();

    @Builder.Default
    private List<String> askabilityDecisions = new ArrayList<>();
}
```

### `RiskDecisionSnapshot`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class RiskDecisionSnapshot {
    private String decisionType;
    private Boolean shouldInterrupt;
    private Boolean needsMoreInfo;

    @Builder.Default
    private List<String> confirmedRiskGaps = new ArrayList<>();

    @Builder.Default
    private List<String> suspectedRiskGaps = new ArrayList<>();

    @Builder.Default
    private List<String> unresolvedRiskGaps = new ArrayList<>();
}
```

### `HistorySnapshot`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class HistorySnapshot {
    private Integer turnUnderstandingHistoryCount;
    private Integer stateReducerHistoryCount;
    private Integer riskDecisionHistoryCount;
    private String finalPrimaryComplaint;
}
```

## 3.4 关键实现要求

- 所有 snapshot 都要 null-safe
- 没有 understanding / reducer / riskDecision 时不能抛异常
- regression / extended observed report 都复用这份 normalized 结果

---

# 4. `TriageRegressionJudge` v2 草案

## 4.1 设计原则

从“reply/fact/slot proxy 优先”改成“结构化优先，文本兜底”。

## 4.2 目标执行顺序

```java
public TriageRegressionJudgement judgeCase(TriageEvalCase c, TriageEvalObservedCaseResult o) {
    List<String> ok = new ArrayList<>();
    List<String> bad = new ArrayList<>();

    var n = o == null ? null : o.getNormalizedResult();
    var e = c == null ? null : c.getExpected();

    if (e != null) {
        understanding(e.getUnderstanding(), n, ok, bad);
        reducer(e.getReducer(), n, ok, bad);
        planner(e.getPlanner(), n, ok, bad);
        riskDecision(e.getRiskDecision(), n, ok, bad);

        eq("nextAction", e.getNextAction(), n == null ? null : n.getNextAction(), ok, bad);
        slots("slotValues", e.getSlotValues(), n, ok, bad, false);
        slots("finalSlotValues", e.getFinalSlotValues(), n, ok, bad, false);
        slots("slotStatuses", e.getSlotStatuses(), n, ok, bad, true);
        containsAll("answeredSlots", e.getAnsweredSlotsContains(), n == null ? List.of() : n.getAnsweredSlots(), ok, bad);
        excludesAll("pendingSlots", e.getPendingSlotsNotContains(), n == null ? List.of() : n.getPendingSlots(), ok, bad);
        risk(e.getRiskLevelAtLeast(), n, ok, bad);
        qplan(e.getQuestionPlan(), n, ok, bad);
        ack(e.getMustAcknowledgeInsufficient(), n, ok, bad);

        compatibilityFacts(c, e, n, ok, bad);
        replyHints(e.getRiskHintsContains(), n, ok, bad);
    }

    forbidden(c == null ? null : c.getForbidden(), n, ok, bad);
    return buildJudgement(...);
}
```

## 4.3 新增判定函数建议

### `understanding(...)`

职责：

- 判 `intent`
- 判 `primaryComplaint`
- 判 `answeredSlotsContains`
- 判 `riskSignalsContains`
- 判 `correctionsContains`

### `reducer(...)`

职责：

- 判 `reducedSlotValues`
- 判 reducer answered slots
- 判 reducer pending candidates
- 判 reducer risk signals
- 判 correction count

### `planner(...)`

职责：

- 判 candidate / selected / suppressed gaps
- 判 askability decisions
- 判 mustNotSelectGaps

### `riskDecision(...)`

职责：

- 判 `decisionType`
- 判 `shouldInterrupt`
- 判 `needsMoreInfo`
- 判 confirmed / suspected / unresolved gaps

## 4.4 旧断言降级建议

### `factPolarityHints`

改为：

- 默认 compatibility-only
- 仅当 `strictCompatibilityFacts=true` 时才直接 fail

### `riskHintsContains`

改为：

- 仅作为 reply-level 观察项
- 不再承担风险主链正确性主判

### `forbidden.REASK_*`

改为：

1. 先判结构化：`questionPlan / planner.selectedGaps / pendingSlots`
2. 再用 reply 文本扫词兜底

## 4.5 输出格式要求

judged report 中应能分层展示失败信息，例如：

- Understanding failures
- Reducer failures
- Planner failures
- RiskDecision failures
- Final behavior failures

---

# 5. regression / extended 的共用关系

## 5.1 共用底座不变

以下类继续共用：

- `TriageEvalCase`
- `TriageEvalRunner`
- `TriageEvalRealExecutor`
- `TriageEvalNormalizer`
- `TriageRegressionJudge`
- `TriageEvalReportWriter`
- `TriageRegressionJudgementReportWriter`

## 5.2 两个 suite 的差异只留在：

- case 文件路径
- report 输出路径
- case 设计策略

### regression

继续偏：

- 门禁
- 稳定回归
- 关键主行为

### extended

继续偏：

- 多轮
- 变体
- 结构化链路验证
- Step 7 收口边界

## 5.3 实施要求

- 不允许只升级 regression judge，不升级 extended judge
- 不允许只升级 judged report，不升级 observed report
- 不允许只升级 normalizer 给 regression 用，extended 仍走旧结构

---

# 6. 最小落地顺序

建议按下面顺序开发：

1. 改 `TriageEvalCase`
2. 改 `TriageEvalNormalizer`
3. 改 `TriageRegressionJudge`
4. 改 observed / judged report writer
5. 先给 extended 增加一批 v2 case
6. 再慢慢让 regression 吃到 v2 字段

---

## 7. 一句话总结

这版代码结构草案的核心思想是：

- `TriageEvalCase` 继续做统一输入 schema
- `TriageEvalNormalizer` 成为新主链的统一观测出口
- `TriageRegressionJudge` 从旧 proxy 判定器升级为结构化分层判定器
- regression 和 extended 继续共用同一套 eval 底座，只在 case 内容与使用目标上分工