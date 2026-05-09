# 03-risk-semantics-and-planner

## 目标

这份文档专门回答胸部风险链暴露出来的结构问题：为什么当前会出现“风险动作有了，但主诉对象和 risk semantics 还没收口”，以及后续应如何分层，让 planner 不再回退成主诉兜底机。

---

## 一、当前胸部链真正暴露出的不是识别问题，而是语义层缺失

如果只是 risk signal 检测不到，那问题相对简单：补 detection 即可。

但当前 `034/035/048/049` 的症状不是纯 detection 失败，而是：
- signal 有时已经被看见
- risk action 有时也已经出来
- 但中间缺少稳定的 risk semantic object
- planner 因为拿不到稳定风险对象，只能回退到 slot-first
- 最后 unresolved risk 被错误地映射回 `PRIMARY_SYMPTOM`

这说明系统在 risk 这里跳过了一层：

**它从 risk signal 直接跳到了 risk action，中间没有足够稳定的 risk concern state。**

---

## 二、risk 层至少应该分成四件事

### 1. risk signal detection

回答：当前输入或当前归约状态里，检测到了什么风险信号。

典型对象：`RiskSignalUnderstanding`

它适合表达：
- signal type
- assertion
- confidence
- evidence

它不适合表达：
- 当前风险 concern 是否已经闭合
- 应该继续问什么
- 这个 risk concern 当前挂在哪条 complaint 语义线上

### 2. risk semantic object construction

回答：这些 signal 在当前会话里形成了什么风险 concern，它的状态是什么。

这层应该把 signal 组织成更稳定的一等对象。

### 3. risk decision action

回答：基于当前 risk semantic state 和 risk assessment，这一轮该采取什么动作。

例如：
- `TRIGGER_WARNING`
- `ASK_RISK_CLARIFICATION`
- `MONITOR`
- `NO_RISK_SIGNAL`
- `ESCALATE_FROM_HISTORY`

### 4. risk history persistence

回答：这个风险 concern 在跨轮上如何持续存在、升级或闭合。

如果只有 decision history，没有 semantic history，planner 每一轮都像在重新猜一个新对象。

---

## 三、当前结构为什么会出现“动作有了，但对象没收口”

### 原因 1：risk worker 直接承担了 semantic gap construction

当前 `RiskStratifierWorker` 不仅在做 risk assessment，还直接在构造：
- unresolved risk gaps
- confirmed risk gaps
- suspected risk gaps

这让 risk worker 成了：
- assessment worker
- semantic builder
- action decider

职责一旦混在一起，后续任何 risk 复杂化都会继续堆进这一个点里。

### 原因 2：signal 到 follow-up target 的映射没有独立持有层

当前 unresolved risk gap 的 follow-up target 很大程度上依赖映射逻辑，而不是依赖独立 risk object 本身。

这意味着：
- 一旦映射不到自然 slot
- 或者当前 signal 与 complaint 语义之间还没闭合
- 系统就只能找个兼容兜底

现在这个兜底常常就是 `PRIMARY_SYMPTOM`。

### 原因 3：planner 仍然主要吃 slot-first 对象

虽然 planner 已经会看 risk decision，但它最终做 selection 时，依赖的仍然是 slot/gap 体系。

如果 risk semantic object 不稳定，planner 就会自然回退成：
- unresolved risk → 找一个 slot
- 没有明确 slot → `PRIMARY_SYMPTOM`

于是风险补问被错误地降级为主诉澄清。

---

## 四、为什么 unresolved risk 退回 `PRIMARY_SYMPTOM` 是结构性错误

这个点非常关键。

### 表面上看为什么它“能跑”

回退到 `PRIMARY_SYMPTOM` 的好处是：
- 系统已经有主诉相关提问机制
- planner 已经支持主诉 gap
- 不需要额外建 risk-specific follow-up target

所以它短期是最省事的兼容手段。

### 但本质上为什么它错

因为 unresolved risk 的语义不是：
- “我还不知道你的主诉是什么”

而是：
- “我知道这里有一个风险 concern，但它还没闭合”

这两者是完全不同的问题。

### 退回主诉的直接后果

1. planner 把风险补问降级为 complaint clarification
2. complaint memory 被风险追问污染
3. 风险 concern 无法作为一等对象稳定存在
4. 胸部链会持续出现：
   - 风险动作有了
   - 但主诉状态继续晃动
   - question plan 看起来像还在重新找主诉

所以这个回退不是“稍微不优雅”，而是会系统性破坏边界。

---

## 五、risk semantic object 应该长什么语义，而不是长什么类名

这层的核心不是类名，而是它必须承载什么。

一个合格的 risk semantic object 至少应表达：
- 当前 concern 对应的 signal type
- 它关联的 complaint context
- 它当前是 confirmed / suspected / unresolved / resolved
- 它当前的 follow-up target 是什么
- 它历史上是否持续存在
- 当前这个 concern 是否已经足够驱动 action

也就是说，这层对象的任务是把 risk 从“零散 signal”收敛成“可追问、可持久、可决策的 concern”。

---

## 六、risk signal detection 应该负责到哪里为止

`RiskSignalDeriver` 只应负责 detection，不应负责 semantic closure。

它可以读取：
- answered slots
- current complaint candidate
- stable complaint memory

它可以输出：
- `RiskSignalUnderstanding` 列表

