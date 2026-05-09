# Triage Eval v2 验证窗口独立核验报告

## 核验范围与边界

本报告基于当前代码、当前已生成的 eval 输出，以及本轮独立结构审查形成。

本轮严格遵守以下边界：

- 不改业务代码
- 不改 judge
- 不改 report writer
- 不改 normalizer 逻辑
- 不改 case json
- 不为验证新增日志型逻辑
- 不顺手修 planner / risk / reducer / worker
- 发现问题只报告，不实现

本次验证重点围绕以下 case：

### 第一组：必须核验

- `TRIAGE-EXT-V2-031`
- `TRIAGE-EXT-V2-032`
- `TRIAGE-EXT-V2-033`
- `TRIAGE-EXT-V2-041`
- `TRIAGE-EXT-V2-045`
- `TRIAGE-EXT-V2-046`
- `TRIAGE-EXT-V2-047`

### 第二组：补充核验

- `TRIAGE-EXT-V2-034`
- `TRIAGE-EXT-V2-035`
- `TRIAGE-EXT-V2-048`
- `TRIAGE-EXT-V2-049`

---

## 独立验证说明

本轮我做了两类验证：

1. **行为面**：读取当前 `triage-regression-extended-judged-report.json/.md` 中上述目标 case 的 observed 结果，逐层核对 understanding / reducer / planner / history / riskDecision。
2. **结构面**：直接审查以下实现边界：
   - `TurnUnderstandingWorker`
   - `CorrectionPhraseParser`
   - `CorrectionTargetResolver`
   - `ReducerCorrectionApplier`
   - `StateReducer`

另外，我尝试通过测试入口独立复跑：

```bash
.\mvnw.cmd -pl bootstrap -Dtest=TriageEvalRunnerTest#shouldGenerateRegressionExtendedJudgementReportIntoFixedOutputsDirectory test
```

结果：

- **未完成独立 rerun**
- 失败原因不是业务断言失败，而是测试编译失败
- 失败点位于 `TurnUnderstandingWorkerSemanticBoundaryTest` 仍调用旧构造器，导致当前测试面与实现面未完全同步

因此，本报告的行为判断以**当前已生成的 judged report 输出**为主，而不是基于一轮新的成功 rerun。

这个偏差需要明确记录，不能写成“本轮已成功独立复跑”。

---

## 1. 行为结果概览

### 1.1 腹痛保护样本整体是否稳定

结论：**整体稳定，无明显退化。**

目标样本结果：

- `031` PASS
- `032` PASS
- `033` FAIL
- `041` FAIL
- `045` FAIL
- `046` PASS
- `047` FAIL

但这里的 FAIL 主要不是 correction 主链退化，而是剩余 planner 表现层问题：

- `041 / 045`：主要卡在 `suppressedGaps` 没把 `BODY_PART` 收进去
- `033 / 047`：主要卡在 planner 仍选择 `PAIN_SEVERITY`，没有按期望 suppress 已答 gap，并改去问 `VOMITING_PRESENCE`

在这组样本里，我没有看到以下退化：

- correction 丢失
- primaryComplaint 异常掉空
- reducer 接不住 correction
- answeredSlots 因 correction 拆分而坏掉

因此从保护线角度看，这一刀没有把腹痛主链打坏。

### 1.2 胸部观察样本整体是否有变化

结论：**没有因为这轮 correction 拆分而显著改善，也没有看到它把胸部链额外打坏。**

目标样本结果：

- `034` FAIL
- `035` FAIL
- `048` FAIL
- `049` FAIL

失败形态主要仍是旧问题残留：

- 胸部主诉延续不稳，尤其 `035`
- planner 没围绕 `DYSPNEA_PRESENCE` 继续组织追问，尤其 `035 / 048`
- riskDecision 的语义命名与期望口径不一致，`034 / 049` 观测值为 `TRIGGER_WARNING`

所以胸部链没有因为这轮变清爽，但也不能把胸部链问题归咎于本次 correction 拆分。

### 1.3 有没有明显退化

结论：**没有看到这轮 correction 第一刀导致的明显行为退化。**

最大的现实问题反而是：

- 测试边界没有完全跟上实现边界
- 导致独立 rerun 在测试编译层被挡住

这削弱了验证闭环的完整性，但它本身不是业务行为退化。

---

## 2. Turn 层观察

### 2.1 correction 识别是否稳定

结论：**稳定。**

腹痛 correction 样本中：

#### `TRIAGE-EXT-V2-031`

- `understanding.intent = CORRECTION`
- `understanding.primaryComplaint = 腹痛`
- `understanding.corrections = BODY_PART|SLOT_VALUE|reject=左下腹|confirm=右下腹`

#### `TRIAGE-EXT-V2-041`

- `understanding.intent = CORRECTION`
- `understanding.corrections = BODY_PART|SLOT_VALUE|confirm=右下腹`

#### `TRIAGE-EXT-V2-045`

- `understanding.intent = CORRECTION`
- `understanding.corrections = BODY_PART|SLOT_VALUE|reject=左下腹|confirm=右下腹`

