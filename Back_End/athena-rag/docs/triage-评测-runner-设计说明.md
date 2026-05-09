# Triage 评测 Runner 设计说明（v1）

> 适用模块：`athena-rag / triage`
> 配套数据：`resources/eval/triage/triage-smoke-cases.json`、`triage-regression-cases.json`
> 目标：定义 triage 评测 runner 的输入输出、执行流程、判分规则与回归接入方式

---

## 一、设计目标

这份 runner 说明解决的是一个具体问题：

> 已经有评测 case 了，后续怎么把它稳定跑起来，并在开发窗口持续回归？

第一版 runner 不追求复杂，不做 LLM-as-judge，不做花哨分数。只做三件事：

1. 批量执行 triage case
2. 采集结构化结果
3. 根据硬规则输出通过 / 失败 / 回归项

第一版优先保障：

- 否定识别不翻车
- 轻度表达不漏判
- 多轮补槽不串槽
- 高风险红旗能及时 warning
- 弱输入不脑补

---

## 二、为什么 runner 应直接观测 `TriageContext`

结合当前代码，`TriageContext` 已经天然具备作为评测载体的条件：

```18:88:d:\athenaworktwo\athena\Back_End\athena-rag\bootstrap\src\main\java\com\nageoffer\ai\ragent\triage\model\TriageContext.java
public class TriageContext {

    private String sessionId;
    private String userInput;
    private String latestUserTurn;
    private String conversationSummary;
    @Default
    private List<String> conversationHistory = new ArrayList<>();
    @Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();
    @Default
    private List<String> missingFields = new ArrayList<>();
    @Default
    private List<Fact> factHistory = new ArrayList<>();
    @Default
    private SlotState slotState = SlotState.empty();
    @Default
    private List<SlotCode> lastAskedSlots = new ArrayList<>();
    @Default
    private List<SlotCode> pendingSlots = new ArrayList<>();
    private QuestionPlan questionPlan;
    private RiskLevel riskAssessment;
    private TriageAction nextAction;
    private String finalReply;
```

这意味着 runner 不需要靠“猜回复文本”来评测，而是可以直接看：

- `factHistory`
- `slotState`
- `pendingSlots`
- `lastAskedSlots`
- `questionPlan`
- `riskAssessment`
- `nextAction`
- `finalReply`

其中真正的硬判定应尽量基于结构化字段，而不是中文文案。

---

## 三、Runner 的最小职责边界

v1 runner 只负责：

1. 读取 case 文件
2. 组装输入上下文
3. 执行 triage 一轮或多轮流程
4. 导出标准化结果
5. 按规则打分
6. 生成报告

v1 runner 不负责：

- 修复业务逻辑
- 自动改 case
- 评判回复是否“自然”
- 评判中文是否“像医生”

这些都应留给后续更高层评测。

---

## 四、输入格式约定

runner 的输入就是结构化 case 文件。

### 4.1 文件级输入

建议支持两种文件：

- `triage-smoke-cases.json`
- `triage-regression-cases.json`

### 4.2 case 级输入字段

v1 以当前已落地 schema 为准：

```json
{
  "id": "TRIAGE-NEG-001",
  "category": "negation",
  "priority": "P0",
  "turns": [{ "role": "user", "text": "不发烧" }],
  "context": {
    "lastAskedSlots": ["FEVER_PRESENCE"],
    "pendingSlots": ["FEVER_PRESENCE", "DURATION"]
  },
  "expected": {
    "slotValues": { "FEVER_PRESENCE": "NO" },
    "answeredSlotsContains": ["FEVER_PRESENCE"],
    "pendingSlotsNotContains": ["FEVER_PRESENCE"],
    "nextAction": "ASK_CLARIFICATION"
  },
  "forbidden": ["FEVER_PRESENCE=YES", "NEW_PRIMARY_SYMPTOM", "REASK_FEVER_PRESENCE"]
}
```

### 4.3 多轮输入约定

多轮 case 目前采用：

- `turns`：顺序用户输入
- `context.perTurnContext`：每轮附加上下文

例如：

