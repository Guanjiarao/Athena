# Triage Eval v2 Extended Case 本轮进展记录

> 时间：本轮仅记录 extended v2 case 推进，不涉及 Java 逻辑修改。
>
> 约束执行情况：
>
> - 未修改 `TriageEvalCase`
> - 未修改 `TriageEvalNormalizer`
> - 未修改 `TriageRegressionJudge`
> - 仅修改 `resources/eval/triage/triage-regression-cases-extended.json`

## 本轮目标

把 extended case 从以旧版断言为主，推进到真正开始使用 triage eval v2 的结构化字段：

- `expected.understanding`
- `expected.reducer`
- `expected.planner`
- `expected.riskDecision`
- `expected.history`

本轮不做整份文件重写，只新增一小批 v2 风格 case，优先覆盖以下链路：

1. correction
2. multi-turn primary complaint persistence
3. planner suppression
4. unresolved risk gap
5. history escalation

---

## 已核对的真实契约

### `TriageEvalCase.Expected` 已支持的 v2 字段

- `understanding`
- `reducer`
- `planner`
- `riskDecision`
- `history`
- `strictCompatibilityFacts`

### `TriageEvalNormalizer` 已输出的结构化字段

#### understanding

- `intent`
- `primaryComplaint`
- `answeredSlots`
- `riskSignals`
- `corrections`

#### reducer

- `reducedSlotValues`
- `answeredSlots`
- `pendingCandidates`
- `accumulatedRiskSignals`
- `correctionCount`

#### planner

- `candidateGaps`
- `selectedGaps`
- `suppressedGaps`
- `askabilityDecisions`

#### riskDecision

- `decisionType`
- `shouldInterrupt`
- `needsMoreInfo`
- `confirmedRiskGaps`
- `suspectedRiskGaps`
- `unresolvedRiskGaps`

#### history

- `turnUnderstandingHistoryCount`
- `stateReducerHistoryCount`
- `riskDecisionHistoryCount`
- `finalPrimaryComplaint`

说明：case 文件中的断言字段命名，仍然必须使用 `TriageEvalCase.Expected` 中定义的 expectation 命名，而不是直接复用 normalizer snapshot 字段名。

---

## 本轮新增的 true v2 extended case

本轮新增以下 5 条，作为“结构化链路判定”的第一批 extended 种子 case：

- `TRIAGE-EXT-V2-045`
- `TRIAGE-EXT-V2-046`
- `TRIAGE-EXT-V2-047`
- `TRIAGE-EXT-V2-048`
- `TRIAGE-EXT-V2-049`

这些 case 的共同特点：

- 断言重点放在 `understanding / reducer / planner / riskDecision / history`
- 旧字段只保留少量兼容兜底，如 `nextAction`、少量 `forbidden`
- 不再以 `slotValues / questionPlan / pendingSlotsNotContains` 作为唯一主断言

---

## Case 逐条说明

### 1. `TRIAGE-EXT-V2-045`

**类型**：`v2_correction_structured`

**覆盖链路能力**：

- correction
- understanding 识别纠正意图
- reducer 覆盖旧槽位值
- planner suppress 已纠正槽位
- history 维持最终主诉

**核心 v2 断言**：

- `expected.understanding.intent = CORRECTION`
- `expected.understanding.primaryComplaint = 腹痛`
- `expected.understanding.correctionsContains = [BODY_PART]`
- `expected.reducer.reducedSlotValues.BODY_PART = 右下腹`
- `expected.reducer.correctionCountAtLeast = 1`
- `expected.reducer.pendingSlotsNotContains = [BODY_PART]`
- `expected.planner.suppressedGapsContains = [BODY_PART]`
- `expected.planner.mustNotSelectGaps = [BODY_PART]`
- `expected.history.turnUnderstandingCountAtLeast = 2`
- `expected.history.finalPrimarySymptomMustPersist = 腹痛`

**价值**：

这是第一条真正把 correction 串到 understanding → reducer → planner → history 的 case，不再只是验证“最终值变了”。

---

### 2. `TRIAGE-EXT-V2-046`

**类型**：`v2_multi_turn_primary_complaint_persistence`

**覆盖链路能力**：

- multi-turn primary complaint persistence
- understanding 判定后续轮次是在回答 slot，而不是提出新主诉
- reducer 累积多轮状态
- history 追踪最终主诉持续

**核心 v2 断言**：

- `expected.understanding.intent = ANSWER_SLOT`
- `expected.understanding.primaryComplaint = 腹痛`
- `expected.understanding.answeredSlotsContains = [FEVER_PRESENCE=NO]`
- `expected.reducer.reducedSlotValues` 包含：
  - `BODY_PART = 右下腹`
  - `DURATION = 昨晚开始`
  - `FEVER_PRESENCE = NO`
- `expected.reducer.pendingSlotsNotContains` 覆盖已答槽位
- `expected.history.turnUnderstandingCountAtLeast = 3`
- `expected.history.finalPrimarySymptomMustPersist = 腹痛`

**价值**：

这条是主诉持续链路的 v2 样本，重点不再是最终 slot 填对，而是验证“最后一轮不是新 complaint 漂移”。

---

### 3. `TRIAGE-EXT-V2-047`

**类型**：`v2_planner_suppression_structured`

**覆盖链路能力**：

- planner suppression
- understanding 识别本轮一次性回答了多个 gap
- reducer 去除已回答 pending
- planner 区分 selected gap 和 suppressed gap

**核心 v2 断言**：

