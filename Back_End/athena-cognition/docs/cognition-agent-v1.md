# Athena Cognition Agent V1 交接文档

> 文档状态：本地开发冻结（节点 1-10 已完成并进入第二次后端交接）  
> 当前工作流：`CognitionGraphWorkflow` / `ActionFeedbackWorkflow`  
> 当前版本：`cognition-agent-v1` / `cognition-graph-workflow-v1` / `action-feedback-workflow-v1`  
> 文档用途：记录 Agent 的详细技术协议和开发过程；后端部署执行以 `docs/cognition-agent-backend-handoff-v1.md` 为准。

## 0. 先看这里

Athena 的 Agent 分两次交接：

1. Agent 开发者先在没有 Athena 正式数据库、正式服务器的情况下，完成 Agent 的业务逻辑、工作流、Prompt、结构化输出、测试和本地运行原型。
2. 后端开发者收到完整交接包后，负责部署 Agent 的正式运行环境，并把它接入 Athena 主后端、数据库、权限、异步任务、日志、推送和生产模型配置。

当前 Android 认知闭环中的数据仍然是 Mock。Agent 本地开发已冻结，后端应按第二次交接文档开始部署、持久化和联调。

Agent 的正式架构如下：

```text
Android
  -> Athena 主后端
  -> 主后端组装经过权限过滤的上下文快照
  -> Cognition Agent
  -> Agent 返回结构化 Proposal
  -> 主后端校验并保存 Proposal
  -> 用户确认
  -> 主后端推进正式业务状态
```

Agent 只能提出建议，不能直接创建正式认知主题、修改正式身体事实或写入 Athena 业务表。

从 2026-08-27 起，“帮我整理”不再定义为“每次生成一篇新的整理文章”，而是定义为：

```text
读取未处理线索和现有个人认知图谱
-> 生成对同一图谱的增量语义更新草稿
-> 节点 6 规划下一步行动
-> 节点 7 组装 GraphUpdateProposal
-> 节点 8 完整安全校验
-> 用户确认
-> 主后端按 graphVersion 原子应用
```

每个用户只有一份持续演化的 `PersonalCognitionGraph`。同一线索重复提交必须返回 `NO_CHANGE`，不能重复创建草稿、主题或图谱节点。

用户完成下一步行动后的反馈使用独立工作流：

```text
用户提交 OCCURRED / NOT_OCCURRED / UNCERTAIN / SKIPPED
-> 节点 9 校验并归一化反馈
-> 节点 10 关闭原行动并组装反馈图谱 Patch
-> 复用节点 8 完整安全校验
-> 返回待确认 Proposal 和 graphPreview
-> 用户确认
-> 主后端按 graphVersion 原子应用
```

`graphPreview` 只是把 Patch 应用到内存副本后的预览，不是正式图谱，也不能替代后端事务。

## 1. 当前责任边界

### 1.1 Agent 开发阶段由 Agent 开发者完成

- 定义每个节点解决的问题和不解决的问题。
- 定义节点输入、输出、字段类型、枚举、空值和错误规则。
- 定义工作流顺序、路由条件和安全边界。
- 编写 Prompt 和模型输出约束。
- 编写不依赖数据库的 Java Agent 代码。
- 编写确定性规则、模拟模型 Provider 和本地测试。
- 使用 JSON fixture 模拟任务输入和任务输出。
- 记录运行 ID、节点版本、输入输出摘要和校验结果。
- 生成后端可以直接运行和验收的交接包。

### 1.2 正式部署阶段由 Athena 后端开发者完成

- 保存线索、Agent 运行记录、节点结果、整理草稿、主题和行动反馈。
- 从登录身份中获取用户，不信任 Android 或 Agent 请求中的用户身份字段。
- 从数据库组装经过权限过滤的上下文快照。
- 调用部署后的 Agent 服务，并保存返回的 Proposal。
- 在用户确认后推进正式业务状态。
- 配置模型 API Key、模型地址、模型名称和调用限额。
- 处理异步执行、超时、重试、幂等、权限和服务间认证。
- 部署 Agent JAR 或等价服务，接入生产日志、指标、链路追踪和推送。

Agent 服务不需要数据库账号。后端可以把它作为独立 Spring Boot 服务部署，也可以把 Agent 核心作为依赖包嵌入自己的服务，但都必须保留“Agent 只返回 Proposal，主后端拥有业务写入权”的边界。

## 2. 当前开发和交接流程

每个 Agent 节点都按同一套流程完成：

```text
1. 业务定义
2. 输入输出协议
3. 上下文白名单
4. 确定性规则和模型边界
5. 本地工作流实现
6. Prompt 和 Provider 接入
7. Schema 与 Policy 校验
8. 正常、失败、拒绝测试
9. 本地评估和可观测性检查
10. 更新本文档
11. 全部节点完成后冻结正式交接版本
```

图谱工作流节点 1-10 已完成本地工程闭环：业务定义、协议、两层上下文白名单、确定性规则、正式工作流、Prompt/Provider、运行时 Schema、业务与模型 Policy、模型冲突记录、正常/失败/拒绝/注入测试、固定评估集、脱敏运行记录和 Micrometer 指标均已实现。尚未完成的是使用真实千问凭证跑在线评估，以及由 Athena 后端部署、鉴权、持久化运行记录、接入正式数据库和在用户确认后原子应用 Patch；这些部署任务已冻结在 `docs/cognition-agent-backend-handoff-v1.md`，不属于本地无数据库 Agent 代码。

本文档每完成一个节点都要补充：

- 节点状态：`DESIGNED`、`IMPLEMENTED`、`TESTED`、`HANDED_OFF`。
- 节点代码位置。
- 请求和响应版本。
- Prompt 版本。
- 测试 fixture 和预期输出。
- 已知限制。
- 后端必须完成的接入任务。
- 版本变更记录。

## 3. 本地真实模型配置约定

本地可以增加真实模型调用，但密钥只能通过环境变量提供，不能写进 Java 源码、`application.yml` 的真实值、Android 代码或 Git 仓库。

约定的环境变量名称：

```text
ATHENA_MODEL_PROVIDER
ATHENA_MODEL_API_KEY
ATHENA_MODEL_BASE_URL
ATHENA_MODEL_NAME
ATHENA_MODEL_ENDPOINT_PATH
ATHENA_MODEL_TIMEOUT_MS
ATHENA_MODEL_JSON_MODE
```

PowerShell 当前窗口临时配置示例。下面的 API Key、Base URL 和模型名必须替换成你实际服务商提供的值：

```powershell
$env:ATHENA_MODEL_PROVIDER = "openai-compatible"
$env:ATHENA_MODEL_API_KEY = "在这里填写真实密钥"
$env:ATHENA_MODEL_BASE_URL = "https://服务商提供的基础地址"
$env:ATHENA_MODEL_NAME = "服务商提供的模型名"
$env:ATHENA_MODEL_ENDPOINT_PATH = "/v1/chat/completions"
$env:ATHENA_MODEL_TIMEOUT_MS = "15000"
$env:ATHENA_MODEL_JSON_MODE = "true"
$env:SPRING_PROFILES_ACTIVE = "model-probe"
.\mvnw.cmd spring-boot:run
```

注意：`ATHENA_MODEL_BASE_URL` 不是完整接口地址时，程序会自动拼接 `ATHENA_MODEL_ENDPOINT_PATH`。如果服务商给出的完整地址已经包含路径，就应把 Base URL 和 Endpoint Path 拆开填写，不能重复拼接 `/v1/chat/completions`。

启动后使用本文档第 8.4 节的探测接口验证真实模型。探测接口返回 `PROVIDER_SUCCEEDED`，才说明 API Key、地址、模型名、请求格式和响应格式全部连通。

IntelliJ IDEA 配置位置：

```text
Run -> Edit Configurations -> 选择 Cognition Agent -> Environment variables
```

在这里添加相同的变量。变量只保存在本机运行配置中，不提交到 Git。

第一版本地测试默认使用 `mock` Provider。只有把 `ATHENA_MODEL_PROVIDER` 设置为 `openai-compatible`，并启用 `model-probe` Profile，才会发起真实模型请求。真实 Provider 是可替换适配器，不能改变节点输入输出协议，也不能直接写数据库。

### 3.1 千问模型的准确配置方式

千问使用的是 OpenAI 兼容协议，因此不需要新增 `QwenIntentModelProvider`。当前代码中：

```text
ATHENA_MODEL_PROVIDER=openai-compatible
```

不要把 `api host` 和 `OpenAI 兼容地址`混为一谈：

| 你手里的信息 | 含义 | 在本项目中的用法 |
| --- | --- | --- |
| API Key | 鉴权密钥 | `ATHENA_MODEL_API_KEY` |
| API Host | 服务商域名或主机地址 | 用来核对地址来源，不单独填写到 Java 代码 |
| OpenAI 兼容地址 | OpenAI-compatible API 的 Base URL 或完整接口地址 | 拆分后填写 `ATHENA_MODEL_BASE_URL` 和 `ATHENA_MODEL_ENDPOINT_PATH` |
| 模型名称 | 千问具体模型 ID | `ATHENA_MODEL_NAME` |

#### 情况 A：兼容地址是 Base URL

如果控制台给出的兼容地址形如：

```text
https://dashscope.aliyuncs.com/compatible-mode/v1
```

则这样配置：

```powershell
$env:ATHENA_MODEL_PROVIDER = "openai-compatible"
$env:ATHENA_MODEL_API_KEY = "你的千问 API Key"
$env:ATHENA_MODEL_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:ATHENA_MODEL_NAME = "控制台显示的千问模型 ID"
$env:ATHENA_MODEL_ENDPOINT_PATH = "/chat/completions"
$env:ATHENA_MODEL_TIMEOUT_MS = "30000"
$env:ATHENA_MODEL_JSON_MODE = "true"
$env:SPRING_PROFILES_ACTIVE = "model-probe"
```

程序最终请求的地址必须是：

```text
https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
```

#### 情况 B：兼容地址已经是完整接口地址

如果控制台给出的地址已经以 `/chat/completions` 结尾，例如：

```text
https://服务商地址/compatible-mode/v1/chat/completions
```

则这样配置：

```powershell
$env:ATHENA_MODEL_BASE_URL = "https://服务商地址/compatible-mode/v1/chat/completions"
$env:ATHENA_MODEL_ENDPOINT_PATH = ""
```

不能再使用默认的 `/v1/chat/completions`，否则最终 URL 会重复拼接，导致 404。

