# Acceptance Report

## 1. 行为结果概览

本轮验收的是最新 residual regression cleanup cut，不沿用上一版 SlotManager / TurnComplaintSemanticsCoordinator recovery cut 的结论。

本轮最新开发声明的目标是：

- 保留 judged artifact closure
- 收掉最后一个胸部 residual：`TRIAGE-EXT-V2-035` 的 `primaryComplaint -> null`
- 不让腹痛保护线或胸部 `034 / 048 / 049` 回退
- 结构上只补 complaint entry path：`胸口有点不舒服 -> 胸部不适`

验收结论：**本轮目标达成。**

当前 actual judged artifacts 中：

- `TRIAGE-EXT-V2-035` 不再出现：
  - `expected primaryComplaint=胸部不适, actual=null`
- 腹痛保护线不再重新出现大面积 `primaryComplaint=null`
- 胸部 `034 / 048 / 049` 没有重新掉空
- judged artifact closure 仍然保留

腹痛保护样本当前状态：

- `TRIAGE-EXT-V2-031` PASS
- `TRIAGE-EXT-V2-032` PASS
- `TRIAGE-EXT-V2-033` FAIL
- `TRIAGE-EXT-V2-041` FAIL
- `TRIAGE-EXT-V2-045` FAIL
- `TRIAGE-EXT-V2-046` PASS
- `TRIAGE-EXT-V2-047` FAIL

其中所有腹痛样本均未再出现 `primaryComplaint=腹痛, actual=null` 的 regression failure。

腹痛线剩余 FAIL 仍是旧问题：

- `033 / 047`：planner 仍选 `PAIN_SEVERITY` 而不是 `VOMITING_PRESENCE`
- `041 / 045`：`suppressedGaps` 仍未包含 `BODY_PART`

胸部观察样本当前状态：

- `TRIAGE-EXT-V2-034` FAIL
- `TRIAGE-EXT-V2-035` FAIL
- `TRIAGE-EXT-V2-048` FAIL
- `TRIAGE-EXT-V2-049` FAIL

但这些胸部样本均未再出现新增的 `primaryComplaint -> null` regression。其中 `035` 的 markdown 已显示：

- `Understanding`
  - `intent=ANSWER_SLOT`
  - `primaryComplaint=胸部不适`

`035` 仍 FAIL，但失败原因已转回 planner 旧问题：

- `expected selectedGaps contains DYSPNEA_PRESENCE`

这不属于本轮 residual cleanup cut 要收的 `primaryComplaint -> null` 回归。

## 2. Turn 层观察

### `TRIAGE-EXT-V2-035` residual 是否消失

结论：**消失了。**

我直接检查了 actual emitted judged JSON / markdown。

`TRIAGE-EXT-V2-035` 当前：

- 不再有 `primaryComplaint` 相关 failed check
- judged markdown 的 passed checks 中明确显示：
  - `primaryComplaint=胸部不适`

也就是说，本轮开发声称的 residual 根因：

- 首轮文本 `胸口有点不舒服` 没有被识别为 `胸部不适`

已经被这次 entry path fix 收掉。

### 腹痛保护线是否重新大面积掉空

结论：**没有。**

我检查了：

- `TRIAGE-EXT-V2-031`
- `TRIAGE-EXT-V2-032`
- `TRIAGE-EXT-V2-033`
- `TRIAGE-EXT-V2-041`
- `TRIAGE-EXT-V2-045`
- `TRIAGE-EXT-V2-046`
- `TRIAGE-EXT-V2-047`

这些样本均未再出现：

- `expected primaryComplaint=腹痛, actual=null`

说明上一轮已经恢复的腹痛保护线没有被本轮 `ComplaintFallbackResolver` entry path 修改打坏。

### 胸部 `034 / 048 / 049` 是否重新掉空

结论：**没有。**

我检查了：

- `TRIAGE-EXT-V2-034`
- `TRIAGE-EXT-V2-048`
- `TRIAGE-EXT-V2-049`

这些样本均未出现 `primaryComplaint` 相关 failed check。

这说明：

- `胸口闷 / 胸闷 -> 胸闷` 没被打坏
- `胸口不舒服 -> 胸部不适` 没被打坏
- 本轮新增 `胸口有点不舒服 -> 胸部不适` 没有破坏已恢复胸部路径

## 3. Reducer / state 层观察

### actual judged JSON 是否仍有 complaint-truth fields

结论：**有。**

我直接检查了：

- `resources/eval/outputs/triage-regression-extended-judged-report.json`

样本 `TRIAGE-EXT-003` 顶层仍包含：

- `reducerComplaintTruth`
- `historyFinalPrimaryComplaint`
- `historyReducerComplaintTruth`
- `complaintTruthSynchronized`

对应值仍然存在：

- `reducerComplaintTruth = 腹痛`
- `historyFinalPrimaryComplaint = 腹痛`
- `historyReducerComplaintTruth = 腹痛`
- `complaintTruthSynchronized = true`

