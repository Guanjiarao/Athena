# Step7 详细流程

> 配套：`docs/triage-正式重构路线图-v2.md`

## Step 7 的定义

Step 7 不是“删除 heuristic 代码比赛”，而是把 triage 从“新旧链路并存”推进到“新主链拥有唯一主决策权”。

目标是让：

1. `TurnUnderstandingWorker`
2. `StateReducer`
3. `SlotManager`（projection only）
4. `QuestionPlanner`
5. `RiskStratifierWorker`
6. `TriageStateMachine`

成为唯一主链；heuristic 只保留在以下边缘职责：

- hard safety floor
- model failure fallback
- compatibility helper
- debug helper

---

## Step 7 总体验收口径

### 架构验收

- planner 主路径不再依赖大段关键词逻辑
- risk 主路径不再依赖普通 heuristic 文本扫词
- answered slot / pending slot 主路径不再依赖 fact-based fallback 推断
- heuristic 职责完成分类：保留 / 下沉 / 删除
- 主路径调用顺序清晰稳定

### 行为验收

- 主回归集不回退
- 扩展回归集不回退
- 高危 case 不漏报
- `REASK_DURATION` 不明显回升
- `REASK_BODY_PART` 不明显回升
- `ROUTINE_FOLLOW_UP_FIRST` 在高危场景中不明显回升

### 过程验收

- 不再新增普通主路径 heuristic
- 每个 legacy helper 都有退出条件
- 团队能明确说清：谁是主链，谁是 fallback，谁是 compatibility

---

# Phase 7A：冻结旧 heuristic 增长

## 7A-1 建立 freeze 规则

### 目标

禁止继续在旧主路径里“顺手补一条规则修 case”。

### 范围

默认不再新增主路径能力的对象：

- `FactHeuristicExtractor`
- `ComplaintFallbackResolver`
- `SemanticSignalResolver`
- `SlotManager` 中 fact-based fallback
- `RiskHeuristicHelper` 的普通灰区判断

### 新问题优先修复位置

- `TurnUnderstandingWorker`
- `StateReducer`
- `QuestionPlanner`
- `RiskStratifierWorker`
- regression / acceptance baseline

### 完成标准

- 团队有书面 freeze 规则
- 新 PR 不再默认往旧 heuristic 补词表
- review 时把“新增 heuristic 规则”列为显式审查项

## 7A-2 给 heuristic 组件打标签

### 建议标签

- `KEEP_GUARDRAIL`
- `COMPATIBILITY_ONLY`
- `DEBUG_ONLY`
- `DEPRECATED_MAIN_PATH`

### 至少要标注的组件

- `RiskHeuristicHelper`
- `FactHeuristicExtractor`
- `ComplaintFallbackResolver`
- `SemanticSignalResolver`
- `SlotManager` fallback path

### 完成标准

- 所有 legacy heuristic 都有分类
- 团队能一眼看出哪些还能增强，哪些只能下沉

## 7A-3 立即执行的 freeze / review 规则

### 默认禁止事项

以下位置默认禁止继续新增“为了修单个 case 而补关键词/补映射/补扫词”的主路径能力：

- `FactHeuristicExtractor`
- `ComplaintFallbackResolver`
- `SemanticSignalResolver`
- `SlotManager.resolveAnsweredSlotsFromFacts(...)`
- `SlotManager.isFollowUpLikeSlot(...)`
- `RiskHeuristicHelper.heuristicRiskFallback(...)`
- `RiskHeuristicHelper.buildCombinedText(...)` 的普通主判断扩写

### 允许修改的前提

只有在以下四类场景中，才允许修改 legacy heuristic：

- `KEEP_GUARDRAIL`：硬安全护栏必须保留
- `COMPATIBILITY_ONLY`：兼容旧输出或旧评测种子
- `DEBUG_ONLY`：调试可观测性增强
- 模型失败 fallback：用于避免主链失效后的空结果

