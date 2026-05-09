# Triage Regression 剩余 14 条未过项说明

## 这份文档是干什么的

这份文档用于解释：

- 当前最新 judged regression 为什么还是有 14 条没过
- 这 14 条分别差在哪里
- 哪些属于真正影响验收的大问题
- 哪些更像严格规则下的尾差

当前最新 judged 结果：

- Total: 23
- Passed: 9
- Failed: 14

这份结论基于最新重刷后的：

- `resources/eval/outputs/triage-regression-judged-report.json`
- `resources/eval/outputs/triage-regression-judged-report.md`

---

## 先讲结论

这 14 条没过，**并不等于 14 条都很差**。

更准确地说：

- 有几条属于真正还需要优先修的结构化能力问题
- 但更多条目其实已经把主行为做对了，只剩 1 个细规则点没满足

也就是说，当前 triage 已经不是“完全不会”的状态，而是：

> 很多 case 的主行为已经成立，但 strict judged regression 还在抓结构化状态、风险提示、对话记忆这些更细的质量要求。

---

## judge 是怎么判 fail 的

当前 regression 不是人工看个大概，而是按 case 里的 `expected` 和 `forbidden` 做逐项判断。

每条 case 会检查：

- `nextAction`
- `slotValues`
- `finalSlotValues`
- `slotStatuses`
- `lastAskedSlots`
- `pendingSlots`
- `riskLevel`
- `questionPlan`
- `finalReply` 是否承认信息不足
- facts 里的 polarity
- risk hints 是否出现在回复里
- forbidden 规则是否命中

规则很严格：

> 只要某个 case 还有 1 条 `failedChecks`，这一条 case 就算没过。

所以当前很多 fail 的真实含义是：

- 不是完全不会
- 而是“主行为已对，但严格规则还差一个点”

---

## 14 条未过项总览

### 一、negation 记忆回写类

1. `TRIAGE-NEG-001`
2. `TRIAGE-NEG-002`
3. `TRIAGE-NEG-003`
4. `TRIAGE-NEG-004`
5. `TRIAGE-NEG-005`

### 二、多轮补槽动作过激类

6. `TRIAGE-MULTI-001`
7. `TRIAGE-MULTI-004`

### 三、高风险结构化/解释类

8. `TRIAGE-RISK-002`
9. `TRIAGE-RISK-004`
10. `TRIAGE-RISK-005`

### 四、weak input 结构化状态外显类

11. `TRIAGE-WEAK-001`
12. `TRIAGE-WEAK-002`
13. `TRIAGE-WEAK-003`
14. `TRIAGE-WEAK-004`

---

## 逐条解释

---

### 1. TRIAGE-NEG-001

**用户输入**：`不发烧`

**已经做对的部分**：

- `nextAction=ASK_CLARIFICATION`
- `FEVER_PRESENCE=NO`
- `pendingSlots` 已经去掉 `FEVER_PRESENCE`
- 没把发热误判成阳性
- 没重复追问发热

**没过的原因**：

- `expected lastAskedSlots contains FEVER_PRESENCE, actual=[PRIMARY_SYMPTOM, DURATION]`

**本质**：

系统已经理解了“否定发热”，但没有把这轮回答正确记录成“我刚回答的是 FEVER_PRESENCE”。

**问题级别**：小问题

---

### 2. TRIAGE-NEG-002

**用户输入**：`没发热`

**已经做对的部分**：

- `FEVER_PRESENCE=NO`
- `pendingSlots` 去掉了 `FEVER_PRESENCE`
- 没重复追问发热

**没过的原因**：

- `expected lastAskedSlots contains FEVER_PRESENCE, actual=[PRIMARY_SYMPTOM, DURATION]`

**本质**：

和 `TRIAGE-NEG-001` 相同，属于补槽成功但对话记忆回写不对。

**问题级别**：小问题

---

### 3. TRIAGE-NEG-003

**用户输入**：`没有恶心`

**已经做对的部分**：

- `NAUSEA_PRESENCE=NO`
- `pendingSlots` 去掉了 `NAUSEA_PRESENCE`
- 没重复追问恶心

**没过的原因**：

- `expected lastAskedSlots contains NAUSEA_PRESENCE, actual=[PRIMARY_SYMPTOM, DURATION]`

**本质**：

已经理解用户是否认恶心，但没有把这轮回答的槽位记成 `NAUSEA_PRESENCE`。

