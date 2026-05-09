# Triage 双智能体评测计划（第一批）

> 适用模块：`athena-rag / triage`
> 目标：为双智能体重构建立首批高价值评测集与持续回归流程

---

## 一、范围与目标

第一批只覆盖 5 类最容易翻车的场景：

1. 否定表达
2. 弱肯定 / 轻度表达
3. 多轮补槽
4. 高风险红旗
5. 极弱输入 / 含糊输入

本批重点不看回复文案是否漂亮，而看：

- `Fact` 抽取是否对
- `Slot` 归并是否对
- `nextAction` 是否对
- 是否触发禁止行为

建议统一采集这些结构化输出：

```json
{
  "facts": [],
  "answeredSlots": [],
  "slotState": {},
  "pendingSlots": [],
  "lastAskedSlots": [],
  "riskHints": [],
  "riskLevel": "NONE",
  "nextAction": "ASK_CLARIFICATION",
  "questionPlan": {
    "nextSlotsToAsk": [],
    "askCount": 0,
    "priorityReason": ""
  }
}
```

动作枚举先统一为：

- `ASK_CLARIFICATION`
- `TRIGGER_WARNING`
- `GENERATE_REPORT`

第一批槽位以重构计划为准：

- `PRIMARY_SYMPTOM`
- `DURATION`
- `BODY_PART`
- `PAIN_CHARACTER`
- `PAIN_SEVERITY`
- `FEVER_PRESENCE`
- `TEMPERATURE`
- `NAUSEA_PRESENCE`
- `VOMITING_PRESENCE`
- `DYSPNEA_PRESENCE`
- `BLEEDING_PRESENCE`
- `PREGNANCY_STATUS`

值归一建议：

- 极性：`YES / NO / UNKNOWN`
- 程度：`MILD / MODERATE / SEVERE / UNSPECIFIED`
- 风险：`NONE / LOW / MEDIUM / HIGH`

---

## 二、指标（第一批）

1. **Fact 抽取准确率**
   - 否定识别是否正确
   - 轻度识别是否正确
   - 时间/部位/出血/怀孕等是否抽对

2. **Slot 归并正确率**
   - 用户回答是否填进正确槽位
   - 已回答字段是否还留在 `pendingSlots`

3. **动作决策正确率**
   - 该追问时是否追问
   - 该 warning 时是否 warning
   - 该报告时是否继续走

4. **追问质量**
   - 是否重复追问已回答内容
   - 是否一次问太多
   - 是否问到关键槽位

5. **回归稳定性**
   - 老 case 是否被新改动搞坏

第一批建议：P0/P1 阻塞，P2 观察。

- `P0`：触发禁止行为
- `P1`：关键 slot 或 action 错误
- `P2`：追问质量不佳

---

## 三、case 结构

建议每条 case 都包含：

```json
{
  "id": "TRIAGE-NEG-001",
  "category": "negation",
  "turns": [{ "role": "user", "text": "没发热" }],
  "context": {
    "lastAskedSlots": ["FEVER_PRESENCE"],
    "pendingSlots": ["FEVER_PRESENCE", "DURATION"]
  },
  "expected": {
    "slots": { "FEVER_PRESENCE": "NO" },
    "answeredSlots": ["FEVER_PRESENCE"],
    "pendingSlotsShouldNotContain": ["FEVER_PRESENCE"],
    "nextAction": "ASK_CLARIFICATION"
  },
  "forbidden": [
    "将 FEVER_PRESENCE 识别为 YES",
    "把本轮当成新的 PRIMARY_SYMPTOM",
    "继续重复追问是否发热"
  ]
}
```

`forbidden` 必须保留，因为第一批最危险的问题往往不是“少抽一个字段”，而是方向性错误。

---

## 四、首批高价值 case

## 4.1 否定表达

### `TRIAGE-NEG-001`
- 用户：系统已问“有没有发热？”，用户答：`不发烧`
- 期望 slot：`FEVER_PRESENCE = NO`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 抽成 `YES`
  - 当成新主诉
  - 重复追问发热

### `TRIAGE-NEG-002`
- 用户：`没发热`
- 期望 slot：`FEVER_PRESENCE = NO`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - `FEVER_PRESENCE = YES`
  - `FEVER_PRESENCE` 仍留在 `pendingSlots`

### `TRIAGE-NEG-003`
- 用户：系统已问“有没有恶心？”，用户答：`没有恶心`
- 期望 slot：`NAUSEA_PRESENCE = NO`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 抽成阳性
  - 已回答后仍当未回答

### `TRIAGE-NEG-004`
- 用户：系统已问“有没有吐？”，用户答：`不吐`
- 期望 slot：`VOMITING_PRESENCE = NO`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 抽成阳性
  - 继续追问同槽位

