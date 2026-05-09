# Triage Eval Schema v2 开发任务清单

> 配套文档：`docs/triage-eval-schema-v2-升级清单.md`
>
> 目标：把 eval 体系从“reply/fact/slot 的旧 proxy 驱动”，升级到“understanding / reducer / planner / riskDecision 结构化优先”的分层评测体系。

---

## 1. 使用说明

这份文档不是设计说明，而是可直接分配给开发同学的任务清单。

每个任务都包含：

- 目标
- 影响范围
- 要改什么
- 不该改什么
- 依赖关系
- 验收标准

所有任务默认同时考虑：

- regression 链
- extended 链

不允许只升级 regression、不升级 extended。

---

# 2. 总体任务分组

建议拆成 6 个任务包：

1. Task A：扩展 eval schema v2
2. Task B：扩展 normalizer 输出
3. Task C：升级 judge 到结构化优先
4. Task D：升级 observed / judged report
5. Task E：补充 v2 case（优先 extended）
6. Task F：旧字段降级与清理

建议执行顺序：

- A -> B -> C -> D -> E -> F

其中：

- A / B 是底座
- C / D 建立评测能力
- E 才能真正把新能力用起来
- F 是收尾

---

# 3. Task A：扩展 eval schema v2

## A1. 扩展 `TriageEvalCase.Expected`

### 目标

为 understanding / reducer / planner / riskDecision / history 增加结构化断言字段。

### 影响范围

- `bootstrap/src/test/java/.../triage/eval/TriageEvalCase.java`
- regression case 文件
- extended case 文件

### 要改什么

在 `Expected` 中新增以下块：

- `understanding`
- `reducer`
- `planner`
- `riskDecision`
- `history`

建议新增内部类：

- `UnderstandingExpectation`
- `ReducerExpectation`
- `PlannerExpectation`
- `RiskDecisionExpectation`
- `HistoryExpectation`

### 不该改什么

- 不要移除现有 `slotValues / nextAction / questionPlan / forbidden`
- 不要先删除旧字段
- 不要让旧 case 解析失败

### 依赖

- 无前置依赖，可最先开始

### 验收标准

- 旧 regression / extended case 文件仍可正常加载
- 新字段可被 Jackson 正常解析
- 新增一个最小样例 case 能通过反序列化测试

---

## A2. 明确旧字段的 v2 状态标记

### 目标

给旧字段加上“保留 / 降级 / 废弃”的状态说明，避免后续混用。

### 影响范围

- `TriageEvalCase.java`
- 配套文档说明

### 要改什么

至少标识这些字段：

- `factModifiers` -> deprecated
- `factPolarityHints` -> compatibility-only
- `riskHintsContains` -> reply-level observation
- `forbidden.REASK_*` -> fallback textual check only

### 不该改什么

- 不要在 A2 阶段直接删除这些字段

### 依赖

- A1 完成后即可做

### 验收标准

- 代码中有清晰注释或命名说明
- 文档和实现口径一致

---

# 4. Task B：扩展 normalizer 输出

## B1. 输出 understanding 结构

### 目标

让 normalizer 能直接暴露 `TurnUnderstanding` 关键内容。

### 影响范围

- `TriageEvalNormalizer.java`

### 要改什么

新增输出字段：

- `turnIntent`
- `primaryComplaint`
- `understandingAnsweredSlots`
- `understandingRiskSignals`
- `understandingCorrections`

### 不该改什么

- 不要移除现有 `facts / slotValues / pendingSlots / finalReply`

### 依赖

- Task A 完成后可并行开始

### 验收标准

- normalizer 对有 `latestTurnUnderstanding` 的 case 能输出对应字段
- 对无 `latestTurnUnderstanding` 的 case 不抛异常

---

## B2. 输出 reducer 结构

### 目标

让 normalizer 能直接暴露 `StateReducerResult`。

### 要改什么

新增输出字段：

- `reducedSlotValues`
- `reducerAnsweredSlots`
- `reducerPendingCandidates`
- `correctionLog`
- `accumulatedRiskSignals`

### 验收标准

- reducer 字段在多轮 case 中可见
- 无 reducer 结果时返回空结构或 null，不报错

---

## B3. 输出 planner 结构

### 目标

让 normalizer 能暴露 question gaps 和 askability 信息。

### 要改什么

新增输出字段：

