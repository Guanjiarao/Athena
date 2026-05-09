# Triage Eval Schema v2 升级清单

> 适用范围：`triage-regression-cases.json`、`triage-regression-cases-extended.json`
>
> 目标：在不推翻现有 eval 体系的前提下，把评测从“旧 reply/fact proxy 驱动”升级到“understanding / reducer / planner / riskDecision 结构化优先”。

---

## 1. 先给结论

当前 regression 和 extended 两套评测链，共用一套底座：

- `TriageEvalCase`
- `TriageEvalRunner`
- `TriageEvalRealExecutor`
- `TriageEvalNormalizer`
- `TriageRegressionJudge`
- `TriageEvalReportWriter`
- `TriageRegressionJudgementReportWriter`

区别主要只是：

- case 文件不同
  - `triage-regression-cases.json`
  - `triage-regression-cases-extended.json`
- 输出文件不同
  - `triage-regression-*`
  - `triage-regression-extended-*`

因此 v2 升级不能只改 judge，必须同时改：

1. case schema
2. normalizer
3. judge
4. observed report
5. judged report

否则会出现：

- judge 想判新字段
- normalizer 根本没产出
- regression 升级了，extended 仍然跑旧逻辑

---

## 2. 当前问题总览

当前 eval 体系仍然可以做：

- baseline regression smoke check
- 最终行为层回归
- 大方向不翻车的守门

但已经不够做：

- Step 5/6/7 后的主链开发导航
- understanding / reducer / planner / riskDecision 分层定位
- 多轮历史升级与结构化风险决策验证

当前最明显的问题是：

- case 输入格式还基本可用
- 但 normalizer 没暴露新主链核心产物
- judge 仍然大量依赖 reply/fact/slot 的旧 proxy
- observed / extended report 对新主链解释力不足

---

# 3. Schema v2：新增字段清单

原则：

- 不推翻现有 `TriageEvalCase`
- 保留 `turns + context + expected + forbidden` 大框架
- 只在 `Expected` 中增量加结构化断言块

---

## 3.1 新增 `expected.understanding`

### 目标

直接断言 `TurnUnderstanding` 是否正确，而不是只靠最终 slot/fact 反推。

### 建议字段

```json
"expected": {
  "understanding": {
    "intent": "FOLLOW_UP_ANSWER",
    "primaryComplaint": "腹痛",
    "answeredSlotsContains": ["DURATION", "BODY_PART"],
    "riskSignalsContains": ["DYSPNEA", "BLEEDING"],
    "correctionsContains": ["PRIMARY_SYMPTOM"]
  }
}
```

### Java 结构建议

新增：

- `UnderstandingExpectation`
  - `intent`
  - `primaryComplaint`
  - `answeredSlotsContains`
  - `riskSignalsContains`
  - `correctionsContains`

### 适用场景

- correction 类 case
- mild affirmative / weak expression case
- high-risk 口语变体 case
- multi-turn follow-up case

---

## 3.2 新增 `expected.reducer`

### 目标

直接断言 `StateReducerResult` 是否正确推进，而不是只看最终 `slotState`。

### 建议字段

```json
"expected": {
  "reducer": {
    "reducedSlotValues": {
      "PRIMARY_SYMPTOM": "腹痛",
      "DURATION": "今天开始"
    },
    "answeredSlotsContains": ["DURATION"],
    "pendingSlotsNotContains": ["DURATION"],
    "riskSignalsContains": ["DYSPNEA"],
    "correctionCountAtLeast": 1
  }
}
```

### Java 结构建议

新增：

- `ReducerExpectation`
  - `reducedSlotValues`
  - `answeredSlotsContains`
  - `pendingSlotsNotContains`
  - `riskSignalsContains`
  - `correctionCountAtLeast`

### 适用场景

- no-reask
- multi-turn slot fill
- correction overwrite
- pending refresh
- risk accumulation

---

## 3.3 新增 `expected.planner`

### 目标

直接断言 gap 发现、gap 选择、gap 抑制和 askability，而不是只看最终 `questionPlan`。

