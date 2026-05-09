# Triage Step 5 / Step 6 真实差距清单

> 目标：按“代码/设计是否真正落地”来评估进度，而不是按“测试是否暂时变绿”来评估。
>
> 原则：正确的代码应自然产出正确的评测结果；不能为了通过评测而不断堆 patch、补词或放宽 expectation。

---

## Step 5 当前判断

**状态：未完成**

更准确地说：

- 已经有 `QuestionNeed`
- 已经有 `priority`
- 已经有部分 `risk-driven need`
- 已经开始从结构化状态出发规划提问

但当前还**不是**一个完整的：

- `state`
- `question gaps`
- `risk gaps`
- `do-not-ask-again`
- `policy`

共同驱动的 planner。

---

## Step 5 真实差距

### 5.1 缺统一的 `QuestionGap` 模型

当前更多是：

- 有若干 `QuestionNeed`
- 有场景化优先级
- 有一些 helper 直接给出 next slots

但还没有把“为什么要问这个问题”统一建模成 gap。

建议补一个统一对象，至少包含：

- `slot`
- `gapType`
  - `MISSING`
  - `CONFLICTED`
  - `LOW_CONFIDENCE`
  - `RISK_REQUIRED`
  - `FOLLOW_UP_REQUIRED`
- `reason`
- `source`
  - state / risk / policy / pattern
- `priority`
- `askable`
- `blockedBy`
- `satisfiedByExistingState`

目标不是继续堆条件，而是把 planner 变成“消费 gap 的系统”。

### 5.2 缺 `risk gaps` 与 `routine gaps` 的统一排序机制

当前虽然已经有部分 risk-driven need，但还没有做到：

- routine gaps 与 risk gaps 作为同层对象统一排序
- planner 不再先走 routine，再局部插 risk

应明确区分：

#### routine gaps

例如：

- `DURATION`
- `BODY_PART`
- `TEMPERATURE`
- `PAIN_SEVERITY`

#### risk gaps

例如：

- 胸痛时是否合并呼吸困难
- 妊娠时是否合并出血
- 腹痛时是否伴随红旗
- 发热时是否达到高风险阈值

目标是：

1. 先从 state / pattern / risk decision 里派生 gap
2. 再统一排序
3. 再由 policy 决定本轮问哪些

### 5.3 缺严格的 `do-not-ask-again` 策略对象

当前已经有一些“不要重问”的行为，但更像分散规则，不是显式策略。

应有统一判定：某个 slot 为什么这轮不能再问？

可能原因包括：

- 已明确回答
- 已明确否定
- 当前轮刚纠正过
- 已在当前问题计划中
- 当前 policy 认为信息足够
- 当前风险路径不再需要该问题

建议至少沉淀出显式策略层，例如：

- `DoNotAskDecision`
- 或 `AskabilityPolicy`

输入：

- 当前 state
- gap
- answered history
- correction history
- current plan
- risk decision

输出：

- askable / not askable
- suppression reason

### 5.4 缺 planner 的 `policy` 层

这是 Step 5 的核心缺口之一。

当前更像：

- priority + helper + 场景规则

但还不是显式 policy。

policy 至少应回答：

- 一次最多问几个？
- 哪些场景必须单问？
- 哪些场景允许双问？
- 哪些场景应停止 routine follow-up？
- 弱输入时先问主诉还是先问病程？
- 高危待确认时是否抢占 routine gap？

建议把这些规则从零散 helper 中上提，变成独立策略层。

### 5.5 缺可解释的 selection / suppression 决策结构

当前虽然有部分 `priorityReason` 和最终回复文本，但还没有完整的结构化决策链。

planner 应能够解释：

- 为什么选这个 gap？
- 为什么压掉另一个 gap？
- 为什么本轮问 1 个 / 2 个？
- 为什么在高危场景停止普通补问？

建议沉淀结构字段，例如：

- `selectedGaps`
- `suppressedGaps`
- `selectionReason`
- `suppressionReason`
- `policyDecision`

否则后续 debug 会越来越依赖“读 if/else 猜策略”。

### 5.6 缺从 pattern 到 gap 的规范派生机制

当前仍有不少“腹痛场景问这些”“发热场景问那些”的场景性逻辑。

这没有错，但还不够深。

目标应是：

- 某个 state pattern / complaint pattern / risk pattern
- 触发一组 candidate gaps
- 再交由 policy 排序与裁剪

例如：

#### 腹痛 pattern -> candidate gaps

- `DURATION`
- `BODY_PART`
- `PAIN_SEVERITY`
- `NAUSEA_PRESENCE`
- `VOMITING_PRESENCE`
- `FEVER_PRESENCE`

#### 发热 pattern -> candidate gaps

- `DURATION`
- `TEMPERATURE`
- 高危症状补问

这样 planner 才是“从 pattern 派生 gap”，而不是 helper 直接拼 next slots。

---

## Step 6 当前判断

**状态：部分完成，但未达标**

更准确地说：

- 已经开始消费结构化状态
- 已经不再只是扫原始文本
- 已经具备部分结构化风险判断能力

但当前还没有完整的：

- `RiskDecision`
- 跨轮升级策略
- `confirmed / suspected / unresolved` 分层

所以它还不是一个真正完成的结构化风险决策系统。

---

## Step 6 真实差距

### 6.1 缺完整的 `RiskDecision` 对象