- `candidateQuestionGaps`
- `selectedQuestionGaps`
- `suppressedQuestionGaps`
- `askabilityDecisions`

### 验收标准

- planner case 中可看到 gap 选择过程
- 观察 report 时能分辨“没问是没发现 gap，还是被 suppress”

---

## B4. 输出 riskDecision 结构

### 目标

让 normalizer 能直接暴露风险决策，而不是只输出 riskLevel。

### 要改什么

新增输出字段：

- `riskDecisionType`
- `riskDecisionShouldInterrupt`
- `riskDecisionNeedsMoreInfo`
- `confirmedRiskGaps`
- `suspectedRiskGaps`
- `unresolvedRiskGaps`
- `riskDecisionHistoryCount`

### 验收标准

- RLQ unresolved、history escalation、高危中断类 case 可直接看到 risk decision

---

## B5. 输出 history / multi-turn summary

### 目标

为 extended 提供多轮摘要信息。

### 要改什么

新增输出字段：

- `turnUnderstandingHistoryCount`
- `stateReducerHistoryCount`
- `riskDecisionHistoryCount`
- `finalPrimaryComplaint`

### 验收标准

- 多轮 case 输出中可直接看到 history 计数与最终主诉

---

# 5. Task C：升级 judge 到结构化优先

## C1. 新增 understanding 判定函数

### 目标

支持 `expected.understanding`。

### 影响范围

- `TriageRegressionJudge.java`
- regression judged report
- extended judged report

### 要改什么

新增判定：

- `intent`
- `primaryComplaint`
- `answeredSlotsContains`
- `riskSignalsContains`
- `correctionsContains`

### 验收标准

- understanding 相关 case 可以直接 fail 在 understanding 层
- fail 信息明确指出 mismatch 字段

---

## C2. 新增 reducer 判定函数

### 目标

支持 `expected.reducer`。

### 要改什么

新增判定：

- `reducedSlotValues`
- `answeredSlotsContains`
- `pendingSlotsNotContains`
- `riskSignalsContains`
- `correctionCountAtLeast`

### 验收标准

- no-reask / correction / pending refresh 类 case 能直接在 reducer 层定位失败

---

## C3. 新增 planner 判定函数

### 目标

支持 `expected.planner`。

### 要改什么

新增判定：

- `candidateGapsContains`
- `selectedGapsContains`
- `suppressedGapsContains`
- `askabilityDecisionsContains`
- `mustNotSelectGaps`

### 验收标准

- gap discovery / suppression / askability case 有明确结构化判定

---

## C4. 新增 riskDecision 判定函数

### 目标

支持 `expected.riskDecision`。

### 要改什么

新增判定：

- `decisionType`
- `shouldInterrupt`
- `needsMoreInfo`
- `confirmedRiskGapsContains`
- `suspectedRiskGapsContains`
- `unresolvedRiskGapsContains`

### 验收标准

- 风险类 case 不再主要依赖 final reply 文本扫词
- fail 信息能明确说明风险决策错在何处

---

## C5. 调整 judge 执行顺序

### 目标

从“reply/fact/slot proxy 优先”切换到“结构化断言优先”。

### 新顺序

1. understanding
2. reducer
3. planner
4. riskDecision
5. slotValues / pending / nextAction / questionPlan
6. finalReply / 文本类 forbidden

### 验收标准

- judge 输出的失败项能分层定位
- 旧 case 仍可跑

---

## C6. 降级旧断言语义

### 目标

把旧字段从主断言降级成 compatibility / fallback 观测项。

### 要改什么

- `factPolarityHints` -> 默认 warning 或 compatibility-only
- `riskHintsContains` -> reply-level 观察项
- `REASK_*` -> 结构化优先，reply 文本兜底

### 验收标准

- “功能上对了，但 fact 没产出” 不再轻易导致主判 fail
- 文本改措辞不会大量误伤 judge

---

# 6. Task D：升级 observed / judged report

## D1. observed report 增加 understanding 版块

### 目标

让 observed report 直接展示理解层结果。

### 要改什么

在 observed report 中新增：

- intent
- primary complaint
- understanding answered slots
- risk signals
- corrections

### 验收标准

- 开发同学不看源码也能看出 understanding 有没有识别对

---

## D2. observed report 增加 reducer 版块

### 要改什么

新增：

