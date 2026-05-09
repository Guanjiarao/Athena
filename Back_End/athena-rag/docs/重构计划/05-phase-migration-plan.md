# 05-phase-migration-plan

## 目标

这份文档专门解决“怎么迁移”而不是“最终应该长什么样”。

因为这轮最容易失败的方式就是：
- 方案说得很对
- 但第一步就试图大爆炸式重构
- 最后实现窗口根本无从下手

所以这里强调的是现实可执行的增量迁移顺序。

---

## 总体迁移原则

1. 每一阶段结束后代码都应尽量保持可运行
2. 先拆 source of truth，再拆策略层
3. 先稳 complaint/correction，再上 risk semantic layer
4. 每一步都尽量让新结构通过旧入口接入，而不是一上来推翻主链路
5. 如果某一步只能“换函数名不换边界”，宁可不做

---

## Phase 1：先把 complaint candidate / follow-up answer / correction phrase 从大 worker 中切出来

### 目标

把 `TurnUnderstandingWorker` 中最明显的“文本观察职责”抽出来，并停止它直接承担 complaint carry 与主诉覆盖。

### 具体动作

#### 动作 1

抽出 `ComplaintCandidateExtractor`。

要求：
- 只从当前 turn 文本提 complaint candidate
- 不再从 history / slotState 做 carry 回填

#### 动作 2

抽出 `FollowUpAnswerResolver`。

要求：
- 基于 latest turn + lastAsked/pending 解析回答槽位
- 保持当前已有 follow-up 能力不回退

#### 动作 3

抽出 `CorrectionPhraseParser`。

要求：
- 只解析 correction cue / reject / confirm
- 不再在这个组件里读 session state

#### 动作 4

收紧 `TurnUnderstandingWorker`：
- 停止 complaint carry
- 停止通过 correction 直接覆盖 `u.setPrimaryComplaint(...)`
- 保留 intent repair，但仅作为 turn-level 辅助

#### 动作 5

在 reducer 中加最小版 `ComplaintMemoryPolicy`。

要求：
- 可以建立主诉
- 可以 carry 主诉
- 可以把当前 resolved complaint 投影到 `PRIMARY_SYMPTOM`

### 为什么先做这一步

因为当前最大风险不是 risk 本身，而是主诉 source of truth 已经摇摆。如果主诉继续在 worker 中被 carry 和覆盖，后续 risk semantic builder 也会建立在不稳的 complaint context 上。

### 这一阶段不要求做什么

- 不要求立刻引入完整 complaint memory 类图
- 不要求改 planner
- 不要求改 risk decision
- 不要求一次性把 correction target resolution 也完全迁走

### 阶段完成条件

1. `TurnUnderstanding.primaryComplaint` 已明确退化为 candidate
2. complaint carry 只在 reducer 路径发生
3. worker 不再因为 correction 直接覆盖主诉
4. 腹痛保护样本未出现明显回退

### 如果这一阶段做错，常见症状

- 表面抽了类，但 worker 仍在做 carry
- parser 仍偷偷读 session state
- reducer 只是继续写 `PRIMARY_SYMPTOM`，没有独立 complaint memory 语义

---

## Phase 2：把 correction target resolution 和 correction consumption 收回 state 层

### 目标

让 correction 彻底从“大 worker 的一段顺手逻辑”变成有清晰三段职责的结构：
- phrase parsing
- target resolution
- reducer application

### 具体动作

#### 动作 1

新增 `CorrectionTargetResolver`。

要求：
- 基于 reducer 当前 state 判定 target
- 能区分 primary complaint / slot value / unresolved

#### 动作 2

在 reducer 中引入 `ReducerCorrectionApplier`。

要求：
- 负责最终更新 complaint memory
- 负责最终更新 slotState
- 负责累计 correction history

#### 动作 3

对 unresolved correction 建立显式分支。

要求：
- target 不唯一时不强拍板
- 不再靠 worker 里多写一点匹配逻辑硬猜一个 target

#### 动作 4

让 complaint correction 与 slot correction 共用同一 resolution/consumption 框架。

要求：
- 区别只在 target 类型
- 不再是两套分散逻辑

### 为什么第二步做这个

因为一旦 complaint memory 先稳下来，correction 就终于有稳定参照系。否则 correction 解析永远只能围绕“当前好像像哪个值”去猜。

### 这一阶段不要求做什么

- 不要求大改 risk worker
- 不要求重写 planner
- 不要求一次性完善所有 correction 歧义策略

### 阶段完成条件

1. correction parsing 与 target resolution 完全分离
2. worker 不再读取 state 并直接落 final correction target
3. reducer 成为 correction history 唯一写入点
4. 主诉 correction 与普通 slot correction 通过同一消费路径落地

