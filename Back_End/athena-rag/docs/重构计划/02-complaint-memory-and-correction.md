# 02-complaint-memory-and-correction

## 目标

这份文档只处理一件事：把 complaint memory 和 correction 从当前的大 worker 混杂态中拆出来，变成可执行的结构边界。

之所以先讲这部分，是因为它是后续 risk semantic layer 能否稳定建立的前提。如果主诉状态本身摇摆，任何风险闭环都会漂。

---

## 一、当前 complaint 相关逻辑为什么已经开始长歪

当前 `TurnUnderstandingWorker` 里，complaint 相关逻辑至少包括：
- explicit complaint 提取
- carried complaint 回填
- primary complaint fallback
- correction 中主诉覆盖
- intent repair 时把 explicit complaint 作为新主诉线索

从功能上看这些都“和主诉有关”，但它们不是同一层职责：

1. 有的是文本观察
2. 有的是 session 记忆延续
3. 有的是纠正语义
4. 有的是状态替换
5. 有的是 turn intent 推断线索

现在这些都塞在一个 worker 里，会产生两个问题：
- complaint 既像 turn candidate，又像 session truth
- correction 既像文本解析，又像状态写入

这就是为什么当前腹痛链虽然能跑，但继续演进一定会变成更多 helper 堆积。

---

## 二、complaint memory 到底是什么

complaint memory 不是“上一次主诉槽位里有什么值”。

它更准确地说是：

**当前 session 对主诉语义对象的稳定记忆。**

它至少承担四类能力：
- establish：首次建立主诉
- carry：在后续 follow-up 轮继续延续主诉上下文
- correct：在用户纠正时修正主诉
- replace：当用户开启新主诉线时替换原主诉

只要一个对象承担了这四类能力，它就不能只是 turn-level 字段，也不能只是 slot 兼容视图。

---

## 三、complaint memory 为什么应由 reducer 持有

### 1. 它是跨轮归约，不是单轮理解

worker 做的是“这一轮我看到了什么”。

complaint memory 做的是“我综合当前与历史后，当前会话主诉是什么”。

这天然是 reducer 语义。

### 2. 它依赖 correction 消费结果

主诉并不是永远靠显式提及更新。很多时候它会被 correction 改写。

而 correction 本身又需要基于已有 session state 来判断 target。这就说明 complaint memory 不是一个可以在 worker 里顺手决定的东西。

### 3. 它要为 planner 与 risk 层提供稳定输入

如果 complaint memory 不是稳定 session state，而是某个 worker 的随手输出：
- planner 拿到的主诉会抖动
- risk semantic builder 拿到的 complaint context 会抖动
- 最终胸部风险链就会继续不收口

---

## 四、建议的 complaint memory 结构语义

这里先讲语义，不强制一步到位实现最终类图。

complaint memory 至少应具备以下信息：
- current resolved complaint
- previous complaint history
- establish source
- latest correction source
- latest replacement source
- 当前是否稳定

即使短期不建完整新类，也至少要保证 reducer 中存在一个独立于 `slotState.PRIMARY_SYMPTOM` 的概念层。

原因是：
- `PRIMARY_SYMPTOM` 是兼容投影
- complaint memory 才是定义本体

---

## 五、complaint establish / carry / correct / replace 应该如何分层

### 5.1 establish

#### 语义

用户在当前轮首次明确表达主诉，系统需要建立 session 主诉。

#### 上游负责什么

`ComplaintCandidateExtractor` 从文本中提取显式 complaint candidate。

#### reducer 负责什么

`ComplaintMemoryPolicy` 决定：
- 当前是否应建立主诉
- 如果已有主诉，当前 candidate 是 reinforce、replace 还是忽略

#### 不应该发生什么

不应在 worker 中只要提到症状词就直接把 session 主诉定死。

### 5.2 carry

#### 语义

当前轮虽然没有重复主诉词，但仍在同一 complaint 上回答 follow-up。

#### 负责层

carry 只能由 reducer 中 complaint memory policy 负责。

#### 为什么不能放在 worker

因为 carry 本质上是在说：
- 当前轮没有新主诉 candidate
- 但 session 主诉应继续延续

这已经是 session state 决策，不是文本抽取。

#### 典型错误

worker 把 carried complaint 回填到 `TurnUnderstanding.primaryComplaint`，会让 candidate 和 truth 再次混在一起。

### 5.3 correct

#### 语义

用户不是在开启新 complaint，而是在修正已有 complaint 认定。

#### 正确路径

1. `CorrectionPhraseParser` 识别 correction phrase
2. `CorrectionTargetResolver` 基于 current state 判断这是主诉 correction
3. reducer 中 `ComplaintMemoryPolicy` 应用 correction

#### 不应发生什么

不应在 worker 里一旦发现 correction phrase 就直接 `setPrimaryComplaint(confirmValue)`。

这会绕过 session state resolution。

### 5.4 replace

#### 语义

用户后续引入了新的主诉语义线，系统应把 session 主诉替换为新对象。

#### 谁来决定

仍应由 reducer 中的 `ComplaintMemoryPolicy` 决定。

#### 为什么不能由 turn worker 决定

因为 replace 不是“这一轮提到了新症状”就够了，而是要结合：
- 先前 complaint memory
- 当前对话是否已转线
- 当前 correction 与新 complaint 的关系
- planner 是否仍在原 complaint 的追问路径上

---

## 六、当前 correction 为什么虽然方向对，但结构已经不对

