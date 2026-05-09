# Triage 正式重构路线图 v2

## 这份文档解决什么问题

这份文档不是讨论某一个 case 的修复细节，而是把 triage 从当前架构迁移到长期目标架构时，给出一份正式、可执行、可验收的路线图。

目标不是“想到什么修什么”，而是明确：

- 每一步要解决什么问题
- 每一步的输入输出是什么
- 每一步最容易踩哪些坑
- 每一步做到什么算完成

这份路线图基于我们已经达成共识的长期方向：

> 把 triage 从 `rule-first + LLM 点缀`，逐步迁移到 `LLM-first 的语义理解 + 确定性状态机 + 规则护栏`。

---

## 先讲结论

我建议正式路线图采用 7 步，而不是原先的 6 步。

原因很简单：

- `TurnUnderstanding` 不是直接等于 `SlotState`
- 回合级语义理解和跨轮状态归并之间，必须有单独的一层
- planner 和 risk 也不能直接各自去“解读 state”，中间需要显式的 gap / signal 抽象

因此，正式路线图 v2 为：

1. 新增 `TurnUnderstanding` 及相关语义对象
2. 实现 `TurnUnderstandingWorker` 并接入 parsing 主链路
3. 新增 `StateReducer`，负责把回合语义合并到会话状态
4. 重构 `SlotManager`，优先消费 reducer 结果并做状态投影
5. 重构 `QuestionPlanner`，改为 `state + question gaps + risk gaps` 驱动
6. 重构 `RiskStratifierWorker`，支持跨轮风险升级与结构化风险融合
7. 冻结、下沉并逐步删除非必要 heuristic

---

## 总体设计原则

### 原则 1：理解交给 LLM，约束交给规则

### 原则 2：不要让规则去猜自然语言语义

### 原则 3：不要把 LLM 的 rich semantics 压扁成过窄结构

### 原则 4：状态机负责跨轮归并，不负责重新解释自然语言

### 原则 5：planner 和 risk 必须吃结构化语义状态，而不是继续扫原始文本

### 原则 6：heuristic 是 fallback，不是长期主引擎

---

## Step 1：新增 `TurnUnderstanding` 及相关语义对象

## 目标

建立一套足够丰富的回合级语义表示，承接大模型真正理解出来的内容，避免继续把语义压扁成过窄的 `Fact` / `Symptom` 组合。

这一层的核心任务不是“把旧对象改大一点”，而是明确引入一个真正能表达回合语义的对象体系。

---

## 输入

- 当前用户输入
- 最近若干轮对话上下文
- 当前 `lastAskedSlots`
- 当前 `pendingSlots`
- 当前简化版状态快照

---

## 输出

至少建议包含以下对象：

- `TurnUnderstanding`
- `TurnIntent`
- `AnsweredSlotCandidate`
- `PrimaryComplaintUnderstanding`
- `RiskSignal`
- `Correction`
- `NegationAssertion`
- `EvidenceSpan`

建议 `TurnUnderstanding` 至少能表达：

- 这轮是回答 follow-up、开启新主诉、纠正前文还是弱输入
- 这轮回答了哪些槽位
- 每个槽位的 evidence 和 confidence
- 这轮是否包含 correction / contradiction
- 这轮是否暴露了高风险信号
- 这轮是否有 unresolved ambiguity

---

## 风险点

### 风险 1：对象命名很新，但语义仍然很旧

如果只是把 `Fact` 换个名字，或者只是把 `Symptom` 外面再包一层 DTO，后续收益会非常有限。

### 风险 2：对象太大，但边界不清晰

如果 `TurnUnderstanding` 把回合理解、会话状态、最终动作建议都混在一起，后面状态层和策略层会继续互相污染。

### 风险 3：没有 correction / contradiction 抽象

如果不显式支持“不是 A，是 B”“刚才说错了”“不是今天，是昨天半夜开始”这类结构，后面仍然会退回字符串规则。

---

## 验收标准

### 结构层验收

- `TurnUnderstanding` 能完整表示 answered slots、risk signals、corrections、turn intent
- 每个核心字段都支持 evidence 与 confidence
- 能表达 affirmative / negative / corrected / uncertain 等语义差异

### 用例层验收

至少用以下几类 case 做对象级验证：

- `今天早上开始的`
- `没发热，也没吐`
- `不是胸口痛，是胃这边不舒服`
- `感觉喘不上来`
- `怀孕了，下面一直流血`

只要对象层还放不下这些语义，就说明 Step 1 没完成。

---

## Step 2：实现 `TurnUnderstandingWorker` 并接入 parsing 主链路

