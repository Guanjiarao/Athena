# 06-validation-and-guardrails

## 目标

这份文档不讨论怎么设计，而讨论怎么判断重构是不是做对了。

因为结构重构最容易出现的假象是：
- 类变多了
- 函数名更好看了
- 通过率也许短期没掉

但实际上 source of truth 没变，系统仍然只是把原来的混乱搬到了更多文件里。

所以这里要定义的是：
- 每阶段该看哪些保护样本
- 该看哪些结构信号
- 如何判断没有重新走回 v1 规则泥球
- 如何判断模块化是真的发生了

---

## 一、基础保护样本

### 腹痛保护样本

必须持续观察：
- 031
- 032
- 033
- 041
- 045
- 046
- 047

### 这些样本主要保护什么

- follow-up answer resolution
- complaint correction
- final primary complaint persistence
- slot-level correction
- complaint carry 的稳定性

这些样本之所以重要，不是因为它们覆盖所有问题，而是因为它们能防止“为了修胸部风险链，把已经稳定下来的 complaint/correction 主线又打回去”。

### 胸部风险样本

必须重点观察：
- 034
- 035
- 048
- 049

### 这些样本主要保护什么

- risk signal detection 与 complaint context 的衔接
- risk semantic closure
- unresolved risk follow-up target
- planner 是否继续追风险对象而不是退回主诉澄清

---

## 二、Phase 1 的验证重点

Phase 1 的目标是：
- 把 observation parsing 从大 worker 中切出
- 把 complaint carry 收回 reducer
- 停止 worker 直接覆盖主诉

### 应重点看什么

#### 1. 腹痛样本的 `finalPrimaryComplaint` 是否仍稳定

如果 Phase 1 一做就让 final complaint 开始漂，说明 complaint candidate 和 complaint memory 边界没立住。

#### 2. complaint carry 从 worker 挪到 reducer 后是否还能跨轮保持

如果 follow-up 轮里主诉突然丢失，说明 reducer 侧的 complaint policy 还没真正接住 carry 责任。

#### 3. correction phrase 拆出去后，已有 correction 样本是否明显回退

如果 correction 样本一下大退步，通常说明原先 parser 中其实混着 resolution 逻辑，拆分时没有把责任补到正确位置。

### Phase 1 的结构性验收信号

- `TurnUnderstanding.primaryComplaint` 不再体现 carry 结果
- complaint memory 的写入只在 reducer 路径发生
- worker 不再因为 correction 顺手覆盖主诉

这些比单一 case 通过更重要。

---

## 三、Phase 2 的验证重点

Phase 2 的目标是：
- 把 correction target resolution 收回 state-aware resolver
- 把 correction application 收回 reducer

### 应重点看什么

#### 1. 主诉 correction 与 slot correction 是否都能通过同一路径落地

如果两者仍是两套散逻辑，说明结构并没有真正统一。

#### 2. correction target 是否不再依赖 worker 内部临时猜测

如果 worker 仍然在看 state 并拍 final target，说明只是换了函数名。

#### 3. reducer 是否成为 correction history 唯一写入点

如果 correction history 还有旁路写入，后面所有历史一致性都会继续出问题。

### Phase 2 的结构性验收信号

- correction parsing 与 target resolution 已完全分离
- unresolved correction 有显式表达，而不是被硬压成一个 target
- reducer 是 complaint / slot correction 的统一落点

---

## 四、Phase 3 的验证重点

Phase 3 的目标是：
- 建 risk semantic layer
- 让 planner 改为消费它
- 不再让 unresolved risk 回退 `PRIMARY_SYMPTOM`

### 应重点看什么

#### 1. `034/035/048/049` 中，风险动作出现后，planner 是否仍继续追风险 concern

如果 question plan 又开始回头追主诉澄清，说明 risk semantic layer 还没真正被 planner 吃进去。

#### 2. unresolved risk 是否有稳定 follow-up target

这比“有没有 unresolved risk gap”更重要。因为关键不是有无 gap，而是 gap 是否仍然语义上绑定风险 concern。

#### 3. 主诉状态是否不再被胸部风险追问带偏

如果 risk clarification 一出现，`finalPrimaryComplaint` 又开始晃动，说明 complaint memory 与 risk semantic layer 仍然耦合不清。

### Phase 3 的结构性验收信号

- signal / semantic / action 三层在代码与状态上可区分
- planner 不需要再把 risk unresolved 翻译成主诉 gap
- 胸部链中的风险补问不再表现成“重新找主诉”