**问题级别**：小问题

---

### 4. TRIAGE-NEG-004

**用户输入**：`不吐`

**已经做对的部分**：

- `VOMITING_PRESENCE=NO`
- `pendingSlots` 去掉了 `VOMITING_PRESENCE`
- 没重复追问呕吐

**没过的原因**：

- `expected lastAskedSlots contains VOMITING_PRESENCE, actual=[PRIMARY_SYMPTOM, DURATION]`

**本质**：

补槽正确，但回答槽位的状态回写不正确。

**问题级别**：小问题

---

### 5. TRIAGE-NEG-005

**用户输入**：`没拉肚子`

**已经做对的部分**：

- `nextAction=ASK_CLARIFICATION`
- 没把腹泻误当成阳性
- 没把它误当成新的主诉

**没过的原因**：

- `expected factPolarityHint evidence=没拉肚子 polarity=NEGATIVE`

**本质**：

facts 层没有把“没拉肚子”稳定沉成一个 `NEGATIVE` polarity 的 fact。

这影响：

- 事实层可解释性
- 规则消费的一致性
- 语义证据沉淀质量

**问题级别**：中等问题

---

### 6. TRIAGE-MULTI-001

**场景**：

多轮连续补槽：

- 肚子疼
- 右下腹
- 今天开始的
- 没发热

**已经做对的部分**：

- `PRIMARY_SYMPTOM=腹痛`
- `BODY_PART=右下腹`
- `DURATION=今天开始`
- `FEVER_PRESENCE=NO`
- 对应 pending 都已收敛
- 没把后续回答误当新主诉
- 没重复问已填槽位

**没过的原因**：

- `expected nextAction=ASK_CLARIFICATION, actual=TRIGGER_WARNING`

**本质**：

多轮补槽本身已经成立，但系统在这个腹痛场景里动作过激，提前升级成 warning。

**问题级别**：中等问题

---

### 7. TRIAGE-MULTI-004

**用户输入**：`没发热`

**上下文**：腹痛 follow-up

**已经做对的部分**：

- `FEVER_PRESENCE=NO`
- `pendingSlots` 已去掉 `FEVER_PRESENCE`
- 没误加阳性发热
- 没把这轮回答当作新主诉

**没过的原因**：

- `expected nextAction=ASK_CLARIFICATION, actual=TRIGGER_WARNING`

**本质**：

和 `TRIAGE-MULTI-001` 类似，槽位已经补对，但风险动作过激。

**问题级别**：中等问题

---

### 8. TRIAGE-RISK-002

**用户输入**：`大出血`

**已经做对的部分**：

- `nextAction=TRIGGER_WARNING`
- `riskLevel=HIGH`
- 没走 routine follow-up
- 没把 bleeding 降级

**没过的原因**：

- `expected slotValues contains BLEEDING_PRESENCE=YES, actual=null`

**本质**：

系统已经知道这是高风险，需要 warning，但没有把“大出血”稳定结构化成 `BLEEDING_PRESENCE=YES`。

这会影响：

- 风险原因结构化沉淀
- 下游规则判断
- 报告解释性

**问题级别**：大问题

---

### 9. TRIAGE-RISK-004

**用户输入**：`抽搐`

**已经做对的部分**：

- `nextAction=TRIGGER_WARNING`
- `riskLevel=HIGH`
- 没走 routine follow-up
- 没出现“完全不 warning”的问题

**没过的原因**：

- `expected riskHintsContains SEIZURE`

**本质**：

warning 动作已经对了，但回复里没有明确带出“抽搐/惊厥”这类具体风险提示，而是用了更泛化的红旗说明。

**问题级别**：中等到大问题

---

### 10. TRIAGE-RISK-005

**用户输入**：`怀孕还见红`

**已经做对的部分**：

- `nextAction=TRIGGER_WARNING`
- `riskLevel=HIGH`
- 没走 routine gyne query
- 回复里已经知道“妊娠相关出血属于高危场景”

**没过的原因**：

- `expected slotValues contains PREGNANCY_STATUS=YES, actual=null`
- `expected slotValues contains BLEEDING_PRESENCE=YES, actual=null`

**本质**：

系统知道这是高危妊娠出血，但没有把“怀孕”和“出血”两个关键结构化槽位稳定落出来。

**问题级别**：大问题

---

### 11. TRIAGE-WEAK-001