## 目标

把 `TurnUnderstanding` 从设计对象变成 parsing 主产物之一。

这一步的关键不是“再加一个 worker”，而是要明确：

> 回合级语义理解开始进入主流程，而不是只作为旁路增强信息存在。

---

## 输入

- 当前轮用户输入
- 最近若干轮上下文
- `lastAskedSlots`
- `pendingSlots`
- 当前状态摘要

---

## 输出

- `TurnUnderstanding`
- 可选：调试信息、raw parse trace、normalization trace

---

## 风险点

### 风险 1：只是接入 parsing，但后续没人消费

如果只是把 `TurnUnderstanding` 挂到 context 上，而 planner / slot / risk 仍然完全走旧逻辑，那只是“对象进系统了”，不代表系统真用上了它。

### 风险 2：worker 仍然只产弱结构

如果 worker 最终只是在内部做了一轮 LLM，然后把结果重新映射成旧版 `Fact[]` 思维，那长期价值仍然有限。

### 风险 3：prompt 只关注抽槽，不关注回合意图

如果 prompt 还是只问“识别哪些 symptom / facts”，而没有显式要求它区分：

- 回答 follow-up
- 新主诉
- correction
- 风险信号

那 Step 2 实际效果会打折。

---

## 验收标准

### 工程接入验收

- parsing 链路稳定产出 `TurnUnderstanding`
- context 中可以追踪 `TurnUnderstanding`
- 调试日志可以查看 turn-level semantics

### 行为级验收

选取至少 10 条扩展失败 case，验证 `TurnUnderstandingWorker` 可以稳定识别：

- `DURATION` 回答
- 多槽位同轮回答
- dyspnea 口语变体
- bleeding 口语变体
- correction / negation

如果它只能在模板表达上工作，Step 2 仍然不算完成。

---

## Step 3：新增 `StateReducer`，负责把回合语义合并到会话状态

## 目标

在回合级语义理解和会话级状态之间，插入一个专门的归并层。

这是 v2 路线图里新增的一步，也是最关键的一步。

这一层专门负责：

- 把 `TurnUnderstanding` 合并到 session state
- 处理 correction / overwrite / contradiction
- 更新 answered slots
- 刷新 resolved / pending / corrected 状态
- 归并跨轮风险信号

---

## 输入

- old session state
- current `TurnUnderstanding`
- old answered slots / pending slots
- old risk signal memory

---

## 输出

- new session state
- updated answered slots
- updated pending candidates
- correction log
- updated risk signal accumulation

---

## 风险点

### 风险 1：把 merge 逻辑继续塞进 `SlotManager`

这是最危险的做法。

如果没有独立 `StateReducer`，开发很容易把：

- correction 处理
- answered slot 更新
- conflict resolution
- overwrite policy

全部堆进 `SlotManager`，最后 `SlotManager` 会变成一个难以维护的大杂烩。

### 风险 2：把 turn-level truth 当作 session-level truth 直接覆盖

并不是所有当前轮信息都应该无脑覆盖旧值。

例如：

- 用户是在纠正旧值，还是只是补充新值
- 当前表达是模糊怀疑，还是明确确认
- 当前是否存在冲突未解

这些都必须显式处理。

### 风险 3：answered slot 仍然靠 slot code 枚举硬判断

如果新状态层里 answered slot 还是主要靠“某些 slot code 在这一轮出现了，所以算 answered”，那么多轮状态推进问题仍然不会真正解决。

---

## 验收标准

### 状态归并验收

至少要通过以下几类状态转换测试：

1. 单槽位 follow-up 回答
2. 多槽位同轮回答
3. correction 覆盖旧值
4. negation 更新旧状态
5. 第二轮新增 risk signal 后状态升级

### 架构验收

- `StateReducer` 独立存在
- merge 策略集中在 reducer 中，而不是散落在 planner / slot / risk 中
- reducer 输出的状态能被后续层直接消费

---

## Step 4：重构 `SlotManager`，优先消费 reducer 结果并做状态投影

## 目标

让 `SlotManager` 从“兼顾语义猜测 + 状态维护”的角色，逐步退化为“状态投影器”。

它的核心职责应该变成：

- 接收 reducer 归并后的状态
- 产出当前兼容体系所需的 `SlotState`
- 为旧链路和过渡阶段提供兼容视图

也就是说：

> `SlotManager` 不再是主要的语义解释器，而是状态 materialization 层。

---

## 输入

- reducer 输出的新 session state
- correction log
- answered slot summary
- risk signal summary（只读）

---

## 输出

