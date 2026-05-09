# Triage 长期重构方案：从规则优先到 LLM-first 混合架构

## 这份文档解决什么问题

这份文档不讨论某一个 case 的小修小补，而是讨论 triage 在长期演进上应该怎么改，才能真正把大模型能力用起来。

当前系统已经证明两件事：

- 主回归集可以稳定通过，说明主体能力不是假的
- 扩展回归集明显掉分，说明当前架构对自然语言变体、多轮状态推进和口语化红旗识别仍然不够稳

因此，当前问题已经不再只是“补几个词表”或者“加几个 pattern”，而是一个架构层面的问题：

> 当前 triage 仍然是 `rule-first + LLM 点缀`，而不是 `LLM-first + deterministic guardrails`。

这份文档的目标是给出一个清晰、可分阶段落地的长期重构方向。

---

## 先讲结论

长期来看，我建议把 triage 改造成三层结构：

1. **Meaning Layer**：大模型负责回合级语义理解
2. **State Layer**：确定性状态机负责跨轮记忆、冲突合并与状态推进
3. **Policy Layer**：规则只负责医疗安全边界、输出约束和高精度兜底

一句话总结：

> 让大模型负责“听懂用户到底在说什么”，让状态机负责“把多轮信息积起来”，让规则负责“哪些事绝不能做错”。

---

## 当前架构的核心问题

### 1. 大模型接进来了，但关键行为仍由死规则决定

当前代码里，虽然已经有：

- `SemanticParserWorker`
- `FactExtractor`
- `RiskStratifierWorker`

都在调用 LLM，但系统最终的关键行为仍然大量依赖：

- `Pattern`
- `containsAny(...)`
- 固定词表
- 固定必填槽位表

也就是说：

- LLM 在前面做了一部分抽取
- 但后面真正决定“问什么”“要不要 warning”“哪些槽已经答了”的，仍然主要是 rule table

这就是为什么系统表现出来像：

> 有一点大模型能力，但核心仍然是规则系统。

---

### 2. 结构化对象太薄，承载不了大模型真正理解出的语义

当前主要依赖的结构化对象大致是：

- `Symptom`
- `Fact`
- `SlotState`

其中 `Fact` 最关键的问题是：

- 主要只有 `slot`
- `canonicalValue`
- `polarity`
- `evidence`

这太薄了。

很多对 triage 极其重要的语义，在这里都放不下或者没有被持续利用：

- 用户是在回答上一轮问题，还是开启新主诉
- 用户是在补充，还是在纠正前文
- “不是 A，是 B” 的修正关系
- 某个风险信号是明确阳性、弱阳性、模糊怀疑，还是只是一种比喻表达
- 哪个表达只是口语化等价，哪个表达是真的医学红旗
- 用户刚才这一轮回答的信息增益，主要落在哪个维度

这会导致：

- LLM 可能已经理解了
- 但系统后面没地方承接这份理解

---

### 3. planner 和 risk 仍然像“表单驱动”，不像“语义驱动”

当前 planner 更像是：

- 主诉 = 腹痛 -> 问部位、发热、恶心、呕吐
- 主诉 = 胸痛 -> 问部位、呼吸困难
- 主诉 = 发热 -> 问体温

这种方式在早期没问题，但长期一定不够。

因为真实多轮对话里，用户会：

- 直接一次回答两个问题
- 只补充最想说的信息
- 先纠正再补充
- 口语化回答 follow-up
- 在第二轮才暴露高风险信号

这时如果 planner 仍然只按固定 required slots 推，就会出现：

- 已经回答了还重问
- 该升级风险时还在 routine follow-up
- 该先确认的风险信号被低优先级槽位挡住

---

## 长期目标架构

# 一、Meaning Layer：大模型负责“听懂”

## 这一层的职责

这一层的任务只有一个：

> 把用户这一轮输入，转成尽可能完整、可解释、可追踪的语义理解结果。

它不负责最终动作，不负责是否 warning，也不负责最终输出文案。

它只负责回答：

- 用户这轮是在说什么
- 回答了什么
- 否定了什么
- 修正了什么
- 暴露了什么风险信号
- 这些判断分别依据哪段 evidence
- 每个判断的置信度是多少

---

## 建议新增统一对象：`TurnUnderstanding`

建议引入一个新的统一对象，而不是继续只用 `Symptom[]` / `Fact[]` 作为主语义载体。

建议结构示意：

```json
{
  "turnUnderstanding": {
    "intent": "answer_follow_up | new_complaint | correction | weak_input",
    "answeredSlots": [
      {
        "slot": "DURATION",
        "value": "今天早上开始",
        "normalizedValue": "今天早上开始",
        "status": "FILLED",
        "polarity": "NEUTRAL",
        "confidence": 0.93,
        "evidence": "今天早上开始的"
      }
    ],
    "primaryComplaint": {
      "value": "腹痛",
      "confidence": 0.86,
      "evidence": "胃这边不舒服"
    },
    "riskSignals": [
      {
        "type": "DYSPNEA",
        "assertion": "PRESENT",
        "confidence": 0.95,
        "evidence": "感觉喘不上来"
      }
    ],
    "corrections": [
      {
        "reject": "胸痛",
        "confirm": "腹痛"
      }
    ]
  }
}
```