- `expected.understanding.primaryComplaint = 腹痛`
- `expected.understanding.answeredSlotsContains` 包含：
  - `BODY_PART=右下腹`
  - `DURATION=昨晚开始`
  - `FEVER_PRESENCE=NO`
  - `NAUSEA_PRESENCE=NO`
- `expected.reducer.pendingSlotsNotContains` 覆盖已答 gap
- `expected.planner.selectedGapsContains = [VOMITING_PRESENCE]`
- `expected.planner.suppressedGapsContains` 包含：
  - `BODY_PART`
  - `DURATION`
  - `FEVER_PRESENCE`
  - `NAUSEA_PRESENCE`
- `expected.planner.mustNotSelectGaps` 同步约束这些 gap
- `expected.history.finalPrimarySymptomMustPersist = 腹痛`

**价值**：

这是 planner 结构化断言的核心样本，重点是区分“被选中继续问”和“已经被 suppress 不该再问”的 gap 集合。

---

### 4. `TRIAGE-EXT-V2-048`

**类型**：`v2_unresolved_risk_gap_structured`

**覆盖链路能力**：

- unresolved risk gap
- planner 识别当前被追问的风险 gap
- riskDecision 识别 unresolved / needsMoreInfo / 不该 interrupt
- history 记录多轮风险决策轨迹

**核心 v2 断言**：

- `expected.understanding.primaryComplaint = 胸部不适`
- `expected.planner.selectedGapsContains = [DYSPNEA_PRESENCE]`
- `expected.riskDecision.shouldInterrupt = false`
- `expected.riskDecision.needsMoreInfo = true`
- `expected.riskDecision.unresolvedRiskGapsContains = [DYSPNEA_PRESENCE]`
- `expected.history.turnUnderstandingCountAtLeast = 2`
- `expected.history.riskDecisionHistoryCountAtLeast = 1`
- `expected.history.finalPrimarySymptomMustPersist = 胸部不适`

**价值**：

这是 riskDecision 的 unresolved 结构化样本，能帮助后续 judge 真正区分“高危确认”与“高危未确认仍需继续追问”。

---

### 5. `TRIAGE-EXT-V2-049`

**类型**：`v2_history_escalation_structured`

**覆盖链路能力**：

- history escalation
- understanding 识别后续轮次新增高危信息
- reducer 更新风险相关槽位
- riskDecision 从普通追问升级到 confirmed / escalated
- history 维持主诉不漂移

**核心 v2 断言**：

- `expected.understanding.primaryComplaint = 胸闷`
- `expected.understanding.answeredSlotsContains = [DYSPNEA_PRESENCE=YES]`
- `expected.reducer.reducedSlotValues.DYSPNEA_PRESENCE = YES`
- `expected.riskDecision.decisionType = CONFIRMED_OR_ESCALATED`
- `expected.riskDecision.shouldInterrupt = true`
- `expected.riskDecision.confirmedRiskGapsContains = [DYSPNEA_PRESENCE]`
- `expected.history.turnUnderstandingCountAtLeast = 2`
- `expected.history.riskDecisionHistoryCountAtLeast = 1`
- `expected.history.finalPrimarySymptomMustPersist = 胸闷`

**价值**：

这条是 history escalation 的首批结构化样本，用来验证“风险升级”不只是 reply 或 riskLevel 文本变化，而是真正落在 `riskDecision + history` 结构上。

---

## 哪些 case 依赖 judge 新逻辑完成后才能真正发挥作用

当前 `TriageRegressionJudge` 仍是 placeholder，尚未消费这些 v2 expectation。

因此，以下 case 已经是正确的 v2 结构化样本，但仍需要 judge 新逻辑接入后，才能真正作为 pass/fail 门禁生效：

- `TRIAGE-EXT-V2-045`
- `TRIAGE-EXT-V2-046`
- `TRIAGE-EXT-V2-047`
- `TRIAGE-EXT-V2-048`
- `TRIAGE-EXT-V2-049`

### 这些 case 主要依赖 judge 补齐的能力

#### understanding

- `intent`
- `primaryComplaint`
- `answeredSlotsContains`
- `correctionsContains`

#### reducer

- `reducedSlotValues`
- `pendingSlotsNotContains`
- `correctionCountAtLeast`

#### planner

- `selectedGapsContains`
- `suppressedGapsContains`
- `mustNotSelectGaps`

#### riskDecision

- `decisionType`
- `shouldInterrupt`
- `needsMoreInfo`
- `confirmedRiskGapsContains`
- `unresolvedRiskGapsContains`

#### history

- `turnUnderstandingCountAtLeast`
- `riskDecisionHistoryCountAtLeast`
- `finalPrimarySymptomMustPersist`

---

## 本轮结果总结

本轮的关键成果不是“又补了几条普通 case”，而是：

1. extended 中开始出现真正由 v2 结构字段主导的 case
2. case 字段命名严格对齐现有 schema / normalizer / 文档
3. 第一批结构化链路样本已经覆盖：
   - correction
   - multi-turn primary complaint persistence
   - planner suppression
   - unresolved risk gap
   - history escalation
4. observed report 已经能展示这些 case 对应的结构输出
5. judge 未来只要补上结构化比对逻辑，这批 case 就可以直接转化为真正的 v2 门禁

---

## 后续建议

下一轮优先方向：

1. 继续补 `050+` 的 true v2 structured case
   - correction + planner suppression 的多槽位联动
   - suspected / unresolved / confirmed 的更细分层
   - riskDecision 与 history 的交叉升级路径

2. judge 升级时优先接这 5 类 expectation：
   - `understanding`
   - `reducer`
   - `planner`
   - `riskDecision`
   - `history`

3. 在 judge 未完成前，extended 继续优先积累这类结构化样本，不急着重写 regression。
