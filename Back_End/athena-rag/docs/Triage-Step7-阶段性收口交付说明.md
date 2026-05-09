# Triage Step 7 阶段性收口交付说明

> 这份文档用于给测试同学快速对照当前代码状态。
> 不替代 `step7详细流程.md`，而是补充“这几轮实际已经落地了什么、还保留什么、建议怎么验收”。

## 1. 本轮实际已落地的收口项

### 1.1 `StateReducer`

已将 facts merge 收紧为**受限 compatibility fallback**：

- 本轮 `TurnUnderstanding` 已覆盖的 slot，不再由 facts 覆盖主状态
- facts 仅在结构化理解缺位时补 compatibility state

这意味着：

- 主状态推进优先信任 `TurnUnderstanding`
- fact-based merge 不再和主链抢解释权

---

### 1.2 `SlotManager`

已收紧 answered-slot 的 compatibility fallback：

- 不再通过 follow-up-like slot 的旧规则自动把一批 slot 判为 answered
- 当前 compatibility answered slots 仅来自：
  - 本轮 fact
  - 且该 slot 出现在 `lastAskedSlots` 中

这意味着：

- answered slot 的主判断权进一步回到 reducer / understanding 主链
- compatibility answered 不再无条件扩散

---

### 1.3 `FactHeuristicExtractor`

已收紧为：**仅在 `TurnUnderstanding` 缺失覆盖时才补 heuristic facts**。

当前行为：

- 若 `TurnUnderstanding` 已覆盖某个 slot，则不再补对应 heuristic fact
- 若 `TurnUnderstanding` 已覆盖 primary complaint，则不再补 heuristic primary fact
- 只在主链缺位时保留 compatibility fact 产出

这意味着：

- heuristic facts 不再与 structured understanding 重复推状态
- facts 更接近兼容层 / debug 层，而不是主链推进器

---

### 1.4 `ComplaintFallbackResolver`

已收缩为**高置信、一跳 fallback**，并去掉过宽强归类。

当前保留：

- 明确腹痛表达 -> `腹痛`
- 明确胸痛表达 -> `胸痛`
- 明确发热表达 -> `发热`
- 弱症状词 + 腹部 body cue -> `腹痛`

已去掉：

- `不是胸口痛，是胃这边不舒服` 这类强重分类 fallback
- `胸闷` / `胸口闷` 直接当主诉 fallback

额外保护：

- `胸痛` fallback 已增加否定保护：
  - `不是胸痛`
  - `不是胸口痛`
  - `不是心口痛`
  不再误判为 `胸痛`

---

### 1.5 `SemanticSignalResolver`

已继续收缩为**更窄的 compatibility bridge**。

当前保留：

- `slotState.PRIMARY_SYMPTOM`
- `extractedSymptoms`
- `riskSignalState`
- `factHistory` 中仅保留：`PRIMARY_SYMPTOM` fact -> semantic signal

已去掉：

- `FEVER_PRESENCE=YES` -> semantic `发热`
- `BODY_PART=胸前区` -> semantic `胸痛`

这意味着：

- semantic discovery 更依赖 canonical state / structured signal
- 普通 fact bridge 不再直接驱动 planner 语义发现

---

### 1.6 `RiskHeuristicHelper`

已进一步收窄到：**硬护栏 + 极少量明确 fallback**。

当前保留：

1. `hardRedFlagFallback(...)`
   - 大出血
   - 呼吸困难
   - 抽搐
   - 意识障碍
   - 胸痛 + 呼吸困难
   - 妊娠相关出血

2. 右下腹痛 unresolved fallback
   - 已知右下腹痛
   - 但还缺少 `VOMITING_PRESENCE`
   - 会提示继续补问关键伴随症状

3. `chest pain only` fallback
   - 单独胸痛仍作为高优先级线下评估 fallback

已去掉：

- 普通灰区 `moderateRisk` fallback
- `moderate symptom load`
- `moderate slot load`

这意味着：

- heuristic risk 不再因为“症状较多 / slot 较多”就抬高主判断级别
- 普通灰区风险更多回到结构化 risk 主链和风险分层结果

---

## 2. 当前主路径与边缘辅助边界

### 2.1 当前主路径

1. `TurnUnderstandingWorker`
2. `StateReducer`
3. `SlotManager`
4. `QuestionPlanner`
5. `RiskStratifierWorker`
6. `TriageStateMachine`

### 2.2 当前保留的边缘辅助