#### 情况 C：你只有 API Host

只有 API Host 不能直接配置。你还需要从千问控制台或接口文档确认兼容路径，例如 Host 后面是否需要：

```text
/compatible-mode/v1
```

本项目不根据 Host 自动猜路径。最可靠的做法是优先使用控制台给出的完整 OpenAI 兼容地址，然后按情况 A 或情况 B 拆分。

#### 模型名

`ATHENA_MODEL_NAME` 必须填写千问控制台或模型列表中真实存在的模型 ID，例如某个具体的 `qwen-*` 模型。不要把展示名称、中文名称或 API Host 填到这里。模型是否支持 JSON mode 也以该模型的接口说明为准。

#### JSON mode

当前代码默认发送：

```json
"response_format": {
  "type": "json_object"
}
```

同时 Prompt 要求模型只返回 `suggestedIntent` 和 `rationale`。如果千问当前模型或兼容地址返回 HTTP 400，并明确提示不支持 `response_format`，将下面配置改为：

```powershell
$env:ATHENA_MODEL_JSON_MODE = "false"
```

Prompt 仍然要求 JSON，Provider 仍然会校验返回内容必须是合法 JSON；关闭 JSON mode 只是去掉请求参数，不是放弃结构化输出。

#### 启动和验证

配置完成后，在 Agent 项目目录执行：

```powershell
cd "D:\aa\athena-zyj (2)\Back_End\athena-cognition-agent"
.\mvnw.cmd spring-boot:run
```

看到 Spring Boot 启动成功后，使用一份完整的 `IntentClassificationRequest` 调用：

```http
POST http://localhost:8080/internal/v1/cognition/nodes/intent-classification/model-probe
Content-Type: application/json
```

只在返回下面结果时，才算第八步的真实服务商验证完成：

```json
{
  "status": "PROVIDER_SUCCEEDED",
  "suggestion": {
    "provider": "openai-compatible",
    "modelName": "你配置的千问模型 ID",
    "promptVersion": "intent-evidence-prompt-v1",
    "suggestedIntent": "RELATED",
    "rationale": "..."
  },
  "error": null
}
```

`PROVIDER_SUCCEEDED` 只证明 API Key、地址、模型名、请求格式和模型响应可以连通。正式分类流程已经具备 Schema 校验、模型内容 Policy 校验和冲突记录；真实千问仍需在本机使用固定评估集完成在线验收，才能确认其真实输出质量。

## 4. 第一个节点：业务定义

### 4.1 节点名称

```text
INTENT_AND_EVIDENCE_CLASSIFICATION
```

节点版本：

```text
intent-evidence-v1
```

### 4.2 节点解决的问题

判断用户这次输入想表达哪一种准确意图，并判断这条输入在证据上属于什么性质，供后续工作流路由使用。

允许的业务意图只有三个：

| 枚举 | 用户表达的意思 | 后续用途 |
| --- | --- | --- |
| `RELATED` | 用户认为内容和自己的经历有关 | 进入候选主题匹配和线索整理 |
| `QUESTION` | 用户想弄明白一个问题，但不等于确认自己有该问题 | 进入待回答问题或等待确认 |
| `KNOWLEDGE_ONLY` | 用户只想保存知识，以后可能查看 | 进入知识保存，不进入身体事实和健康预测 |

这三个枚举表示用户意图，不表示疾病、诊断或概率。

### 4.3 节点不解决的问题

本节点不能判断：

- 用户有什么症状。
- 用户是否一定存在某种身体问题。
- 用户是否患有某种疾病。
- 用户未来出现某种症状的概率。
- 线索属于哪个正式认知主题。
- 是否达到创建整理草稿的阈值。
- 是否创建正式认知主题。
- 是否给用户推送文章、视频或提醒。
- 是否修改数据库中的身体事实、主题或行动。

### 4.4 允许进入本节点的输入

第一版只处理当前 Android 代码真实产生的、已经保存的 `Clue` 对象。现有代码虽然在 `CognitionModels.ClueType` 中预留了 `USER_QUESTION`，但 `CognitionQuestionMarkActivity` 实际调用的仍是 `createArticleClue(...)`，所以当前真实数据的 `type` 仍然是 `ARTICLE_HIGHLIGHT`。

因此第一版输入只允许：

| `clue.type` | 来源 | 说明 |
| --- | --- | --- |
| `ARTICLE_HIGHLIGHT` | 用户在文章中选择文字并提交“和我有关 / 我有疑问 / 保存为知识” | 与 Android 当前 `Clue` 和后端创建线索接口一致 |

`USER_QUESTION` 暂时只是预留枚举，不是当前第一节点的可用输入。等项目真的增加独立问题的后端创建接口后，再提升协议版本或新增节点输入类型。

`BODY_RECORD` 不进入本节点。结构化身体记录已经是另一类输入，后续由独立的身体记录标准化节点处理。第一节点收到 `BODY_RECORD` 或当前尚未实现的 `USER_QUESTION` 独立对象时，返回 `UNSUPPORTED_SOURCE_TYPE`，由工作流编排器转到未来节点。

### 4.5 节点输出给谁

本节点输出给 Agent 内部的工作流编排器和 Athena 主后端，不直接给 Android 页面展示。

路由结果：

```text
RELATED
  -> MATCH_EXISTING_TOPIC_CANDIDATE

QUESTION
  -> QUESTION_INBOX

KNOWLEDGE_ONLY
  -> KNOWLEDGE_INBOX

无法判断
  -> NEEDS_CLARIFICATION
```

主后端以后根据这个结果保存线索状态，但不能把本节点输出直接当成正式身体事实。

## 5. 第一个节点：输入协议

### 5.1 输入 JSON

这里必须区分两层字段：

- `clue` 内部字段沿用现有 Android `CognitionModels.Clue` 和 `cognition-contract-v1` 的字段名。
- 外层 `contractVersion`、`nodeVersion`、`runId`、`idempotencyKey`、`triggerType`、`contextSnapshotId` 是 Agent 运行所需的新元数据，不是 Android 现有业务字段。

上一版曾把现有字段改写成 `declaredIntent`、`declaredRelation`，并新增了嵌套 `article`、`sectionTitle`、`contentHash`、`userNote`。这些字段目前不在 Android 的 `ArticleClueInput`、`Clue` 或现有创建接口中，本版不再使用。

注意：`clue.type` 描述 Agent 收到的线索类型；它不等同于现有认知数据契约中 `Evidence.sourceType` 的值。后端以后把线索作为证据引用时，仍按现有契约保存为 `Evidence.sourceType=CLUE`、`sourceId=clue.id`。

```json
{
  "contractVersion": "cognition-agent-v1",
  "nodeVersion": "intent-evidence-v1",
  "runId": "run_20260820_0001",
  "idempotencyKey": "clue_101:intent-evidence-v1",
  "triggerType": "CLUE_CREATED",
  "contextSnapshotId": "ctx_local_0001",
  "clue": {
    "id": "clue_1001",
    "type": "ARTICLE_HIGHLIGHT",
    "intent": "RELATED",
    "relationType": "CURRENT",
    "helpRequestType": "OBSERVE",
    "articleId": "1024",
    "articleTitle": "经期前情绪变化值得怎样记录",
    "articleType": 100,
    "selectedText": "经期前几天出现的情绪变化，需要结合时间和重复情况继续观察。",
    "questionType": null,
    "questionText": null,
    "occurredAt": "2026-08-10T00:00:00+08:00",
    "cycleRelation": "BEFORE_PERIOD",
    "severity": 3,
    "resolved": false,
    "source": "KNOWLEDGE_ARTICLE",
    "status": "PENDING",
    "suggestedTopicId": null,
    "suggestedTopicTitle": "经前情绪变化",
    "originalLabel": "和我有关",
    "createdAt": "2026-08-20T10:00:00+08:00",
    "updatedAt": "2026-08-20T10:00:00+08:00"
  }
}
```

### 5.2 输入字段定义

| 字段 | 类型 | 必填 | 允许值或规则 | 来源 | 是否是证据 |
| --- | --- | --- | --- | --- | --- |
| `contractVersion` | string | 是 | `cognition-agent-v1` | Agent 调用方 | 否，协议元数据 |
| `nodeVersion` | string | 是 | `intent-evidence-v1` | Agent 调用方 | 否，版本元数据 |
| `runId` | string | 是 | 单次运行唯一 ID | 主后端；本地为 fixture | 否，用于追踪 |
| `idempotencyKey` | string | 是 | 同一线索同一节点保持不变 | 主后端；本地为 fixture | 否，用于防重复 |
| `triggerType` | enum | 是 | `CLUE_CREATED`、`RETRY` | 主后端 | 否，用于运行追踪 |
| `contextSnapshotId` | string | 是 | 本次上下文快照 ID | 主后端；本地为 fixture | 否，用于追溯 |
| `clue.id` | string | 是 | 已保存线索 ID | 主后端；本地为 fixture | 只能作为证据 ID |
| `clue.type` | enum | 是 | 当前只允许 `ARTICLE_HIGHLIGHT` | 主后端 | 是来源类型 |
| `clue.intent` | enum | 是 | `RELATED`、`QUESTION`、`KNOWLEDGE_ONLY` | 用户最终选择；现有前端字段 | 是用户意图 |
| `clue.relationType` | enum/null | 条件必填 | `CURRENT`、`PAST`、`OBSERVE`、`KNOWLEDGE_ONLY` | 用户确认页；现有前端字段 | 是用户声明，不是诊断 |
| `clue.helpRequestType` | enum | 是 | `OBSERVE`、`KNOWLEDGE`、`ATTENTION`、`SAVE_ONLY` | 用户确认页；现有前端字段 | 是用户希望获得的帮助 |
| `clue.articleId` | string | 是 | 非空 | 文章服务/主后端 | 否，来源 ID |
| `clue.articleTitle` | string | 是 | 非空 | 文章服务/主后端 | 否，来源标题 |
| `clue.articleType` | int | 否 | 当前默认 `100` | 文章服务/主后端 | 否 |
| `clue.selectedText` | string | 是 | 非空；建议不超过 3000 字 | 用户选择的文章原文 | 是知识内容，不能当身体事实 |
| `clue.questionType` | enum/null | `QUESTION` 时必填 | `IS_COMMON`、`POSSIBLE_CAUSES`、`SELF_CARE`、`PROFESSIONAL_HELP`、`CUSTOM` | 用户问题页 | 是问题类型 |
| `clue.questionText` | string/null | `QUESTION` 时必填 | 非空；建议不超过 2000 字 | 用户问题页 | 是用户问题，不是身体事实 |
| `clue.occurredAt` | string/null | 否 | ISO 8601 | 用户确认页 | 是用户填写的时间声明 |
| `clue.cycleRelation` | enum | 是 | `BEFORE_PERIOD`、`DURING_PERIOD`、`AFTER_PERIOD`、`NO_RELATION`、`UNKNOWN` | 用户确认页 | 是用户填写的周期关系 |
| `clue.severity` | int/null | 否 | `0` 到 `10` | 用户确认页 | 是用户填写的程度 |
| `clue.resolved` | boolean/null | 否 | true/false | 用户确认页 | 是用户填写的状态 |
| `clue.source` | enum/string | 是 | 当前文章入口为 `KNOWLEDGE_ARTICLE` | 主后端校验 | 否，来源渠道 |
| `clue.status` | enum | 是 | `PENDING`、`PROCESSING`、`ORGANIZED`、`DISMISSED` | 主后端 | 否，业务状态 |
| `clue.suggestedTopicId` | string/null | 否 | 当前通常为空 | 主后端 | 否，不是本节点结论 |
| `clue.suggestedTopicTitle` | string/null | 否 | 前端当前传入的简单标签 | 前端暂传/主后端校验 | 否，不能视为 AI 结果 |
| `clue.originalLabel` | string | 是 | 例如“和我有关” | 前端 | 是用户当时选择的入口 |
| `clue.createdAt` | string | 是 | ISO 8601 | 主后端 | 否 |
| `clue.updatedAt` | string | 是 | ISO 8601 | 主后端 | 否 |