当前更多是：

- risk level
- risk score
- 若干 hints / final reply 文案

但还不是显式“决策对象”。

建议 `RiskDecision` 至少包含：

- `finalRiskLevel`
- `decisionType`
  - `NO_RISK_SIGNAL`
  - `MONITOR`
  - `ASK_RISK_CLARIFICATION`
  - `TRIGGER_WARNING`
  - `ESCALATE_FROM_HISTORY`
- `signals`
- `evidence`
- `resolvedRiskGaps`
- `unresolvedRiskGaps`
- `decisionReason`
- `confidence`

目标是让风险输出不只是一个 level，而是一份结构化决策结果。

### 6.2 缺 `confirmed / suspected / unresolved` 分层

这是 Step 6 的核心缺口。

当前很多判断仍偏向二元：

- 命中 -> 高危
- 未命中 -> 非高危

应明确区分：

#### confirmed

用户已明确表达：

- 喘不过气
- 大量出血
- 抽搐
- 意识不清

#### suspected

存在高风险暗示，但不足以直接下最终判断：

- 胸口闷得厉害
- 快喘不上来了
- 头晕得站不住
- 流了很多

#### unresolved

当前 state / pattern 已提示必须确认，但尚未问清：

- 胸痛是否合并呼吸困难
- 妊娠是否合并出血
- 发热是否高热
- 腹痛是否剧烈或伴随红旗

没有这三层，risk system 就容易过早警报或漏掉“应优先确认的风险问题”。

### 6.3 缺跨轮风险升级状态机

虽然当前已开始消费结构化 state，但还没有真正的跨轮风险升级机制。

应能回答：

- 某轮 `suspected`，下一轮 `confirmed`，如何升级？
- 某轮已高危，下一轮补 routine 信息，是否允许降级？
- 某轮仍存在 unresolved risk gap，下一轮 planner 是否必须优先确认？

建议至少显式记录：

- `riskBeforeTurn`
- `newSignalsThisTurn`
- `resolvedSignals`
- `escalationReason`
- `stickyHighRisk`
- `deescalationAllowed`

否则 risk 仍然偏向“本轮临时判断”，而不是“跨轮决策流”。

### 6.4 缺风险确认型澄清与普通澄清的分层

当前外部动作仍主要是：

- `ASK_CLARIFICATION`
- `TRIGGER_WARNING`
- `GENERATE_REPORT`

但内部至少应区分：

- routine clarification
- risk clarification
- immediate warning
- report-ready

否则系统内部无法明确知道：

- 这轮是在补普通信息
- 还是在确认高危问题

即使外部枚举暂不改，内部 decision path 也应分层。

### 6.5 缺 risk -> planner 的明确接口

Step 5 和 Step 6 目前都在演进，但接口还不够清晰。

理想接口应该是：

#### risk 模块输出

- confirmed signals
- suspected signals
- unresolved risk gaps
- 是否必须立即 warning
- 是否允许继续 routine follow-up

#### planner 模块消费

- 若必须 warning，则停止 routine questioning
- 若存在 unresolved risk gaps，则优先问这些
- 若风险闭合，再回到 routine gaps

否则就容易退化成：

- risk helper 与 planner helper 相互绕
- 若干 if/else 横向串联

### 6.6 缺风险证据的结构化追踪能力

当前 final reply 已有部分“依据”，但还不够结构化。

系统应可追踪：

- 哪个 signal 触发了 warning
- 来自哪一轮
- 是 confirmed 还是 suspected
- 哪些高危项已问清
- 哪些仍未问清

这会直接影响：

- debug 能力
- judged explainability
- 后续规则调整的可控性

---

## 不应再继续用 patch 解决的问题

以下问题不应继续依赖：

- 单个 case 补词
- 为了 judged 变绿而放宽过紧 expectation 之外的设计性妥协
- 在多个 helper 中继续散落 planner / risk 条件
- 把 complaint fallback 不断扩成隐式分类器

这些做法可能暂时让测试更绿，但会让 Step 5/6 的目标越来越难真正落地。

---

## 推荐推进顺序

### 必须先做

1. 为 Step 5 补统一 `QuestionGap` 模型
2. 为 Step 5 补显式 `AskabilityPolicy / DoNotAskDecision`
3. 为 Step 5 补显式 planner `policy`
4. 为 Step 6 补完整 `RiskDecision`
5. 为 Step 6 补 `confirmed / suspected / unresolved` 分层
6. 为 Step 6 补 risk -> planner 明确接口

### 可以后做

1. 更细的 selection / suppression explainability
2. 更完整的跨轮风险升级状态机细节
3. 更细化的 pattern -> candidate gaps 派生机制
4. 输出文案层的质量优化

### 当前不该优先做

1. 继续围绕单个 fail case 打补丁
2. 继续扩 `ComplaintFallbackResolver` 词表
3. 继续在 helper 内叠更多局部 if/else
4. 只为了 judged 变绿而先改大批 expectation

---

## 一句话总结

### Step 5 本质缺口

还没有真正把“问什么”变成统一的 **gap planning** 问题。

### Step 6 本质缺口

还没有真正把“风险判断”变成跨轮、分层、可解释的 **RiskDecision** 系统。

当前实现已经显著改善行为和评测表现，但从 Step 5/6 的目标定义看，仍属于中后段推进，而不是最终完成态。
