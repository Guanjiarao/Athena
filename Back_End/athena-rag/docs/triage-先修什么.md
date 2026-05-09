# Triage 先修什么（基于 stricter judged regression）

## 这份清单是干什么的

这份文档不是讲架构理想状态，而是给开发同学一个非常直接的结论：

> 如果只看当前外部行为评测结果，接下来最应该先修什么。

排序依据不是主观感觉，而是基于当前 stricter judged regression 的结果：

- `resources/eval/outputs/triage-regression-judged-report.json`
- `resources/eval/outputs/triage-regression-judged-report.md`

当前结果：

- Total: 23
- Passed: 1
- Failed: 22

所以这份清单的目标很简单：

- 先把最危险、最影响用户体验、最能拉高通过率的问题优先收掉
- 不讨论大而全，只讨论“先修什么最值”

---

## 优先级总览

### P0：先修红旗拦截

这是第一优先级，必须最先修。

涉及 case：

- `TRIAGE-RISK-001`
- `TRIAGE-RISK-002`
- `TRIAGE-RISK-003`
- `TRIAGE-RISK-004`
- `TRIAGE-RISK-005`
- `TRIAGE-MILD-004`

当前共性问题：

- 预期 `TRIGGER_WARNING`
- 实际 `ASK_CLARIFICATION`
- 预期 `riskLevel >= HIGH`
- 实际 `riskLevel = null`
- 还会命中：
  - `ROUTINE_FOLLOW_UP_FIRST`
  - `TREAT_AS_ROUTINE_GYNE_QUERY`
  - `TREAT_AS_ROUTINE_SLOT_FILLING`

### 为什么这是 P0

因为它不是“结构化结果不够漂亮”的问题，而是：

> 用户已经给了明显高风险输入，系统还在当普通澄清处理。

这类问题直接影响：

- 用户安全感
- 产品可信度
- 评测结论是否能通过最基本验收

### 建议先修目标

不是一口气修所有风险语义，而是先做到：

1. 胸痛 + 呼吸困难 -> 直接 warning
2. 大出血 -> 直接 warning
3. 意识不清 / 抽搐 -> 直接 warning
4. 妊娠 + 出血 -> 直接 warning
5. “有点喘不过气” 这种轻度高风险表达，也不能当普通澄清吞掉

### 修完后的直接收益

这是当前最能立刻提升 judged report 的一组。

如果这组修好：

- 至少能直接拉回 5~6 条关键 case
- 也是最容易向外部证明“核心安全行为已经站住”的部分

---

## P1：修 negation，不要用户答完还重复问

涉及 case：

- `TRIAGE-NEG-001`
- `TRIAGE-NEG-002`
- `TRIAGE-NEG-003`
- `TRIAGE-NEG-004`
- `TRIAGE-NEG-005`

当前问题不是单一的“没识别出来”，而是组合问题：

- negation 没稳定进 slot / fact
- `lastAskedSlots` 不稳定
- pending 没正确收敛
- 还出现 forbidden：
  - `REASK_FEVER_PRESENCE`
  - `REASK_NAUSEA_PRESENCE`
  - `REASK_VOMITING_PRESENCE`

### 为什么这是 P1

从用户体感上，这组问题会非常明显：

> 用户已经回答“没发热”“不吐”，系统还像没听懂一样继续问。

这会直接造成：

- 用户觉得系统笨
- 多轮对话体验差
- 双智能体链路看起来不可信

### 建议先修目标

先把最典型 negation 立住：

1. `不发烧` / `没发热` -> `FEVER_PRESENCE=NO`
2. `没有恶心` -> `NAUSEA_PRESENCE=NO`
3. `不吐` -> `VOMITING_PRESENCE=NO`
4. 回答完以后，不再重复问同一槽位

### 修完后的直接收益

这组修完以后：

- judged report 会马上少掉一整组全灭 case
- 多轮体验改善会非常明显
- 这也是最容易让评测和真实体验同时变好的部分

---

## P2：修 mild affirmative，尤其是轻度高风险表达

涉及 case：

- `TRIAGE-MILD-001`
- `TRIAGE-MILD-002`
- `TRIAGE-MILD-003`
- `TRIAGE-MILD-004`

当前问题：