当前 correction 逻辑已经在语义上做对了一些事：
- 有 correction cue 识别
- 有 reject / confirm 提取
- 有主诉与槽位两类 target 的雏形

问题不在“有没有 correction”，而在 correction 被塞进了错误层级。

现在它在 `TurnUnderstandingWorker` 中同时做了：
- phrase parsing
- state lookup
- target resolution
- 某些路径上的主诉覆盖

这意味着：
- 它既是 parser，又是 resolver，又是 state mutator
- 任何 correction 复杂化都会继续侵入 worker

---

## 七、correction 应该怎么拆

### 7.1 CorrectionPhraseParser

负责：
- 识别 correction cue
- 提取 reject value
- 提取 confirm value
- 标记当前话语存在纠正语义

只回答：用户是不是在说“不是 A，是 B”。

它不回答：
- A/B 对应的是哪个 session 对象
- 最终该改什么状态

### 7.2 CorrectionTargetResolver

负责：
- 读取 complaint memory
- 读取 reducer 当前 slot state
- 读取 asked/pending context
- 判断 correction target 是 primary complaint、某个 slot，还是 unresolved

它回答：这个 correction 作用于谁。

### 7.3 ReducerCorrectionApplier

负责：
- 把 resolved correction 应用到 complaint memory
- 把 resolved correction 应用到 slot state
- 记录 correction history

它回答：这个 correction 如何改变 session 真相。

---

## 八、哪些 correction 逻辑必须迁出 `TurnUnderstandingWorker`

下面这些逻辑不应继续留在 worker：

### 1. 基于 `slotState` 的最终 target 匹配

原因：这是 state-aware resolution，不是文本理解。

### 2. 基于 asked/pending context 的最终 target 拍板

原因：这已经不是 parsing，而是在做 session-level disambiguation。

### 3. 通过 correction 直接覆盖主诉

原因：这等于 worker 越过 reducer 直接改 session truth。

### 4. correction 对 slot 的最终落槽

原因：slot state 的最终写入只能由 reducer 负责。

### 5. correction history 的最终累计

原因：history 是 session 持久状态，不应由 turn worker 侧写。

---

## 九、`ComplaintMemoryPolicy` 应该怎么想，而不是怎么写

这不是一个“helper 工具类”，而是一个真正的 session policy。

它至少要回答以下问题：

### 问题 1：当前 candidate 是 establish、reinforce、replace 还是 ignore

这个判断影响主诉是否变化。

### 问题 2：当前 correction 是否作用于主诉

这个判断影响 correction 是走 complaint 路径还是 slot 路径。

### 问题 3：当本轮没有显式 complaint candidate 时，session 主诉是否继续 carry

这个判断影响 follow-up 轮里 planner 与 risk 层看到的 complaint context。

### 问题 4：什么时候允许 replace

如果 replace 过宽，会让主诉抖动；如果过窄，会让多 complaint 转线无法发生。

因此它必须是 policy，而不是简单 setter。

---

## 十、recommend 的最小落地路径

如果实现窗口要先做一个不大爆炸但方向正确的版本，建议这样落：

### 第一步

从 `TurnUnderstandingWorker` 中抽出：
- `ComplaintCandidateExtractor`
- `FollowUpAnswerResolver`
- `CorrectionPhraseParser`

即使先作为内部类或小组件，也先把 parsing 责任从混合逻辑中切开。

### 第二步

把 complaint carry 从 worker 中拿掉。

worker 可以在需要时读取 complaint memory 作为语义提示，但不再把 carry 结果写回 `TurnUnderstanding.primaryComplaint`。

### 第三步

在 reducer 中增加最小版 `ComplaintMemoryPolicy`：
- 如果有显式 candidate，决定 establish / reinforce / replace
- 如果没有 candidate，决定 carry
- 如果有 resolved correction，决定主诉是否被修正

### 第四步

把 correction target resolution 从 worker 挪到 state-aware resolver。

初期哪怕逻辑还简单，也要先把责任放对层。

---

## 十一、这部分重构完成后的直接收益

### 1. 主诉不再双重真相

candidate 归 candidate，session truth 归 session truth。

### 2. correction 不再继续长在大 worker 里

后续 correction 扩展时，不需要继续污染 turn understanding。

### 3. risk 层终于有稳定 complaint context 可读

这对胸部风险链尤其关键。

### 4. planner 后续改输入对象时不会建立在漂移主诉上

也就是说，risk/planner 的后续结构改造才有稳定地基。

---

## 十二、这一部分如果做错，最常见会怎么错

### 错法 1：只是把 worker 私有方法改名成几个 helper

如果 complaint carry、correction target resolution、主诉覆盖仍发生在 worker，只是函数名变多了，那不算拆分。

### 错法 2：reducer 里继续只写 `PRIMARY_SYMPTOM`

如果 complaint memory 没有独立概念层，那后续 replace/correct/carry 仍会被 slot 语义绑死。

### 错法 3：correction parser 顺手继续做 target resolution

只要 parser 还在读 session state，它就不是 parser，而是混合 resolver。

---

## 十三、这份文档和后续文件的关系

读完这份文档后，后续实现窗口应该继续看：
- `03-risk-semantics-and-planner.md`：理解为什么 complaint 稳定后还必须补 risk semantic layer
- `04-module-split-蓝图.md`：看具体模块边界
- `05-phase-migration-plan.md`：看先做哪些步骤最稳