---

## 五、如何判断“没有重新走回 v1 规则泥球”

这个问题必须明确，因为很多重构表面上是在抽象，实际上只是把补丁分散化了。

### 判断标准 1：新增逻辑是否落在正确层，而不是继续堆大 worker

如果新逻辑主要继续落在：
- `TurnUnderstandingWorker`
- `RiskStratifierWorker`

那即使 case 更通过，也说明结构没有真正改善。

### 判断标准 2：source of truth 是否变单一了

至少要看到：
- complaint truth 来自 complaint memory
- correction target truth 来自 state-aware resolution
- risk unresolved target 不再默认等于主诉槽位

如果这些仍然是双重或三重真相，系统仍然在泥球路线上。

### 判断标准 3：planner 是否吃稳定对象，而不是吃临时观察值

如果 planner 还需要直接依赖 turn-level correction / signal 才能工作，说明 reducer 和 risk semantic 层还没有真正成立。

### 判断标准 4：新增 case 通过是否需要继续 case-driven patch

如果每来一个 case 都得：
- 在 worker 里加一个特判
- 在 risk worker 里再加一个 fallback
- 在 planner 里再塞一个 slot 规则

那就说明结构仍然不深。

---

## 六、如何判断“抽象是真的落成了模块，而不是只是换了函数名”

### 信号 1：读写权限发生了实质变化

例如：
- complaint memory 只有 reducer 能写
- correction history 只有 reducer 能写
- risk semantic state 有明确持有层

如果读写权限没变，只是函数改名，那不算模块化。

### 信号 2：上游只产候选，下游才做 resolution

例如：
- parser 只产 candidate
- resolver 才拍 target
- reducer 才写 session truth

如果 parser/worker 继续顺手做 resolution 或 state writeback，那边界还是没立住。

### 信号 3：替换某个解析策略时，不需要联动改三层逻辑

例如：
- 改 correction phrase parser，不应逼着 planner 也一起改
- 加强 risk signal detection，不应逼着 complaint memory 规则也一起改

如果一个小变动必须横穿所有层，说明模块仍然很浅。

### 信号 4：planner 不再自己发明 session truth

如果 planner 还要自己通过 scattered fields 推断：
- 当前主诉是什么
- correction 最终落在哪
- unresolved risk 该追哪个对象

那它仍然在扮演 resolver，模块边界还是没落地。

---

## 七、建议的验证观察面，不只看最终 nextAction

重构期间不要只看最终输出。应同时观察：

### 1. turn observation 层

看：
- `TurnUnderstanding.primaryComplaint` 是否只反映 candidate
- `answeredSlots` 是否仍合理
- correction phrase 是否被正确识别

### 2. reducer 层

看：
- complaint memory 是否稳定
- `slotState.PRIMARY_SYMPTOM` 是否只是投影
- correction 是否正确落入 history

### 3. risk 层

看：
- signal 是否只是 signal
- concern 是否能稳定持续
- unresolved target 是否仍绑定 risk 对象

### 4. planner 层

看：
- selected gaps
- suppressed gaps
- 是否仍在主诉与风险之间错配

如果只看最终回答，很容易把结构回退掩盖掉。

---

## 八、推荐的阶段性验收节奏

### 每完成一个小步骤，都至少回答四个问题

1. complaint truth 现在是不是更单一了
2. correction 是不是更往 reducer 靠了
3. risk unresolved target 是不是更少回退主诉了
4. planner 是不是更少在补 session truth 了

### 每完成一个 Phase，都至少复核两类样本

- 腹痛保护样本：防止 complaint/correction 回退
- 胸部风险样本：观察 risk/planner 是否真正收口

---

## 九、这份 guardrails 的真正作用

这份文档不是为了增加流程负担，而是为了防止两种非常常见的误判：

### 误判 1：case 过了，所以结构对了

不成立。很多 case-driven patch 也能让 case 过，但结构反而更差。

### 误判 2：类拆了，所以模块化了

也不成立。只有 source of truth、读写权限、resolution 边界真的变了，才叫结构改善。

---

## 十、实现窗口在执行时最应该盯住的三条红线

### 红线 1

不要再让 `TurnUnderstandingWorker` 获得新的 session truth 写入责任。

### 红线 2

不要再让 unresolved risk 默认回退成 `PRIMARY_SYMPTOM`。

### 红线 3

不要为了让某个 case 通过，就临时在多个层同时加 patch。

只要守住这三条，重构就更不容易重新滑回旧路。
