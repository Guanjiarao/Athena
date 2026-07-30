# Triage 分诊系统代码溯源全流程

本文档按一次 `triage analyze` 请求的真实代码调用链梳理，方便从入口一路追到 Agent、状态机、落槽、风险、问题规划、回复、报告和 trace 落库。

---

## 0. 快速总览

一次请求的主路径：

```text
TriageAnalyzeRequest
  -> TriageOrchestratorServiceImpl.analyze
  -> loadOrCreateContext
  -> TriageStateMachine.execute
  -> TriageSupervisor.runUnderstandingAndAgents
  -> NormalizationAgent / RuleAgent / SlotAgent / RiskAgent / QuestionPlanner
  -> TriageContextReducer
  -> TriageStateMachine 决策 nextAction
  -> TriageResponseAgent.toResponse
  -> TriageAnalyzeResponse
  -> TriageSessionManager.saveContext
```

核心目录：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/
framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/
```

---

## 1. API 入口与会话加载

### 1.1 主入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/service/impl/TriageOrchestratorServiceImpl.java
```

方法：

```text
TriageOrchestratorServiceImpl.analyze(TriageAnalyzeRequest request)
```

职责：

1. 校验请求。
2. 加载或创建 `TriageContext`。
3. 执行状态机。
4. 转换响应。
5. 保存 Redis session。
6. 如果终态，则持久化 terminal context。

关键代码位置：

```text
TriageOrchestratorServiceImpl.analyze
  -> validateRequest
  -> loadOrCreateContext
  -> triageStateMachine.execute
  -> toResponse
  -> triageSessionManager.saveContext
  -> persistTerminalContext
```

入口上有 trace root：

```text
@RagTraceRoot(name = "TRIAGE_ANALYZE", conversationIdArg = "request", conversationIdGetter = "getSessionId")
```

这表示每次分诊请求会生成一条 trace run。

---

### 1.2 会话加载与轮次递增

同文件：

```text
TriageOrchestratorServiceImpl.loadOrCreateContext
```

关键逻辑：

```text
sessionId = request.sessionId 或 Snowflake ID
context = triageSessionManager.getContext(sessionId)
context.resetTurnState()
context.totalTurnCount += 1
context.latestUserTurn = request.userInput.trim()
context.appendConversation(latestUserInput)
compressConversationMemoryIfNeeded(context)
context.userInput = context.buildConversationTranscript(true)
```

这里是轮次计数的来源：

```text
TriageContext.totalTurnCount
```

如果你怀疑“第 8 轮没有出报告”，首先看这里是否正确递增。

---

### 1.3 Redis Session 存取

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/session/TriageSessionManager.java
```

方法：

```text
getContext(sessionId)
saveContext(ctx)
```

配置：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/session/TriageSessionProperties.java
bootstrap/src/main/resources/config/application-prod.yaml
```

关键配置：

```yaml
triage:
  session:
    ttl-minutes: 120
    key-prefix: "triage:session:"
    target-clarification-turns: 7
    max-total-turns: 8
    min-required-turns: 5
```

---

## 2. 状态机总控

### 2.1 状态机入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/engine/TriageStateMachine.java
```

方法：

```text
TriageStateMachine.execute(TriageContext context)
```

职责：

1. 全局最大轮次硬拦截。
2. 状态流转。
3. 调用每个状态 handler。
4. 设置 `currentState`、`nextAction`、`finalReply`。
5. 记录 audit trail 和 state log。

状态枚举：

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/engine/TriageState.java
```

状态：

```text
INIT
PARSING
VALIDATING
RISK_ASSESSING
REPORT_GENERATING
COMPLETED
INTERRUPTED
```

---

### 2.2 最大轮次强制报告

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/engine/TurnLimitHelper.java
```

方法：

```text
shouldForceReport(context)
```

判断：

```text
totalTurnCount >= triage.session.maxTotalTurns
```

当前默认：

```text
maxTotalTurns = 8
```

状态机入口现在应当有全局硬拦截：

```text
TriageStateMachine.execute
  -> turnLimitHelper.shouldForceReport(context)
  -> 命中后直接 GENERATE_REPORT
```

命中后应清理：

```text
pendingSlots = []
missingFields = []
questionPlan.nextSlotsToAsk = []
nextAction = GENERATE_REPORT
finalReply = TriageReplyBuilder.generatePreTriageReport(...)
currentState = COMPLETED 或 REPORT_GENERATING/COMPLETED
```

如果第 9 轮仍在问，重点排查：

```text
TriageOrchestratorServiceImpl.loadOrCreateContext 是否递增 totalTurnCount
TurnLimitHelper.shouldForceReport 是否读取到 maxTotalTurns=8
TriageStateMachine.execute 是否在入口硬拦截
TriageSessionManager 是否读到了同一个 session
```

---

## 3. Supervisor 多 Agent 编排

### 3.1 Supervisor 入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/agent/TriageSupervisor.java
```