### 5.3 输入枚举和条件规则

```text
clue.type:
ARTICLE_HIGHLIGHT

clue.intent:
RELATED
QUESTION
KNOWLEDGE_ONLY

clue.relationType:
CURRENT
PAST
OBSERVE
KNOWLEDGE_ONLY
```

条件规则：

| 条件 | 要求 |
| --- | --- |
| `type=ARTICLE_HIGHLIGHT` | `articleId`、`articleTitle`、`selectedText`、`intent` 必填 |
| `intent=RELATED` | `relationType` 必须是 `CURRENT`、`PAST` 或 `OBSERVE`；不能把文章阅读当成身体事实 |
| `intent=QUESTION` | `questionType`、`questionText` 必填；当前文章问题的 `relationType` 必须为 null |
| `intent=KNOWLEDGE_ONLY` | `relationType` 必须为 `KNOWLEDGE_ONLY`；`helpRequestType` 应为 `SAVE_ONLY` |
| `relationType=KNOWLEDGE_ONLY` | `intent` 必须为 `KNOWLEDGE_ONLY`，不得进入身体事实、主题证据或健康预测 |
| `BODY_RECORD` 或独立 `USER_QUESTION` | 本节点当前不处理，返回 `UNSUPPORTED_SOURCE_TYPE` |
| 用户声明字段与模型判断冲突 | 以用户明确声明为准，模型不得覆盖 |

### 5.4 空值规则

- 必填字段缺失、空字符串或只包含空格，属于非法输入。
- 条件不适用的对象必须使用 `null`，不能伪造空对象。
- 可选字符串没有内容时使用 `null`，不使用含义不明确的空字符串。
- `questionType`、`questionText` 只在 `intent=QUESTION` 时填写。
- `relationType` 在 `intent=QUESTION` 时按当前 Android 代码为 null。
- `source`、`status`、`id`、`createdAt`、`updatedAt` 由主后端生成或维护，不由 Android 创建请求提供。
- 前端 Java 对象中的默认空字符串是内存对象实现细节；Agent JSON 使用 `null` 表示不适用的条件字段。
- `contextSnapshotId`、`runId`、`clue.id` 不能为 null。

## 6. 第一个节点：输出协议

### 6.1 成功输出 JSON

```json
{
  "contractVersion": "cognition-agent-v1",
  "nodeVersion": "intent-evidence-v1",
  "runId": "run_20260820_0001",
  "clueId": "clue_101",
  "nodeId": "INTENT_AND_EVIDENCE_CLASSIFICATION",
  "status": "SUCCEEDED",
  "intent": "RELATED",
  "evidenceClass": "USER_PERSONAL_CLAIM",
  "factEligibility": "CANDIDATE_ONLY",
  "decisionSource": "USER_DECLARED",
  "ambiguityCode": "NONE",
  "nextRoute": "MATCH_EXISTING_TOPIC_CANDIDATE",
  "evidenceIds": ["clue_101"],
  "policyResult": "PASS",
  "schemaResult": "PASS",
  "modelSuggestion": {
    "provider": "openai-compatible",
    "modelName": "qwen-plus",
    "promptVersion": "intent-evidence-prompt-v1",
    "suggestedIntent": "RELATED",
    "rationale": "用户明确将这段内容标记为和自己有关",
    "inputTokens": 100,
    "outputTokens": 20,
    "totalTokens": 120,
    "estimatedCost": null
  },
  "modelConflict": false,
  "observation": {
    "runId": "run_20260820_0001",
    "triggerType": "CLUE_CREATED",
    "userDecision": "RELATED",
    "workflowVersion": "cognition-workflow-v1",
    "nodeVersion": "intent-evidence-v1",
    "promptVersion": "intent-evidence-prompt-v1",
    "modelProvider": "openai-compatible",
    "modelName": "qwen-plus",
    "contextSnapshotId": "ctx_local_0001",
    "nodes": [],
    "evidenceIds": ["clue_101"],
    "latencyMs": 620,
    "inputTokens": 100,
    "outputTokens": 20,
    "totalTokens": 120,
    "estimatedCost": null,
    "retryCount": 0,
    "schemaResult": "PASS",
    "policyResult": "PASS",
    "modelPolicyResult": "PASS",
    "modelCallStatus": "SUCCEEDED",
    "modelErrorCode": null,
    "modelConflict": false
  },
  "error": null
}
```

### 6.2 输出字段定义

| 字段 | 类型 | 必填 | 允许值或规则 | 用途 |
| --- | --- | --- | --- | --- |
| `contractVersion` | string | 是 | 与输入一致 | 协议版本 |
| `nodeVersion` | string | 是 | 与输入一致 | 节点版本 |
| `runId` | string | 是 | 与输入一致 | 关联整次运行 |
| `clueId` | string | 是 | 与输入一致 | 追溯原始线索 |
| `nodeId` | string | 是 | 固定为本节点名称 | 标识节点 |
| `status` | enum | 是 | `SUCCEEDED`、`NEEDS_CLARIFICATION`、`REJECTED`、`FAILED` | 执行状态 |
| `intent` | enum/null | 成功时是 | `RELATED`、`QUESTION`、`KNOWLEDGE_ONLY` | 用户意图 |
| `evidenceClass` | enum/null | 成功时是 | `ARTICLE_KNOWLEDGE`、`USER_QUESTION`、`USER_PERSONAL_CLAIM`、`USER_DECLARED_RELEVANCE`、`UNKNOWN` | Agent 新增的证据性质，不替代现有 `Evidence.factLevel` |
| `factEligibility` | enum/null | 成功时是 | `NOT_BODY_FACT`、`CANDIDATE_ONLY` | 是否能进入后续身体事实流程 |
| `decisionSource` | enum/null | 成功时是 | `USER_DECLARED`、`RULE`、`MODEL_ASSISTED` | 结果依据 |
| `ambiguityCode` | enum/null | 成功时是 | `NONE`、`MIXED_INTENT`、`INSUFFICIENT_TEXT`、`CONFLICTING_INPUT` | 是否存在歧义 |
| `nextRoute` | enum/null | 成功时是 | `MATCH_EXISTING_TOPIC_CANDIDATE`、`QUESTION_INBOX`、`KNOWLEDGE_INBOX`、`NEEDS_CLARIFICATION` | 工作流下一路由 |
| `evidenceIds` | string[] | 成功时是 | 至少包含当前 `clueId` | 证据追溯 |
| `policyResult` | enum/null | 有结果时是 | `PASS`、`BLOCK` | 安全策略结果 |
| `schemaResult` | enum | 是 | `PASS`、`FAIL`、`NOT_RUN` | 本次模型输出是否实际通过运行时 Schema 校验 |
| `modelSuggestion` | object/null | 模型建议通过 Schema 和模型 Policy 时存在 | 只作为辅助建议 | 不拥有最终业务决定权 |
| `modelConflict` | boolean | 是 | 模型建议是否与最终用户意图不一致 | 只记录冲突，不覆盖用户选择 |
| `observation` | object | 是 | 本次运行的脱敏节点摘要、版本、耗时、Token、成本和校验结果 | 由后端以后持久化 |
| `error` | object/null | 失败时是 | 见错误结构 | 机器可读错误 |

### 6.3 输出语义规则

| 输入情形 | 输出 intent | evidenceClass | factEligibility | nextRoute |
| --- | --- | --- | --- | --- |
| 文章标记为“和我有关”，关系为当前或过去 | `RELATED` | `USER_PERSONAL_CLAIM` | `CANDIDATE_ONLY` | `MATCH_EXISTING_TOPIC_CANDIDATE` |
| 文章标记为“和我有关”，但选择“我不确定，但想观察” | `RELATED` | `USER_DECLARED_RELEVANCE` | `CANDIDATE_ONLY` | `MATCH_EXISTING_TOPIC_CANDIDATE` |
| 文章标记为“我有疑问” | `QUESTION` | `USER_QUESTION` | `NOT_BODY_FACT` | `QUESTION_INBOX` |
| 文章标记为“为以后保存” | `KNOWLEDGE_ONLY` | `ARTICLE_KNOWLEDGE` | `NOT_BODY_FACT` | `KNOWLEDGE_INBOX` |
| 当前版本不支持的独立用户问题 | null | `UNKNOWN` | `NOT_BODY_FACT` | `NEEDS_CLARIFICATION` |
| 信息不足或互相矛盾 | null | `UNKNOWN` | `NOT_BODY_FACT` | `NEEDS_CLARIFICATION` |

`CANDIDATE_ONLY` 仍然不是正式身体事实，只表示这条输入可以交给后续主题候选匹配。正式身体事实必须经过后续用户确认或结构化身体记录流程。

### 6.4 错误结构