---

## 为什么这一步最重要

因为当前最根本的问题不是“词表太少”，而是：

> 大模型即使理解到了更丰富的语义，也没有一个足够丰富的结构来承接这些语义。

如果没有 `TurnUnderstanding` 这种对象，后面就只能继续：

- 拿关键词猜
- 拿 slot 猜
- 拿 pendingSlots 猜
- 拿 latestTurn 的字符串去扫

这就是当前系统迟迟摆脱不了 rule-first 味道的根源。

---

# 二、State Layer：确定性状态机负责“记住并推进”

## 这一层的职责

这一层不去重新理解自然语言。

它的职责是：

- 把 `TurnUnderstanding` 合并进会话状态
- 处理新信息和旧信息的冲突
- 记录哪个槽位是：已确认 / 未知 / 被否定 / 被修正 / 被覆盖
- 产出跨轮可持续使用的稳定状态

这一层应该非常 deterministic。

---

## State Layer 需要重点解决的几类问题

### 1. 回答 vs 新主诉

例如：

- 上一轮问的是 `DURATION`
- 用户这轮说：`今天早上开始的`

系统应该明确知道：

- 这是对 follow-up 的直接回答
- 不是新的 primary complaint
- 也不是泛泛补充信息

当前系统这件事做得不够稳，所以才会出现：

- duration 明明答了，pending 还挂着
- planner 继续重问 duration

---

### 2. 纠正 / 改口 / 反转

例如：

- `不是胸口痛，是胃这边不舒服`
- `不是今天，是昨天半夜开始`
- `刚才说错了，其实是右下腹`

这类表达不应该只靠 negation pattern 去扫。

状态层应该明确支持：

- reject old value
- confirm new value
- 标记这是 correction，不是独立并列事实

---

### 3. 多槽位同轮回答

例如：

- `38度多，今天下午开始`
- `没发热，也没吐`
- `右下腹，昨天晚上开始`

状态层应该允许：

- 一轮同时回答多个待问槽位
- 一次合并多个 answered slots
- 正确刷新 pending

而不是“吃进去一个，漏掉另一个”。

---

## State Layer 的关键原则

### 原则 1：状态机不重新做语义理解

它只消费结构化的 `TurnUnderstanding`。

### 原则 2：状态机必须显式支持冲突与修正

不要默认后来的值和先前值一定能直接并存。

### 原则 3：状态机要输出比今天更强的状态标签

建议槽位状态不再只有粗糙的 `FILLED / UNKNOWN`，而应支持更细粒度语义，例如：

- `FILLED`
- `NEGATED`
- `CORRECTED`
- `INFERRED`
- `CONFLICTING`
- `UNKNOWN`

这会极大增强 planner 和 risk 的可解释性。

---

# 三、Policy Layer：规则负责“安全与约束”

## 这一层不该做什么

它不该负责：

- 猜用户是不是 dyspnea
- 猜“喘不上来”是不是红旗
- 猜“今天早上开始”是不是 duration
- 猜“不是胸口痛，是胃这边不舒服”到底在纠正什么

这些都应该交给 LLM + Meaning Layer。

---

## 这一层应该做什么

规则层应该只负责：

### 1. 医疗安全硬边界

比如：

- pregnancy + bleeding => 一定高危
- altered consciousness => 一定高危
- chest pain + dyspnea => 一定高危
- seizure => 一定高危

注意：

- 规则负责的是“已确认信号如何处理”
- 不是“从原始文本里识别信号”

---

### 2. 输出约束

例如：

- 不允许做诊断结论
- 不允许给出具体处方
- 高危场景必须输出线下就医建议
- 问句数量上限
- 禁止在某些场景下继续 routine follow-up

---

### 3. schema 与一致性校验

例如：

- slot 值是否合法
- 同一轮 correction 是否自洽
- risk signals 与 final action 是否矛盾
- 问题规划是否重复已答槽位

---

## 这层的设计原则

### 原则 1：规则负责“不能错”

### 原则 2：规则不要负责“尽量懂”

### 原则 3：规则是护栏，不是主引擎

---

## 目标工作流

长期目标下，建议 triage 主链路演进成下面这样：

### Step 1：Turn Interpreter

输入：

- latest user turn
- recent conversation context
- last asked slots
- pending slots
- current state snapshot

输出：

- `TurnUnderstanding`

---

### Step 2：State Reducer

输入：

- old state
- `TurnUnderstanding`

输出：

- new state
- answered slots
- corrections applied
- unresolved uncertainties

---

### Step 3：Risk Policy

输入：

- new state
- normalized risk signals

输出：

- `riskDecision`
- whether interrupt
- why

这里分两级：

#### Level A：hard deterministic policy

处理绝不能漏的高危

#### Level B：LLM risk assessor

处理灰区风险组合与跨轮升级

---

### Step 4：Question Planner