- `SlotState`
- compatibility symptoms
- compatibility facts（如仍需兼容旧链路）
- current answered slots view

---

## 风险点

### 风险 1：`SlotManager` 继续偷做语义推断

如果这一层仍然继续用关键词或 slot code 枚举偷偷做语义判断，整个架构分层会再次失效。

### 风险 2：兼容层反过来绑架新架构

过渡期为了兼容旧接口，可以保留兼容视图；但不能因为兼容视图存在，就让 reducer 和 `TurnUnderstanding` 退化成配角。

### 风险 3：状态投影与真实状态不一致

如果 slot projection 没有正确反映 correction / contradiction / uncertainty，新架构的价值会在这里被削弱。

---

## 验收标准

### 架构验收

- `SlotManager` 不再直接依赖大段 heuristic 去猜测语义
- 回合级语义 merge 不在 `SlotManager` 内完成
- `SlotManager` 明确以 reducer 结果为主输入

### 行为验收

- 已回答的 `DURATION` / `BODY_PART` 不再因为 projection 问题重新回到 pending
- correction 后的主诉在 slot projection 中能正确体现
- compatibility 输出与 reducer 状态保持一致

---

## Step 5：重构 `QuestionPlanner`，改为 `state + question gaps + risk gaps` 驱动

## 目标

让 planner 从静态 required slots 推进器，升级为真正的状态驱动规划器。

它需要基于：

- 当前已确认信息
- 当前未解决的 question gaps
- 当前未解决的 risk gaps
- 已答内容与禁止重问内容
- 当前 risk policy 约束

来决定：

- 下一步最值得问什么
- 该问 1 个还是 2 个
- 当前是 routine follow-up 还是必须先确认风险相关问题

---

## 输入

- reducer 输出的新 session state
- current `SlotState`（投影视图）
- `questionGaps`
- `riskGaps`
- do-not-ask-again set
- current risk decision / risk policy constraints

---

## 输出

- `QuestionPlan`
- next 1-2 questions
- question priority rationale
- skipped questions with reasons（可选，用于 debug）

---

## 风险点

### 风险 1：planner 只是换了一层对象名，底层仍按旧 required slots 工作

如果 planner 仍然只是：

- `PRIMARY_SYMPTOM=腹痛 -> BODY_PART / FEVER / NAUSEA / VOMITING`

那只是在旧逻辑外包了一层新名字，不算真正重构。

### 风险 2：risk gap 和 question gap 混在一起

不是所有未填槽位都应该平等对待。

例如：

- `DURATION` 可能是普通 question gap
- `DYSPNEA_PRESENCE` 在胸痛场景下可能是高优先级 risk gap

如果不区分这两类，planner 会继续出现“低优先级槽位盖过高风险确认”的问题。

### 风险 3：没有显式的 do-not-ask-again 机制

如果 planner 仍然不显式消费“已答过、已否定、已纠正”的信息，重复提问问题会延续。

---

## 验收标准

### 行为验收

至少要通过以下几类规划测试：

1. `DURATION` 已答后不再重问
2. 一轮回答两个 follow-up 后，planner 能正确收口
3. 高风险确认问题优先于 routine follow-up
4. correction 后 planner 不再追旧值相关问题
5. 弱输入场景只问最有信息增益的 1-2 个问题

### 指标验收

- `REASK_DURATION` 命中率明显下降
- `REASK_BODY_PART` 命中率明显下降
- `ROUTINE_FOLLOW_UP_FIRST` 在红旗场景中的命中率明显下降

---

## Step 6：重构 `RiskStratifierWorker`，支持跨轮风险升级与结构化风险融合

## 目标

让风险层从“扫原始文本/组合文本的关键词”升级为“消费结构化风险信号和跨轮状态”。

风险层长期应支持：

- 单轮高危直接中断
- 跨轮风险信号追加后升级
- confirmed / suspected / unresolved risk 的区分
- 对灰区场景进行结构化综合判断

---

## 输入

- reducer 输出的新 session state
- normalized risk signals
- correction-aware state
- unresolved risk gaps
- optional LLM risk assessment input payload

---

## 输出

- `RiskDecision`
- risk level
- should interrupt / should continue asking
- risk rationale
- risk hints / risk signal trace

建议把风险层内部拆成两级：

### Level A：hard deterministic policy

负责：

- 极少数绝不能漏的 hard high-risk
- safety floor

### Level B：LLM-assisted risk assessor

负责：

- 灰区综合判断
- 跨轮风险升级
- 结构化证据融合

---

## 风险点

### 风险 1：风险层继续直接扫原始文本