```json
{
  "code": "MODEL_OUTPUT_INVALID",
  "message": "模型输出不符合节点协议",
  "retryable": false,
  "field": "intent",
  "details": null
}
```

错误码第一版固定为：

```text
INVALID_REQUEST
MISSING_REQUIRED_FIELD
INVALID_ENUM
TEXT_TOO_LONG
UNSUPPORTED_SOURCE_TYPE
UNSUPPORTED_VERSION
MODEL_TIMEOUT
MODEL_UNAVAILABLE
MODEL_OUTPUT_INVALID
POLICY_BLOCKED
INTERNAL_ERROR
```

错误规则：

- `REJECTED`：输入协议错误、版本不支持、节点不适用。`error` 必填。
- `FAILED`：模型超时、服务不可用或内部异常。`error` 必填，注明 `retryable`。
- `NEEDS_CLARIFICATION`：输入没有技术错误，但无法安全判断。不能把它当成 `FAILED`，也不能自动猜测为 `RELATED`。
- `SUCCEEDED`：`error` 必须为 null。
- 任何失败都不能删除原始线索。

### 6.5 关于“置信度”和概率

第一节点不输出 `0.62`、`62%` 之类的数字。模型自报的数字不是经过校准的统计概率，不能用于健康判断。

第一版业务结论只记录：

- `decisionSource`
- `ambiguityCode`
- `policyResult`

可观测性另行记录模型调用的 Token、耗时和按配置单价计算的估算成本，但这些不是健康概率。

以后如果业务确实需要概率，必须由独立的统计模型提供，并同时记录统计模型版本、训练数据范围、校准方法和评估结果。LLM 只能解释这个已经存在的统计结果。

## 7. 第一个节点：上下文白名单

### 7.1 节点内部上下文白名单

节点内部上下文由 `IntentContext` 表示。它是第一个节点内部确定性规则和校验逻辑可以使用的字段集合，不等于可以全部发送给模型。

| 字段 | 来自哪里 | 节点用途 | 能否作为证据 |
| --- | --- | --- | --- |
| `clue.id` | 主后端保存线索后生成 | 追溯原始输入 | 只能作为证据 ID |
| `clue.type` | 主后端根据业务入口确定 | 判断当前版本是否支持 | 是来源信息，不是身体事实 |
| `clue.intent` | 用户最终选择 | 判断明确意图 | 是用户意图证据 |
| `clue.relationType` | 用户确认页选择 | 判断用户表达的是当前、过去、观察还是知识保存 | 是用户声明，不是诊断 |
| `clue.helpRequestType` | 用户确认页选择 | 判断用户希望 Athena 怎样帮助 | 是用户需求，不是诊断 |
| `clue.articleId` | 文章服务/主后端 | 标记文章来源 | 否，属于来源信息 |
| `clue.articleTitle` | 文章服务/主后端 | 辅助理解文章主题 | 否，不能证明用户情况 |
| `clue.articleType` | 文章服务/主后端 | 保留现有文章类型 | 否 |
| `clue.selectedText` | 用户选中的文章原文 | 判断用户标记了什么内容 | 是知识内容，不能作为用户身体事实 |
| `clue.questionType` | 用户问题页 | 判断问题类别 | 是问题类型，不是身体事实 |
| `clue.questionText` | 用户问题页 | 判断用户想问什么 | 是用户问题，不是身体事实 |
| `clue.occurredAt` | 用户确认页 | 保留用户填写的发生时间 | 是用户时间声明，不是诊断 |
| `clue.cycleRelation` | 用户确认页 | 保留用户填写的周期关系 | 是用户声明，不是诊断 |
| `clue.severity` | 用户确认页 | 保留用户填写的程度 | 是用户声明，不是诊断 |
| `clue.resolved` | 用户确认页 | 保留用户填写的缓解状态 | 是用户声明，不是诊断 |
| `clue.originalLabel` | Android 当前入口 | 还原用户点击的入口 | 是用户操作证据 |
| `clue.suggestedTopicTitle` | 前端当前传入 | 仅作用户侧标签参考 | 否，不能视为 Agent 结论 |
| `clue.source`、`clue.status`、`clue.createdAt`、`clue.updatedAt` | 主后端 | 校验和追踪 | 否，不作为语义证据 |

### 7.2 模型可见上下文白名单

模型可见上下文由独立的 `IntentModelContext` 表示，只包含：

```text
explicitIntent
relationType
helpRequestType
articleTitle
selectedText
questionType
questionText
cycleRelation
```

固定转换链路为：

```text
CluePayload
-> IntentContextBuilder
-> IntentContext（节点内部白名单）
-> IntentModelContextBuilder
-> IntentModelContext（模型可见白名单）
-> IntentModelProvider
```

`IntentModelProvider` 的 Java 接口只接受 `IntentModelContext`，因此 Provider 在类型层面无法读取 `clue.id`、`articleId`、`source`、`status`、主题建议和时间字段。测试会解析实际发送给模型的 Prompt JSON，并断言字段集合必须恰好等于上述 8 个字段。

### 7.3 禁止传入的字段

第一节点禁止接收以下内容：

- 用户 Token、密码、手机号、身份证号和设备标识。
- 用户全部文章阅读历史。
- 用户全部健康记录、周期记录和手环数据。
- 用户全部认知主题、全部草稿和全部行动历史。
- 其他用户的任何数据。
- 文章完整正文；第一版只允许当前 `clue.selectedText` 和 `clue.articleTitle`。
- 数据库连接信息、模型 API Key 和系统内部密钥。
- 其他节点未经筛选的原始输出。
- 当前 Android 和后端不存在的 `declaredIntent`、`declaredRelation`、`userNote`、`sectionTitle`、`contentHash`、`locale` 等字段。
- 已经计算好的疾病诊断、风险等级或模型概率。

后续节点需要历史主题、周期记录或统计数据时，由主后端为那个具体节点重新组装最小上下文，不能因为第一个节点需要少量信息，就把所有历史数据一次性传给 Agent。

### 7.4 证据边界

必须遵守以下转换限制：

```text
文章原文
  -> 只能证明文章表达了什么

用户问题
  -> 只能证明用户想了解什么

用户选择“和我有关”
  -> 只能证明用户主动声明有关

`clue.questionText`
  -> 只能证明用户提出了问题

`clue.selectedText`
  -> 只能证明文章原文被用户标记

结构化身体记录
  -> 不属于本节点，交给后续记录节点
```

禁止以下转换：

```text
文章提到痛经 -> 用户一定痛经
用户点击“我有疑问” -> 用户确认有该症状
`clue.selectedText` 提到一次不适 -> 用户患有某种疾病
用户选择“和我有关” -> 立即创建正式认知主题
```

## 8. 当前交接状态

### 已确定

- Agent 与 Athena 主后端的职责边界。
- 第一个节点的名称和业务范围。
- 第一个节点当前只接收现有 `Clue.type=ARTICLE_HIGHLIGHT`；`USER_QUESTION` 是 Android 预留枚举，尚未有真实创建入口。
- 三种业务意图及其含义。
- 输入字段、必填条件、枚举、空值规则和版本字段。
- 输出字段、路由、事实资格和错误结构。
- 上下文白名单及证据边界。
- 本地同步执行：输入 JSON 进入，输出 JSON 返回。
- 本地真实模型通过环境变量读取 API Key；默认测试使用 Mock Provider。

### 8.1 2026-08-26 第一节点代码进度

当前节点状态：

```text
业务协议：DESIGNED
本地核心代码：IMPLEMENTED
同步 HTTP 接口：TESTED
Mock Provider：TESTED
真实模型 Provider 适配器：TESTED（使用本地模拟 HTTP 服务）
独立模型上下文边界：TESTED
正式 JSON Schema：TESTED（文件语法和 Java 合同字段一致性）
运行时独立 Schema 校验器：TESTED
模型建议冲突路由：TESTED
模型内容 Policy：TESTED
固定本地评估集：TESTED（9 条案例，3 条冲突全部识别）
脱敏运行记录和 Micrometer 指标：TESTED
真实服务商 API Key 连通性：PENDING_USER_CONFIGURATION
正式后端交接：NOT_HANDED_OFF
```

已完成：

```text
第 1 步 Agent 合同类和枚举
第 2 步 输入校验器
第 3 步 上下文白名单构建器
第 4 步 确定性分类服务
第 5 步 Policy 校验器
第 6 步 同步 HTTP 接口
第 7 步 Mock Provider
第 8 步 真实模型 Provider
第 9 步 运行时 Schema 校验器
第 10 步 模型建议路由与冲突处理
第 11 步 失败、安全和 Prompt 注入测试
第 12 步 本地评估、运行记录和指标
```

代码位置：

```text
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/contract/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/validation/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/context/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/service/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/policy/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/web/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/provider/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/schema/
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/observability/
Back_End/athena-cognition-agent/src/main/resources/schemas/intent-evidence-v1/
```

测试位置：

```text
Back_End/athena-cognition-agent/src/test/java/com/whu/software/athena/cognitionagent/intent/IntentNodeCoreTest.java
Back_End/athena-cognition-agent/src/test/java/com/whu/software/athena/cognitionagent/intent/web/
Back_End/athena-cognition-agent/src/test/java/com/whu/software/athena/cognitionagent/intent/provider/
Back_End/athena-cognition-agent/src/test/java/com/whu/software/athena/cognitionagent/intent/schema/
Back_End/athena-cognition-agent/src/test/resources/fixtures/intent-evidence-v1/
```

第一节点专项历史快照测试结果（2026-08-26）：

