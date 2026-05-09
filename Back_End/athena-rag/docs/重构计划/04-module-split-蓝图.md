# 04-module-split-蓝图

## 目标

这份文档把建议的模块拆分写成可以执行的蓝图。

原则不是“起很多类名”，而是让后续实现窗口能明确知道：
- 每一块逻辑为什么存在
- 它该读什么
- 它该产出什么
- 它绝不能顺手做什么

如果这些边界不清楚，再多的“抽模块”都只是把一个大函数切成几个小函数。

---

## 一、整体分层图（概念层）

建议把 triage v2 的核心运行层次理解为：

1. Turn Observation Layer
2. State Reduction Layer
3. Risk Semantic Layer
4. Planning Layer

### Turn Observation Layer

只负责从当前轮中抽出候选观察：
- complaint candidate
- answered slot candidates
- correction phrase candidates
- risk signal candidates（或其输入）

### State Reduction Layer

负责把本轮候选观察合并为 session truth：
- complaint memory
- stable slot state
- correction history
- accumulated risk semantic state

### Risk Semantic Layer

负责把 risk 从“观测值”升级为“可追问、可持久、可决策的 concern”。

### Planning Layer

负责基于稳定状态做：
- askability
- suppression
- selection

---

## 二、模块清单

### 2.1 TurnUtteranceInterpreter

#### Responsibilities
- 调用 LLM 获得最基础的 turn understanding
- 做最薄的 intent repair
- 汇总本轮 observation components 的输出

#### Inputs
- latest user turn
- transcript / summary
- last asked slots（仅作理解提示）

#### Outputs
- `TurnUnderstanding`

#### What it must NOT do
- 不做 complaint carry
- 不做 correction target 最终 resolution
- 不做 reducer merge
- 不直接改 `slotState`
- 不直接写 complaint memory

#### 备注
这是未来取代“当前大而全 TurnUnderstandingWorker”的入口壳，但它本身应尽量薄。

---

### 2.2 ComplaintCandidateExtractor

#### Responsibilities
- 从当前文本中提取显式主诉候选
- 必要时提取弱症状 + body cue complaint candidate

#### Inputs
- latest turn text

#### Outputs
- `ComplaintUnderstanding` candidate 或 null

#### What it must NOT do
- 不读取 history 做 carry
- 不做 complaint replace 决策
- 不直接更新 session complaint

#### 依赖关系
- 可被 `TurnUtteranceInterpreter` 调用
- 输出交给 reducer 层的 complaint policy 使用

---

### 2.3 FollowUpAnswerResolver

#### Responsibilities
- 基于 last asked / pending context 解析本轮回答了哪些 slot
- 做 answer candidate 的最小标准化

#### Inputs
- latest turn text
- lastAskedSlots
- pendingSlots

#### Outputs
- `List<AnsweredSlotUnderstanding>`

#### What it must NOT do
- 不决定最终是否写入 slotState
- 不做 planner selection
- 不做 risk action 决策

#### 依赖关系
- 可被 `TurnUtteranceInterpreter` 调用
- 输出交给 reducer merge 与 risk signal derivation 使用

---

### 2.4 CorrectionPhraseParser

#### Responsibilities
- 识别 correction cue
- 解析 reject / confirm phrase
- 产出 correction phrase candidate

#### Inputs
- latest turn text

#### Outputs
- correction phrase candidate

#### What it must NOT do
- 不读取 session state
- 不最终决定 correction target
- 不直接改 complaint 或 slot

#### 依赖关系
- 可被 `TurnUtteranceInterpreter` 调用
- 输出交给 `CorrectionTargetResolver`

---

### 2.5 ComplaintMemoryPolicy

#### Responsibilities
- 维护 complaint memory
- 决定 establish / carry / correct / replace
- 生成 `PRIMARY_SYMPTOM` 的兼容投影

#### Inputs
- previous complaint memory
- current complaint candidate
- resolved correction events
- reducer 当前上下文

#### Outputs
- updated complaint memory
- complaint projection to slotState

#### What it must NOT do
- 不解析原始文本
- 不做 risk signal detection
- 不做 planner suppression

#### 依赖关系
- 被 `StateReducer` 调用
- 输出可被 risk 与 planner 层只读消费

---

### 2.6 CorrectionTargetResolver

#### Responsibilities
- 基于 reducer 当前 state 解析 correction 作用目标
- 区分 primary complaint / slot value / unresolved correction

#### Inputs
- complaint memory
- reduced slot state
- asked / pending context
- correction phrase candidate

#### Outputs
- `CorrectionUnderstanding` 或 unresolved correction object

#### What it must NOT do
- 不直接写 slotState
- 不直接写 complaint memory
- 不累计 correction history

#### 依赖关系
- 被 reducer 路径调用，或在 reducer 前的 resolution 步骤调用
- 输出交给 `ReducerCorrectionApplier`

---

### 2.7 ReducerCorrectionApplier

#### Responsibilities
- 在 reducer 中消费 correction resolution 结果
- 更新 complaint memory
- 更新 slotState
- 更新 correction history

#### Inputs
- previous reducer state
- resolved correction events

#### Outputs
- updated reducer state