输入：

- new state
- unresolved slots
- uncertainty map
- risk decision

输出：

- next 1-2 questions
- why these questions provide the most information gain

注意：

- planner 不再只是 required slots 的静态排序器
- 而是一个受状态约束的、带信息增益意识的策略规划器

---

### Step 5：Response Composer

输入：

- action
- risk decision
- question plan

输出：

- 最终回复

这层只负责“怎么说”，不负责“该做什么”。

---

# 为什么这套架构更能发挥大模型能力

## 1. 大模型终于被用在它最擅长的地方

大模型最擅长的是：

- 理解口语变体
- 做纠正与反转理解
- 识别隐式回答
- 识别多槽位同轮回答
- 识别非模板化红旗表达

这些恰恰是当前系统最弱的地方。

如果还让 regex / containsAny 继续当主引擎，那就是在用最不擅长的工具处理最依赖语义理解的问题。

---

## 2. 规则终于被用在它最擅长的地方

规则最擅长的是：

- 不能错的 hard policy
- 格式与 schema 约束
- 输出边界
- fallback
- 高精度兜底

这时规则系统的价值会变得非常高，而且不会和 LLM 争抢职责。

---

## 3. 多轮行为会从“表单驱动”变成“状态驱动”

这是非常关键的一步。

现在的问题不是单轮 parsing 不够，而是跨轮推进逻辑不够语义化。

只要 State Layer 足够强：

- `今天早上开始的` 就不会再被错当作普通补充
- `没发热，也没吐` 就不会只吸收一个槽
- `现在喘不上来` 也可以在第二轮立刻升级 risk

---

# 分阶段落地建议

## Phase 0：短期止血不等于长期方案

短期仍然可以做：

- 扩 `DURATION_PATTERN`
- 扩 red flag 词表
- 扩口语 body part 词表

这些不是错的，但要明确：

> 这些只是止血，不是长期解法。

---

## Phase 1：新增 `TurnInterpreterWorker`

这是长期方案的第一步，也是最值得开始做的一步。

做法：

- 保留现有 `SemanticParserWorker` / `FactExtractor`
- 新增一个统一的 `TurnInterpreterWorker`
- 先让它输出 richer structure
- 暂时不替换所有旧链路
- 先让 planner / risk 在部分场景优先消费它

这一阶段的目标不是一次推翻旧系统，而是：

> 先让 richer semantics 进入主流程。

---

## Phase 2：把 planner 从静态 required slots 升级为状态驱动规划

这一阶段要改的是：

- 不再只靠 `PRIMARY_SYMPTOM -> required slots`
- 引入 answered slots、corrections、uncertainty、risk signals
- 让 planner 面向“信息增益”做选择

同时保留 deterministic policy：

- 不重复问
- 高危场景不 routine follow-up
- 最多问 1-2 个问题

---

## Phase 3：把风险层改成“语义证据融合”

这一阶段的重点是：

- 风险层优先消费 normalized risk signals
- hard rules 只作用于 confirmed signal
- LLM risk assessor 只看结构化状态，不直接再扫原始用户文本

这样可以避免：

- 同一段自然语言在多个层次被重复扫词
- 词表稍有缺漏，整条风险链路直接断掉

---

## Phase 4：让 heuristic 退到 fallback 位置

长期理想状态下：

- heuristic 仍然存在
- 但它是 fallback，不是主判断来源

它的职责可以保留为：

- obvious hard keywords
- 模型宕机兜底
- JSON / schema repair
- 超高精度 low-recall 护栏

---

# 设计原则

## 原则 1：理解交给 LLM，约束交给规则

## 原则 2：不要让规则去猜用户语义

## 原则 3：不要把 LLM 的丰富理解压扁成太窄的结构

## 原则 4：planner 和 risk 必须吃语义状态，而不是只吃关键词

## 原则 5：heuristic 是 fallback，不是主引擎

---

# 推荐的长期评价指标

未来如果采用这条路线，评测不应只盯“主回归是否通过”，还要单独看以下能力：

### 1. 口语变体泛化

- dyspnea 变体
- bleeding 变体
- altered consciousness 变体
- body part 口语变体
- duration 口语变体

### 2. 多槽位同轮回答

- 一轮回答两个 follow-up
- 一轮同时给 duration + temperature

### 3. correction / negation 能力

- 不是 A，是 B
- 没有 X，但有 Y
- 刚才说错了

### 4. 跨轮风险升级

- 首轮普通，二轮暴露红旗
- 二轮后必须 interrupt

### 5. 重复提问率

- 已答槽位再次进入 questionPlan 的比例

---

# 一句话总结

长期来看，不应该继续在 `DURATION_PATTERN`、`containsAny(...)` 和静态 `requiredSlots` 上无限打补丁，而应该把 triage 重构为：

> **LLM 负责回合级语义理解，状态机负责跨轮记忆与冲突合并，规则层只负责医疗安全与输出约束。**

只有这样，系统才会真正充分利用大模型能力，而不是继续停留在“规则系统外面包了一层大模型”的阶段。