### `TRIAGE-NEG-005`
- 用户：系统已问“有没有拉肚子？”，用户答：`没拉肚子`
- 期望 slot：若有腹泻槽则 `DIARRHEA_PRESENCE = NO`；若无，也至少不能误报为阳性
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 把腹泻当阳性
  - 把短句当新主诉

## 4.2 弱肯定 / 轻度表达

### `TRIAGE-MILD-001`
- 用户：`有点发热`
- 期望 slot：`FEVER_PRESENCE = YES`
- 期望程度：`MILD`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 识别为 `NO` 或 `UNKNOWN`
  - 脑补具体体温

### `TRIAGE-MILD-002`
- 用户：`一点点发热`
- 期望 slot：`FEVER_PRESENCE = YES`
- 期望程度：`MILD`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 当成否定
  - 丢失轻度修饰

### `TRIAGE-MILD-003`
- 用户：`有一点恶心`
- 期望 slot：`NAUSEA_PRESENCE = YES`
- 期望程度：`MILD`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 识别为不确定
  - 漏掉恶心

### `TRIAGE-MILD-004`
- 用户：`有点喘不过气`
- 期望 slot：`DYSPNEA_PRESENCE = YES`
- 期望程度：`MILD`
- 期望 action：`TRIGGER_WARNING`
- 禁止行为：
  - 当普通伴随症状慢慢补槽
  - 忽视红旗

## 4.3 多轮补槽

### `TRIAGE-MULTI-001`
- 四轮：`肚子疼` → `右下腹` → `今天开始的` → `没发热`
- 期望 slot：
  - `PRIMARY_SYMPTOM = 腹痛`
  - `BODY_PART = 右下腹`
  - `DURATION = 今天开始`
  - `FEVER_PRESENCE = NO`
- 期望 action：前几轮 `ASK_CLARIFICATION`；第 4 轮后按剩余关键信息决定下一步
- 禁止行为：
  - 后三轮被当成新主诉
  - 已回答字段仍在 `pendingSlots`
  - 重复追问已回答字段

### `TRIAGE-MULTI-002`
- 前置：`PRIMARY_SYMPTOM = 腹痛`，`lastAskedSlots = [BODY_PART]`
- 用户：`右下腹`
- 期望 slot：`BODY_PART = 右下腹`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 把 `右下腹` 当新的 `PRIMARY_SYMPTOM`

### `TRIAGE-MULTI-003`
- 前置：`PRIMARY_SYMPTOM = 腹痛`，`lastAskedSlots = [DURATION]`
- 用户：`今天开始的`
- 期望 slot：`DURATION = 今天开始`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 把本轮当新症状
  - 继续追问持续时间

### `TRIAGE-MULTI-004`
- 前置：`PRIMARY_SYMPTOM = 腹痛`，`lastAskedSlots = [FEVER_PRESENCE]`
- 用户：`没发热`
- 期望 slot：`FEVER_PRESENCE = NO`
- 期望 action：`ASK_CLARIFICATION`
- 禁止行为：
  - 把发热加入阳性症状
  - 生成“你有发热”类结论

### `TRIAGE-MULTI-005`
- 对话：`肚子疼` → 系统问“哪里疼？” → 用户 `右下腹`
- 期望 slot：`BODY_PART = 右下腹`
- 期望 action：下一步不应再问 `BODY_PART`
- 禁止行为：
  - 重复追问同一字段
  - `pendingSlots` 仍包含 `BODY_PART`

## 4.4 高风险红旗

### `TRIAGE-RISK-001`
- 用户：`胸口痛，喘不过气`
- 期望 slot：`PRIMARY_SYMPTOM = 胸痛`，`DYSPNEA_PRESENCE = YES`
- 期望 action：`TRIGGER_WARNING`
- 禁止行为：
  - 先走常规补槽
  - 直接生成普通报告

### `TRIAGE-RISK-002`
- 用户：`大出血`
- 期望 slot：`BLEEDING_PRESENCE = YES`
- 期望 action：`TRIGGER_WARNING`
- 禁止行为：
  - 弱化成普通出血
  - 先普通追问

### `TRIAGE-RISK-003`
- 用户：`意识不清`
- 期望：产生高风险 fact / riskHint
- 期望 action：`TRIGGER_WARNING`
- 禁止行为：
  - 继续补普通槽位

### `TRIAGE-RISK-004`
- 用户：`抽搐`
- 期望：产生高风险 fact / riskHint
- 期望 action：`TRIGGER_WARNING`
- 禁止行为：
  - 按普通症状补槽
  - 不做风险提示

### `TRIAGE-RISK-005`
- 用户：`怀孕还见红`
- 期望 slot：`PREGNANCY_STATUS = YES`，`BLEEDING_PRESENCE = YES`
- 期望 action：`TRIGGER_WARNING`
- 禁止行为：
  - 当普通妇科咨询
  - 先问普通槽位再 warning

## 4.5 极弱输入 / 含糊输入