```text
Tests run: 33
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

验证命令：

```powershell
.\mvnw.cmd -o '-Dmaven.repo.local=D:\aa\athena-zyj (2)\.codex-build-temp\athena-cognition-agent-m2\repository' test
```

当前实现仍然不包含数据库、异步任务、用户鉴权和正式业务状态写入。正式分类接口已经调用当前配置的模型 Provider，但主分类结果仍由用户声明、确定性规则和 Policy 产生；模型只提供辅助建议。模型冲突、超时、非法输出和 Policy 拒绝均不会覆盖用户选择，也不会让本来合法的确定性结果失败。

### 8.2 第 6 步：同步 HTTP 接口

本地接口如下：

```http
POST /internal/v1/cognition/nodes/intent-classification
Content-Type: application/json
```

请求体直接使用本文档第 5 节的 `IntentClassificationRequest` JSON，不新增一套 HTTP 字段。

接口行为：

- JSON 合法且业务校验通过：返回 `200` 和 `status=SUCCEEDED`。
- JSON 合法但业务输入不合格：仍返回 `200`，响应中的 `status=REJECTED`，具体原因在 `error`。
- 当前节点无法安全判断：返回 `status=NEEDS_CLARIFICATION`。
- 当前核心代码不会写数据库，不会创建正式主题，也不会调用 Android。

后端以后接入时，可以直接把主后端组装好的同一份请求发送到这个接口；不要改字段名，也不要让 Android 直接调用这个内部接口。

### 8.3 第 7 步：Mock Provider

代码位置：

```text
Back_End/athena-cognition-agent/src/main/java/com/whu/software/athena/cognitionagent/intent/provider/
```

`IntentModelProvider` 是未来真实模型的替换接口，`MockIntentModelProvider` 是本地测试替身。它只返回结构化的 `IntentModelSuggestion`，不产生概率，不写数据库，也不能覆盖用户明确选择和 Policy 结果。

当前节点的意图已经由 Android 保存的 `clue.intent` 明确给出，因此正式结果仍以确定性分类为准。同步主流程会调用当前 Provider：本地默认使用 Mock Provider，配置 `openai-compatible` 时调用千问。Provider 返回的是辅助建议；建议不一致时只设置 `modelConflict=true`，不能修改正式 `intent`。

### 8.4 第 8 步：真实模型 Provider

真实 Provider 使用 `OpenAI-compatible chat completions` 协议。这里的 `OpenAI-compatible` 只描述 HTTP 请求格式，不代表必须使用某一家模型服务商。只要服务商支持以下形式，就可以直接配置：

```http
POST {BASE_URL}{ENDPOINT_PATH}
Authorization: Bearer {API_KEY}
Content-Type: application/json
```

默认接口路径：

```text
/v1/chat/completions
```

主要代码：

```text
IntentModelProperties.java                 环境变量配置
ModelGatewayConfiguration.java             在 mock 和真实统一网关之间切换
OpenAiCompatibleModelGateway.java          所有节点共享的真实 HTTP、鉴权、Token 和成本实现
GatewayIntentModelProvider.java             第一个节点到统一网关的适配器
IntentModelProviderConfiguration.java      注册第一个节点适配器
IntentModelPromptBuilder.java               构建最小白名单 Prompt
IntentModelProviderException.java           统一模型错误
IntentModelProbeController.java             本地真实模型连通性探测
```

#### 环境变量

真实密钥只能放在环境变量中：

```text
ATHENA_MODEL_PROVIDER=openai-compatible
ATHENA_MODEL_API_KEY=真实 API Key
ATHENA_MODEL_BASE_URL=服务商提供的基础地址
ATHENA_MODEL_NAME=服务商提供的模型名
ATHENA_MODEL_ENDPOINT_PATH=/v1/chat/completions
ATHENA_MODEL_TIMEOUT_MS=15000
ATHENA_MODEL_JSON_MODE=true
ATHENA_MODEL_INPUT_COST_PER_MILLION=可选的每百万输入 Token 单价
ATHENA_MODEL_OUTPUT_COST_PER_MILLION=可选的每百万输出 Token 单价
```

`ATHENA_MODEL_BASE_URL` 示例结构是 `https://模型服务商域名`。不能根据 API Key 猜测 Base URL 或模型名，必须查对应服务商的接口文档。

#### Provider 输入

真实模型只能看到 `IntentModelContext` 定义的模型可见白名单字段：

```text
explicitIntent
relationType
helpRequestType
articleTitle
selectedText
questionType
questionText
cycleRelation
```

它看不到用户身份、Token、完整文章、完整健康历史、数据库连接和其他主题。

#### Provider 输出

模型必须只返回：

```json
{
  "suggestedIntent": "QUESTION",
  "rationale": "用户明确提出想弄明白该内容"
}
```

Provider 将其转换为：

```json
{
  "provider": "openai-compatible",
  "modelName": "实际模型名",
  "promptVersion": "intent-evidence-prompt-v1",
  "suggestedIntent": "QUESTION",
  "rationale": "用户明确提出想弄明白该内容"
}
```

不允许输出概率、疾病诊断、正式主题或数据库修改指令。

#### 本地真实模型探测接口

该接口只在启用 `model-probe` Profile 时存在：

```http
POST /internal/v1/cognition/nodes/intent-classification/model-probe
```

探测流程：

```text
收到正式请求合同
-> 运行输入校验
-> 构建上下文白名单
-> 调用当前配置的 IntentModelProvider
-> 返回模型建议或结构化错误
```

它不会把模型建议保存到数据库，也不会改变正式分类结果。

成功响应：

```json
{
  "status": "PROVIDER_SUCCEEDED",
  "suggestion": {
    "provider": "openai-compatible",
    "modelName": "实际模型名",
    "promptVersion": "intent-evidence-prompt-v1",
    "suggestedIntent": "QUESTION",
    "rationale": "用户明确提出想弄明白该内容"
  },
  "error": null
}
```

失败响应会使用现有 Agent 错误码：

| 情况 | 错误码 | 是否可重试 |
| --- | --- | --- |
| 请求超时 | `MODEL_TIMEOUT` | 是 |
| 网络失败、429、5xx | `MODEL_UNAVAILABLE` | 是 |
| 401、403、其他不可恢复 HTTP 错误 | `MODEL_UNAVAILABLE` | 否 |
| 返回内容不是要求的 JSON | `MODEL_OUTPUT_INVALID` | 否 |

#### 当前真实 Provider 的边界

- 已完成真实 HTTP 调用、Bearer 鉴权、超时、JSON 模式、结构化解析和错误映射。
- 已使用本地模拟 HTTP 服务验证请求和响应解析，没有使用真实 API Key 运行测试。
- 当前只兼容 Chat Completions 风格接口；使用其他协议的服务商需要新增适配器，不能硬改现有合同。
- 模型建议已经进入正式分类响应，但只作为 `modelSuggestion` 辅助字段；用户明确选择与确定性规则始终优先。
- 模型输出先经过运行时 Schema，再经过模型内容 Policy；不通过时不展示模型建议，只在 `observation` 记录失败原因。

### 8.5 正式 JSON Schema

当前已建立四份 Draft 2020-12 Schema：

```text
intent-classification-request.schema.json   节点请求合同
intent-classification-response.schema.json  节点响应合同
intent-model-context.schema.json             模型可见上下文合同
intent-model-output.schema.json              模型原始输出合同
```

这些文件位于：

```text
Back_End/athena-cognition-agent/src/main/resources/schemas/intent-evidence-v1/
```

Schema 文件是机器可读的格式规则。当前测试已经确认文件可以解析、请求和响应字段与 Java 合同一致、模型上下文只能包含 8 个字段、模型原始输出只能包含 `suggestedIntent` 和 `rationale`。

当前实现仍必须区分：

```text
已有 Schema 文件
!=
每次运行已经执行 Schema 校验
```

当前已经实现 `IntentModelOutputSchemaValidator`。真实 Provider 会对千问原始 JSON 运行校验，正式工作流也会对 Provider 结构化建议再次校验，所以响应里的 `schemaResult` 是本次实际执行结果，不是固定值。`policyResult` 仍表示最终业务结果是否安全，`observation.modelPolicyResult` 表示模型文案是否越过第一节点边界，三者不能互相替代。

### 8.6 冲突、失败和降级规则

```text
用户选择 == 模型建议
-> 返回模型建议，modelConflict=false

用户选择 != 模型建议
-> 正式 intent 保持用户选择，modelConflict=true

模型 Schema 不合法
-> schemaResult=FAIL，丢弃模型建议，保留确定性结果

模型文案包含诊断、概率、症状确认或写库指令
-> modelPolicyResult=BLOCK，丢弃模型建议，保留确定性结果

模型超时或不可用
-> modelCallStatus=FAILED，保留确定性结果
```

### 8.7 本地评估和可观测性

固定评估集：

```text
src/test/resources/fixtures/intent-evidence-v1/evaluation-cases.json
```

当前包含 9 条案例：三种正常意图、三种冲突、Schema 拒绝、模型 Policy 拒绝、Provider 超时降级。自动验收要求最终用户意图保持率为 `9/9`，预期冲突识别为 `3/3`。

每次运行返回并脱敏记录：

```text
runId
triggerType
userDecision
workflowVersion
nodeVersion
promptVersion
modelProvider/modelName
contextSnapshotId
节点输入摘要和输出摘要
evidenceIds
latencyMs
Token 和可选估算成本
retryCount
schemaResult
policyResult/modelPolicyResult
modelCallStatus/modelErrorCode
modelConflict
```

日志不记录 `selectedText` 和 `questionText` 原文，只记录长度。指标可通过以下端点查看：

```http
GET /actuator/metrics/athena.agent.node.runs
GET /actuator/metrics/athena.agent.node.duration
```

### 8.8 仍需后端完成

- 使用服务间认证保护 `/internal/**` 和 `/actuator/**`。
- 将 `observation` 持久化到 Agent 运行表，并建立日志、指标、告警平台。
- 根据生产重试策略填写真实 `retryCount`；当前同步本地原型不自动重试，所以固定为 0。
- 在部署环境配置真实千问 Key、Base URL、模型名和可选 Token 单价。
- 由主后端提供真实 `runId`、`contextSnapshotId`、权限过滤后的线索数据并保存 Agent Proposal。
- 完成真实千问固定评估集运行，记录模型版本、Prompt 版本、通过率、冲突率、P95 延迟、Token 和成本。

## 9. 个人认知图谱与节点 2-10

### 9.1 架构结论

当前采用：

```text
一个 CognitionGraphWorkflow
+ 多个职责明确的工作流节点
+ 一个统一 ModelGateway（千问使用 OpenAI-compatible 实现）
+ 确定性规则、JSON Schema、Policy 和人工确认
```

当前不采用多个自治 Agent。不同节点可以有不同 Prompt 和不同模型可见上下文，但共享同一个模型传输网关、模型配置和密钥。节点之间通过 Java 结构化合同传值，不通过自然语言互相聊天。

生产代码中只有一套真实模型 HTTP 传输：

```text
ModelGateway
-> OpenAiCompatibleModelGateway
```

节点 1、节点 3、节点 5 和节点 6 分别通过自己的适配器使用同一网关。节点 2、节点 4、节点 7、节点 8、节点 9 和节点 10 是纯确定性 Java 节点，不调用模型。

### 9.2 PersonalCognitionGraph 总合同