- hard risk guardrail：`RiskHeuristicHelper.hardRedFlagFallback(...)`
- unresolved RLQ fallback：`RiskHeuristicHelper.heuristicRiskFallback(...)` 中的右下腹痛补问分支
- chest pain only fallback：`RiskHeuristicHelper.heuristicRiskFallback(...)` 中的胸痛分支
- compatibility facts：`FactExtractor` + `FactHeuristicExtractor`
- compatibility complaint fallback：`ComplaintFallbackResolver`
- planner compatibility bridge：`SemanticSignalResolver`
- debug / synthesis helper：`RiskHeuristicHelper.buildCombinedText(...)`

---

## 3. 建议测试同学重点验收的边界

### 3.1 answered / pending 主链边界

重点确认：

- 已回答的 `DURATION` / `BODY_PART` 不再重复追问
- correction 后不再追旧值
- 单轮多槽位回答后，不再保留无意义 pending
- answered slot 不再主要依赖 fact fallback 自动推进

建议关注：

- no-reask
- correction collapse
- multi-slot answer collapse

---

### 3.2 planner 语义发现边界

重点确认：

- `PRIMARY_SYMPTOM`、`structured symptoms`、`risk signals` 仍能驱动合理补问
- 普通 fact bridge 被收掉后，planner 不应出现明显能力回退

建议关注：

- 腹痛 follow-up gap 发现
- 胸痛风险补问优先级
- 发热 -> 体温补问
- 妊娠相关出血 -> 妊娠状态补问

---

### 3.3 complaint fallback 边界

重点确认：

- 高置信一跳 fallback 仍可用
- 过宽表达不再被强归类
- 否定表达不再误触发胸痛 fallback

建议关注：

- `肚子疼` / `胃疼` / `腹痛`
- `胸口痛`
- `今天一直发烧`
- `不是胸口痛，是胃这边不舒服`
- `胸闷`

---

### 3.4 risk fallback 边界

重点确认：

- 高危场景不漏报
- 右下腹痛 + 呕吐未知 仍能触发关键补问
- 单独胸痛仍可触发高优先级风险动作
- 普通灰区不再由 heuristic risk 主导升级

建议关注：

- 胸痛 + 呼吸困难
- 妊娠 + 出血
- 意识障碍口语变体
- 右下腹痛但未回答呕吐
- 普通腹痛 + 多个一般症状，不应仅靠 heuristic 自动升级

---

## 4. 本轮新增 / 重点回归测试

本轮新增或重点使用过的测试包括：

- `StateReducerCompatibilityFallbackTest`
- `SlotManagerCompatibilityFallbackTest`
- `FactHeuristicExtractorCompatibilityFallbackTest`
- `ComplaintFallbackResolverCompatibilityFallbackTest`
- `SemanticSignalResolverCompatibilityBridgeTest`
- `RiskHeuristicHelperCompatibilityFallbackTest`
- `QuestionPlanSupportSemanticDiscoveryTest`
- `TriagePlannerRiskAcceptanceTest`
- `RiskStratifierWorkerHistoryTest`

本轮组合回归结果：

- 26 tests
- 0 failures
- 0 errors

---

## 5. 当前仍保留、暂未彻底删除的 legacy / compatibility 组件

以下能力仍然保留，但定位已更偏边缘辅助：

- `FactHeuristicExtractor`
- `ComplaintFallbackResolver`
- `SemanticSignalResolver`
- `RiskHeuristicHelper.heuristicRiskFallback(...)`

这几个组件**不是已经完全删除**，而是已经：

- 收窄职责
- 限制主判断权
- 退到 compatibility / bridge / guardrail / unresolved fallback

---

## 6. 给测试同学的结论性口径

如果要一句话概括当前阶段：

> Step 7 这一轮不是“删光所有 legacy helper”，而是把 legacy heuristic / fallback 的主判断权明显往边缘退，主链更多回到 `TurnUnderstanding -> Reducer -> Planner -> RiskStratifier`。

更具体地说：

- facts 不再主导主状态推进
- complaint fallback 不再做过宽强归类
- semantic bridge 不再靠普通 fact 间接推语义
- risk heuristic 不再主导普通灰区升级
- 但 hard safety floor 和少量关键 unresolved fallback 仍然保留

---

## 7. 建议一起发送给测试同学的文件

建议把下面两份一起发：

1. `docs/step7详细流程.md`
2. `docs/Triage-Step7-阶段性收口交付说明.md`

推荐用途：

- `step7详细流程.md`：看背景、路线、分阶段设计
- `Triage-Step7-阶段性收口交付说明.md`：看当前真实代码状态、验收边界、测试重点