方法：

```text
runUnderstandingAndAgents(TriageContext context)
```

职责：

1. 调用 Normalization Agent。
2. 调用 Rule Agent。
3. 调用 Slot Agent。
4. 调用 Risk Agent。
5. 调用 Question Planner。
6. 把结果交给 `TriageContextReducer` 写回。

trace node：

```text
@RagTraceNode(name = "TriageSupervisor", type = "TRIAGE_SUP")
```

---

### 3.2 Context 单写者

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/agent/TriageContextReducer.java
```

核心方法：

```text
applyNormalization(context, normalizationResult)
apply(context, normalizedTurn, riskResult, ruleResult, slotResult, plannerResult)
applyQuestionPlan(context, plannerResult)
```

设计原则：

```text
各 Agent 只产出 result，不直接修改共享 context。
Reducer 统一把 result 合并写回 TriageContext。
```

写回内容包括：

```text
latestTurnUnderstanding
factHistory
extractedSymptoms
finalPrimaryComplaint
slotState
answeredSlots
riskAssessment
riskDecision
candidateQuestionGaps
questionPlan
pendingSlots
lastAskedSlots
forceGenerateReport
```

---

## 4. Normalization Agent：语义归一化

### 4.1 入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/normalization/NormalizationAgent.java
```

方法：

```text
normalizeToResult(context)
```

内部调用：

```text
TurnUnderstandingWorker.execute
SemanticParserWorker.execute
FactExtractor.execute
```

输出：

```text
NormalizationAgentResult
NormalizedTurn
```

trace node：

```text
@RagTraceNode(name = "NormalizationAgent", type = "TRIAGE_NORM")
```

---

### 4.2 TurnUnderstandingWorker：理解当前轮

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/normalization/TurnUnderstandingWorker.java
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/normalization/TurnUnderstandingExecutionEngine.java
```

核心输出：

```text
TurnUnderstanding
```

重要字段：

```text
intent
primaryComplaint
answeredSlots
riskSignals
corrections
```

典型场景：

上一轮问：

```text
DURATION: 这种不适持续多久了？
```

用户答：

```text
几小时内
```

期望识别：

```text
answeredSlots:
  - slot: DURATION
    normalizedValue: 几小时
    answersPreviousQuestion: true
```

辅助规则类：

```text
AnsweredSlotFlowCollector
SlotAnswerInferenceHelper
TurnComplaintSemanticsCoordinator
TurnUnderstandingPostProcessor
```

---

### 4.3 SemanticParserWorker：症状解析

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/normalization/SemanticParserWorker.java
```

职责：

```text
从用户输入中抽取症状、部位、持续时间、伴随症状等。
```

输出：

```text
List<Symptom>
```

---

### 4.4 FactExtractor：事实抽取

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/normalization/FactExtractor.java
```

职责：

```text
把当前轮输入抽成结构化 Fact，供 SlotAgent/StateReducer 使用。
```

输出：

```text
List<Fact>
```

---

## 5. Slot Agent：槽位落槽

### 5.1 入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/slot/SlotAgent.java
```

方法：

```text
reduce(context, normalizedTurn)
```

内部调用：

```text
StateReducer.execute
SlotManager.execute
applyAnsweredSlotsFromTurnUnderstanding
```

输出：

```text
SlotAgentResult
```

包含：

```text
slotPatch
answeredSlots
```

trace node：

```text
@RagTraceNode(name = "SlotAgent", type = "TRIAGE_SLOT")
```

---

### 5.2 槽位状态

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/model/SlotState.java
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/model/SlotValue.java
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/model/SlotStatus.java
```

`SlotValue` 字段：

```text
slot
value
status
evidence
updatedAt
```

常见状态：

```text
FILLED
NEGATED
CORRECTED
INFERRED
CONFLICTING
```

---

### 5.3 已回答槽位直写 SlotState

关键修复点：

```text
SlotAgent.applyAnsweredSlotsFromTurnUnderstanding
```

作用：

```text
latestTurnUnderstanding.answeredSlots
  -> SlotState
```

避免这种问题：

```text
LLM 已识别 DURATION=几小时
但 SlotState 未闭合
Risk gap 又把 DURATION 放回 pendingSlots
导致重复问“持续多久”
```

---

## 6. Rule Agent：规则候选问题

### 6.1 入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/rule/RuleAgent.java
```

方法：

```text
lookup(RuleLookupRequest request)
```

职责：

```text
根据 signal 查询规则，产生候选 QuestionGap 和选项。
```

输出：

```text
RuleAgentResult
```

包含：