- `有点发热` 没稳定沉成 `FEVER_PRESENCE=YES`
- `有一点恶心` 没稳定沉成 `NAUSEA_PRESENCE=YES`
- `有点喘不过气` 没升级 warning

### 为什么它在 P2

它的重要性介于 negation 和普通 slot filling 之间。

因为：

- 普通 mild affirmative 会影响结构化抽取质量
- 但 mild + 红旗（比如 dyspnea）会直接影响安全行为

所以这组虽然整体排在 P2，但其中：

- `TRIAGE-MILD-004`

实际上应该跟 P0 一起看。

### 建议先修目标

先做到：

1. `有点/一点点/有一点` 不再默认吞成未知
2. mild affirmative 至少能沉为 positive
3. mild + 高风险症状时，不能继续按 routine follow-up 处理

### 修完后的直接收益

这组修好之后：

- 语义解析会明显更贴近真实用户表达
- judged report 对“口语化输入”的容忍度会提升

---

## P3：修多轮补槽，不要只有“方向对”，结果却落不下来

涉及 case：

- `TRIAGE-MULTI-001`
- `TRIAGE-MULTI-002`
- `TRIAGE-MULTI-003`
- `TRIAGE-MULTI-004`
- `TRIAGE-MULTI-005`

当前问题：

- 多轮行为表面上像在推进
- 但关键 slot 没稳定外显
- question plan 不够稳
- 还命中 forbidden：
  - `REASK_DURATION`
  - `REASK_BODY_PART`

### 为什么它排在 P3

因为和红旗、negation 比，这组更偏“体验优化 + 结构化稳定性”。

它当然重要，但不是当前最危险的问题。

### 建议先修目标

先把最基础的多轮补槽立住：

1. 问 body part 后回答 `右下腹`，不要再像没收到一样
2. 问 duration 后回答 `今天开始的`，不要继续空着
3. 问 fever 后回答 `没发热`，不要重复问 duration / fever 出错
4. question plan 要和当前已回答内容一致

### 修完后的直接收益

这组修好后：

- 双智能体链路会更像真的“理解上下文”
- 多轮 case 的 judged report 会从“结构没落下来”逐步转向真正可用

---

## P4：最后再收 weak input 的精细一致性

涉及 case：

- `TRIAGE-WEAK-001`
- `TRIAGE-WEAK-002`
- `TRIAGE-WEAK-003`
- `TRIAGE-WEAK-004`

当前状况：

- `TRIAGE-WEAK-001` 已经能过
- 其他几条在 stricter judge 下失败，主要是：
  - `slotStatuses=UNKNOWN` 没稳定外显
  - `mustAskAnyOf` 不够完整
  - forbidden 命中“提问过量/策略偏移”

### 为什么这是 P4

因为这组说明系统已经有一定基础能力，只是还不够稳定。

相比前面几组，它不是最先影响安全和用户信任的点。

### 建议先修目标

1. `PRIMARY_SYMPTOM=UNKNOWN` 明确外显
2. question plan 对弱输入保持简洁稳定
3. 不要问太多，不要偏题

---

## 建议开发顺序

如果只给开发同学一个最短执行顺序，我建议就是：

### 第 1 批

1. 红旗 warning 先立起来
2. negation 先立起来

这是当前最值的两件事。

### 第 2 批

3. mild affirmative，尤其 mild + 红旗
4. 多轮补槽的基础闭环

### 第 3 批

5. weak input 的结构化一致性

---

## 不建议现在先做什么

基于当前评测结果，我不建议开发同学现在优先把时间花在这些事上：

### 1. 先去优化文案自然度

因为现在更大的问题不是“说得不够像人”，而是：

- 红旗没拦住
- negation 没吃进去
- 多轮没稳定落槽

### 2. 先去追求完整 schema 漂亮输出

因为现在最需要的是：

- 关键行为先对
- 再谈结构够不够漂亮

### 3. 先去做大量新 case 扩容

因为现在已有 case 已经足够说明问题。

当前更缺的是：

- 先把最核心失败模式修掉
- 再继续扩 case

---

## 一句话给开发同学的版本

如果只能给开发同学一句话，我建议直接说：

> 先别分散精力，先把红旗 warning 和 negation 修到外部行为站住；这两组修完，当前 judged regression 会立刻从“整体不可信”提升到“开始可用”。