说明：

- correction 仍能被识别
- 至少在这批重点样本里，主诉 correction 与普通 slot correction 仍能区分
- `BODY_PART` 没被误拍成 `PRIMARY_COMPLAINT`

### 2.2 `primaryComplaint` 是否仍然合理

#### 腹痛保护样本

结论：**稳定。**

- `031 / 032 / 033 / 041 / 045 / 046 / 047` 的 `understanding.primaryComplaint` 都保持为 `腹痛`
- 对应 `history.finalPrimaryComplaint` 也保持为 `腹痛`

说明 correction 拆分后，没有把腹痛主链的主诉延续搞丢。

#### 胸部观察样本

结论：**仍不稳定。**

- `034`：`understanding.primaryComplaint = 胸闷`
- `048`：`understanding.primaryComplaint = 胸部不适`
- `049`：`understanding.primaryComplaint = 胸闷`
- `035`：`understanding.primaryComplaint = null`

这说明：

- 胸部链的问题还在
- 但它更像 follow-up carry / 主诉 truth 收口未完成
- 不是 correction 识别本身坏了

### 2.3 `answeredSlots` 是否受影响

结论：**未见 correction 拆分带来的副作用。**

腹痛线：

- `032 / 046`：`FEVER_PRESENCE=NO|ABSENT`
- `033 / 047`：一次性产出
  - `BODY_PART=右下腹`
  - `DURATION=昨晚开始`
  - `FEVER_PRESENCE=NO`
  - `NAUSEA_PRESENCE=NO`

胸部线：

- `034 / 049`：`DYSPNEA_PRESENCE=YES|PRESENT`
- `035 / 048`：`DYSPNEA_PRESENCE=UNKNOWN|UNKNOWN`

说明：

- answeredSlots 仍能正常产出
- correction 拆开后，没有把 follow-up answer 识别链打坏

### 2.4 Turn 层是否还残留 worker 承担过多职责的痕迹

结论：**有，而且很明显。**

`TurnUnderstandingWorker` 目前仍同时承担：

- complaint carry
- slot answer inference
- risk signal inference
- correction assembly
- intent repair

并且：

- `assembleCorrection(...)` 仍在 worker 内部
- `repairIntent(...)` 仍在 worker 内部对 correction / answer / new complaint 做最终 turn-level intent 拍板
- `CorrectionTargetResolver` 仍依赖 worker 的 `this::inferSlot`

所以 Turn 层虽然拆出了 parser / resolver，但 worker 还没真正瘦下来。

---

## 3. Reducer / state 层观察

### 3.1 correction 是否仍正确落地

结论：**是。**

腹痛 correction 样本：

#### `TRIAGE-EXT-V2-031`

- reducer `BODY_PART = 右下腹`
- status `CORRECTED`
- `correctionCount = 1`

#### `TRIAGE-EXT-V2-041`

- reducer `BODY_PART = 右下腹`
- `correctionCount = 1`

#### `TRIAGE-EXT-V2-045`

- reducer `BODY_PART = 右下腹`
- `correctionCount = 1`

这不是 understanding 层单纯打标签，而是 reducer 真正写入了最终状态。

### 3.2 correction history 是否仍一致

结论：**更一致，而且 owner 更明确地偏向 reducer。**

从 `StateReducer` 与 `ReducerCorrectionApplier` 的实现看：

- `StateReducer` 会复制并延续 `correctionLog`
- `ReducerCorrectionApplier.apply(...)` 会先把 correction 写入 `correctionLog`
- 然后再根据 target 更新 `reducedSlots / answeredSlots / pendingCandidates`

这说明 correction history 的承载层已更清晰地落到 reducer，而不是停在 worker 的临时理解结果里。

### 3.3 主诉 correction 与 slot correction 是否都还能稳定落地

结论：

- **slot correction：证据充分，成立**
- **主诉 correction：从实现上支持，但本轮目标样本证据不如 slot correction 充分**

本轮强证的是 `BODY_PART` 这一类 slot correction 已稳定落地。

### 3.4 是否出现“turn 里看着对，但 reducer 没接住”的情况

结论：**本轮重点样本里没看到这种断裂。**

对于 `031 / 041 / 045`：

- understanding 的 correction 是对的
- reducer 最终也接住了
- 没出现 correction 停留在 turn 层、state 没更新的情况

### 3.5 reducer 是否更明确成为 session truth 持有层

结论：**比之前更明确，但还没有完全收口。**

成立的部分：

- correction final apply 已进入 `ReducerCorrectionApplier`
- `StateReducer` 持有 `reducedSlots / answeredSlots / pendingCandidates / correctionLog`
- correction 的最终落状态动作不再挂在 worker 上

没收干净的部分：

- `StateReducer` 仍直接 `mergePrimaryComplaint(... understanding.getPrimaryComplaint())`
- 也就是说，worker 产出的 `primaryComplaint` 仍会直接写进 reducer truth

因此 reducer 更像 truth holder 了，但 `primaryComplaint` 的 truth 还没有完全从 worker 手里收回来。

