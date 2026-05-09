# Triage Eval v2 Judge 本轮改动说明

## 本轮目标

把 triage eval v2 的 judge 从 placeholder 补成真正可用的结构化判定逻辑，并与 judged report writer 的 layer grouping 对齐。

## 本轮实际修改文件

- `bootstrap/src/test/java/com/nageoffer/ai/ragent/triage/eval/TriageRegressionJudge.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/triage/eval/TriageRegressionJudgeSupport.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/triage/eval/TriageRegressionJudgementReportWriter.java`

## 1. Judge 当前结构

### `TriageRegressionJudge.java`

保持为薄入口：

- `public class TriageRegressionJudge extends TriageRegressionJudgeSupport {}`

实际判定逻辑放在 `TriageRegressionJudgeSupport.java`。

### `TriageRegressionJudgeSupport.java`

已经从 placeholder 改为真实 judge 逻辑。

之前的 placeholder 行为是：

- `passed=false`
- `failedChecks=["judge support placeholder"]`

现在已经改为：

- 真正消费 `TriageEvalNormalizer.NormalizedEvalResult`
- 产出分层 `passedChecks`
- 产出分层 `failedChecks`
- 根据失败列表是否为空决定 `passed`

## 2. 新增的结构化判定层

judge 现在按以下顺序执行：

1. `Understanding`
2. `Reducer`
3. `Planner`
4. `RiskDecision`
5. `Final behavior`
6. `Compatibility`

### Understanding 层

消费 normalizer 的：

- `understanding.intent`
- `understanding.primaryComplaint`
- `understanding.answeredSlots`
- `understanding.riskSignals`
- `understanding.corrections`

对应断言：

- `expected.understanding.intent`
- `expected.understanding.primaryComplaint`
- `expected.understanding.answeredSlotsContains`
- `expected.understanding.riskSignalsContains`
- `expected.understanding.correctionsContains`

### Reducer 层

消费 normalizer 的：

- `reducer.reducedSlotValues`
- `reducer.answeredSlots`
- `reducer.pendingCandidates`
- `reducer.accumulatedRiskSignals`
- `reducer.correctionCount`

对应断言：

- `expected.reducer.reducedSlotValues`
- `expected.reducer.answeredSlotsContains`
- `expected.reducer.pendingSlotsNotContains`
- `expected.reducer.riskSignalsContains`
- `expected.reducer.correctionCountAtLeast`

### Planner 层

消费 normalizer 的：

- `planner.candidateGaps`
- `planner.selectedGaps`
- `planner.suppressedGaps`
- `planner.askabilityDecisions`

对应断言：

- `expected.planner.candidateGapsContains`
- `expected.planner.selectedGapsContains`
- `expected.planner.suppressedGapsContains`
- `expected.planner.askabilityDecisionsContains`
- `expected.planner.mustNotSelectGaps`

### RiskDecision 层

消费 normalizer 的：

- `riskDecision.decisionType`
- `riskDecision.shouldInterrupt`
- `riskDecision.needsMoreInfo`
- `riskDecision.confirmedRiskGaps`
- `riskDecision.suspectedRiskGaps`
- `riskDecision.unresolvedRiskGaps`

对应断言：

- `expected.riskDecision.decisionType`
- `expected.riskDecision.shouldInterrupt`
- `expected.riskDecision.needsMoreInfo`
- `expected.riskDecision.confirmedRiskGapsContains`
- `expected.riskDecision.suspectedRiskGapsContains`
- `expected.riskDecision.unresolvedRiskGapsContains`

## 3. 保留的旧判定

以下旧断言仍然保留在 `Final behavior` 层：

- `nextAction`
- `slotValues`
- `finalSlotValues`
- `slotStatuses`
- `answeredSlotsContains`
- `pendingSlotsNotContains`
- `riskLevelAtLeast`
- `questionPlan`
- `mustAcknowledgeInsufficient`
- `forbidden`

其中：

- `slotValues/finalSlotValues/slotStatuses` 仍然直接消费 `normalizedResult.slotValues`
- `questionPlan` 仍然消费 `normalizedResult.questionPlan`
- `mustAcknowledgeInsufficient` 仍然使用 final reply 文本判断

## 4. Compatibility 降级实现

### `factPolarityHints`

已降级为 compatibility-only：

- 默认情况下：
  - 不再严格 fail
  - 只记录为 `[Compatibility] factPolarityHint ignored ...`
- 只有 `strictCompatibilityFacts=true` 时：
  - 才进入失败列表
  - 输出形如：`[Compatibility] expected factPolarityHint evidence=... polarity=...`

### `riskHintsContains`

已改为 reply-level observation：

- 不再作为严格失败条件
- 无论命中与否，都会记录一条 compatibility 信息
- 输出形如：
  - `[Compatibility] riskHintsContains observed ALTERED_CONSCIOUSNESS`
  - `[Compatibility] riskHintsContains missing ALTERED_CONSCIOUSNESS`

### `forbidden.REASK_*`

已实现“结构化优先，文本兜底”：

先看结构化：

- `planner.selectedGaps`
- `questionPlan.nextSlotsToAsk`

如果这些结构化结果里已经命中对应 gap / slot，就认为触发了 `REASK_*`。

如果结构化没有命中，再退回到 final reply 文本匹配：

- `REASK_FEVER_PRESENCE`
- `REASK_NAUSEA_PRESENCE`
- `REASK_VOMITING_PRESENCE`
- `REASK_DURATION`
- `REASK_BODY_PART`

## 5. 失败项格式

judge 现在输出的失败项已经带层级前缀，和 report writer 的 grouping 对齐。

示例：

- `[Understanding] expected intent=FOLLOW_UP_ANSWER, actual=...`
- `[Reducer] expected pendingSlots excludes DURATION, actual=...`
- `[Planner] expected selectedGaps contains DURATION, actual=...`
- `[RiskDecision] expected decisionType=ASK_RISK_CLARIFICATION, actual=...`
- `[Final behavior] expected nextAction=ASK_CLARIFICATION, actual=...`
- `[Compatibility] expected factPolarityHint evidence=... polarity=...`

## 6. Judged report writer 本轮状态

`TriageRegressionJudgementReportWriter.java` 已支持按层展示：

- `Understanding failures`
- `Reducer failures`
- `Planner failures`
- `RiskDecision failures`
- `Final behavior failures`

并且会把 `Compatibility` 失败并入 `Final behavior failures` 区块中展示。

## 7. 最小自检结果

已对以下文件做最小 lints 检查：

- `TriageRegressionJudge.java`
- `TriageRegressionJudgeSupport.java`
- `TriageRegressionJudgementReportWriter.java`

结果：

- no linter errors

## 8. 本轮未改内容

按约束，本轮没有修改：

- `TriageEvalCase`
- `TriageEvalNormalizer`
- observed report writer
- json case 文件

## 9. 当前风险 / 后续建议

虽然 judge 已从 placeholder 接成真实逻辑，但建议下一步再做一次真实 case 级验证，重点确认：

1. `fragment contains` 策略是否与现有 case 文案完全匹配
2. `REASK_*` 的结构化优先判定是否覆盖现有 planner 输出格式
3. `Compatibility` 降级后的输出是否符合预期，不会误导读报告的人
4. `History` 结构当前尚未接入 judge，如后续需要可以继续补一层 `History` 判定