### review 明确检查项

每个 Step 7 相关 PR 都应显式回答：

- 这次改动是否新增了 legacy heuristic 规则？
- 如果新增了，它属于 guardrail / compatibility / debug / model failure fallback 中的哪一种？
- 为什么不能改 `TurnUnderstandingWorker` / `StateReducer` / `QuestionPlanner` / `RiskStratifierWorker`？
- 是否新增了对应 regression / acceptance 观测点？

### 当前第一版分类建议

- `RiskHeuristicHelper.hardRedFlagFallback(...)` -> `KEEP_GUARDRAIL`
- 风险 schema / normalize / conservative fallback -> `KEEP_GUARDRAIL`
- `FactHeuristicExtractor` -> `COMPATIBILITY_ONLY`
- `ComplaintFallbackResolver` -> `COMPATIBILITY_ONLY`
- `SemanticSignalResolver` -> `DEPRECATED_MAIN_PATH`
- `SlotManager.resolveAnsweredSlotsFromFacts(...)` -> `DEPRECATED_MAIN_PATH`
- `SlotManager.isFollowUpLikeSlot(...)` -> `DEPRECATED_MAIN_PATH`
- `RiskHeuristicHelper.heuristicRiskFallback(...)` 中已被新主链覆盖的灰区规则 -> `DEPRECATED_MAIN_PATH`

### baseline 观测口径

Step 7 后续每个子阶段至少观察：

- 主回归集是否回退
- 扩展回归集是否回退
- 高危 case 是否漏报
- `REASK_DURATION` 是否明显回升
- `REASK_BODY_PART` 是否明显回升
- `ROUTINE_FOLLOW_UP_FIRST` 在高危场景中是否明显回升

---

# Phase 7B：切断主路径对 legacy heuristic 的依赖

## 7B-1 answered slot 主路径完全来自 reducer

### 当前问题

`SlotManager` fallback 里还有：

- `resolveAnsweredSlotsFromFacts(...)`
- `isFollowUpLikeSlot(...)`

### 目标

answered slot 主来源只来自：

- `TurnUnderstanding`
- `StateReducerResult`

### 要做什么

- 确认 reducer 输出覆盖主回归场景
- 缩小 `SlotManager` fallback 使用范围
- 将 facts 推 answered slots 退为兜底路径

### 完成标准

- 正常主链不再靠 facts 推 answered slots
- no-reask 类 case 稳定
- `REASK_DURATION` / `REASK_BODY_PART` 不回升

## 7B-2 pending slot 主路径只来自 reducer + planner

### 目标

pending 的主来源固定为：

- reducer 状态
- planner policy selection

### 要做什么

- 排查 compatibility 分支是否把 resolved slot 带回 pending
- 收窄 `SlotManager` 对 pending 的二次修正
- 明确 pending 唯一写入边界

### 完成标准

- `DURATION` / `BODY_PART` resolved 后不再重新进入 pending
- correction 后的 slot 不再回 pending

## 7B-3 `FactHeuristicExtractor` 下沉

### 当前问题

它仍承担：

- negation
- symptom presence
- duration / body part / temperature
- fallback primary complaint

### 目标

它不再是主路径语义解释器，只保留为：

- compatibility facts producer
- debug helper
- fallback helper

### 要做什么

- 梳理 reducer 中对 facts 的依赖点
- 让 reducer 优先信任 `TurnUnderstanding`
- 限制 fact-only 输出对 planner / risk 主链的影响

### 完成标准

- planner 关键决策不再因 fact-only 逻辑变化而变化
- risk 关键动作不再依赖 fact-only 命中
- reducer 对 facts 的依赖弱于对 `TurnUnderstanding` 的依赖

## 7B-4 `ComplaintFallbackResolver` 收缩为极窄 fallback

### 目标

主诉理解默认由 `TurnUnderstandingWorker` 完成；resolver 只保留高置信、一跳等价 fallback。