但它不应继续做：
- unresolved risk gap 构造
- confirmed/suspected/monitor action 决策
- risk history 写入

否则 detection 层会再次变成一个半决策层。

---

## 七、RiskSemanticBuilder 应该负责什么

这是当前结构最缺的模块之一。

### 它的职责

它应负责把：
- previous risk semantic state
- new risk signals
- complaint memory
- stable slotState

合并成稳定的 risk concern state。

### 它应回答的问题

1. 新 signal 应归入哪个 existing concern
2. 当前 concern 的状态是 confirmed、suspected、unresolved 还是已闭合
3. 当前 concern 的 next follow-up target 是什么
4. 当前 concern 是否和当前主诉语义上下文绑定，还是独立存在

### 它不应负责什么

- 不做 final action decision
- 不做 planner selection
- 不把无 target 的 concern 粗暴兜底成 `PRIMARY_SYMPTOM`

---

## 八、RiskDecisionPolicy 应该负责什么

一旦 risk semantic layer 存在，`RiskDecisionPolicy` 才真正有清晰输入。

它不再需要直接从零散 signal 或 slot fallback 做动作判断，而是基于：
- risk semantic state
- risk assessment (`RiskLevel`)
- risk history

输出 `RiskDecision`。

### 它的职责应收敛为

- 是否触发 warning
- 是否继续 risk clarification
- 是否仅 monitor
- 是否因 history 升级

### 它不应负责

- signal detection
- complaint memory 更新
- follow-up target fallback

---

## 九、planner 到底应该消费什么对象

### 当前困境

planner 目前已经不只是简单看 `slotState`，但又还没有真正拥有稳定的 risk semantic input。

所以它处在一个尴尬位置：
- 上游说“这里有 risk decision”
- 但给不出稳定 concern object
- planner 最后还是只能转回 slot gap selection

### 未来的主要输入

planner 应该基于 reducer 后稳定对象工作，至少包括：
- slotState
- complaint memory
- resolved corrections
- risk semantic objects
- risk decision

### planner 的策略职责

planner 依旧负责：
- askability
- suppression
- selection

但它的输入必须从“零散 slot + 一点 risk 提示”升级成“稳定 session state + 稳定 risk concern”。

---

## 十、`suppressedGaps` 应由谁决定

### 结论

`suppressedGaps` 仍然应由 planner 决定。

### 原因

因为 suppression 是策略问题，不是状态事实问题。

worker 不应决定：
- 某个 gap 以后都不该问

reducer 也不应决定：
- 某个 gap 这一轮该不该被更高优先级 gap 抑制

这些都属于 planner 的策略域。

### 但 planner 的 suppression 依据必须升级

它不应只基于：
- slot 是否 answered
- slot 是否 corrected

还应基于：
- 当前是否存在 active unresolved risk concern
- 该 concern 的 follow-up target 是否优先于普通 slot gap
- 当前 complaint context 是否已稳定，不需要再回头主诉澄清

---

## 十一、胸部链为什么特别需要 risk semantic layer

腹痛链的大多数问题仍然可以沿 complaint 主线收口。

胸部链不同。它天然更容易出现：
- 主诉对象与风险 concern 同时存在
- 风险 concern 的 follow-up 目标未必就是主诉本身
- 风险动作与普通 slot 提问会竞争 planner 注意力

如果没有 risk semantic layer，系统就会本能地做两件事：
1. 把所有 risk unresolved 重新翻译成 slot gap
2. 再在 slot gap 里选一个最兼容的兜底槽位

这就是为什么胸部链特别容易把 unresolved risk 错误地转译为主诉问题。

---

## 十二、建议的最小落地路径

### 第一步

把 risk signal derivation 从大 worker 中概念上独立出来。

即使最初还不完全改类，也先在结构上承认：
- detection 是 detection
- 不再让它顺手承担 action 语义

### 第二步

引入最小版 risk semantic state。

一开始不必追求完美设计，但必须至少能表达：
- concern type
- concern status
- current follow-up target
- complaint context reference

### 第三步

让 unresolved risk 绑定到 risk semantic follow-up target，而不是 `PRIMARY_SYMPTOM`。

这一步即使实现上还需要兼容桥接，也要先把语义方向扳正。

### 第四步

让 planner 通过 `PlannerInputAssembler` 消费 risk semantic state。

也就是说，不直接让 planner 从 scattered fields 拼装 risk 语义。

---

## 十三、这一部分如果做错，常见会怎么错

### 错法 1：只给 risk worker 再加更多 helper

如果 unresolved / confirmed / suspected 的构造还是继续堆在 `RiskStratifierWorker`，那不算分层。

### 错法 2：把 risk semantic state 继续偷放进 `RiskDecision`

如果 risk concern 只能通过当前 decision 或 history 反推，那 planner 还是没有稳定的一等对象。

### 错法 3：planner 形式上看 risk，实质上仍然 slot-first

如果 planner 一遇到 unresolved risk 就回退主诉槽位，那风险层的抽象仍然没有真正落地。

---

## 十四、重构后希望出现的状态

理想但现实可落地的状态应该是：

1. risk signal 只是 observation
2. risk semantic object 是 session state
3. risk decision 只是 action
4. planner 基于 risk semantic follow-up target 做选择
5. complaint memory 不再被 unresolved risk 反向污染

一旦达到这五点，胸部风险链才算真正从结构上收口，而不是靠 case patch 勉强过关。
