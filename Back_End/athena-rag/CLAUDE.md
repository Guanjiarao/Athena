# Athena 分诊系统测评项目

## 工作模式（重要）

**Opus 主控模式**：本项目采用 Opus 主控、Sonnet 执行的协作模式。

### 角色分工

**Opus（主控 - 你）**：
- 负责整体规划和架构设计
- 使用 `EnterPlanMode` 进入规划模式，制定详细的实施计划
- 审查和验收 Sonnet 的工作成果
- 做出关键技术决策
- 不直接编写代码或运行测试

**Sonnet（执行者 - 子 Agent）**：
- 负责具体的代码实现
- 运行测试和验证
- 按照 Opus 的计划执行开发任务
- 报告执行结果和遇到的问题

### 工作流程

1. **规划阶段**：Opus 使用 `EnterPlanMode` 进入规划模式，探索代码库，制定实施计划
2. **执行阶段**：Opus 通过 `Agent` 工具派发任务给 Sonnet 子 Agent，Sonnet 执行具体开发和测试
3. **验收阶段**：Opus 审查 Sonnet 的工作成果，验证是否符合预期
4. **迭代阶段**：如有问题，Opus 重新规划，Sonnet 继续执行

### 使用示例

```
# Opus 进入规划模式
EnterPlanMode()

# Opus 派发任务给 Sonnet
Agent(description="实现 Phase 2", prompt="按照计划实现科室推荐功能...", model="sonnet")

# Opus 验收结果
Read(测试报告) -> 分析结果 -> 决定下一步
```

### 并行测试策略（重要）

**问题**：完整的烟雾测试（10个用例）需要 15 分钟，效率太低。

**解决方案**：将测试用例分成两组，并行运行两个测试 agent。

**分组策略**：
- **Group A**：用例 001-005（消化系统 + 呼吸系统前3个）
- **Group B**：用例 006-010（呼吸系统后2个 + 其他）

**并行测试命令**：
```bash
# Agent A 运行 Group A
mvn test -Dtest=TriageEvalRunnerTests#runGroupA -pl bootstrap

# Agent B 运行 Group B  
mvn test -Dtest=TriageEvalRunnerTests#runGroupB -pl bootstrap
```

**注意事项**：
- 两个 agent 同时启动，各自独立运行测试
- 测试报告分别保存为 `triage_eval_report_groupA_*.json` 和 `triage_eval_report_groupB_*.json`
- Opus 等待两个 agent 都完成后，合并结果分析
- 如果 Redis 连接失败，立即停止并通知用户

## 项目概述

这是一个医疗预分诊系统，通过多轮对话收集患者症状信息，最终生成包含风险等级、建议科室和行动建议的分诊报告。

## 核心架构

### 分诊系统主要组件

- **TriageController** (`triage/controller/TriageController.java`): 分诊主入口，接收 `/triage/analyze` 请求
- **TriageOrchestratorService** (`triage/service/TriageOrchestratorService.java`): 分诊编排服务，协调整个分诊流程
- **TriageStateMachine** (`triage/engine/TriageStateMachine.java`): 状态机引擎，管理对话状态转换
- **TurnUnderstandingExecutionEngine** (`triage/worker/TurnUnderstandingExecutionEngine.java`): 轮次理解引擎，解析用户输入

### 数据模型

- **TriageAnalyzeRequest**: 分诊请求（包含 sessionId, userInput, conversationHistory）
- **TriageAnalyzeResponse**: 分诊响应（包含 action, data, message, riskLevel）
- **TurnUnderstanding**: 轮次理解结果（包含意图、症状提取、风险信号等）
- **TriageState**: 分诊状态（包含已收集的槽位、风险评估等）

## 测试用例结构

### 测试用例位置

测试用例位于 `triage/预分诊测试用例/` 目录下，共100个用例，分为20个医疗系统：

1. 消化系统（5个）
2. 呼吸系统（5个）
3. 心血管系统（5个）
4. 神经系统（5个）
5. 骨科运动系统（5个）
6. 皮肤科（5个）
7. 眼科耳鼻喉（5个）
8. 泌尿系统（5个）
9. 妇产科（5个）
10. 儿科（5个）
11. 急症危重（5个）
12. 全科其他（5个）
13. 内分泌代谢（5个）
14. 口腔肛肠（5个）
15. 风湿免疫（5个）
16. 精神心理（5个）
17. 中毒与环境（5个）
18. 外科术后（5个）
19. 眼科听力补充（5个）
20. 体检异常（5个）

### 测试用例格式

每个测试用例包含：

```markdown
## 用例XXX：疾病名称（风险等级）

**输入**：用户初始主诉

**标准对话**：
系统：问题1
用户：回答1
系统：问题2
用户：回答2
...

**评分标准**：
| 维度 | 标准答案 | 分值 |
|------|---------|:----:|
| 风险等级 | 低/中/高风险 | 20 |
| 建议科室 | 科室名称 | 15 |
| 主诉提炼 | 主诉文本 | 15 |
| 症状提取 | 症状列表 | 20 |
| 风险分析 | 分析文本 | 15 |
| 行动建议 | 建议文本 | 15 |
```