### 要做什么

- 盘点每条映射规则
- 分成：高置信保留 / 过度 case-specific 删除 / 被新链路覆盖的下沉

### 完成标准

- 主诉识别主路径不再依赖该 resolver
- ambiguous 主诉不再被 fallback 过度强归类
- 规则数量明显下降

## 7B-5 削弱 `SemanticSignalResolver` 对 planner 主链的控制权

### 当前问题

`QuestionPlanSupport` 仍通过它拼装：

- `slotState`
- `factHistory`
- `extractedSymptoms`
- `riskSignalState`

### 目标

planner gap 生成更多基于：

- canonical complaint state
- reducer state
- structured risk signals

### 完成标准

- planner gap 生成不再依赖多源 heuristic 拼装
- semantic signal 更接近 canonical state lookup

---

# Phase 7C：收窄 risk heuristic 到 safety floor

## 7C-1 将 `RiskHeuristicHelper` 拆成两种职责

### 应保留

- `hardRedFlagFallback(...)`
- schema / normalize / conservative fallback

### 应弱化或下沉

- `heuristicRiskFallback(...)` 中的普通灰区判断
- `buildCombinedText(...)` 的主路径解释权

### 完成标准

- 团队能清楚区分：哪些是不可删 guardrail，哪些只是过渡 fallback
- 普通灰区判断不再优先盖过 `RiskDecision`

## 7C-2 降低 `heuristicRiskFallback(...)` 在正常路径中的存在感

### 当前覆盖

例如：

- 右下腹痛 + 未知呕吐
- chestPainOnly
- moderate symptom load

### 目标

普通灰区风险更多交给：

- `RiskDecision`
- `RiskGap`
- LLM-assisted risk assessor

### 完成标准

- `heuristicRiskFallback` 只剩少量必要规则
- 普通灰区风险不再主要靠 combined text heuristics

## 7C-3 限制 `buildCombinedText(...)` 的主路径影响

### 目标

combined text 不再是主决策材料，只作为 fallback input synthesis / debug。

### 完成标准

- risk 主决策不再依赖 combined text 才能成立
- `containsAny(combinedText, ...)` 数量明显减少

---

# Phase 7D：清理兼容层并形成最终调用图

## 7D-1 输出 heuristic 分类清单

每个组件至少列出：

- 名称
- 当前职责
- 分类（保留 / 下沉 / 删除）
- 当前主路径是否引用
- 退出条件
- 删除前依赖项

## 7D-2 输出精简版主路径调用图

目标调用图：

1. `TurnUnderstandingWorker`
2. `StateReducer`
3. `SlotManager`（projection only）
4. `QuestionPlanner`
5. `RiskStratifierWorker`
6. `TriageStateMachine`

边缘辅助：

- hard risk guardrail
- model failure fallback
- compatibility / debug helper

## 7D-3 删除已无主链依赖的 legacy helper

### 完成标准

- 删除后主回归不回退
- 扩展回归不回退
- 无新增安全退化

---

# 当前代码的第一版分类建议

## 应保留

### `RiskHeuristicHelper.hardRedFlagFallback(...)`

分类：`KEEP_GUARDRAIL`

原因：属于 deterministic safety floor。

### 风险层的 schema / normalize / conservative fallback

分类：`KEEP_GUARDRAIL`

原因：属于模型失败时的输出边界保护。

## 应下沉

### `FactHeuristicExtractor`

分类：`COMPATIBILITY_ONLY`

下沉后职责：

- compatibility facts
- debug trace
- test bootstrap helper
- fallback merge input

### `ComplaintFallbackResolver`

分类：`COMPATIBILITY_ONLY`

下沉后职责：

- 高置信短语的一跳 fallback
- LLM 缺失时兜底

### `SemanticSignalResolver`

分类：`DEPRECATED_MAIN_PATH`

下沉后职责：

- 兼容层信号桥接
- 调试过渡层