**用户输入**：`不舒服`

**已经做对的部分**：

- `nextAction=ASK_CLARIFICATION`
- question plan 合理
- `askCount=2`
- reply 承认信息不足
- 没 invent 主诉
- 没直接生成报告

**没过的原因**：

- `expected slotStatuses contains PRIMARY_SYMPTOM=UNKNOWN, actual=null`

**本质**：

行为上已经把它当作信息不足处理，但结构化状态没有显式外显 `PRIMARY_SYMPTOM=UNKNOWN`。

**问题级别**：小问题

---

### 12. TRIAGE-WEAK-002

**用户输入**：`难受`

**已经做对的部分**：

- `ASK_CLARIFICATION`
- question plan 合理
- `askCount=2`
- reply 承认信息不足
- 没 invent body part / duration / severity

**没过的原因**：

- `expected slotStatuses contains PRIMARY_SYMPTOM=UNKNOWN, actual=null`

**本质**：

行为已经符合“弱输入继续澄清”，但结构化 UNKNOWN 状态没有外显。

**问题级别**：小问题

---

### 13. TRIAGE-WEAK-003

**用户输入**：`有问题`

**已经做对的部分**：

- `ASK_CLARIFICATION`
- question plan 合理
- reply 承认信息不足
- 没假设疾病
- 没假设已有诊断

**没过的原因**：

- `expected slotStatuses contains PRIMARY_SYMPTOM=UNKNOWN, actual=null`

**本质**：

和前两条相同，缺的是结构化 UNKNOWN 状态外显。

**问题级别**：小问题

---

### 14. TRIAGE-WEAK-004

**用户输入**：`状态不太对`

**已经做对的部分**：

- `ASK_CLARIFICATION`
- question plan 合理
- `askCount=2`
- reply 承认信息不足
- 没 invent 主诉
- 没问太多

**没过的原因**：

- `expected slotStatuses contains PRIMARY_SYMPTOM=UNKNOWN, actual=null`

**本质**：

行为已经像 UNKNOWN，但结构化状态没有显式给出来。

**问题级别**：小问题

---

## 哪些更影响验收

### 优先关注的大问题

#### 1. 高风险结构化未落槽

- `TRIAGE-RISK-002`
- `TRIAGE-RISK-005`

原因：

- warning 已经触发
- 但关键红旗原因没有稳定落成结构化 slot
- 会影响风险解释、规则消费和下游稳定性

#### 2. 红旗解释还不够具体

- `TRIAGE-RISK-004`

原因：

- 已经 warning
- 但回复里没有把“抽搐/惊厥”明确点出来

#### 3. fact polarity 沉淀不稳

- `TRIAGE-NEG-005`

原因：

- 会影响语义证据层质量
- 也会影响规则层对否定事实的可靠消费

---

## 哪些更像尾差，不太阻断验收

### 1. negation 记忆回写类

- `TRIAGE-NEG-001`
- `TRIAGE-NEG-002`
- `TRIAGE-NEG-003`
- `TRIAGE-NEG-004`

原因：

- 主行为已经对了
- 差在 `lastAskedSlots` 状态回写

### 2. weak input UNKNOWN 外显类

- `TRIAGE-WEAK-001`
- `TRIAGE-WEAK-002`
- `TRIAGE-WEAK-003`
- `TRIAGE-WEAK-004`

原因：

- 已经会继续澄清
- 只是没有显式输出 `PRIMARY_SYMPTOM=UNKNOWN`

### 3. 多轮动作偏激类

- `TRIAGE-MULTI-001`
- `TRIAGE-MULTI-004`

原因：

- 多轮补槽已经基本成立
- 只是 action 比预期更激进

---

## 当前最准确的验收口径

如果只看这 14 条没过项，可以得出一个更准确的结论：

> 当前 triage 已经不是“完全不会”的笨蛋状态。
> 很多 case 的主行为已经成立。
> 当前剩余未过项里，真正值得优先修的，是高风险结构化槽位、个别红旗解释、以及否定 fact polarity 这几类问题；其余相当一部分属于严格 judged regression 下的尾差。

---

## 给开发同学的一句话版本

> 现在剩余 14 条未过项里，很多不是主行为错了，而是严格规则下的结构化尾差；真正优先修的是高风险场景下的结构化落槽、个别风险提示具体化，以及 negation 的 fact polarity 沉淀。