---

## 4. 结构审查结论

### 4.1 `TurnUnderstandingWorker` 是否真的不再负责 correction 的最终拍板

结论：**部分成立，不是完全成立。**

成立的部分：

- 它不再自己把 correction 直接落到最终 session state
- correction 的最终状态写入已下沉到 `ReducerCorrectionApplier`

不成立的部分：

- 它仍负责 `assembleCorrection(...)`
- 它仍决定何时走 correction 解析链
- 它仍在 `repairIntent(...)` 中决定 turn-level intent 是否定为 `CORRECTION`
- 它仍把 `explicitComplaint` 作为 resolver 输入，影响 correction target 的走向

所以更准确的说法是：

> worker 退出了 correction 的最终落状态职责，但没有完全退出 correction 的最终 turn-level 拍板职责。

### 4.2 `CorrectionPhraseParser` 是否真的只做 parsing

结论：**基本成立。**

它现在主要做：

- `hasCorrectionCue(text)`
- `reject(text)`
- `confirm(text)`
- 返回 `ParsedCorrectionPhrase`

它不读 state，不碰 slot state，不改 context。

这一层边界是成立的。

### 4.3 `CorrectionTargetResolver` 是否真的承担了 state-aware resolution

结论：**成立，而且这是这轮最实质的结构进步之一。**

它现在明确承担：

- `resolveFromSlotState(...)`
- `resolveFromAskedSlots(...)`
- fallback 到 `explicitComplaint`
- ambiguous match 时返回 `UNKNOWN`

这说明：

- target resolution 确实从 worker 中抽出来了
- 而且它做的是带 state 意识的 resolution，不是纯字符串拍脑袋

这不是换名字式拆分。

### 4.4 `ReducerCorrectionApplier` 是否真的成为 correction 最终落点

结论：**成立。**

它现在明确负责：

- 写 `correctionLog`
- 确定 corrected slot
- 更新 `reducedSlots`
- 更新 `answeredSlots`
- 移除 `pendingCandidates`

这是 correction 真正的最终状态落点。

### 4.5 reducer 是否比之前更明确地成为 session truth 的持有层

结论：**更明确了，但还不能说完全收口。**

原因：

- correction final apply 已下沉到 reducer，这是真变化
- 但 `primaryComplaint` 仍高度依赖 worker 先给出结果
- reducer 目前像是“更强的 truth holder”，还不是“唯一干净的 truth owner”

### 4.6 哪些地方仍然是假拆分

我认为主要有三处残留：

#### 1. `TurnUnderstandingWorker` 仍然过厚

它还同时负责：

- complaint carry
- slot inference
- risk inference
- correction assembly
- intent repair

说明 worker 仍是一个厚 orchestration + inference 混合体。

#### 2. `CorrectionTargetResolver` 的底层能力还挂在 worker 上

resolver 虽独立了，但它仍依赖：

- `this::inferSlot`

也就是说：

- resolution 边界拆出来了
- 但 slot normalization / slot semantic inference 的能力没有真正独立出来

这是一种“半收口”。

#### 3. `primaryComplaint` truth 仍没彻底回到 reducer / memory 层

虽然 correction apply 已经下沉，
但 `primaryComplaint` 仍通过 `understanding.getPrimaryComplaint()` 直接进入 reducer。

这意味着主诉 truth 的 source of truth 还没有完全从 worker 脱钩。

---

## 5. 总结判断

### B. 这轮部分成功

理由如下：

#### 行为面

- 腹痛保护样本整体稳定，没有看到 correction 第一刀导致的明显退化
- 胸部观察样本没有因本轮 correction 拆分而额外恶化
- 但胸部链原有问题也没有因为这轮结构变化而得到明显改善

#### 结构面

- `CorrectionPhraseParser` 的 parsing 边界基本成立
- `CorrectionTargetResolver` 确实承担了 state-aware resolution
- `ReducerCorrectionApplier` 确实成为 correction 最终落点
- 这说明本轮不是单纯私有方法改名，不是纯表面拆分

#### 仍未完全成功的原因

- `TurnUnderstandingWorker` 仍然过厚
- correction 的 turn-level 拍板仍未完全从 worker 手里退出
- resolver 仍依赖 worker 的 `inferSlot`
- reducer 更像 truth holder 了，但 `primaryComplaint` truth 还没完全收回来
- 同时，当前独立 rerun 又被测试编译错误挡住，说明验证面没有完全跟上实现面

因此，这轮不能判为“完全成功”，但也不能判为“失败”。

---

## 6. 下一步建议

只给一条结构上最该继续收的边界：

- **继续把 `TurnUnderstandingWorker` 里的 slot inference / answer inference 能力往外收，至少让 `CorrectionTargetResolver` 不再依赖 `this::inferSlot`，并进一步削弱 worker 对 `primaryComplaint` 与 correction turn-level 拍板的绑定。**

这是下一步最关键的一刀，因为当前真正没收干净的，不是 applier，而是 worker 仍像一个总控大脑。