- reduced slots
- reducer answered slots
- pending candidates
- correction log
- accumulated risk signals

### 验收标准

- Step 3/4 问题可直接从 observed report 观察

---

## D3. observed report 增加 planner 与 riskDecision 版块

### 要改什么

新增：

- candidate / selected / suppressed gaps
- askability decisions
- risk decision type
- confirmed / suspected / unresolved risk gaps

### 验收标准

- planner 与 risk 决策链能直接在 observed report 中解释

---

## D4. extended observed report 增加 history summary

### 目标

让 extended 在多轮 case 中真正有解释力。

### 要改什么

新增：

- turns count
- latest turn intent
- final primary complaint
- stateReducerHistory count
- riskDecisionHistory count

### 验收标准

- 多轮 case 一眼可看出历史链路是否形成

---

## D5. judged report 增加“分层失败摘要”

### 目标

让 judged report 不只是 PASS/FAIL，而是告诉开发失败发生在哪一层。

### 要改什么

增加失败分组：

- Understanding failures
- Reducer failures
- Planner failures
- RiskDecision failures
- Final behavior failures

### 验收标准

- judged report 对 fail case 的说明比当前更可行动

---

# 7. Task E：补充 v2 case（优先 extended）

## E1. 先为 extended 增加结构化链路 case

### 原因

extended 更适合作为：

- understanding / reducer / planner / riskDecision 的探索验证集

### 优先补充类型

1. correction
2. multi-turn primary complaint persistence
3. planner suppression
4. unresolved / confirmed / history escalation
5. Step 7 compatibility boundary

### 验收标准

- extended 至少新增一批直接使用 `understanding/reducer/planner/riskDecision/history` 字段的 case

---

## E2. regression 保持保守补充

### 原因

regression 主要还是做稳定门禁，不适合一下子过度复杂化。

### 要做什么

- 只补最关键的主链行为 case
- 先不把所有结构化探索型 case 都塞进 regression

### 验收标准

- regression 仍然易读、稳定、适合做门禁

---

# 8. Task F：旧字段降级与清理

## F1. 标记 deprecated / compatibility-only 字段

### 要做什么

- `factModifiers` -> deprecated
- `factPolarityHints` -> compatibility-only
- `riskHintsContains` -> reply-level observation
- `forbidden.REASK_*` -> fallback textual check only

### 验收标准

- 代码、文档、case 使用口径一致

---

## F2. 停止新 case 使用旧主断言

### 要做什么

约定：

- 新增 case 优先写 `understanding / reducer / planner / riskDecision`
- 不再优先堆 `factPolarityHints`、reply 文案 forbidden

### 验收标准

- 新 case 中 v2 字段占主流
- 旧字段只用于兼容观察或历史遗留 case

---

# 9. 建议拆分给开发同学的方式

建议分成 4 个可并行 issue：

## Issue 1：Schema + Normalizer

包含：

- A1
- A2
- B1
- B2
- B3
- B4
- B5

## Issue 2：Judge v2

包含：

- C1
- C2
- C3
- C4
- C5
- C6

## Issue 3：Observed / Judged Report v2

包含：

- D1
- D2
- D3
- D4
- D5

## Issue 4：Case 迁移与旧字段降级

包含：

- E1
- E2
- F1
- F2

---

# 10. 最终验收口径

只有同时满足以下条件，eval schema v2 才算真正落地：

## A. regression 与 extended 都能跑

- `triage-regression-cases.json` 可跑
- `triage-regression-cases-extended.json` 可跑
- observed / judged 两套输出都可生成

## B. 新主链关键产物可观测

- understanding 可观测
- reducer 可观测
- planner 可观测
- riskDecision 可观测
- multi-turn history 可观测

## C. judge 可以按层定位失败

- fail 不再主要表现为“slotValues=null”
- 能明确定位到 understanding / reducer / planner / riskDecision 层

## D. 旧字段完成降级

- 不再由 `factPolarityHints` 主导失败
- 不再由 reply 文案扫词主导结构化链路正确性判定

---

## 11. 一句话总结

这次开发任务的核心不是“多加几个字段”，而是把 eval 从“只能看最终结果是否像对”，升级成“能沿着 understanding -> reducer -> planner -> riskDecision 逐层解释为什么对/为什么错”；并且 regression 与 extended 两条链必须一起升级，不能只改 judge。