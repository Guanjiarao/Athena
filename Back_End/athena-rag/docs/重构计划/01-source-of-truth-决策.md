# 01-source-of-truth-决策

## 目标

这份文档专门解决一个问题：哪些对象到底是谁的真相来源。

在 triage v2 当前这批问题里，很多 bug 表面上看像识别问题，实际上是 source of truth 不单一造成的。只要真相来源不单一，后续任何 worker 的增强都会把复杂度继续堆高。

---

## 一、primaryComplaint 的 source of truth

### 结论

**session 级 primary complaint 的 source of truth 必须是 reducer 持有的 complaint memory。**

这句话的关键在于“session 级”。

很多当前的混乱，来自把“本轮看见了什么主诉”和“整个会话当前认定的主诉是什么”混成一件事。

### turn-level `primaryComplaint` 的角色

turn-level `primaryComplaint` 只代表：
- 本轮文本中显式表达出的主诉候选
- 或者本轮 correction 语义指向的新主诉候选

它是 candidate，不是 truth。

它的价值是：
- 为 reducer 提供建立/替换主诉的输入
- 为 risk signal derivation 提供当轮语义线索

它不应该：
- 直接充当跨轮主诉记忆
- 直接代表 `history.finalPrimaryComplaint`
- 被 worker 自己 carry 成 session truth

### `slotState.PRIMARY_SYMPTOM` 的角色

`slotState.PRIMARY_SYMPTOM` 仍然有用，但它应该被重新定位为：

**complaint memory 在 slot 体系中的兼容投影。**

也就是说，它的价值在于：
- 给 slot-first 的既有逻辑提供兼容输入
- 让 planner、report、部分现有代码还能继续通过 slot 体系读取主诉

但它不应再是 complaint memory 的本体。

如果把它继续当本体，会出现两个问题：
1. correction 只能围绕单个 slot value 硬碰硬
2. risk unresolved 时很容易又退回把主诉槽位当万能兜底

### `history.finalPrimaryComplaint` 应从哪里汇总

它必须从 complaint memory 的当前 resolved value 汇总。

不能从下面任何一个对象直接汇总：
- `latestTurnUnderstanding.primaryComplaint`
- 单纯的 `slotState.PRIMARY_SYMPTOM`
- 某轮 risk gap 的 fallback 结果

原因很简单：
- latest turn 可能只是候选
- slot projection 可能滞后或受兼容逻辑影响
- risk fallback 本来就不应定义主诉

### establish / carry / correct / replace 由谁负责

#### establish

由 turn 层产生 complaint candidate，再由 reducer 中的 `ComplaintMemoryPolicy` 决定是否建立 session 主诉。

#### carry

由 reducer 中的 complaint memory policy 负责，不应在 worker 中显式回填 candidate。

#### correct

由 correction resolver 产出“主诉被纠正”的语义事件，再由 reducer 应用到 complaint memory。

#### replace

当后续轮明确出现新主诉且满足替换条件时，由 complaint memory policy 决定替换，不由 turn worker 顺手拍板。

---

## 二、complaint memory 的 source of truth 与持有层

### 结论

**complaint memory 属于 session state。持有与写入责任在 reducer；规则责任在独立 policy。**

这意味着 complaint memory 既不应漂在 `TurnUnderstanding` 里，也不应只是 `slotState.PRIMARY_SYMPTOM` 的别名。

### 为什么必须让 reducer 持有

因为 complaint memory 本质上是“跨轮归约结果”，而不是“单轮理解结果”。

只要存在下面任一能力，它就天然属于 reducer 层：
- 跨轮延续
- 被 correction 改写
- 被新主诉替换
- 被 history 汇总

这些都说明 complaint memory 是 session state，而不是 turn state。

### 哪些模块可以读 complaint memory

可以读：
- turn worker 中需要做语义提示的部分
- risk signal derivation
- risk semantic builder
- planner input assembler
- report / history 汇总层

### 哪些模块可以写 complaint memory

只能写：
- reducer 内部的 complaint merge 路径

这是一条硬边界。

只要 worker、planner、risk decision 也能直接写 complaint memory，系统就会再次变成谁都能顺手改主诉。

---

## 三、correction target 的 source of truth

### 结论

**correction target 的 source of truth 是 reducer 当前 session state。**

worker 解析 correction phrase 时可以看见语言线索，但它不应拥有最终 target 拍板权。

### 为什么 worker 不适合拍板 correction target

因为 target resolution 需要的不是“这句话看起来像在改什么”，而是：
- 当前 session 已有哪些稳定主诉/槽位
- 上一轮问了什么
- 当前 pending 是什么
- 当前 complaint memory 是什么
- 是否存在多个候选 target

这已经是 state-aware resolution，而不是文本 parsing。

### correction 应拆成哪三层

#### 1. correction phrase parsing