### 常见失败方式

- parser 形式上抽出去了，但 resolver 还是塞在 worker
- reducer 只是被动收结果，真正 target 拍板还在上游
- unresolved correction 继续被压扁成某个“最像的 target”

---

## Phase 3：建立 risk semantic layer，并让 planner 改为消费它

### 目标

解决当前胸部链最深层的结构问题：risk action 出来了，但 risk concern 不是稳定 session object，planner 只能回退成主诉/slot 兜底。

### 具体动作

#### 动作 1

概念上独立 `RiskSignalDeriver`。

要求：
- detection 只做 detection
- 不再顺手承担 unresolved/confirmed/suspected gap 构造

#### 动作 2

增加最小版 `RiskSemanticBuilder`。

要求：
- 把 signal 组织成 concern
- concern 有 status
- concern 有 follow-up target
- concern 绑定 complaint context

#### 动作 3

让 `RiskDecisionPolicy` 基于 risk semantic state + risk assessment 产出动作。

要求：
- 把 final action 从 concern state 中派生
- 不再让 risk worker 同时承担 detection + semantic + action 三层

#### 动作 4

引入 `PlannerInputAssembler`。

要求：
- 把 reducer state、complaint memory、risk semantic state、risk decision 组装成 planner 输入

#### 动作 5

planner 改为优先消费 risk semantic unresolved target。

要求：
- unresolved risk 不再回退到 `PRIMARY_SYMPTOM`
- 风险未闭合时继续追风险 concern，而不是重新找主诉

### 为什么这一步放最后

因为如果 complaint/correction 的 source of truth 还没拉直，risk semantic layer 会直接建立在不稳的 complaint context 上，最后还是会漂。

### 这一阶段不要求做什么

- 不要求立即做 riskDecision 全域重构
- 不要求把所有旧 risk helper 一次全部清空
- 不要求推翻现有 question planner 整体框架

### 阶段完成条件

1. signal / semantic / action 三层已经可分辨
2. unresolved risk 不再退回 `PRIMARY_SYMPTOM`
3. planner 的 risk follow-up 目标来自 risk concern，而不是主诉兜底
4. `034/035/048/049` 在不改 judge/report/case 的前提下更自然收口

### 常见失败方式

- 只是给 risk worker 再加几个 helper
- risk concern 仍然没有独立 session state
- planner 形式上看 risk，实质上还是 slot-first

---

## Phase 4（可选，非下一步必做）：清理兼容桥与沉淀长期接口

### 目标

当前三阶段完成后，系统大概率仍会存在若干兼容桥。可选的第四阶段才适合做更彻底的长期清理。

### 可做内容

- 清理 turn worker 中遗留的过时兼容路径
- 收敛 reducer result 与 context 中重复字段
- 进一步抽象 planner input view
- 梳理 risk semantic state 与 risk decision history 的长期结构

### 为什么不放在下一步

因为这一步更多是“结构收尾”，而不是“当前批问题闭环的必要前提”。

---

## 每阶段之间的依赖关系

### Phase 1 -> Phase 2

Phase 2 需要 complaint memory 先稳定，否则 correction target resolution 没有可靠参照系。

### Phase 2 -> Phase 3

Phase 3 需要 correction consumption 收回 state 层，否则 risk concern 会继续读到抖动 complaint 与抖动 slot state。

### Phase 3 -> Phase 4

Phase 4 只有在 risk concern 已作为稳定状态存在后，才值得做清理与长期接口收敛。

---

## 实现窗口的推荐执行策略

如果后续实现窗口要照这份计划执行，最稳的做法是：

1. 先按 Phase 1 拆 observation 组件 + complaint policy
2. 验腹痛保护样本
3. 再按 Phase 2 收 correction target / correction application
4. 再验腹痛保护样本
5. 最后按 Phase 3 建 risk semantic layer 并调整 planner 输入
6. 重点验胸部风险样本

不要跨阶段混做。比如一边还没稳 complaint memory，一边就去大动 risk planner，这样最容易把问题重新搅回一起。

---

## 这份迁移计划真正想防止什么

它不是为了限制重构，而是为了防止三种很常见的失败：

### 失败 1：第一步就想一口气重写全部 worker/reducer/planner

结果往往是：范围爆炸、验证困难、回滚成本高。

### 失败 2：只抽函数，不改 source of truth

结果往往是：代码看起来模块化了，但 complaint/correction/risk 的真实耦合一点没减。

### 失败 3：被胸部链样本催着直接修 risk 表象

结果往往是：暂时多过几个 case，但把 unresolved risk 和主诉继续绑死。

这份计划的意义，就是让实现窗口知道：
- 先稳什么
- 再拆什么
- 最后碰什么
- 为什么必须这样排顺序