```json
{
  "id": "TRIAGE-MULTI-001",
  "turns": [
    { "role": "user", "text": "肚子疼" },
    { "role": "user", "text": "右下腹" },
    { "role": "user", "text": "今天开始的" },
    { "role": "user", "text": "没发热" }
  ],
  "context": {
    "perTurnContext": [
      {},
      { "lastAskedSlots": ["BODY_PART"] },
      { "lastAskedSlots": ["DURATION"] },
      { "lastAskedSlots": ["FEVER_PRESENCE"] }
    ]
  }
}
```

runner 对多轮 case 的处理原则是：

- 同一个 case 使用同一个 `TriageContext`
- 每一轮都更新 `latestUserTurn`
- 每一轮都执行一遍完整 triage 流程
- 最终以最后一轮输出作为主判定对象
- 如 case 显式要求，也可对中间轮次做断言

---

## 五、执行流程

## 5.1 单轮 case 执行

### 步骤

1. 新建 `TriageContext`
2. 根据 `context` 字段注入：
   - `slotState`
   - `lastAskedSlots`
   - `pendingSlots`
   - 必要的 `factHistory`
3. 写入本轮 `latestUserTurn`
4. 调用 triage 主流程
5. 采集结构化输出
6. 与 `expected / forbidden` 比对

### 伪代码

```java
TriageContext context = buildContext(testCase.context());
context.setLatestUserTurn(userTurn);
context.appendConversation(userTurn);
triageStateMachine.execute(context);
EvalResult result = normalize(context);
CaseAssertion assertion = assertCase(testCase, result);
```

## 5.2 多轮 case 执行

### 步骤

1. 新建一个共享 `TriageContext`
2. 按顺序遍历 `turns`
3. 每轮前合并对应的 `perTurnContext`
4. 执行 triage 流程
5. 保留每轮快照
6. 最后一轮做主断言
7. 若需要，可额外检查中间轮快照

### 关键原则

多轮 case 的重点不是“每轮回复像不像人”，而是：

- 后续轮次有没有补进正确 slot
- 已回答槽位是否仍在 `pendingSlots`
- 后续短句有没有被误判为新主诉

---

## 六、标准化输出格式

runner 不应直接拿原始 `TriageContext` 做断言，而应先归一成 `NormalizedEvalResult`。

建议格式：

```json
{
  "caseId": "TRIAGE-NEG-001",
  "facts": [
    {
      "slot": "FEVER_PRESENCE",
      "canonicalValue": "NO",
      "polarity": "NEGATIVE",
      "evidence": "不发烧"
    }
  ],
  "slotValues": {
    "PRIMARY_SYMPTOM": { "value": "腹痛", "status": "FILLED" },
    "FEVER_PRESENCE": { "value": "NO", "status": "FILLED" }
  },
  "lastAskedSlots": ["FEVER_PRESENCE"],
  "pendingSlots": ["DURATION"],
  "riskLevel": "LOW",
  "riskScore": 10,
  "nextAction": "ASK_CLARIFICATION",
  "questionPlan": {
    "nextSlotsToAsk": ["DURATION"],
    "askCount": 1,
    "priorityReason": "补充持续时间"
  },
  "finalReply": "...",
  "turnSnapshots": []
}
```

### 6.1 为什么要做 normalized result

因为当前各模块内部对象未来可能还会变，但评测断言不应频繁跟着改。

也就是说：

- **业务对象允许演化**
- **评测输出协议应尽量稳定**

这样老 case 才能持续回归。

---

## 七、判分顺序

v1 runner 建议严格按下面顺序判定。

## 7.1 第一步：先判 forbidden

这是最高优先级。

原因：

- 否定被抽成肯定
- 红旗场景没 warning
- 弱输入脑补主诉

这类问题即使其他字段“看起来还行”，也必须直接 fail。

### 规则示例

- `FEVER_PRESENCE=YES`
  - 若结果中 `slotValues.FEVER_PRESENCE.value == YES`，则命中 forbidden
- `NEW_PRIMARY_SYMPTOM`
  - 若本轮是补槽型回答，却把本轮文本归成新的 `PRIMARY_SYMPTOM`，则命中
- `REASK_FEVER_PRESENCE`
  - 若 `questionPlan.nextSlotsToAsk` 仍包含 `FEVER_PRESENCE`，则命中
- `GENERATE_REPORT`
  - 若弱输入 case 结果 action 为 `GENERATE_REPORT`，则命中

## 7.2 第二步：判关键 action

然后再判：

- `expected.nextAction`
- `expected.riskLevelAtLeast`