## 测评体系设计

### 评分维度（总分100分）

1. **风险等级**（20分）：低风险🟢 / 中风险🟡 / 高风险🔴
2. **建议科室**（15分）：推荐的就诊科室
3. **主诉提炼**（15分）：对用户主诉的提炼
4. **症状提取**（20分）：从对话中提取的症状信息
5. **风险分析**（15分）：风险评估的理由
6. **行动建议**（15分）：给用户的行动建议

### LLM Judge 评分标准

- **100分（完全符合）**：完全匹配标准答案
- **80分（比较符合）**：大部分匹配，有小偏差
- **60分（符合）**：基本匹配，有一定偏差
- **40分（比较不符合）**：部分匹配，偏差较大
- **20分（非常不符合）**：少量匹配，严重偏差
- **0分（完全不符合）**：完全不匹配

## 测试框架

### 现有测试基础设施

项目已有 RAG 评测框架（`src/test/java/com/nageoffer/ai/ragent/rag/eval/`）：

- `RagV3CaseLoader`: 用例加载器
- `RagV3Invoker`: 调用器
- `RagV3RuleChecker`: 规则检查器
- `RagV3ReportWriter`: 报告生成器
- `RagV3EvalRunnerTests`: 测试运行器

### 分诊测评框架（待实现）

需要创建类似的框架用于分诊系统测评：

- `TriageCaseLoader`: 加载100个分诊测试用例
- `TriageInvoker`: 调用分诊系统API
- `TriageLLMJudge`: 使用LLM对结果打分
- `TriageReportWriter`: 生成测评报告
- `TriageEvalRunnerTests`: 测试运行器

## 开发指南

### 运行测试

```bash
# 运行分诊测评
mvn test -Dtest=TriageEvalRunnerTests#runAllCases
```

### 添加新测试用例

1. 在对应的医疗系统文件中添加用例
2. 遵循标准格式
3. 确保评分标准完整

### 调试分诊系统

1. 查看日志：`logs/athena-rag.log`
2. 使用 Swagger UI：`http://localhost:8080/swagger-ui.html`
3. 直接调用 API：`POST /triage/analyze`

## 技术栈

- **框架**：Spring Boot 3.x
- **测试**：JUnit 5
- **LLM**：通过 TriageModelGateway 调用
- **数据库**：PostgreSQL（用于会话记录）

## 沟通语言偏好

**重要**：与用户沟通时请使用中文。用户更习惯阅读中文，英文会造成理解困难。所有的解释、总结、计划都应该用中文书写。

## 注意事项

1. **测试用例格式**：严格遵循 Markdown 格式，便于解析
2. **LLM Judge**：需要配置 LLM API 密钥
3. **并发测试**：注意 API 限流
4. **结果缓存**：可以缓存测试结果避免重复调用

## 下一步工作

1. ✅ 创建 CLAUDE.md 文档
2. ✅ 实现 TriageCaseLoader 解析测试用例
3. ✅ 实现 TriageInvoker 调用分诊系统
4. ✅ 实现 TriageLLMJudge 使用LLM打分
5. ✅ 实现 TriageReportWriter 生成报告
6. ✅ 实现 TriageEvalRunnerTests 运行测试
7. ⏳ 运行完整测评并优化系统

## 已实现的测评框架

### 核心组件

所有组件位于 `src/test/java/com/nageoffer/ai/ragent/triage/eval/` 目录：

1. **TriageCaseLoader** - 测试用例加载器
   - 从 Markdown 文件解析100个测试用例
   - 支持解析：用例编号、疾病名称、风险等级、系统分类、用户输入、标准对话、评分标准
   - 已验证：成功加载100个用例

2. **TriageInvoker** - 分诊系统调用器
   - 模拟多轮对话调用分诊系统
   - 根据标准对话自动回答系统问题
   - 收集完整的对话记录和分诊报告

3. **TriageLLMJudge** - LLM Judge 打分器
   - 使用 LLM 对6个维度分别打分
   - 评分标准：完全符合100%、比较符合80%、符合60%、比较不符合40%、非常不符合20%、完全不符合0%
   - 自动从分诊报告中提取各维度内容

4. **TriageReportWriter** - 报告生成器
   - 生成 JSON 格式的详细报告
   - 生成可读的文本格式报告
   - 报告保存在 `eval-reports/` 目录

5. **TriageEvalRunnerTests** - 测试运行器
   - `runAllCases()`: 运行全部100个用例
   - `runSmokeCases()`: 运行前10个用例（快速验证）
   - 自动统计通过率、平均分、各维度得分

### 数据模型

- **TriageEvalCase**: 测试用例
- **TriageEvalCriteria**: 评分标准（6个维度）
- **TriageEvalResult**: 单个用例的评测结果
- **TriageEvalScore**: 各维度得分
- **TriageEvalReport**: 完整评测报告

### 运行测试

```bash
# 快速验证（前10个用例）
mvn test -Dtest=TriageEvalRunnerTests#runSmokeCases -pl bootstrap

# 完整测评（全部100个用例）
mvn test -Dtest=TriageEvalRunnerTests#runAllCases -pl bootstrap
```