每个用户在主后端只有一份图谱：

```text
PersonalCognitionGraph
graphSchemaVersion = personal-cognition-graph-v1
graphId
graphVersion
nodes
edges
updatedAt
```

`userId` 不进入 Agent 合同。主后端根据登录身份加载用户图谱，再把过滤后的图谱快照传给 Agent。

允许的节点类型：

| 类型 | 含义 | 是否可直接由文章产生 |
| --- | --- | --- |
| `TOPIC` | 用户持续理解的问题分支 | 只能产生候选，后续需用户确认 |
| `SOURCE_EVIDENCE` | 文章线索、身体记录、行动反馈来源 | 是 |
| `SELF_REPORTED_FACT` | 用户明确声明的个人经历 | 仅 `CURRENT/PAST`、身体记录或“发生/未发生”行动反馈可支持 |
| `PATTERN_HYPOTHESIS` | 可能联系或待验证模式 | 可以，但必须保留不确定性并引用证据 |
| `OPEN_QUESTION` | 当前仍不能确认的问题 | 是 |
| `ACTION` | 后续需要完成的观察任务 | 节点 5 不创建，由后续行动规划节点负责 |

节点状态：

```text
ACTIVE
SUPERSEDED
ARCHIVED
```

V1 不允许物理删除图谱节点。理解改变时，后续 Patch 节点应使用 `SUPERSEDE_NODE`，保留历史和证据追溯。

允许的边：

```text
ABOUT
GROUNDS
SUPPORTS
CHALLENGES
NEXT_STEP_FOR
FEEDBACK_FOR
```

正式 Schema：

```text
schemas/personal-cognition-graph-v1/personal-cognition-graph.schema.json
```

这里的“图谱”是业务数据关系，不要求后端使用 Neo4j。后端可以使用关系型数据库分别保存图、节点、边、版本和证据关联。

### 9.3 GraphUpdateProposal 总合同

`GraphUpdateProposal` 是后续唯一允许主后端保存并等待用户确认的更新提案：

```text
proposalSchemaVersion
proposalId
graphId
baseGraphVersion
status
route
targetTopicId
evidenceIds
operations
changeSummary
requiresUserConfirmation = true
workflowVersion
createdAt
```

路由：

```text
UPDATE_EXISTING
CREATE_BRANCH
NO_CHANGE
NEEDS_CONFIRMATION
```

允许的 Patch 操作已冻结：

```text
ADD_NODE
UPDATE_NODE
ADD_EDGE
SUPERSEDE_NODE
DEACTIVATE_EDGE
NO_OP
```

不允许 `DELETE`。节点 5 只输出语义草稿，节点 6 只输出行动计划，节点 7 负责确定性组装 Patch，节点 8 负责完整校验和模拟应用，节点 9 归一化行动反馈，节点 10 生成反馈 Patch 和图谱预览。所有节点都不能把 Patch 应用到正式图谱。

正式 Schema：

```text
schemas/personal-cognition-graph-v1/graph-update-proposal.schema.json
```

### 9.4 节点 2：证据标准化与去重

节点：

```text
EVIDENCE_CANONICALIZATION_AND_DEDUPLICATION
版本：evidence-canonicalization-v1
模型：不调用
```

业务职责：

- 只接收节点 1 已路由为 `RELATED` 的候选证据。
- 统一生成 `CanonicalEvidence`。
- 根据来源和标准化内容指纹做精确去重。
- 判定证据事实等级。
- 重复证据返回 `NO_CHANGE`，不继续调用节点 3-5。

事实等级：

```text
文章 + CURRENT/PAST -> SELF_REPORTED
文章 + OBSERVE      -> DECLARED_RELEVANCE
BODY_RECORD         -> OBSERVED
ACTION_FEEDBACK     -> OBSERVED
```

`QUESTION` 和 `KNOWLEDGE_ONLY` 不允许进入本节点。文章内容不能被转换成 `OBSERVED`。

去重顺序：

```text
同 sourceType + sourceId
-> EXACT_SOURCE_DUPLICATE

否则同标准化内容 SHA-256 指纹
-> EXACT_CONTENT_DUPLICATE

否则
-> UNIQUE
```

节点内部白名单包含候选证据和已有标准证据；本节点没有模型可见白名单，因为不调用模型。

同步接口：

```http
POST /internal/v1/cognition/nodes/evidence-canonicalization
```

正式 Schema：

```text
schemas/evidence-canonicalization-v1/evidence-canonicalization-request.schema.json
schemas/evidence-canonicalization-v1/evidence-canonicalization-response.schema.json
```

### 9.5 节点 3：图谱目标分支解析

节点：

```text
GRAPH_TARGET_RESOLUTION
版本：graph-target-resolution-v1
Prompt：graph-target-resolution-prompt-v1
模型：规则优先，歧义时模型辅助
```

固定决策顺序：

```text
1. 全部证据已在图谱中 -> NO_CHANGE
2. 用户明确选择有效 topicId -> UPDATE_EXISTING，USER_DECLARED
3. suggestedTopicTitle 精确匹配唯一活动主题 -> UPDATE_EXISTING，RULE
4. 图谱没有活动主题且有有效标题 -> CREATE_BRANCH，RULE
5. 其他情况 -> 调用模型从候选主题中建议
6. 模型失败、越界或选择候选列表外 topicId -> NEEDS_CONFIRMATION
```

模型只能输出：

```json
{
  "route": "UPDATE_EXISTING | CREATE_BRANCH | NEEDS_CONFIRMATION",
  "matchedTopicId": "候选 topicId 或 null",
  "suggestedTopicTitle": "候选标题或 null",
  "rationale": "非诊断性说明"
}
```

节点内部白名单可以读取完整图谱以执行完整性和重复检查。模型只能看到：

```text
suggestedTopicTitle
当前选定证据的最小摘要
活动主题候选的 id、标题、domain、阶段摘要
```

模型看不到 `graphVersion`、完整边、非活动节点、证据 `sourceId`、内容指纹、创建时间、数据库信息和用户身份。

同步接口：

```http
POST /internal/v1/cognition/nodes/graph-target-resolution
```

Schema 目录：

```text
schemas/graph-target-resolution-v1/
```

### 9.6 节点 4：更新范围与证据集规划

节点：

```text
GRAPH_UPDATE_SCOPE_PLANNING
版本：graph-update-scope-v1
模型：不调用
```

它不生成健康理解，只给节点 5 划定边界：

```text
graphId
baseGraphVersion
route
targetTopicId
proposedTopicTitle
selectedEvidenceIds
readableNodeIds
mutableNodeIds
immutableNodeIds
```

`UPDATE_EXISTING` 只暴露目标主题及其活动子图，其他主题分支不可见。`CREATE_BRANCH` 不暴露旧分支。节点 5 可读取目标分支，但身体事实、来源证据和行动节点不可修改。

`NO_CHANGE` 直接停止流程；`NEEDS_CONFIRMATION` 在用户选定目标前被拦截，不能进入语义生成。

同步接口：

```http
POST /internal/v1/cognition/nodes/graph-update-scope
```

Schema 目录：

```text
schemas/graph-update-scope-v1/
```

### 9.7 节点 5：图谱语义更新生成

节点：

```text
GRAPH_SEMANTIC_UPDATE_GENERATION
版本：graph-semantic-update-v1
Prompt：graph-semantic-update-prompt-v1
模型：调用统一 ModelGateway
```

本节点不是生成文章，而是生成最小语义变化草稿：

```text
topicTitle
stageUnderstanding
stageUnderstandingEvidenceIds
changes[]
changeSummary
```

每个 `change` 包含：

```text
changeType = ADD / REVISE / NO_CHANGE
nodeType = SELF_REPORTED_FACT / PATTERN_HYPOTHESIS / OPEN_QUESTION
targetNodeId
content
evidenceIds
```

硬性规则：

1. `topicTitle` 必须与节点 4 冻结的标题一致，节点 5 无权改名。
2. `stageUnderstanding` 必须通过 `stageUnderstandingEvidenceIds` 引用本次选定证据。
3. 每个非 `NO_CHANGE` 变化必须引用本次选定证据。
4. `REVISE` 只能修改节点 4 标记为可修改、且类型相同的节点。
5. `ADD` 不能指定已有 `targetNodeId`。
6. `DECLARED_RELEVANCE` 不能单独支持 `SELF_REPORTED_FACT`。
7. 禁止诊断、疾病概率、用药、治疗、数据库操作、删除、创建行动。
8. Schema 或 Policy 不通过时不返回草稿，不继续后续节点。

模型可见内容只有：

```text
route
targetTopicId
targetTopicTitle
节点 4 允许读取的子图节点
节点 4 选定的标准证据
```

模型看不到完整图谱、其他主题、`graphVersion`、内容指纹、原始来源 ID、用户身份和数据库字段。

同步接口：

```http
POST /internal/v1/cognition/nodes/graph-semantic-update
```

Schema 目录：

```text
schemas/graph-semantic-update-v1/
```

### 9.8 节点 6：下一步行动规划

节点：

```text
NEXT_ACTION_PLANNING
版本：next-action-planning-v1
Prompt：next-action-planning-prompt-v1
模型：有待完成行动时使用确定性规则；否则调用统一 ModelGateway
```

本节点只规划一条低负担、可观察、可反馈、可跳过的下一步行动，不诊断、不治疗、不直接创建正式行动。输出：

```text
decision = CREATE_NEW / KEEP_EXISTING
existingActionNodeId
actionType
title
description
dueAt
feedbackOptions
evidenceIds
rationale
```

允许的行动类型与 Android 现有合同一致：

```text
RECORD_BODY
RECORD_MOOD
RECORD_SLEEP
ANSWER_QUESTION
CONFIRM_STATUS
```

`READ_CONTENT` 虽然是前端历史枚举，但 V1 Agent 不允许把“继续阅读”作为认知闭环的下一步观察行动。每个行动必须支持 `OCCURRED`、`NOT_OCCURRED`、`UNCERTAIN`、`SKIPPED`。

确定性规则：目标主题已经存在一条活动且待完成的行动时，返回 `KEEP_EXISTING`，不调用模型、不重复创建行动；同一主题存在多条待完成行动时直接 `BLOCKED`。

模型只看到 `route`、主题标题、阶段理解、本次语义草稿中的开放问题、节点 4 选定证据的最小摘要和允许的行动类型。模型看不到完整图谱、其他主题、用户身份、数据库字段、原始来源 ID、内容指纹和 `graphVersion`。模型输出必须经过独立 Schema 和 Policy；越界行动、诊断、治疗、数据库指令或范围外证据都会被拦截。