因为 action 决策是 triage 的核心行为边界。

### 规则示例

- 期望 `TRIGGER_WARNING`，实际是 `ASK_CLARIFICATION` → fail
- 期望风险至少 `HIGH`，实际只到 `LOW` → fail

## 7.3 第三步：判 slot 与 fact

再判：

- `expected.slotValues`
- `expected.slotStatuses`
- `expected.factModifiers`
- `expected.factPolarityHints`

### 规则示例

- `slotValues.FEVER_PRESENCE == NO`
- `slotStatuses.PRIMARY_SYMPTOM == UNKNOWN`
- `factModifiers.DYSPNEA_PRESENCE == MILD`
- `factPolarityHints` 里需要出现 evidence = `没拉肚子`, polarity = `NEGATIVE`

## 7.4 第四步：判 pending 与 questionPlan

最后再判：

- `pendingSlotsNotContains`
- `questionPlan.mustAskAnyOf`
- `questionPlan.mustNotAsk`
- `questionPlan.maxAskCount`

因为这一层更偏追问质量与流程细节。

---

## 八、失败等级

建议 runner 输出统一失败等级：

### 8.1 P0

表示危险回归，默认阻塞。

触发条件：

- 命中 forbidden
- 红旗场景未 warning
- 否定场景抽成阳性
- 弱输入脑补主诉

### 8.2 P1

表示关键目标未满足，默认阻塞。

触发条件：

- 关键 slot 错误
- `pendingSlots` 清理错误
- 多轮补槽未归并到正确槽位

### 8.3 P2

表示体验问题，先观察。

触发条件：

- 一次问太多
- 追问顺序一般
- 回复承认不足但语气略差

第一版建议：

- `P0/P1` 让 CI 失败
- `P2` 只出现在报告里

---

## 九、报告格式

runner 每次执行后建议生成两份报告：

1. `json` 报告：给机器消费
2. `md` 报告：给人看

## 9.1 JSON 报告建议

```json
{
  "suite": "triage-smoke",
  "total": 12,
  "passed": 10,
  "failed": 2,
  "generatedAt": "2026-04-23T16:00:00Z",
  "byCategory": {
    "negation": { "passed": 2, "failed": 0 },
    "mild_affirmative": { "passed": 1, "failed": 1 },
    "multi_turn_slot_filling": { "passed": 3, "failed": 0 },
    "high_risk_red_flag": { "passed": 2, "failed": 1 },
    "weak_input": { "passed": 2, "failed": 0 }
  },
  "failures": [
    {
      "caseId": "TRIAGE-MILD-004",
      "severity": "P0",
      "message": "expected TRIGGER_WARNING, got ASK_CLARIFICATION"
    }
  ]
}
```

## 9.2 Markdown 报告建议

```text
# Triage Eval Report

- suite: triage-smoke
- total: 12
- passed: 10
- failed: 2

## By category
- negation: 2/2
- mild_affirmative: 1/2
- multi_turn_slot_filling: 3/3
- high_risk_red_flag: 2/3
- weak_input: 2/2

## Failures
1. TRIAGE-MILD-004
   - severity: P0
   - expected: TRIGGER_WARNING
   - actual: ASK_CLARIFICATION
   - forbidden hit: MISS_RED_FLAG

2. TRIAGE-RISK-005
   - severity: P1
   - expected slot: PREGNANCY_STATUS=YES
   - actual: missing
```

---

## 十、Runner 输出文件建议

建议沿用现有 `resources/eval/outputs/` 风格，在其下新增 triage 报告。

推荐：

```text
resources/eval/outputs/
├── triage-smoke-report.json
├── triage-smoke-report.md
├── triage-regression-report.json
├── triage-regression-report.md
└── triage-latest-report.json
```

这样和现有 RAG 评测产物风格一致。

---

## 十一、接入方式建议

## 11.1 本地开发

本地最常用两个入口：

1. **单 case 调试**
   - 适合开发某个 negation / multi-turn bug 时快速验证

2. **smoke 回归**
   - 每次改 `FactExtractor / SlotManager / QuestionPlanner / RiskEvaluator` 后跑

推荐约定命令形态：

```bash
# 只跑一条
triage-eval --case TRIAGE-NEG-001

# 跑 smoke
triage-eval --suite smoke

# 跑全量
triage-eval --suite regression
```