负责从文本里提取：
- reject value
- confirm value
- correction cue

它只回答“用户在说纠正”。

#### 2. correction target resolution

负责基于 reducer state 判断：
- 这是在纠正 primary complaint
- 这是在纠正某个具体 slot
- 还是无法唯一判定，必须 unresolved

它回答“用户在纠正什么”。

#### 3. reducer consumption

负责把 resolved correction 应用到：
- complaint memory
- slotState
- correction history

它回答“这个纠正如何改变 session 真相”。

---

## 四、risk semantic object 的 source of truth

### 结论

**risk semantic object 必须成为 session 级一等状态，不能继续只靠 risk signal 与 risk decision 之间硬连。**

### 为什么 signal 不够

`RiskSignalUnderstanding` 只能表达：
- 检测到了什么风险信号
- assertion 是 present / suspected / unknown / absent

它不能稳定表达：
- 这个风险 concern 当前是否已闭合
- 应该继续问什么
- 它关联的是哪个 complaint 语义上下文
- 它在历史上是持续存在、已确认还是未决

### 为什么 decision 也不够

`RiskDecision` 表达的是动作，不是状态本体。

它适合回答：
- 要不要警示
- 要不要继续补问
- 要不要升级

但它不适合承担 risk concern 的长期持有，否则 planner 就只能依赖“当前这一轮动作对象”来理解风险状态。

### risk semantic object 应该持有什么

至少要能承载：
- 关联 signal type
- 关联 complaint / symptom context
- 当前 semantic status：confirmed / suspected / unresolved / resolved
- 当前 follow-up target
- 历史持续状态

### 谁持有它

应放在 context / reducer 持有的 session-level risk state 中。

可以先不立即造出完美类图，但必须先承认这是一层独立语义，不再把它塞进：
- `riskSignalState`
- `RiskDecision.unresolvedRiskGaps`
- planner 的临时 slot fallback

---

## 五、planner 输入对象的 source of truth

### 结论

**planner 必须以 reducer 后的稳定状态为主输入，而不是直接消费 turn-level observation。**

### planner 可继续兼容消费的对象

- `slotState`
- `answeredSlots`
- `riskDecision`

这些都可以保留。

### 但 planner 的主要语义输入应升级为

- complaint memory
- resolved corrections
- risk semantic objects
- reducer 归约后的稳定 answered / pending 语义

### 为什么 `suppressedGaps` 应由 planner 决定

因为 suppression 是“这一轮问什么/不问什么”的策略决定。

但它不能由 worker 提前做，也不能由 reducer 隐式做。worker 和 reducer 只能提供稳定状态，planner 才负责：
- 哪些问题 askable
- 哪些被更高优先级问题 suppress
- 哪些虽然未决但本轮不该问

### unresolved risk 为什么不应退回 `PRIMARY_SYMPTOM`

因为 unresolved risk 的语义是：
- 某个风险 concern 已被激活
- 但还没被足够确认或闭合

这不是“主诉重新未知”，而是“风险对象未闭合”。

一旦退回 `PRIMARY_SYMPTOM`：
- planner 会把风险补问误判成主诉澄清
- complaint memory 会被风险追问污染
- 胸部链会出现风险动作先有了，但主诉状态继续晃动的现象

所以 unresolved risk 必须绑定到 risk semantic object 的 follow-up target 上；即使短期内这个 target 还只能用兼容结构表达，也不能语义上再回退成主诉槽位。

---

## 六、最容易犯的三个回退错误

### 错误 1：把 turn candidate 再次当成 session truth

表现：
- `TurnUnderstanding.primaryComplaint` 继续承担 carry 结果
- latest turn 直接定义 final complaint

后果：worker 继续膨胀，reducer 继续偏薄。

### 错误 2：把 `PRIMARY_SYMPTOM` 继续当 complaint memory 本体

表现：
- complaint replace / correction 继续用单槽位硬覆盖
- risk unresolved 继续回退到主诉槽位

后果：主诉和风险语义继续互相污染。

### 错误 3：把 `RiskDecision` 当 risk state 本体

表现：
- unresolved/confirmed/suspected 的长期语义只存在于 decision history 中
- planner 只能盯着当前 decision 做 slot fallback

后果：risk action 有了，但 risk 对象永远不稳。

---

## 七、这份文档的执行含义

如果后续实现窗口要真正落地这套 source-of-truth 决策，最先要做的是：

1. 停止 worker 直接 carry complaint
2. 停止 worker 通过 correction 直接覆盖主诉
3. 把 complaint memory 的写入收回 reducer
4. 把 correction target 的最终 resolution 收回 state-aware resolver
5. 让 risk unresolved target 不再退回 `PRIMARY_SYMPTOM`

这五件事不需要一步全部做完，但必须按这个方向推进。否则任何局部修补都会继续制造新的双重真相。