## 应删除目标

### `SlotManager.resolveAnsweredSlotsFromFacts(...)`

分类：`DEPRECATED_MAIN_PATH`

删除前条件：

- reducer answered slots 覆盖主回归
- no-reask 类 case 稳定

### `SlotManager.isFollowUpLikeSlot(...)`

分类：`DEPRECATED_MAIN_PATH`

删除前条件：

- facts fallback 不再承担 answered slot 主逻辑

### `RiskHeuristicHelper.heuristicRiskFallback(...)` 中已被新主链覆盖的灰区规则

分类：`DEPRECATED_MAIN_PATH`

删除前条件：

- 对应场景已有 `RiskDecision` 稳定覆盖
- 删除后高危不漏、低危不乱升

---

# Issue 模板

## 模板 1：Step 7 总 tracking issue

```md
## 背景

Step 1-6 已让 triage 新主链基本成立，但当前代码仍处于新旧链路并存的过渡态：旧 heuristic 仍在部分主路径中保留解释权。

Step 7 的目标不是简单删除 heuristic，而是让 heuristic 失去主决策权，退到 guardrail / fallback / compatibility / debug 位置。

## 目标

- planner 主路径不再依赖大段关键词逻辑
- risk 主路径不再依赖普通 heuristic 扫词
- answered slot / pending slot 主路径不再依赖 fact-based fallback 推断
- heuristic 职责清晰分类
- 主回归集和扩展回归集不回退

## 工作拆分

- [ ] 7A 冻结 heuristic 增长
- [ ] 7B 切断主路径对 legacy heuristic 的依赖
- [ ] 7C 收窄 risk heuristic 到 safety floor
- [ ] 7D 清理兼容层并输出最终调用图

## 验收标准

- [ ] 主回归集不回退
- [ ] 扩展回归集不回退
- [ ] 高危 case 不漏报
- [ ] `REASK_DURATION` 不明显回升
- [ ] `REASK_BODY_PART` 不明显回升
- [ ] `ROUTINE_FOLLOW_UP_FIRST` 在高危场景中不明显回升

## 输出物

- [ ] heuristic 分类清单
- [ ] 主路径精简调用图
- [ ] 回归结果
- [ ] 风险说明
```

## 模板 2：单个 Step 7 子 issue

```md
## 背景

这是 Step 7 的一个子任务，目标是切断某一段主路径对 legacy heuristic 的依赖，而不是直接删除所有相关代码。

## 当前问题

- 当前 legacy 逻辑：<填写>
- 当前主路径依赖点：<填写>
- 为什么阻碍 Step 7：<填写>

## 目标

将以下职责从主路径中移出：

- <职责 1>
- <职责 2>

保留以下边缘职责：

- <guardrail / fallback / compatibility / debug>

## 要改什么

- [ ] <改动点 1>
- [ ] <改动点 2>
- [ ] <改动点 3>

## 不该改什么

- [ ] 不提前删除 fallback
- [ ] 不破坏 compatibility 输出
- [ ] 不降低 hard safety floor

## 验收标准

- [ ] 主路径不再依赖该 legacy heuristic
- [ ] 相关 regression 不回退
- [ ] 相关 acceptance 不回退

## 风险 / 回退

- 风险：<填写>
- 回退策略：<填写>
```

---

# Checklist 模板

## Checklist A：启动前检查

```md
- [ ] Step 1-6 主链已基本成立
- [ ] 主回归集可运行
- [ ] 扩展回归集可运行
- [ ] 已冻结普通 heuristic 增长
- [ ] 已识别当前主路径 heuristic 列表
```

## Checklist B：单个 heuristic 组件改造检查

```md
- [ ] 已明确该组件当前职责
- [ ] 已明确目标分类（保留 / 下沉 / 删除）
- [ ] 已明确主路径依赖点
- [ ] 已明确退出条件
- [ ] 已明确 fallback 是否必须保留
- [ ] 已补充对应测试或回归观测点
- [ ] 改造后主路径已不再依赖该组件
- [ ] 回归未回退
```

