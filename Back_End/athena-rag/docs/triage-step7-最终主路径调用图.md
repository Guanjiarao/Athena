# Triage Step 7 当前阶段主路径调用图

> 这份文档描述的是 Step 7 当前代码状态下，triage 的真实执行顺序。
> 目标不是画概念架构，而是明确：主链从哪里开始、按什么顺序走、哪些节点仍带有 compatibility / fallback 支路。
> 它不是终局架构图，而是当前阶段代码结构的准确快照。

## 1. 主入口

当前主入口位于：

- `com.nageoffer.ai.ragent.triage.engine.TriageStateMachine`

状态机驱动的主流程是：

1. `INIT`
2. `PARSING`
3. `VALIDATING`
4. `RISK_ASSESSING`
5. `REPORT_GENERATING`
6. `COMPLETED / INTERRUPTED`

---

## 2. 当前阶段主路径顺序

按当前真实代码，主路径顺序如下：

```text
TriageStateMachine.execute(context)
  -> handleParsing(...)
    -> TriageStageExecutor.executeParsing(...)
      1. TurnUnderstandingWorker.execute(context)
      2. SemanticParserWorker.execute(context)
      3. FactExtractor.execute(context)
           -> FactHeuristicExtractor.extract(...)            [COMPATIBILITY_ONLY adapter]
           -> ComplaintFallbackResolver                      [LEGACY_FALLBACK, via primary complaint fallback path]
      4. StateReducer.execute(context)
      5. SlotManager.execute(context)                        [projection orchestrator; compatibility seam still exists]
           -> CompatibilitySlotFallback                      [COMPATIBILITY_ONLY seam, only when reducer projection is absent]

  -> handleValidation(...)
    -> TriageStageExecutor.executeValidation(...)
      6. QuestionPlanner.execute(context)
           -> QuestionPlanSupport.determineQuestionGaps(...)
                -> SemanticSignalResolver                    [COMPATIBILITY_ONLY primary-signal fact bridge only]
      7. SOPValidatorWorker.execute(context)
           -> only when QuestionPlan is absent (legacy validation fallback)

  -> handleRiskAssessment(...)
      8. RiskHeuristicHelper.shouldFastTrackHighRisk(context)
           -> hardRedFlagFallback(...)                       [KEEP_GUARDRAIL]
           -> RiskTextSnapshotBuilder.build(...)             [text seam]
      9. RiskStratifierWorker.execute(context)
           -> hardRedFlagFallback(...)                       [KEEP_GUARDRAIL]
           -> heuristicRiskFallback(...)                     [delegates hard -> legacy fallback]
                -> LegacyRiskFallback.evaluate(...)          [LEGACY_FALLBACK]
                -> RiskTextSnapshotBuilder.build(...)        [text seam]

  -> handleReportGeneration(...)
      10. TriageReplyBuilder.generatePreTriageReport(...)
```

---

## 3. 分阶段调用图

### 3.1 Parsing 阶段

这是当前最关键的“结构化理解 -> 状态归并 -> 投影”主链。

```text
latestUserTurn
  -> TurnUnderstandingWorker
      输出：TurnUnderstanding

  -> SemanticParserWorker
      输出：structured symptoms / semantic parse artifacts

  -> FactExtractor
      输出：LLM facts + heuristic compatibility facts

  -> StateReducer
      输入：TurnUnderstanding + existing slotState + factHistory
      输出：StateReducerResult
        - reducedSlots
        - answeredSlots
        - pendingCandidates
        - correctionLog
        - accumulatedRiskSignals

  -> SlotManager
      输入：StateReducerResult + slotState + factHistory
      输出：projected slotState + extractedSymptoms
      若 reducer projection 缺失：
        -> CompatibilitySlotFallback
           - mergeFactsIntoSlotState(...)
           - resolveCompatibilityAnsweredSlots(...)
```

当前阶段的核心变化在这一段：

- `TurnUnderstandingWorker` / `StateReducer` 已成为主解释链
- `FactHeuristicExtractor` 已从厚类拆成 adapter + scope + pattern matcher 结构
- `SlotManager` 已拆成 projection orchestrator + compatibility seam，更接近 projection only，但还不是完全纯投影

---

### 3.2 Validation 阶段

```text
QuestionPlanner.execute(context)
  -> QuestionPlanSupport.determineQuestionGaps(context)
  -> determineQuestionNeeds(context)
  -> evaluateAskability(...)
  -> selectGapsByPolicy(...)
  -> write QuestionPlan / pendingSlots / lastAskedSlots

if QuestionPlan is absent:
  -> SOPValidatorWorker.execute(context)
```

