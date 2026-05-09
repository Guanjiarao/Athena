# Triage Eval v2 收口记录（本轮）

## 范围

本轮目标是做 triage eval v2 的收口、自检和字段对齐，重点确保以下三处可以稳定协同：

- `bootstrap/src/test/java/com/nageoffer/ai/ragent/triage/eval/TriageEvalCase.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/triage/eval/TriageEvalNormalizer.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/triage/eval/TriageEvalReportWriter.java`

本轮明确**不做**：

- 不改 `TriageRegressionJudge`
- 不改 `triage-regression-cases-extended.json`
- 不继续发散新增大量字段
- 不把 question 文本拆分 timeline 升格为真实 history source of truth

---

## 本轮确认的统一字段口径

### 1. Schema 侧：`TriageEvalCase.Expected`

v2 结构化断言块：

- `understanding`
  - `intent`
  - `primaryComplaint`
  - `answeredSlotsContains`
  - `riskSignalsContains`
  - `correctionsContains`

- `reducer`
  - `reducedSlotValues`
  - `answeredSlotsContains`
  - `pendingSlotsNotContains`
  - `riskSignalsContains`
  - `correctionCountAtLeast`

- `planner`
  - `candidateGapsContains`
  - `selectedGapsContains`
  - `suppressedGapsContains`
  - `askabilityDecisionsContains`
  - `mustNotSelectGaps`

- `riskDecision`
  - `decisionType`
  - `shouldInterrupt`
  - `needsMoreInfo`
  - `confirmedRiskGapsContains`
  - `suspectedRiskGapsContains`
  - `unresolvedRiskGapsContains`

- `history`
  - `turnUnderstandingCountAtLeast`
  - `stateReducerHistoryCountAtLeast`
  - `riskDecisionHistoryCountAtLeast`
  - `finalPrimaryComplaint`

顶层保留：

- `strictCompatibilityFacts`

旧字段继续兼容保留：

- `slotValues`
- `finalSlotValues`
- `slotStatuses`
- `answeredSlotsContains`
- `pendingSlotsNotContains`
- `nextAction`
- `riskLevelAtLeast`
- `questionPlan`
- `mustAcknowledgeInsufficient`
- `factModifiers`
- `factPolarityHints`
- `riskHintsContains`

---

### 2. Normalizer 侧：`TriageEvalNormalizer.NormalizedEvalResult`

保留旧输出：

- `facts`
- `slotValues`
- `answeredSlots`
- `lastAskedSlots`
- `pendingSlots`
- `riskLevel`
- `riskScore`
- `nextAction`
- `questionPlan`
- `finalReply`

新增 v2 snapshot 输出：

- `understanding`
  - `intent`
  - `primaryComplaint`
  - `answeredSlots`
  - `riskSignals`
  - `corrections`

- `reducer`
  - `reducedSlotValues`
  - `answeredSlots`
  - `pendingCandidates`
  - `accumulatedRiskSignals`
  - `correctionCount`

- `planner`
  - `candidateGaps`
  - `selectedGaps`
  - `suppressedGaps`
  - `askabilityDecisions`

- `riskDecision`
  - `decisionType`
  - `shouldInterrupt`
  - `needsMoreInfo`
  - `confirmedRiskGaps`
  - `suspectedRiskGaps`
  - `unresolvedRiskGaps`

- `history`
  - `turnUnderstandingHistoryCount`
  - `stateReducerHistoryCount`
  - `riskDecisionHistoryCount`
  - `finalPrimaryComplaint`

---

### 3. Report 展示口径：`TriageEvalReportWriter`

observed markdown 直接展示上述 snapshot 结构：

- `Turn Understanding`
- `State Reducer`
- `Planner`
- `Risk Decision`
- `History / Multi-turn Summary`

不再把主展示建立在早期平铺兼容字段命名上。

---

## 本轮重点核对的字段

已重点核对并统一到 snapshot 口径：

- `history.finalPrimaryComplaint`
- `history.turnUnderstandingHistoryCount`
- `history.stateReducerHistoryCount`
- `history.riskDecisionHistoryCount`
- `understanding.primaryComplaint`
- `reducer.reducedSlotValues`
- `planner.selectedGaps`
- `riskDecision.unresolvedRiskGaps`

说明：

- schema 中的 `turnUnderstandingCountAtLeast` 是期望断言名
- normalized / report 中的 `turnUnderstandingHistoryCount` 是观察值名
- 两者用途不同，但语义链路已对齐