v1 不一定真要做成 CLI，也可以先做成 JUnit + 本地入口。

## 11.2 CI / 持续回归

建议最小接法：

- PR 阶段跑 `smoke`
- 主分支定时或合并前跑 `regression`

建议规则：

- `smoke` 中任一 `P0/P1` 失败 → CI fail
- `regression` 中出现新增 `P0/P1` → fail
- `P2` 仅记录，不阻塞

## 11.3 bad case 沉淀

后续每出现一次线上 triage 翻车，都不要只修逻辑，要同步做两件事：

1. 把问题写成一个新 case
2. 放入 `triage-regression-cases.json` 或未来的 `triage-bad-cases.json`

这样评测集才会越跑越强，而不是每次靠记忆回归。

---

## 十二、实现建议

## 12.1 v1 推荐实现形态

最推荐：

- 一个 `TriageEvalRunner`
- 一个 `TriageEvalCaseLoader`
- 一个 `TriageEvalNormalizer`
- 一个 `TriageEvalAsserter`
- 一个 `TriageEvalReportWriter`

职责拆分：

### `TriageEvalCaseLoader`
- 读取 JSON case 文件
- 反序列化 case

### `TriageEvalRunner`
- 跑单条 / smoke / regression
- 驱动多轮执行

### `TriageEvalNormalizer`
- 从 `TriageContext` 提取稳定的 `NormalizedEvalResult`

### `TriageEvalAsserter`
- 负责 `forbidden / expected` 判定
- 输出失败列表与严重级别

### `TriageEvalReportWriter`
- 写 `json` / `md` 报告

## 12.2 为什么不建议把判分逻辑塞进测试类里

因为评测会持续演化。

如果把所有判断都散落在测试方法中，会很快出现：

- case 难维护
- 规则难复用
- 报告难统一

单独做 runner 和 assertion 层，更适合长期回归。

---

## 十三、与现有测试的关系

当前 `TriageContextTest` 更像模型行为测试，例如：

```54:116:d:\athenaworktwo\athena\Back_End\athena-rag\bootstrap\src\test\java\com\nageoffer\ai\ragent\triage\model\TriageContextTest.java
@Test
void shouldAppendFactsAndInitializeSlotStateStructures() {
    TriageContext context = TriageContext.builder().build();

    Fact fact = Fact.builder()
            .type(FactType.SLOT_EVIDENCE)
            .slot(SlotCode.FEVER_PRESENCE)
            .canonicalValue("NO")
            .polarity(FactPolarity.NEGATIVE)
            .evidence("没发热")
            .sourceTurnIndex(1)
            .sourceText("没发热")
            .build();

    context.appendFacts(List.of(fact));
    context.ensureCollections();

    assertEquals(1, context.getFactHistory().size());
    assertNotNull(context.getSlotState());
    assertTrue(context.getPendingSlots().isEmpty());
    assertTrue(context.getLastAskedSlots().isEmpty());
}
```

runner 与这类单元测试不是替代关系，而是分工关系：

- **单元测试**：保模型、方法、局部模块的行为
- **eval runner**：保跨模块语义与动作决策不回归

两者都要保留。

---

## 十四、v1 成功标准

如果 runner 落地成功，至少应满足：

1. 能跑 smoke 集
2. 能跑全量 regression 集
3. 能输出结构化报告
4. 能定位失败 case 与失败原因
5. 能在后续开发窗口中稳定复用

这就足够支撑第一阶段重构回归，不需要一开始做得特别重。

---

## 十五、推荐落地顺序

建议按下面顺序推进：

1. 先实现 `case loader`
2. 再实现 `normalized result`
3. 再实现 `asserter`
4. 先打通 `smoke`
5. 最后再接 `regression` 和报告输出

理由很简单：

- 先把链路跑通
- 再补丰富断言
- 避免一开始就做复杂框架

---

## 十六、结论

triage 第一批评测现在已经有：

- 文档评测计划
- 结构化 smoke case
- 结构化 regression case

下一步只差一个轻量 runner，就能形成真正可执行的回归护栏。

这套 runner 的核心原则只有一句话：

> 以 `TriageContext` 的结构化状态为准，而不是以回复文案猜系统是否正确。

只要守住这个原则，后续 triage 内核继续重构时，评测体系也能保持稳定。