## Checklist C：Step 7 阶段验收检查

```md
- [ ] planner 主路径不再依赖大段关键词逻辑
- [ ] risk 主路径不再依赖普通 heuristic 扫词
- [ ] answered slot 主路径不再依赖 fact-based fallback
- [ ] pending slot 主路径不再依赖 compatibility 反推
- [ ] hard safety floor 仍然保留
- [ ] 模型失败 fallback 仍然保留
- [ ] compatibility / debug helper 已退边
- [ ] 主回归集不回退
- [ ] 扩展回归集不回退
- [ ] 高危场景不漏报
```

## Checklist D：最终收尾检查

```md
- [ ] 已输出 heuristic 分类清单
- [ ] 已输出主路径精简调用图
- [ ] 已记录仍保留的 fallback 及原因
- [ ] 已删除无主链依赖的 legacy helper
- [ ] 没有新增明显安全退化
- [ ] 团队能明确说清主链与 fallback 边界
```

## 当前代码的阶段性收口状态（执行中）

### 已完成的主链收口

- `StateReducer` 中的 facts merge 已降级为受限 compatibility fallback：
  - 本轮 `TurnUnderstanding` 已覆盖的 slot，不再由 facts 覆盖主状态
  - facts 仅在结构化理解缺位时补 compatibility state
- `SlotManager` 已不再通过 `isFollowUpLikeSlot(...)` 自动把 follow-up-like slot 判为 answered
- `SlotManager` 当前 compatibility answered slots 仅来自：
  - 本轮 fact
  - 且 slot 出现在 `lastAskedSlots` 中

### 当前保留的 compatibility / guardrail

- `FactHeuristicExtractor`：保留为 compatibility facts producer / debug helper
- `ComplaintFallbackResolver`：保留为高置信、一跳 fallback
- `SemanticSignalResolver`：保留为 planner compatibility bridge，不再继续增强为普通主路径 heuristic
- `RiskHeuristicHelper.hardRedFlagFallback(...)`：保留为硬安全护栏
- `RiskHeuristicHelper.heuristicRiskFallback(...)`：已开始收窄，保留少量保守 fallback

### 当前主路径调用图

1. `TurnUnderstandingWorker`
2. `StateReducer`
3. `SlotManager`（projection only + 窄 compatibility fallback）
4. `QuestionPlanner`
5. `RiskStratifierWorker`
6. `TriageStateMachine`

### 边缘辅助调用图

- hard risk guardrail：`RiskHeuristicHelper.hardRedFlagFallback(...)`
- model failure / compatibility facts：`FactExtractor` + `FactHeuristicExtractor`
- compatibility complaint fallback：`ComplaintFallbackResolver`
- planner compatibility semantic bridge：`SemanticSignalResolver`
- debug / synthesis helper：`RiskHeuristicHelper.buildCombinedText(...)`

### 下一步删除目标（满足依赖后）

- `FactHeuristicExtractor` 中已被 `TurnUnderstanding` 覆盖的普通槽位规则
- `ComplaintFallbackResolver` 中过度 case-specific 的映射
- `SemanticSignalResolver` 中不再需要的多源桥接分支
- `RiskHeuristicHelper.heuristicRiskFallback(...)` 中被结构化 risk 主链替代的灰区规则

---

## 建议执行顺序

1. `SlotManager` answered / pending fallback 退边
2. `FactHeuristicExtractor` 从主状态推进中下沉
3. `ComplaintFallbackResolver` 收缩为极窄 fallback
4. `SemanticSignalResolver` 降级为兼容桥接
5. `RiskHeuristicHelper` 拆成 hard guardrail 与 legacy fallback
6. 清理已无主链依赖的方法与分支

不要反过来。先删 risk / fact helper，再想办法补主链，通常会导致回归反弹。