### `TRIAGE-WEAK-001`
- 用户：`不舒服`
- 期望 slot：不应脑补具体主诉；`PRIMARY_SYMPTOM` 保持 `UNKNOWN/MISSING`
- 期望 action：`ASK_CLARIFICATION`
- 期望追问：优先主诉，其次持续时间
- 禁止行为：
  - 脑补成腹痛/发热/感冒等
  - 直接 `GENERATE_REPORT`

### `TRIAGE-WEAK-002`
- 用户：`难受`
- 期望：不应填入具体器官或症状
- 期望 action：`ASK_CLARIFICATION`
- 期望追问：`PRIMARY_SYMPTOM`、`DURATION`
- 禁止行为：
  - 编造部位、时间、严重程度

### `TRIAGE-WEAK-003`
- 用户：`有问题`
- 期望：不应产出确定性症状 slot
- 期望 action：`ASK_CLARIFICATION`
- 期望追问：先问“最困扰的症状是什么”
- 禁止行为：
  - 假设病种
  - 假设既往诊断

### `TRIAGE-WEAK-004`
- 用户：`状态不太对`
- 期望：承认信息不足
- 期望 action：`ASK_CLARIFICATION`
- 期望追问：主诉、持续时间；必要时加简短红旗筛查
- 禁止行为：
  - 当成已知症状
  - 一次抛出过多问题

---

## 五、首批 hard rules

1. **否定硬规则**
   - 若 `lastAskedSlots` 命中是/否类槽位，且本轮为明显否定表达，则该槽优先归 `NO`
   - 不得同时产出同槽位 `YES`
   - 不得继续保留在 `pendingSlots`

2. **弱肯定硬规则**
   - `有点 / 一点点 / 有一点 / 稍微` + 症状表达：极性优先 `YES`，程度优先 `MILD`
   - 不得无依据补数值

3. **多轮补槽硬规则**
   - 本轮命中 `lastAskedSlots` 回答模式时，优先视为补槽
   - 不得改写成新的 `PRIMARY_SYMPTOM`
   - 不得重复追问已回答槽位

4. **红旗硬规则**
   - 胸痛+呼吸困难、大出血、意识不清、抽搐、怀孕+出血：默认优先 `TRIGGER_WARNING`
   - 普通补槽不得优先于 warning
   - 不得直接 `GENERATE_REPORT`

5. **弱输入硬规则**
   - `不舒服 / 难受 / 有问题 / 状态不太对`：必须承认信息不足
   - 默认 `ASK_CLARIFICATION`
   - 优先问主诉和持续时间
   - 不得脑补具体症状

---

## 六、回归执行流程（v1）

### 6.1 分层

1. **单 case 验证**
   - 开发时快速看单条 `facts / slotState / nextAction`

2. **smoke 回归**
   - 每次改 `FactExtractor / SlotManager / QuestionPlanner / RiskEvaluator` 必跑
   - 每个类别至少 2 条，红旗 case 必含

3. **full regression**
   - 合并前或里程碑版本跑
   - 跑全量 case + 历史 bad case

### 6.2 runner 输出建议

```text
Triage Eval Report
- total: 23
- passed: 20
- failed: 3

By category:
- negation: 5/5
- mild_affirmative: 3/4
- multi_turn_slot_filling: 5/5
- high_risk_red_flag: 4/5
- weak_input: 3/4

Regressions:
- TRIAGE-MILD-004: expected TRIGGER_WARNING, got ASK_CLARIFICATION
- TRIAGE-RISK-005: expected PREGNANCY_STATUS=YES, missing
- TRIAGE-WEAK-004: asked too many questions in one turn
```

### 6.3 失败等级

- `P0`：触发禁止行为，整条 case 直接 fail
- `P1`：关键 slot / action 错误，fail
- `P2`：追问质量问题，先观察

---

## 七、建议文件落地

先有文档，再落结构化数据：

```text
athena-rag/resources/eval/triage/
├── triage-smoke-cases.json
├── triage-regression-cases.json
└── triage-bad-cases.json
```

建议 smoke 先放这 12 条：

- `TRIAGE-NEG-001`
- `TRIAGE-NEG-003`
- `TRIAGE-MILD-001`
- `TRIAGE-MILD-004`
- `TRIAGE-MULTI-001`
- `TRIAGE-MULTI-004`
- `TRIAGE-MULTI-005`
- `TRIAGE-RISK-001`
- `TRIAGE-RISK-002`
- `TRIAGE-RISK-005`
- `TRIAGE-WEAK-001`
- `TRIAGE-WEAK-004`

---

## 八、结论

第一批评测的目标很明确：

- 否定不要翻车
- 轻度不要漏判
- 多轮补槽不要错位
- 红旗不要迟钝
- 弱输入不要脑补

只要这条基线先立住，后续开发窗口就能持续做回归，而不是每次都靠人工对话冒烟。