同步接口和 Schema：

```http
POST /internal/v1/cognition/nodes/next-action-planning
```

```text
schemas/next-action-planning-v1/
```

### 9.9 节点 7：Patch 组装

节点：

```text
GRAPH_PATCH_ASSEMBLY
版本：graph-patch-assembly-v1
模型：不调用
```

本节点把节点 5 的语义草稿和节点 6 的行动计划机械转换为 `GraphUpdateProposal`。它不重新理解健康内容，不修改输入图谱。

关键规则：

1. `proposalId`、新节点 ID 和新边 ID 由幂等键确定性生成；同一输入重复运行得到相同 ID。
2. `CREATE_BRANCH` 只新增一个 `TOPIC`；`UPDATE_EXISTING` 只更新目标主题和允许修改的语义节点，不新增第二个主题。
3. 每条来源证据生成或复用一个 `SOURCE_EVIDENCE`，通过 `ABOUT` 和 `GROUNDS` 建立可追溯关系。
4. 已有待完成行动时不新增 `ACTION`；新行动通过 `NEXT_STEP_FOR` 指向目标主题。
5. 节点操作稳定排在边操作之前，方便后端按顺序事务执行。
6. 输出提案状态只能是 `DRAFT`，不能自行推进为正式状态。

同步接口和 Schema：

```http
POST /internal/v1/cognition/nodes/graph-patch-assembly
```

```text
schemas/graph-patch-assembly-v1/
```

### 9.10 节点 8：Patch 完整安全校验

节点：

```text
GRAPH_PATCH_GUARD
版本：graph-patch-guard-v1
模型：不调用
```

节点 8 是任何提案进入人工确认前的最后一道确定性边界：

```text
请求与提案 Schema 校验
-> baseGraphVersion 校验
-> Patch 操作形状校验
-> 证据、目标分支和节点可变范围校验
-> ACTION 字段与反馈能力校验
-> 边类型与方向校验
-> 在图谱副本上模拟应用
-> 模拟结果完整性校验
-> READY_FOR_CONFIRMATION
```

硬性规则：

1. 只接受 `DRAFT` 且 `requiresUserConfirmation=true` 的提案。
2. `baseGraphVersion` 与当前图谱版本不一致时返回 `STALE`，不能覆盖新图谱。
3. 禁止删除；每个目标节点一次提案最多修改一次。
4. `UPDATE_NODE` 只能更新节点 4 冻结为可修改的 `TOPIC`、`PATTERN_HYPOTHESIS`、`OPEN_QUESTION`。
5. 文章“和我有关”不能单独生成 `SELF_REPORTED_FACT`。
6. `CREATE_BRANCH` 必须恰好新增一个目标主题；`UPDATE_EXISTING` 不得新增主题。
7. 一次提案最多新增一个行动，同一主题模拟结果中最多一条活动待办行动。
8. `ABOUT`、`GROUNDS`、`NEXT_STEP_FOR` 必须满足固定方向和目标分支约束。
9. 新增节点和边内部的 `evidenceIds` 必须与对应 Patch 操作声明的证据集合完全一致，不能把未选证据藏进对象内部。
10. 更新节点只能保留旧证据并合并本次操作证据；禁止借更新修改创建时间、领域、行动字段等不可变元数据。
11. 禁止新增与当前活动边具有相同类型、起点和终点的重复关系。
12. Policy 或模拟完整性失败时提案标记为 `BLOCKED`，不会进入人工确认。

通过校验后，节点 8 返回一份状态为 `READY_FOR_CONFIRMATION` 的提案副本；传入图谱和节点 7 的原始 `DRAFT` 提案都不会被修改。

同步接口和 Schema：

```http
POST /internal/v1/cognition/nodes/graph-patch-guard
```

```text
schemas/graph-patch-guard-v1/
```

### 9.11 节点 2-8 同步编排

本地同步入口：

```http
POST /internal/v1/cognition/workflows/graph-update/prepare
```

执行链：

```text
节点 2 标准化与去重
-> 节点 3 目标分支解析
-> 节点 4 更新范围规划
-> 节点 5 语义变化生成
-> 节点 6 下一步行动规划
-> 节点 7 Patch 组装
-> 节点 8 完整安全校验
-> 人工确认
```

停止规则：

| 情况 | 工作流状态 | 后续行为 |
| --- | --- | --- |
| 同一来源或内容已经存在 | `NO_CHANGE` | 不运行节点 3-8 |
| 目标分支无法唯一确定 | `NEEDS_CONFIRMATION` | 等用户选择主题 |
| Scope、行动或 Patch Policy 越界 | `BLOCKED` | 不返回可确认提案 |
| 图谱版本发生变化 | `STALE` | 丢弃旧提案，基于新快照重跑 |
| 模型不可用或输出非法 | `FAILED` | 保留线索，后端决定重试 |
| 节点 8 成功 | `PROPOSAL_READY` | `nextNodeId=HUMAN_CONFIRMATION` |

当前入口会返回完整 `GraphUpdateProposal.operations`，但不会修改传入图谱，也不会应用 Patch。只有主后端在用户确认后，重新校验 `baseGraphVersion` 并在数据库事务中应用操作。

四条核心端到端路径已经自动化：

```text
首次创建 -> CREATE_BRANCH -> PROPOSAL_READY
增量更新 -> UPDATE_EXISTING -> PROPOSAL_READY，不重复创建主题和待办行动
无变化   -> NO_CHANGE，节点 3-8 不执行
冲突     -> NEEDS_CONFIRMATION，节点 4-8 不执行
```

总工作流 Schema 位于 `schemas/cognition-graph-workflow-v1/`。

### 9.12 节点 9：行动反馈归一化

节点：

```text
ACTION_FEEDBACK_NORMALIZATION
版本：action-feedback-normalization-v1
模型：不调用
```

节点 9 只解决“这条反馈能否作为当前待办行动的有效反馈，以及应当按什么证据等级保存”。输入核心字段：

```text
feedbackId
actionId
result
note
occurredAt
graph
existingEvidence
```

反馈结果与输出映射已经冻结：

| 用户反馈 | 行动新状态 | 证据等级 | 能否直接产生身体事实 |
| --- | --- | --- | --- |
| `OCCURRED` | `COMPLETED` | `OBSERVED` | 可以产生一条有证据的自述事实 |
| `NOT_OCCURRED` | `COMPLETED` | `OBSERVED` | 可以记录“本次未发生”，不能改写成“永远不会发生” |
| `UNCERTAIN` | `COMPLETED` | `QUESTION` | 不可以，只能产生待确认问题 |
| `SKIPPED` | `SKIPPED` | `PROCESS_EVENT` | 不可以，不生成身体语义 |

硬性规则：

1. 只接受 `triggerType=ACTION_FEEDBACK`。
2. `actionId` 必须指向当前图谱中活动且状态为 `PENDING` 的 `ACTION`。
3. 反馈值必须包含在该行动的 `feedbackOptions` 中。
4. 同一个 `feedbackId` 和同一内容重复提交返回 `NO_CHANGE`；相同 ID 对应不同内容返回 `BLOCKED`。
5. 已完成、已跳过、已过期或不存在的行动不能再次接收新反馈。
6. `occurredAt` 必须是带时区偏移的 ISO-8601 时间；备注最多 500 字符。
7. 用户备注只进入反馈证据内容，不进入日志、指标标签或确定性语义结论。

同步接口和 Schema：

```http
POST /internal/v1/cognition/nodes/action-feedback-normalization
```

```text
schemas/action-feedback-normalization-v1/
```

### 9.13 节点 10：反馈图谱更新

节点：

```text
ACTION_FEEDBACK_GRAPH_UPDATE
版本：action-feedback-graph-update-v1
模型：不调用
```

节点 10 接收节点 9 的 `NormalizedActionFeedback`，生成一个反馈专用的增量 `GraphUpdateProposal`。所有反馈共同执行：

```text
UPDATE_NODE：原 ACTION 从 PENDING 改为 COMPLETED 或 SKIPPED
ADD_NODE：新增一条 SOURCE_EVIDENCE
ADD_EDGE：SOURCE_EVIDENCE -> TOPIC，类型 ABOUT
ADD_EDGE：SOURCE_EVIDENCE -> ACTION，类型 FEEDBACK_FOR
```

不同反馈的语义操作：

```text
OCCURRED / NOT_OCCURRED
-> 新增一条 SELF_REPORTED_FACT
-> SOURCE_EVIDENCE 通过 GROUNDS 指向该事实

UNCERTAIN
-> 新增一条 OPEN_QUESTION
-> SOURCE_EVIDENCE 通过 GROUNDS 指向该问题

SKIPPED
-> 不新增 SELF_REPORTED_FACT、PATTERN_HYPOTHESIS 或 OPEN_QUESTION
```

节点 10 不信任节点间传入对象，会重新校验反馈证据的必填字段、来源类型、事实等级、内容指纹格式、带偏移时间、行动指向和反馈结果一致性；反馈证据不得夹带周期关系、严重度或解决状态。之后它会调用统一节点 8 Guard。反馈 Patch 必须原子包含“一次行动关闭、一条反馈来源、一条 `FEEDBACK_FOR` 关系和与枚举匹配的语义”；缺少任何一部分都返回 `BLOCKED`。它还会校验 `baseGraphVersion`、行动归属、边方向和最终图谱完整性。

校验成功时返回：

```text
status = READY_FOR_CONFIRMATION
proposal.status = READY_FOR_CONFIRMATION
graphPreview.graphVersion = baseGraphVersion + 1
```

传入的 `PersonalCognitionGraph` 保持不变。`graphPreview` 是内存模拟结果，只用于前端演示、测试和后端联调验收。

同步接口和 Schema：

```http
POST /internal/v1/cognition/nodes/action-feedback-graph-update
```

```text
schemas/action-feedback-graph-update-v1/
```

### 9.14 节点 9-10 同步编排

本地同步入口：

```http
POST /internal/v1/cognition/workflows/action-feedback/prepare
```

执行链：

```text
节点 9 反馈校验、幂等和证据等级归一化
-> 节点 10 行动状态迁移与反馈 Patch 组装
-> 节点 8 Policy 和图谱副本模拟
-> PROPOSAL_READY
-> HUMAN_CONFIRMATION
```