在本轮重点样本中，例如：

- 腹痛保护线 `031 / 032 / 033 / 041 / 045 / 046 / 047`
- 胸部线 `034 / 035 / 048 / 049`

顶层 complaint-truth fields 也仍存在。

### actual judged markdown 是否仍有 complaint-truth lines

结论：**有。**

我直接检查了：

- `resources/eval/outputs/triage-regression-extended-judged-report.md`

全文仍可直接找到：

- `Reducer Complaint Truth`
- `History Final Primary Complaint`
- `History Reducer Complaint Truth`
- `Complaint Truth Synchronized`

`TRIAGE-EXT-V2-035` 的 markdown 中也直接打印了：

- `Reducer Complaint Truth: 胸部不适`
- `History Final Primary Complaint: 胸部不适`
- `History Reducer Complaint Truth: 胸部不适`
- `Complaint Truth Synchronized: true`

说明 judged artifact closure 在本轮 residual cleanup 后仍然保留。

### acceptance 是否仍可直接从 emitted artifacts 核对 final complaint 与 reducer truth

结论：**可以。**

因为 actual judged artifacts 中仍然显式提供：

- `reducerComplaintTruth`
- `historyFinalPrimaryComplaint`
- `historyReducerComplaintTruth`
- `complaintTruthSynchronized`

acceptance 不需要依赖结构推断或 `PRIMARY_SYMPTOM` 代理值来验证 single-path claim。

## 4. 结构审查结论

### 是否只补 complaint entry path

结论：**是。**

本轮直接修改集中在：

- `ComplaintFallbackResolver.java`
- `ComplaintFallbackResolverCompatibilityFallbackTest.java`

实际代码变化是：

- 在 `resolvePrimaryComplaint(...)` 的胸部不适词形中加入：
  - `胸口有点不舒服`

也就是说，它把：

- `胸口有点不舒服 -> 胸部不适`

接入了已有：

- `胸口不舒服 -> 胸部不适`
- `胸部不适 -> 胸部不适`
- `胸前不适 -> 胸部不适`

这符合 development report 声称的最小 entry path fix。

### 是否没有继续扩大 `TurnComplaintSemanticsCoordinator.complaintFromContext()`

结论：**没有扩大。**

我读取了 `TurnComplaintSemanticsCoordinator.java`。

`complaintFromContext()` 仍只从上一轮恢复后的两个来源回填：

1. `context.slotState.PRIMARY_SYMPTOM`
2. `context.finalPrimaryComplaint`

本轮没有新增：

- 更多历史 turn understanding 来源
- reducer complaint truth 来源
- planner / risk / suppressed gap 来源
- 多跳 complaint 推断来源

这说明本轮没有借 `035` residual 继续扩大 carry-forward 角色。

### 是否没有扩到 risk / planner / complaint memory 主线

结论：**没有明显扩出去。**

本轮未修改：

- risk semantic layer
- planner / selectedGaps / suppressedGaps 主线
- complaint memory ownership 主线
- judged artifact writer / support / generator contract
- `SlotManager`
- `TurnComplaintSemanticsCoordinator`

剩余 FAIL 也仍主要是旧有 planner / risk 问题，不是本轮 entry path fix 新引入的问题。

### 是否是假进展

结论：**不是。**

不是假进展的原因：

1. `035` 的 `primaryComplaint -> null` residual 确实消失
2. 已恢复的腹痛线没有回退
3. 已恢复的胸部 `034 / 048 / 049` 没有回退
4. judged artifact closure 没有丢
5. 结构改动确实集中在 complaint entry path

## 5. 最终判断

**Success**

原因：

1. `TRIAGE-EXT-V2-035` residual 已收掉
   - 不再出现 `expected primaryComplaint=胸部不适, actual=null`
   - markdown passed checks 中可见 `primaryComplaint=胸部不适`

2. 已恢复路径没有回退
   - 腹痛保护线没有重新大面积 `primaryComplaint=null`
   - 胸部 `034 / 048 / 049` 没有重新掉空

3. judged artifact closure 保留
   - actual judged JSON 仍有 complaint-truth fields
   - actual judged markdown 仍有 complaint-truth lines

4. 修复足够窄
   - 只补 `ComplaintFallbackResolver` 的 chest complaint entry path
   - 没有继续扩大 `TurnComplaintSemanticsCoordinator.complaintFromContext()`
   - 没有扩到 risk / planner / complaint memory 主线

因此，本轮 residual regression cleanup cut 达成目标，应判：**Success**

## 6. 下一条最该继续收的边界

- **本轮 residual cleanup 已收口；下一步如果继续推进，应不要再回到 primaryComplaint 回归线，而是另起任务处理剩余旧问题，例如 planner 对 `DYSPNEA_PRESENCE` / `VOMITING_PRESENCE` 的 selectedGaps 选择，以及 `034 / 049` 的 riskDecision 语义问题。**