#### What it must NOT do
- 不重新解析文本
- 不重新拍板 target
- 不做 risk decision

#### 依赖关系
- 作为 reducer 内部职责存在
- 与 `ComplaintMemoryPolicy` 协作

---

### 2.8 StateReducer

#### Responsibilities
- 合并本轮 observation 成 session state
- 持有 complaint memory、reduced slots、answered/pending、correction history、risk semantic state

#### Inputs
- previous session state
- current `TurnUnderstanding`
- resolved correction results
- risk semantic updates

#### Outputs
- updated `TriageContext` / reducer result

#### What it must NOT do
- 不做 raw text parsing
- 不做 planner selection
- 不直接承担 risk decision policy 逻辑

#### 备注
未来 reducer 应从“薄合并器”升级为“session truth owner”。

---

### 2.9 RiskSignalDeriver

#### Responsibilities
- 基于 answered slots、complaint candidate、stable complaint context 检测 risk signals

#### Inputs
- answered slot candidates
- complaint candidate
- complaint memory（只读）

#### Outputs
- `List<RiskSignalUnderstanding>`

#### What it must NOT do
- 不构建 risk semantic state
- 不输出 final action
- 不写 risk history

#### 依赖关系
- 上游依赖 turn observation 与 complaint context
- 下游输出给 `RiskSemanticBuilder`

---

### 2.10 RiskSemanticBuilder

#### Responsibilities
- 将新旧 risk signals 归并为 session 级 risk concerns
- 维护 concern status、follow-up target、complaint context 绑定关系

#### Inputs
- previous risk semantic state
- new risk signals
- complaint memory
- stable slotState

#### Outputs
- updated risk semantic state

#### What it must NOT do
- 不做 final warning/monitor action
- 不做 planner selection
- 不把无 target concern 兜底成 `PRIMARY_SYMPTOM`

#### 依赖关系
- 输出给 `RiskDecisionPolicy` 与 `PlannerInputAssembler`

---

### 2.11 RiskDecisionPolicy

#### Responsibilities
- 基于 risk semantic state + risk assessment 生成 `RiskDecision`

#### Inputs
- risk semantic state
- `RiskLevel`
- risk history

#### Outputs
- `RiskDecision`

#### What it must NOT do
- 不重新检测 signal
- 不修改 complaint memory
- 不定义 follow-up target fallback

#### 依赖关系
- 输出给 planner 与 reply/action 层

---

### 2.12 PlannerInputAssembler

#### Responsibilities
- 把 reducer state、complaint memory、risk semantic state、risk decision 组装为 planner 统一输入

#### Inputs
- reduced slot state
- complaint memory
- resolved corrections
- risk semantic state
- risk decision

#### Outputs
- planner input view

#### What it must NOT do
- 不做 question selection
- 不写 session state

#### 依赖关系
- 下游只服务 `QuestionPlanner`

---

### 2.13 QuestionPlanner

#### Responsibilities
- 评估 askability
- 生成 suppressed gaps
- 选择本轮 next question target

#### Inputs
- planner input view

#### Outputs
- `QuestionPlan`
- selected / suppressed gaps

#### What it must NOT do
- 不读取 raw turn text 做解析
- 不自行回推 complaint memory
- 不在 risk unresolved 时自动退回 `PRIMARY_SYMPTOM`

#### 备注
planner 应成为纯策略层，而不是语义补丁层。

---

## 三、几个特别容易被做坏的边界

### 边界 1：TurnUtteranceInterpreter 不应再次长成大 worker

哪怕抽出了几个 resolver，如果解释器壳子继续顺手做 carry、target resolution、state writeback，它还是原来的大 worker。

### 边界 2：ComplaintMemoryPolicy 不是 helper

它不是“写几个 if-else 封装一下”，而是 session 主诉语义的唯一 policy 入口。

### 边界 3：RiskSemanticBuilder 不是 `RiskDecision` 的前置小函数

如果它不能作为可持久状态独立存在，那 planner 还是拿不到稳定 concern object。

### 边界 4：PlannerInputAssembler 不是多余层

没有这层，planner 很容易继续直接读 scattered fields，最后再次变成到处拼 session state。

---

## 四、如果只允许最少新增模块，应该先保哪些

如果考虑现实成本，第一波最值得先落地的是：

1. `ComplaintCandidateExtractor`
2. `FollowUpAnswerResolver`
3. `CorrectionPhraseParser`
4. `ComplaintMemoryPolicy`
5. `CorrectionTargetResolver`

这五个先落地后，当前最大混杂点就会开始解开。

第二波再上：
- `RiskSignalDeriver`
- `RiskSemanticBuilder`
- `PlannerInputAssembler`
- `RiskDecisionPolicy`

这样成本更可控，也更符合当前问题暴露顺序。

---

## 五、这个蓝图的真正验收标准

不是“类名是否创建了”，而是：

1. 谁能写 complaint memory 已经被收紧
2. correction 不再在 worker 内最终落状态
3. risk unresolved target 不再默认掉回主诉槽位
4. planner 拿到的是组装后的稳定输入，而不是自己去猜 session truth

只要这四点没发生，再漂亮的模块名都只是表面抽象。