```text
ruleGaps
options
matchedRules
```

trace node：

```text
@RagTraceNode(name = "RuleAgent", type = "TRIAGE_RULE")
```

---

### 6.2 规则服务

相关文件：

```text
TriageSlotRuleService
SlotRuleDefinition
MatchedSlotRule
RuleLookupRequest
```

规则会产生类似：

```text
DIARRHEA_FREQUENCY
STOOL_CHARACTER
FOOD_HISTORY
DURATION
```

---

## 7. Risk Agent：风险判断

### 7.1 入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/risk/RiskAgent.java
```

方法：

```text
assess(context, normalizedTurn)
```

内部主要依赖：

```text
RiskStratifierWorker
RiskHeuristicHelper
```

输出：

```text
RiskAgentResult
```

包含：

```text
riskLevel
riskDecision
riskGaps
```

trace node：

```text
@RagTraceNode(name = "RiskAgent", type = "TRIAGE_RISK")
```

---

### 7.2 风险决策

相关模型：

```text
RiskLevel
RiskDecision
RiskDecisionType
RiskGap
RiskSignalUnderstanding
```

常见决策：

```text
TRIGGER_WARNING
ASK_RISK_CLARIFICATION
ESCALATE_FROM_HISTORY
```

风险模块可以返回：

```text
unresolvedRiskGaps
confirmedRiskGaps
suspectedRiskGaps
```

注意：现在 `QuestionPlanner` 中已修正优先级：

```text
已回答/已落槽 > unresolved risk gap
```

即：如果用户已经回答 `DURATION`，即使风险模型又返回 `DURATION unresolved`，也不再重复问。

---

## 8. Question Planner：问题规划

### 8.1 入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/question/QuestionPlanner.java
```

方法：

```text
execute(TriageContext context)
```

职责：

1. 合并 rule gaps 和 risk gaps。
2. 评估每个 gap 是否可问。
3. 根据策略选择本轮问题。
4. 生成 `QuestionPlan`。
5. 必要时触发 LLM fallback 或强制报告。

输出：

```text
QuestionPlannerResult
```

trace node：

```text
@RagTraceNode(name = "QuestionPlanner", type = "TRIAGE_PLAN")
```

---

### 8.2 Askability 判断

核心方法：

```text
QuestionPlanner.evaluateAskability
```

主要判断顺序：

```text
当前轮 answeredSlots 已回答 -> 不问
SlotState 已闭合 -> 不问
unresolved risk gap 且未闭合 -> 可问
最近问过且非风险强制 -> 不问
FILLED / NEGATED / CORRECTED -> 不问
INFERRED 且非风险 -> 暂不问
CONFLICTING -> 可以问
否则可问
```

相关输出：

```text
askabilityDecisions
selectedQuestionGaps
suppressedQuestionGaps
pendingSlots
lastAskedSlots
```

---

### 8.3 Cold Start / LLM fallback

相关文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/question/ColdStartSlotSelector.java
```

作用：

```text
当规则无法稳定选出问题时，用 LLM 给候选槽位打分。
```

但有防死循环机制：

```text
连续 fallback 次数过多 -> forceGenerateReport = true
```

---

## 9. 回复与结构化问题

### 9.1 Reply Builder

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/engine/TriageReplyBuilder.java
```

核心方法：

```text
buildClarificationReply
buildClarificationQuestions
buildWarningReply
generatePreTriageReport
```

职责：

```text
根据 questionPlan/pendingSlots/missingFields 生成用户可见回复和结构化 questions。
```

---

### 9.2 问题模板

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/response/TriageReplyPromptSupport.java
```

典型映射：

```text
DURATION -> 这种不适持续多久了？
DIARRHEA_FREQUENCY -> 一天大概腹泻几次？
NAUSEA_PRESENCE -> 有没有恶心？
```

---

### 9.3 选项生成

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/question/QuestionOptionProvider.java
```

方法：

```text
generateOptionsForSlot(SlotCode slot)
```

现在已补齐扩展槽位选项，例如：

```text
DIARRHEA_FREQUENCY:
  1-2次
  3-5次
  6次以上
  频繁到记不清
  其他
```

默认兜底：

```text
有
没有
不确定
其他
```

避免只出现一个“其他”。

---

## 10. Response Agent：接口响应转换

### 10.1 入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/response/TriageResponseAgent.java
```

方法：

```text
toResponse(TriageContext context)
```

职责：

```text
把 TriageContext 转成 TriageAnalyzeResponse。
```

主要 action：

```text
ASK_CLARIFICATION
GENERATE_REPORT
TRIGGER_WARNING
```

当 `ASK_CLARIFICATION` 时，会返回：

```text
TriageClarificationData
```

包含：

```text
sessionId
extractedSymptoms
missingFields
pendingSlots
questionPlan
followUpQuestion
progress
questions
```

---

## 11. 报告生成

### 11.1 报告生成入口

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/engine/TriageReplyBuilder.java
```