---

## 本轮做的收口修正

### 1. 收口到 snapshot 命名，不继续扩散平铺字段

本轮以 `docs/triage-eval-v2-代码结构草案.md` 中的 snapshot 结构为主：

- `understanding.intent`
- `understanding.primaryComplaint`
- `reducer.reducedSlotValues`
- `planner.selectedGaps`
- `riskDecision.unresolvedRiskGaps`
- `history.finalPrimaryComplaint`

不再新增新的平铺兼容字段名作为主口径。

---

### 2. 保证 report 对 understanding / reducer / riskDecision / history 全部 null-safe

确认并收口如下：

- 没有 `understanding` 时，`Turn Understanding` 展示 `None`
- 没有 `reducer` 时，`State Reducer` 展示 `None`
- 没有 `riskDecision` 时，`Risk Decision` 展示 `None`
- `history` 中计数字段为空时，report 不崩，只显示空值

---

### 3. 收口 History / Multi-turn Summary 的展示语义

本轮确认：

- `HistorySnapshot` 才是 history 相关结构化输出的承载面
- `question` 文本拆出来的 timeline 只是辅助展示

因此 report 中把 timeline 明确标注为：

- `Question Timeline (derived, display-only)`
- 并补充说明：`Derived from observed question text only; not the source of truth for turn history.`

这样可以避免后续开发误以为它是真实的 turn history 数据源。

---

### 4. 收口 History 区域字段文案风格

对 report 内 history 区域的展示文案统一为：

- `Turn Understanding History Count`
- `State Reducer History Count`
- `Risk Decision History Count`
- `Final Primary Complaint`

避免此前大小写和前缀风格不一致的问题。

---

## 与文档的一致性说明

### 与 `triage-eval-schema-v2-开发任务清单.md` 的关系

该文档中还保留了一批“平铺字段名”描述，例如：

- `turnIntent`
- `understandingAnsweredSlots`
- `candidateQuestionGaps`
- `riskDecisionType`
- `turnUnderstandingHistoryCount`

这些可以视为任务拆分阶段的说明口径。

### 与 `triage-eval-v2-代码结构草案.md` 的关系

本轮实现和收口更接近该草案中的最终结构：

- `understanding` snapshot
- `reducer` snapshot
- `planner` snapshot
- `riskDecision` snapshot
- `history` snapshot

结论：

**当前代码稳定口径应以 snapshot 嵌套结构为准。**

---

## 仍需团队统一拍板的点

### 1. 文档最终口径是否彻底切到 snapshot 嵌套结构

建议团队拍板：

- 最终外部消费口径是否统一采用：
  - `understanding.intent`
  - `reducer.reducedSlotValues`
  - `planner.selectedGaps`
  - `riskDecision.unresolvedRiskGaps`
  - `history.finalPrimaryComplaint`

而不是继续让平铺字段和嵌套字段长期并存。

---

### 2. `HistoryExpectation` 的最终命名

当前实现是：

- `finalPrimaryComplaint`

早期草案里曾出现：

- `finalPrimarySymptomMustPersist`

这两者语义不同：

- `finalPrimaryComplaint` 更像最终值断言
- `...MustPersist` 更像跨轮保持性断言

建议团队明确最终要哪一种，不要混用。

---

### 3. `turnUnderstandingCountAtLeast` 是否要和另外两个 history 断言完全对称

当前 schema 为：

- `turnUnderstandingCountAtLeast`
- `stateReducerHistoryCountAtLeast`
- `riskDecisionHistoryCountAtLeast`

这里第一项少了 `History`，风格略不对称。

本轮未改，避免扩散。
建议后续团队拍板是否要统一为：

- `turnUnderstandingHistoryCountAtLeast`

---

### 4. `Turns Count` 的地位

当前 report 里的：

- `Turns Count`

仍然来自 observed question 文本拆分，不是真正的多轮 history 计数。

建议团队统一认知：

- 正式多轮计数以 `history.*Count` 为准
- `Turns Count` 只是展示辅助项

---

## 本轮自检结论

已完成最小自检：

- `TriageEvalCase.java` lint 通过
- `TriageEvalNormalizer.java` lint 通过
- `TriageEvalReportWriter.java` lint 通过
- `mvn -q -DskipTests test-compile` 通过

说明当前三者的收口状态可稳定被后续 judge / extended case / report 链路继续消费。