如果 risk 仍然主要依赖 `containsAny(combinedText, ...)`，那新的 Meaning Layer 和 State Layer 价值会大打折扣。

### 风险 2：confirmed risk 和 unresolved risk gap 没区分

如果风险层仍然只会“要么 warning，要么继续问”，而不能清晰区分：

- 已确认高危
- 疑似高危，需补问确认
- 普通未补信息

那 planner 和 risk 仍然会互相打架。

### 风险 3：跨轮风险升级没有显式状态支持

如果第二轮新增 dyspnea / bleeding / altered consciousness 后，风险层仍然只是把每一轮当成独立判断，那么跨轮升级能力不会真正成立。

---

## 验收标准

### 行为验收

至少要通过以下几类测试：

1. dyspnea 口语变体能触发高危
2. pregnancy + bleeding 口语组合能触发高危
3. consciousness 口语变体能触发高危
4. 第二轮新增 risk signal 后能升级 warning
5. 普通 question gap 不会被错误当作 hard risk

### 指标验收

- 高危扩展 case 的通过率显著提升
- `ROUTINE_FOLLOW_UP_FIRST` 在高危场景中的命中率明显下降
- 跨轮风险升级 case 不再停留在 `ASK_CLARIFICATION`

---

## Step 7：冻结、下沉并逐步删除非必要 heuristic

## 目标

在新链路稳定后，系统性地收缩旧 heuristic 的职责边界，避免继续在旧逻辑上叠加新补丁。

这里的关键词不是立即删除，而是：

- 先冻结
- 再观察
- 再下沉 / 保留 / 删除

---

## 输入

- 当前仍在主路径使用的 heuristic 清单
- 新链路的覆盖能力与回归表现
- 每条 heuristic 的职责说明

---

## 输出

- heuristic 分类清单：保留 / 下沉 / 删除
- migration 结果
- 最终主路径精简版调用图

建议分类：

### A. 应删除

- 用于复杂语义理解的规则
- correction / negation / intent 猜测规则
- 被 `TurnUnderstandingWorker` 明显替代的关键词路由

### B. 应保留

- 极少数 hard safety floor
- schema repair
- model failure fallback

### C. 应下沉

- legacy compatibility support
- debug explanation helpers
- test bootstrap helpers

---

## 风险点

### 风险 1：过早删除 heuristic，导致主回归集回退

如果新链路还没有站稳，就过早删除 heuristic，很容易让系统先失稳。

### 风险 2：heuristic 表面冻结，实际继续偷偷增长

如果没有明确 freeze 策略，团队会很自然地在旧逻辑上继续加词表和临时规则，最后新架构会被慢慢侵蚀。

### 风险 3：该保留的 deterministic guardrail 被误删

删除 heuristic 不等于删除所有规则。

必须保留：

- 极少数 hard safety floor
- 输出边界规则
- schema consistency checks

---

## 验收标准

### 架构验收

- 主路径上的 heuristic 数量显著下降
- planner / risk / slot merge 主路径不再依赖大段关键词逻辑
- 新架构的调用顺序清晰稳定

### 回归验收

- 主回归集不回退
- 扩展集通过率继续提升
- 删除 heuristic 后没有新增明显安全回退

---

## 建议的阶段性里程碑

### Milestone A：语义层站住

完成 Step 1-2 后，目标是：

- richer semantics 稳定进入主流程
- 至少在对象层面能正确承接扩展失败 case 的关键语义

### Milestone B：状态推进站住

完成 Step 3-4 后，目标是：

- 多轮 answered slot 更新稳定
- correction / overwrite / pending 刷新稳定
- `DURATION` / `BODY_PART` 重问显著下降

### Milestone C：策略层站住

完成 Step 5-6 后，目标是：

- planner 真正变成 state-driven
- risk 真正支持跨轮升级
- 高危变体通过率明显提升

### Milestone D：旧逻辑退场

完成 Step 7 后，目标是：

- heuristic 退到 fallback / guardrail 位置
- 系统正式完成从 `rule-first` 到 `LLM-first hybrid` 的迁移

---

## 一句话总结

正式重构路线图 v2 的核心思想是：

> **先让大模型产出足够丰富的回合级语义，再用独立的状态归并层把语义沉淀成跨轮状态，然后让 planner 和 risk 真正消费这些结构化状态，最后再把旧 heuristic 收缩到护栏和兜底的位置。**

只有按这个顺序推进，triage 才能真正从“规则系统外面包了一层大模型”演进成“真正由大模型理解驱动、由规则保障安全边界”的混合架构。