### 建议字段

```json
"expected": {
  "planner": {
    "candidateGapsContains": ["DURATION", "BODY_PART"],
    "selectedGapsContains": ["DURATION"],
    "suppressedGapsContains": ["BODY_PART"],
    "askabilityDecisionsContains": ["DURATION:ASKABLE"],
    "mustNotSelectGaps": ["FEVER_PRESENCE"]
  }
}
```

### Java 结构建议

新增：

- `PlannerExpectation`
  - `candidateGapsContains`
  - `selectedGapsContains`
  - `suppressedGapsContains`
  - `askabilityDecisionsContains`
  - `mustNotSelectGaps`

### 适用场景

- gap discovery
- askability
- suppression policy
- high-risk vs routine follow-up priority

---

## 3.4 新增 `expected.riskDecision`

### 目标

直接断言 `RiskDecision`，而不是只断 `riskLevelAtLeast` 或 final reply 里的提示词。

### 建议字段

```json
"expected": {
  "riskDecision": {
    "decisionType": "ASK_RISK_CLARIFICATION",
    "shouldInterrupt": false,
    "needsMoreInfo": true,
    "confirmedRiskGapsContains": [],
    "suspectedRiskGapsContains": ["DYSPNEA"],
    "unresolvedRiskGapsContains": ["VOMITING_PRESENCE"]
  }
}
```

### Java 结构建议

新增：

- `RiskDecisionExpectation`
  - `decisionType`
  - `shouldInterrupt`
  - `needsMoreInfo`
  - `confirmedRiskGapsContains`
  - `suspectedRiskGapsContains`
  - `unresolvedRiskGapsContains`

### 适用场景

- RLQ unresolved
- chest pain only
- history escalation
- hard red flag
- risk clarification vs final warning

---

## 3.5 新增 `expected.history`

### 目标

让 extended case 能直接断言多轮历史是否成立。

### 建议字段

```json
"expected": {
  "history": {
    "turnUnderstandingCountAtLeast": 3,
    "riskDecisionHistoryCountAtLeast": 2,
    "finalPrimarySymptomMustPersist": "腹痛"
  }
}
```

### Java 结构建议

新增：

- `HistoryExpectation`
  - `turnUnderstandingCountAtLeast`
  - `riskDecisionHistoryCountAtLeast`
  - `finalPrimarySymptomMustPersist`

### 适用场景

- multi-turn complaint persistence
- risk history escalation
- cross-turn reducer accumulation

---

# 4. 旧字段降级策略

原则：不是立刻删，而是从“主断言”降级为“compatibility / fallback 观测项”。

---

## 4.1 `factModifiers`

### 当前状态

schema 里有，judge 基本未实际使用。

### v2 处理

- 标记为 `deprecated`
- 保留解析兼容
- 不再新增新 case 使用
- 后续逐步移除

---

## 4.2 `factPolarityHints`

### 当前问题

当前系统已经是 `understanding -> reducer` 主链，很多场景功能正确，但 compatibility fact 不一定还值得强制要求存在。

### v2 处理

- 改为 `compatibilityFactPolarityHints`
- 默认只作为 compatibility 观测项
- 默认不直接导致 fail
- 如确实需要严格判定，case 显式增加：

```json
"strictCompatibilityFacts": true
```

### 影响范围

- regression
- extended

两套都要同步改，不允许只有一边降级。

---

## 4.3 `riskHintsContains`

### 当前问题

现在 judge 实际是靠 `finalReply` 文本扫词判断高危信号提示，这已经落后于 `RiskDecision` 主链。

### v2 处理

- 风险主断言迁移到 `expected.riskDecision`
- `riskHintsContains` 仅保留为 reply 层文案观察项
- 不再作为风险主链正确性的核心判据

---

## 4.4 `forbidden` 中基于 reply 文案的 `REASK_*`

### 当前问题

例如：

- `REASK_DURATION`
- `REASK_BODY_PART`
- `REASK_FEVER_PRESENCE`

当前还是靠 final reply 扫词判断“是不是又问了一遍”，容易受文案措辞影响。