当前阶段的核心变化在这一段：

- planner 主体已经从 `slotState` / structured symptom / `riskSignalState` 出发
- `SemanticSignalResolver` 已进一步缩到仅保留 primary-signal fact bridge
- legacy validation fallback 只有在 `QuestionPlan == null` 时才补位

---

### 3.3 Risk Assessing 阶段

```text
handleValidation(...)
  -> RiskHeuristicHelper.shouldFastTrackHighRisk(context)
     -> hardRedFlagFallback(...)
     -> RiskTextSnapshotBuilder.build(...)

if fast-track high risk:
  -> directly enter risk assessing path

RiskStratifierWorker.execute(context)
  -> hardRedFlagFallback(...)       [KEEP_GUARDRAIL]
  -> heuristicRiskFallback(...)
       -> LegacyRiskFallback.evaluate(...)   [LEGACY_FALLBACK]
       -> RiskTextSnapshotBuilder.build(...) [text seam]
  -> LLM risk stratification
  -> normalize / append RiskDecision
```

当前阶段的核心变化在这一段：

- `hardRedFlagFallback(...)` 是明确保留的安全护栏
- `LegacyRiskFallback` 已从混合启发式里拆出来成为独立结构
- `RiskTextSnapshotBuilder` 已从 helper 主体剥离
- hard guardrail 与 legacy fallback 都开始转向 canonical/state-first，文本 snapshot 仍保留为补缝 seam

---

## 4. 当前主链 vs 边缘辅助边界

### 4.1 真正的主链节点

当前可以明确视为主链主体的节点：

1. `TurnUnderstandingWorker`
2. `StateReducer`
3. `SlotManager`（主职责已更接近 projection orchestrator）
4. `QuestionPlanner`
5. `RiskStratifierWorker`
6. `TriageStateMachine`

### 4.2 仍保留但不应再被视为主链主体的边缘节点

#### `COMPATIBILITY_ONLY`

- `FactHeuristicExtractor`
- `CompatibilityFactScope`
- `CompatibilityFactPatternMatcher`
- `SemanticSignalResolver`
- `CompatibilitySlotFallback`

#### `LEGACY_FALLBACK`

- `ComplaintFallbackResolver`
- `LegacyRiskFallback`

#### `KEEP_GUARDRAIL`

- `RiskHeuristicHelper.hardRedFlagFallback(...)`

#### `TEXT SEAM / SNAPSHOT`

- `RiskTextSnapshotBuilder`

---

## 5. 当前最重要的边界结论

### 5.1 answered / pending 的主来源

现在主来源应该理解为：

- answered：`TurnUnderstanding` + `StateReducerResult`
- pending：`QuestionPlanner` + reducer state

而不是：

- facts 自己广泛推进 answered
- `SlotManager` fallback 广泛二次推断 pending

### 5.2 planner 的主来源

现在 planner 的主来源应该理解为：

- `slotState`
- `QuestionPlanSupport`
- `riskSignalState`
- structured symptoms

而不是：

- 大量普通 fact bridge
- 宽泛 semantic keyword 扫词

### 5.3 risk 的主来源

现在 risk 的主来源应该理解为：

- `RiskStratifierWorker`
- structured risk decision
- `hardRedFlagFallback(...)` 作为安全底线
- `LegacyRiskFallback` 作为少量保守 fallback

而不是：

- 普通灰区 heuristic 升级
- symptom/slot load 式文本 heuristic

---

## 6. 仍存在的支路与残余

当前仍然保留、但已经不应被视为主路径主体的支路包括：

### Parsing 侧

- `FactHeuristicExtractor`
- `ComplaintFallbackResolver`

### Planner 侧

- `SemanticSignalResolver` 的 primary-signal fact bridge

### Slot projection 侧

- `CompatibilitySlotFallback`

### Risk 侧

- `LegacyRiskFallback`
- `RiskTextSnapshotBuilder`
- `hardRedFlagFallback(...)` / `legacy fallback` 中仍存在的文本补缝判断

这些支路就是后续阶段继续清理的主要对象。

---

## 7. 给测试 / review 的一句话版本

如果要用一句话描述当前阶段主路径，可以说：

> triage 现在已经形成“理解 -> reducer -> projection -> planner -> risk -> state machine”的主链，
> heuristic 仍存在，但已经被压缩并映射到 hard guardrail、legacy fallback、compatibility helper、text seam 这几类边缘职责中；它们已明显收缩，但尚未完全退出。 