状态规则：

| 情况 | 状态 | 后续行为 |
| --- | --- | --- |
| 相同反馈已经处理 | `NO_CHANGE` | 不执行节点 10 |
| 行动不存在、已关闭或反馈 ID 冲突 | `BLOCKED` | 不生成 Proposal |
| 输入字段、时间或版本合同非法 | `REJECTED` | 修正请求后重试 |
| 图谱版本在节点间变化 | `STALE` | 主后端读取新图后重跑 |
| Patch 完整通过 | `PROPOSAL_READY` | `nextNodeId=HUMAN_CONFIRMATION` |

总工作流 Schema 位于 `schemas/action-feedback-workflow-v1/`。

### 9.15 可观测性与本地评估

节点 2-10 每次执行记录：

```text
runId
triggerType
workflowVersion
nodeId/nodeVersion
promptVersion
modelProvider/modelName
contextSnapshotId
脱敏步骤输入摘要和输出摘要
evidenceIds
latencyMs
Token 和可选成本
retryCount
schemaResult
policyResult/modelPolicyResult
modelCallStatus/modelErrorCode
feedbackResult
operationCount
baseGraphVersion
previewGraphVersion
```

指标：

```http
GET /actuator/metrics/athena.cognition.graph.node.runs
GET /actuator/metrics/athena.cognition.graph.node.duration
GET /actuator/metrics/athena.cognition.graph.patch.operations
```

日志不记录完整文章摘录、完整身体记录、完整模型 Prompt 或完整图谱。

固定评估集：

```text
src/test/resources/fixtures/cognition-graph-workflow-v1/evaluation-cases.json
src/test/resources/fixtures/action-feedback-workflow-v1/evaluation-cases.json
```

图谱主流程固定集覆盖首次创建分支、更新已有分支、重复证据、用户目标优先、歧义确认、跨分支泄漏、非法身体事实、`sourceId` 内容冲突和相同文本不同时间的身体记录九类基线。反馈固定集覆盖 `OCCURRED`、`NOT_OCCURRED`、`UNCERTAIN`、`SKIPPED`、重复提交、已关闭行动、未知行动和反馈 ID 冲突八类基线。另有节点 5-8 Scope 防伪、节点 6 上下文与行动 Policy、节点 7 幂等组装、节点 8 版本冲突与隐藏证据注入、反馈 Patch 原子性、图谱预览、工作流顶层错误和同步 HTTP 指标测试。

2026-08-27 完整验证结果：

```text
Tests run: 100
Failures: 0
Errors: 0
Skipped: 0
JSON Schema parsed: 35
BUILD SUCCESS
```

验证命令：

```powershell
.\mvnw.cmd -o '-Dmaven.repo.local=D:\aa\athena-zyj (2)\.codex-build-temp\athena-cognition-agent-m2\repository' test
```

### 9.16 当前交接状态

```text
PersonalCognitionGraph 总合同：IMPLEMENTED / TESTED
GraphUpdateProposal 总合同：IMPLEMENTED / TESTED
节点 1 意图与证据分类：IMPLEMENTED / TESTED
节点 2 证据标准化与去重：IMPLEMENTED / TESTED
节点 3 图谱目标分支解析：IMPLEMENTED / TESTED
节点 4 更新范围与证据集规划：IMPLEMENTED / TESTED
节点 5 图谱语义更新生成：IMPLEMENTED / TESTED
节点 6 下一步行动规划：IMPLEMENTED / TESTED
节点 7 Patch 组装：IMPLEMENTED / TESTED
节点 8 Patch 完整安全校验：IMPLEMENTED / TESTED
节点 9 行动反馈归一化：IMPLEMENTED / TESTED
节点 10 反馈图谱更新：IMPLEMENTED / TESTED
节点 2-8 同步编排：IMPLEMENTED / TESTED
节点 9-10 反馈同步编排：IMPLEMENTED / TESTED
统一千问 ModelGateway：IMPLEMENTED / HTTP MOCK TESTED
图谱固定评估集：TESTED（9 条案例逐条自动执行）
反馈固定评估集：TESTED（8 条案例逐条自动执行）
图谱越权与非法输出拦截：TESTED（节点 5-8 各自重验 Scope）
首次创建/增量更新/无变化/冲突：TESTED
真实千问在线评估：PENDING_USER_CONFIGURATION
用户确认与正式 Patch 应用：BACKEND_NOT_IMPLEMENTED
第二次后端交接文档：FROZEN（`docs/cognition-agent-backend-handoff-v1.md`）
正式后端部署：BACKEND_PENDING
```

### 9.17 后端未来必须完成

- 为每个用户创建并持久化唯一 `PersonalCognitionGraph`。
- 保存图谱节点、边、证据关联和每次版本变化，不能只保存一份覆盖式 JSON。
- 组装节点所需的最小上下文，保证候选主题和证据都属于当前登录用户。
- 为工作流提供稳定 `runId`、`contextSnapshotId` 和 `idempotencyKey`。
- 同一线索、同一图谱版本、同一工作流版本重复执行时返回同一任务或 `NO_CHANGE`。
- 保存节点运行观察记录和后续 `GraphUpdateProposal`。
- 行动反馈调用前必须同时提供当前最新图谱和该用户已有反馈证据，用 `feedbackId` 做幂等，不能只传 Android 当前页面上的行动对象。
- 保存节点 9 生成的反馈证据和节点 10 的 Proposal；`graphPreview` 只能用于展示和审计，不得直接覆盖数据库图谱。
- 用户确认前不得修改正式图谱。
- 用户接受提案后，后端必须在同一个数据库事务中再次校验 `baseGraphVersion`、逐条应用节点操作和边操作、把图谱版本加一并保存变更历史；任何一步失败都要整体回滚。
- 应用 Patch 时版本不一致必须标记 `STALE` 并重新生成，不能覆盖新版本。`READY_FOR_CONFIRMATION` 只代表 Agent 校验通过，不代表后端可以跳过确认或版本校验。
- 配置真实千问环境变量、服务间认证、异步任务、重试、告警和限流。
- Android 不直接调用这些 `/internal/**` 接口。

## 10. 第一节点开发顺序

第一节点当前采用以下固定顺序：

```text
1. Agent 合同类和枚举（已完成）
2. 输入校验器（已完成）
3. 上下文白名单构建器（已完成）
4. 确定性分类服务（已完成）
5. Policy 校验器（已完成）
6. 同步 HTTP 接口（已完成）
7. Mock Provider（已完成）
8. 真实模型 Provider（已完成代码和模拟 HTTP 测试；待真实凭证验证）
9. 正式 JSON Schema 和运行时独立 Schema 校验器（已完成）
10. 模型建议路由与冲突处理（已完成）
11. 失败、安全和 Prompt 注入本地测试（已完成）；真实千问在线评估待本地凭证运行
12. 可观测性和运行记录（本地完成；生产持久化由后端完成）
13. 冻结第一节点本地交接版本（已完成）
```

本阶段不接数据库，不把状态写入 Athena 正式业务表。Agent 可以返回“建议新增主题”的待确认 Patch，但不能直接创建正式主题；只有后端在用户确认后才能应用。

## 11. 版本变更记录

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-08-20 | `cognition-agent-v1` / `intent-evidence-v1` | 建立 Agent 交接文档；完成第一个节点业务定义、输入输出协议和上下文白名单 |
| 2026-08-23 | `intent-evidence-v1` | 完成同步 HTTP Controller 和 Mock Provider；新增接口测试和 Provider 测试；15 个测试通过 |
| 2026-08-23 | `intent-evidence-v1` / `intent-evidence-prompt-v1` | 完成 OpenAI-compatible 真实模型 Provider、环境变量配置、模型探测接口和 HTTP 模拟测试；18 个测试通过 |
| 2026-08-26 | `intent-evidence-v1` | 新增独立 `IntentModelContext` 类型边界和字段泄露测试；新增请求、响应、模型上下文、模型输出四份正式 JSON Schema；22 个测试通过 |
| 2026-08-26 | `intent-evidence-v1` / `cognition-workflow-v1` | 完成运行时 Schema、模型内容 Policy、模型建议冲突记录与降级、脱敏运行记录、Micrometer 指标、Token/可选成本、9 条固定评估集和 Prompt 注入测试；补充 `userDecision` 观测字段，测试结果以本次完整构建为准 |
| 2026-08-27 | `personal-cognition-graph-v1` / `cognition-graph-workflow-v1` | 将“每次生成新整理文章”改为“对唯一个人认知图谱生成增量更新”；冻结图谱和 Proposal 总合同；完成节点 2-5、统一模型网关、两层白名单、Schema、Policy、同步编排、固定评估集和可观测性 |
| 2026-08-27 | `personal-cognition-graph-v1` / `cognition-graph-workflow-v1` | 将 7 条图谱基线接入自动化动态测试；新增完整工作流安全测试，验证身体事实越权和非法模型输出不能进入 Patch 组装；本次完整构建为 57 个测试全部通过 |
| 2026-08-27 | `next-action-planning-v1` / `graph-patch-assembly-v1` / `graph-patch-guard-v1` | 完成节点 6 下一步行动规划、节点 7 确定性 Patch 组装、节点 8 版本/证据/操作/图谱完整安全校验；补充节点和边内部隐藏证据注入拦截；工作流推进到人工确认前；首次创建、增量更新、无变化、冲突四条路径通过；本次完整构建为 70 个测试、29 份 Schema 全部通过 |
| 2026-08-27 | `action-feedback-normalization-v1` / `action-feedback-graph-update-v1` / `action-feedback-workflow-v1` | 完成节点 9 反馈归一化、节点 10 行动关闭与反馈图谱 Patch；新增 `FEEDBACK_FOR`、反馈证据等级、内存图谱预览、原子 Guard、8 条反馈固定评估集和反馈指标；收紧节点 10 的反馈证据校验与 Schema；本次完整构建为 87 个测试、35 份 Schema 全部通过 |
| 2026-08-27 | `cognition-agent-v1` 第二次后端交接冻结 | 补齐图谱 schemaVersion 和跨主题边校验、证据 sourceId 幂等冲突、身体记录时间指纹、节点 5-8 Scope 防伪、主工作流图谱预览和顶层错误、工作流终态指标、反馈 Proposal 来源版本以及 `prod` 禁用 Mock；主评估集增至 9 条，完成第二次后端交接文档；本次完整构建为 100 个测试、35 份 Schema 全部通过 |