### v2 处理

主判据改为结构化优先：

1. `questionPlan.nextSlotsToAsk`
2. `planner.selectedGapsContains`
3. `pendingSlots`
4. 最后才回退到 reply 文本兜底

### 保留方式

- `forbidden.REASK_*` 不删
- 但仅保留为 fallback textual check

---

# 5. Normalizer v2：必须新增的输出

`TriageEvalNormalizer` 是 judge 和 observed report 的共同输入，如果它不扩，后续都做不了。

---

## 5.1 新增 understanding 输出

至少新增：

- `turnIntent`
- `primaryComplaint`
- `understandingAnsweredSlots`
- `understandingRiskSignals`
- `understandingCorrections`

---

## 5.2 新增 reducer 输出

至少新增：

- `reducedSlotValues`
- `reducerAnsweredSlots`
- `reducerPendingCandidates`
- `correctionLog`
- `accumulatedRiskSignals`

---

## 5.3 新增 planner 输出

至少新增：

- `candidateQuestionGaps`
- `selectedQuestionGaps`
- `suppressedQuestionGaps`
- `askabilityDecisions`

---

## 5.4 新增 risk 输出

至少新增：

- `riskDecisionType`
- `riskDecisionShouldInterrupt`
- `riskDecisionNeedsMoreInfo`
- `confirmedRiskGaps`
- `suspectedRiskGaps`
- `unresolvedRiskGaps`
- `riskDecisionHistoryCount`

---

## 5.5 新增 history / multi-turn 输出

至少新增：

- `turnUnderstandingHistoryCount`
- `stateReducerHistoryCount`
- `riskDecisionHistoryCount`
- `finalPrimaryComplaint`

---

# 6. Judge v2：升级方案

原则：结构化优先，reply 文本兜底。

---

## 6.1 新增四组结构化断言函数

在 `TriageRegressionJudge` 中新增：

- `understanding(...)`
- `reducer(...)`
- `planner(...)`
- `riskDecision(...)`

现有这些保留：

- `slots(...)`
- `containsAll(...)`
- `excludesAll(...)`
- `qplan(...)`
- `forbidden(...)`

但优先级下降。

---

## 6.2 Judge 新执行顺序

建议按以下顺序判：

1. `understanding`
2. `reducer`
3. `planner`
4. `riskDecision`
5. `slotValues / pending / nextAction / questionPlan`
6. `finalReply / 文案类 forbidden`

也就是从：

- reply / fact / slot 的旧 proxy judge

升级到：

- understanding / reducer / planner / riskDecision / final behavior 的分层 judge

---

## 6.3 Fail 输出升级

v2 judge 的 fail 信息不应只说：

- `expected slotValues contains XXX, actual null`

而应尽量按层输出：

- `understanding.intent mismatch`
- `reducer.pendingCandidates still contains DURATION`
- `planner.selectedGaps unexpectedly contains BODY_PART`
- `riskDecision.decisionType expected ASK_RISK_CLARIFICATION, actual MONITOR`

这样开发才能快速定位到底坏在：

- understanding
- reducer
- planner
- riskDecision

中的哪一层。

---

# 7. Observed report v2：不仅 judge，extended 也要一起升级

你特别提醒得对，不能只改 judge。

extended 很多时候先看 observed report，再看 judged report，所以 observed report 必须同步升级。

---

## 7.1 Observed report 新增版块

### A. Turn Understanding

输出：

- intent
- primary complaint
- answered slots
- risk signals
- corrections

### B. State Reducer

输出：

- reduced slots
- reducer answered slots
- pending candidates
- accumulated risk signals
- correction log

### C. Planner

输出：

- candidate gaps
- selected gaps
- suppressed gaps
- askability decisions

### D. Risk Decision

输出：

- decision type
- should interrupt
- needs more info
- confirmed / suspected / unresolved gaps

### E. Compatibility Footprint

输出：

- facts count
- heuristic fallback hit summary
- 是否走了 compatibility answered fallback

---