方法：

```text
generatePreTriageReport(context, triageModelGateway)
```

内部会调用：

```text
DepartmentRecommender
TriageModelGateway.chatWithReportModel
```

如果 LLM 报告失败，会走 fallback report。

---

### 11.2 报告触发来源

报告可以由以下路径触发：

```text
1. 信息完整 -> TriageStateMachine.handleReportGeneration
2. 风险评估接受 -> GENERATE_REPORT
3. LLM fallback 连续失败 -> forceGenerateReport
4. totalTurnCount >= maxTotalTurns -> 强制报告
```

---

## 12. Trace 可观测性

### 12.1 Trace 注解与上下文

目录：

```text
framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/
```

文件：

```text
RagTraceRoot.java
RagTraceNode.java
RagTraceContext.java
RagStreamTraceSupport.java
```

---

### 12.2 AOP 切面

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/RagTraceAspect.java
```

拦截：

```text
@RagTraceRoot
@RagTraceNode
```

落库服务：

```text
RagTraceRecordService
RagTraceRecordServiceImpl
```

落库表：

```text
t_rag_trace_run
t_rag_trace_node
```

---

### 12.3 Triage Trace 节点

主要 trace：

```text
TRIAGE_ANALYZE
  -> TriageStateMachine
  -> TriageSupervisor
  -> NormalizationAgent
  -> RuleAgent
  -> SlotAgent
  -> RiskAgent
  -> QuestionPlanner
  -> TriageResponseAgent
  -> TriageTextModel
  -> TriageReportModel
  -> TriageMemorySummaryModel
```

可查看：

```text
duration_ms
status
node_name
node_type
start_time
end_time
error_message
```

trace 已做 fail-safe：

```text
trace 落库失败只记录 warn，不影响业务。
```

---

## 13. Battle 流程

### 13.1 Battle 服务

文件：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/battle/BattleRunService.java
```

主流程：

```text
run
  -> selectCases
  -> resolveMaxTurns
  -> runCase
  -> runBaseline
  -> invokeBaseline
```

baseline：

```text
official
pure-prompt
pure-skill
```

其中 `official` 调用：

```text
triageOrchestratorService.analyze(request)
```

现在 battle 最大轮数受业务配置限制：

```text
resolveMaxTurns <= triage.session.maxTotalTurns
```

避免 battle 跑到第 9 轮以后还继续问。

---

## 14. 调试常用排查路径

### 14.1 为什么重复问同一个问题？

查：

```text
TriageContext.latestTurnUnderstanding.answeredSlots
TriageContext.answeredSlots
TriageContext.slotState
TriageContext.pendingSlots
TriageContext.lastAskedSlots
TriageContext.riskDecision.unresolvedRiskGaps
TriageContext.askabilityDecisions
```

对应代码：

```text
TurnUnderstandingExecutionEngine
AnsweredSlotFlowCollector
SlotAgent.applyAnsweredSlotsFromTurnUnderstanding
QuestionPlanner.evaluateAskability
TriageContextReducer.applyQuestionPlan
```

---

### 14.2 为什么只有一个“其他”选项？

查：

```text
QuestionOptionProvider.generateOptionsForSlot
TriageReplyBuilder.buildClarificationReply
TriageReplyBuilder.buildClarificationQuestions
```

重点确认：

```text
SlotCode 是否有专属 case
默认兜底是否返回多个选项
context.generatedOptions 是否被 currentSlots 过滤掉
```

---

### 14.3 为什么第 8 轮还不出报告？

查：

```text
TriageContext.totalTurnCount
TriageSessionProperties.maxTotalTurns
TurnLimitHelper.shouldForceReport
TriageStateMachine.execute 入口硬拦截
BattleRunService.resolveMaxTurns
Redis sessionId 是否一致
```

---

### 14.4 为什么 trace 没落库？

查：

```text
rag.trace.enabled
RagTraceAspect.aroundRoot
RagTraceAspect.aroundNode
RagTraceRecordServiceImpl
表 t_rag_trace_run / t_rag_trace_node 字段长度
日志 trace run start 失败 / trace node start 失败
```

---

## 15. 一句话架构总结

当前 triage 是一个：

```text
状态机驱动 + 多 Agent 编排 + Context Reducer 单写者 + SlotState 槽位闭环 + Risk Gap 风险优先 + QuestionPlanner 问题策略 + RAG Trace 可观测
```

的分诊系统。

业务决策不完全交给 LLM，而是由：

```text
LLM 语义理解
规则候选问题
槽位状态机
风险模型
问题规划策略
最大轮次兜底
```

共同完成。