## 7.2 Extended report 额外新增 `History / Multi-turn Summary`

这一块尤其要加在 extended observed report 中，建议输出：

- conversation turns count
- latest turn intent
- final primary complaint
- riskDecisionHistory count
- stateReducerHistory count

原因：

- regression 更偏主行为回归
- extended 更偏多轮 / 变体 / 链路解释

如果没有 history summary，extended 的价值会被削弱很多。

---

# 8. Regression 与 Extended：同步升级要求

这次升级必须同时覆盖两条链。

---

## 8.1 共用层必须同步修改

以下组件必须一处改动、两边共用：

- `TriageEvalCase`
- `TriageEvalNormalizer`
- `TriageRegressionJudge`
- `TriageEvalReportWriter`
- `TriageRegressionJudgementReportWriter`

---

## 8.2 两个 case 文件都要支持 v2 字段

- `resources/eval/triage/triage-regression-cases.json`
- `resources/eval/triage/triage-regression-cases-extended.json`

即使短期只有 extended 先使用新字段，schema 和 loader 也必须统一支持。

---

## 8.3 两种输出都要升级

- regression observed / judged report
- extended observed / judged report

不能出现：

- judged report 会判新字段
- observed report 仍看不到新字段

这样的割裂状态。

---

# 9. 迁移顺序建议

---

## Phase 1：底层扩展，不破坏旧 case

### 改动

- 扩 `TriageEvalCase.Expected`
- 扩 `TriageEvalNormalizer`
- 扩 `TriageEvalReportWriter`
- 扩 `TriageRegressionJudge`
- 保持旧 case 仍可运行

### 目标

- regression / extended 旧 case 不坏
- 新 case 已可使用 v2 字段

---

## Phase 2：优先给 extended 增加 v2 case

优先补这些类型：

1. correction
2. multi-turn primary complaint persistence
3. planner suppression
4. unresolved / confirmed / history escalation
5. Step 7 compatibility boundary

原因：

- extended 更适合装结构化链路验证 case
- regression 继续保守做门禁

---

## Phase 3：旧字段降级

当 v2 judge 跑稳后：

- `factModifiers` -> deprecated
- `factPolarityHints` -> compatibility-only
- `riskHintsContains` -> reply-level observation only
- `forbidden.REASK_*` -> fallback textual check only

---

## Phase 4：分工明确

### regression

继续当：

- 主行为回归集
- 稳定 PASS/FAIL 门禁

### extended

升级成：

- 结构化链路验证集
- understanding / reducer / planner / riskDecision 的探索与定位场

---

# 10. 最终短版升级清单

## A. Schema v2 新增

- `expected.understanding`
- `expected.reducer`
- `expected.planner`
- `expected.riskDecision`
- `expected.history`

## B. 旧字段降级

- `factModifiers` -> deprecated
- `factPolarityHints` -> compatibility-only
- `riskHintsContains` -> reply-level observation only
- `forbidden.REASK_*` -> fallback textual check only

## C. Normalizer 必加输出

- `turnUnderstanding`
- `latestStateReducerResult`
- `candidate/selected/suppressedQuestionGaps`
- `askabilityDecisions`
- `riskDecision`
- `riskDecisionHistory summary`

## D. Judge 必改

- 新增 understanding / reducer / planner / riskDecision 判定函数
- 结构化优先，reply 文案兜底
- fail 信息按层输出

## E. Observed / Extended report 必改

- observed report 增加 understanding / reducer / planner / riskDecision 版块
- extended report 增加 multi-turn / history summary

## F. 升级范围要求

- regression 与 extended 共用底层必须同步升级
- 不允许只升级 judged，不升级 observed
- 不允许只升级 regression，不升级 extended

---

## 11. 一句话结论

eval schema v2 的核心不是把旧测试推翻重写，而是把评测从“最终文案 / fact / slot 的旧 proxy 体系”，升级到“understanding / reducer / planner / riskDecision 结构化优先”的分层观测体系；并且这次升级必须同时覆盖 regression 与 extended 两条评测链，不能只改 judge。