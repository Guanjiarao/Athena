# Athena Cognition Agent V1 第二次后端交接文档

> 交接日期：2026-08-27  
> Agent 工程：`Back_End/athena-cognition-agent`  
> 合同版本：`cognition-agent-v1`  
> 主图谱工作流：`cognition-graph-workflow-v1`  
> 反馈工作流：`action-feedback-workflow-v1`  
> 图谱版本：`personal-cognition-graph-v1`  
> 当前验证：100 个测试通过，35 份 JSON Schema 可解析，`BUILD SUCCESS`

## 1. 先读结论

这次交付的不是聊天机器人，也不是一个可以直接写数据库的后端服务。它是一个已经在本地跑通的“个人认知图谱提案生成器”：

```text
主后端提供当前图谱和新证据
-> Agent 判断更新哪个主题
-> Agent 生成增量 GraphUpdateProposal
-> Agent 在内存副本上模拟并校验
-> Agent 返回待确认 Proposal 和 graphPreview
-> 用户确认
-> 主后端在数据库事务中应用 Proposal
```

行动反馈使用另一条固定流程：

```text
主后端提供当前图谱和用户反馈
-> 节点 9 归一化反馈
-> 节点 10 生成反馈 Patch
-> 复用节点 8 完整校验
-> 返回待确认 Proposal 和 graphPreview
-> 用户确认
-> 主后端在数据库事务中应用 Proposal
```

Agent 本地代码已经完成节点 1-10、两个工作流、合同、Schema、Policy、固定评估集和可观测性。后端现在要完成的是部署、鉴权、数据库、任务状态、人工确认、事务应用、运行记录、重试和推送。

以下边界不能改：

- Android 不能直接调用 Agent，只能调用 Athena 主后端。
- Agent 不连接 Athena 正式数据库，也不持有数据库写权限。
- Agent 返回的 `graphPreview` 不是正式图谱，不能直接覆盖数据库。
- `PROPOSAL_READY` 只表示提案通过 Agent 校验，不表示已经生效。
- 所有改变正式图谱的 Proposal 都必须经过用户确认。
- Agent 不得执行 `INSERT cognition_topic`、`UPDATE user_body_fact` 或 `DELETE user_record`。
- 主后端不得绕开节点 8，也不得自行拼接一个未校验的 Patch。

## 2. 三个生产调用入口

主后端实际只需要接入下面三个入口。

### 2.1 文章快速标记先调用节点 1

```http
POST /internal/v1/cognition/nodes/intent-classification
```

作用：识别文章标记是“和我有关”“我有疑问”还是“只保存知识”。

路由规则：

| 节点 1 结果 | 主后端处理 |
| --- | --- |
| `intent=RELATED` 且 `nextRoute=MATCH_EXISTING_TOPIC_CANDIDATE` | 转成 `EvidenceCandidate`，继续调用主图谱工作流 |
| `intent=QUESTION` 且 `nextRoute=QUESTION_INBOX` | 保存到问题收件箱，不进入身体图谱 |
| `intent=KNOWLEDGE_ONLY` 且 `nextRoute=KNOWLEDGE_INBOX` | 保存为知识，不进入身体图谱 |
| `nextRoute=NEEDS_CLARIFICATION` | 让用户补充或重新选择，不进入身体图谱 |
| `BLOCKED/REJECTED/FAILED` | 按错误状态处理，不调用主图谱工作流 |

节点 1 当前只接收文章高亮线索。结构化 `BODY_RECORD` 不经过节点 1，由主后端按既定字段直接组装为 `EvidenceCandidate`。

节点 1 到节点 2 的字段映射必须固定如下：

| `EvidenceCandidate` 字段 | 来源 |
| --- | --- |
| `evidenceId` | 节点 1 返回的 `evidenceIds[0]`，当前等于 `clue.id` |
| `sourceType` | 文章标记固定为 `ARTICLE_HIGHLIGHT` |
| `sourceId` | `clue.id` |
| `intent` | 节点 1 返回的 `intent`，只有 `RELATED` 可继续 |
| `relationType` | `clue.relationType` |
| `summary` | `clue.selectedText`，不得让主后端重新生成身体结论 |
| `occurredAt` | `clue.occurredAt` |
| `cycleRelation` | `clue.cycleRelation` |
| `severity` | `clue.severity` |
| `resolved` | `clue.resolved` |
| `relatedActionId` | 文章标记为 `null` |
| `feedbackResult` | 文章标记为 `null` |

### 2.2 图谱新增和增量更新调用主工作流

```http
POST /internal/v1/cognition/workflows/graph-update/prepare
```

该接口固定编排节点 2-8。主后端不要依次调用节点 2、3、4、5、6、7、8，也不要改变执行顺序。

允许的 `triggerType` 只有：

```text
CLUE_CREATED
USER_REQUEST
RETRY
```

`ACTION_FEEDBACK` 会在入口直接被拒绝。

### 2.3 行动反馈调用反馈工作流

```http
POST /internal/v1/cognition/workflows/action-feedback/prepare
```

该接口固定编排节点 9、10，并复用节点 8。`triggerType` 必须是 `ACTION_FEEDBACK`。

### 2.4 不应作为业务入口的接口

`/internal/v1/cognition/nodes/**` 下的节点 2-10 接口用于开发、定位节点问题和自动化测试。正式业务只能调用总工作流，不能由主后端自行编排节点。

下面的探测接口只允许在显式 `model-probe` Profile 下做模型连通性检查，不能暴露给 Android 或公网：

```http
POST /internal/v1/cognition/nodes/intent-classification/model-probe
```

## 3. Agent 已经完成的内容

| 模块 | 状态 | 已验证的关键行为 |
| --- | --- | --- |
| `PersonalCognitionGraph` 总合同 | 完成 | 图谱版本、节点、边、行动字段和完整性校验 |
| `GraphUpdateProposal` 总合同 | 完成 | 只生成增量操作，不修改输入图谱 |
| 节点 1：意图与证据分类 | 完成 | 用户显式选择优先、模型冲突记录、Schema/Policy |
| 节点 2：证据标准化与去重 | 完成 | 来源幂等、内容去重、sourceId 冲突、身体记录时间区分 |
| 节点 3：目标分支解析 | 完成 | 用户指定优先、匹配已有主题、创建候选、歧义确认 |
| 节点 4：更新范围规划 | 完成 | 只开放目标活动分支，冻结可读/可写节点和证据集 |
| 节点 5：图谱语义更新 | 完成 | 增量语义草稿、模型上下文白名单、事实越权拦截 |
| 节点 6：下一步行动规划 | 完成 | 可观察行动、四种反馈选项、已有待办行动复用 |
| 节点 7：Patch 组装 | 完成 | 确定性 ID、节点先于边、最多一个新行动 |
| 节点 8：完整安全校验 | 完成 | 版本、范围、证据、操作、边方向和模拟图谱校验 |
| 节点 9：行动反馈归一化 | 完成 | 四类反馈、行动状态、幂等和冲突检查 |
| 节点 10：反馈图谱更新 | 完成 | 关闭行动、写反馈证据、反向更新图谱提案 |
| 主工作流四条路径 | 完成 | 首次创建、增量更新、无变化、歧义冲突 |
| 固定评估集 | 完成 | 主流程 9 条、反馈流程 8 条 |
| 可观测性 | 完成 | 节点指标、工作流指标、脱敏日志、Token/成本字段 |
| 生产 Mock 防误启 | 完成 | `prod` 必须使用 `openai-compatible` 且配置完整 |

Scope 不只在节点 4 校验一次。节点 5、6、7、8 会根据当前图谱重新验证目标分支，伪造跨分支 `readableNodeIds` 或 `mutableNodeIds` 会被阻止。

## 4. 主工作流请求如何组装

请求合同类：

```text
workflow/contract/GraphUpdatePreparationRequest.java
```

正式 Schema：

```text
schemas/cognition-graph-workflow-v1/cognition-graph-workflow-request.schema.json
```

字段来源：

| 字段 | 谁生成 | 规则 |
| --- | --- | --- |
| `contractVersion` | 固定 | `cognition-agent-v1` |
| `workflowVersion` | 固定 | `cognition-graph-workflow-v1` |
| `runId` | 主后端 | 每次真实执行唯一；重试可生成新 `runId` |
| `idempotencyKey` | 主后端 | 同一业务事件和同一工作流版本保持不变 |
| `triggerType` | 主后端 | `CLUE_CREATED`、`USER_REQUEST` 或 `RETRY` |
| `contextSnapshotId` | 主后端 | 指向本次不可变上下文快照 |
| `graph` | 主后端数据库 | 当前用户最新的完整 `PersonalCognitionGraph` |
| `candidates` | 节点 1 映射或身体记录映射 | 本次新证据，最多 50 条 |
| `existingEvidence` | 主后端数据库 | 用户已有规范证据，用于来源和内容去重 |
| `userSelectedTopicId` | 用户操作 | 用户明确选择主题时填写，否则 `null` |
| `suggestedTopicTitle` | 当前页面或业务规则 | 新主题候选标题，可为 `null` |
| `requestedAt` | 主后端时钟 | 带时区 ISO-8601 时间，不信任 Android 本地时间 |

最小结构示例：

```json
{
  "contractVersion": "cognition-agent-v1",
  "workflowVersion": "cognition-graph-workflow-v1",
  "runId": "run_01J...",
  "idempotencyKey": "clue_101:cognition-graph-workflow-v1",
  "triggerType": "CLUE_CREATED",
  "contextSnapshotId": "ctx_01J...",
  "graph": {
    "graphSchemaVersion": "personal-cognition-graph-v1",
    "graphId": "graph_42",
    "graphVersion": 7,
    "nodes": [],
    "edges": [],
    "updatedAt": "2026-08-27T14:00:00+08:00"
  },
  "candidates": [
    {
      "evidenceId": "clue_101",
      "sourceType": "ARTICLE_HIGHLIGHT",
      "sourceId": "clue_101",
      "intent": "RELATED",
      "relationType": "OBSERVE",
      "summary": "用户实际选中的文章原文",
      "occurredAt": null,
      "cycleRelation": "NO_RELATION",
      "severity": null,
      "resolved": null,
      "relatedActionId": null,
      "feedbackResult": null
    }
  ],
  "existingEvidence": [],
  "userSelectedTopicId": null,
  "suggestedTopicTitle": "经期前情绪变化",
  "requestedAt": "2026-08-27T14:00:00+08:00"
}
```

注意：这个示例中的空图谱只适用于新用户。已有用户必须传数据库里的最新图谱，不能为了省字段传空图，否则 Agent 会错误地产生新分支。

V1 为无数据库 Agent，因此请求需要携带图谱和既有证据。第一阶段可传该用户完整规范证据以保证去重正确；数据量明显增大后，应设计 V2 的索引查询或快照投影合同，不能在 V1 中擅自截断后声称已经全局去重。

## 5. 反馈工作流请求如何组装

请求合同类：

```text
feedbackworkflow/contract/ActionFeedbackWorkflowRequest.java
```

正式 Schema：

```text
schemas/action-feedback-workflow-v1/action-feedback-workflow-request.schema.json
```

字段来源：

| 字段 | 来源和规则 |
| --- | --- |
| `contractVersion` | 固定为 `cognition-agent-v1` |
| `workflowVersion` | 固定为 `action-feedback-workflow-v1` |
| `runId` | 主后端每次执行唯一生成 |
| `idempotencyKey` | 同一 `feedbackId` 和反馈工作流版本保持不变 |
| `triggerType` | 固定为 `ACTION_FEEDBACK` |
| `contextSnapshotId` | 主后端生成并保存 |
| `graph` | 当前用户最新图谱，必须包含目标 `ACTION` |
| `existingEvidence` | 该用户已有反馈证据，至少能检测同一 `feedbackId` 重复和冲突 |
| `feedback.feedbackId` | 主后端为一次逻辑反馈生成的稳定 ID |
| `feedback.actionId` | 用户正在反馈的图谱行动 ID |
| `feedback.result` | `OCCURRED`、`NOT_OCCURRED`、`UNCERTAIN`、`SKIPPED` |
| `feedback.note` | 用户可选备注 |
| `feedback.occurredAt` | 主后端校正后的带时区时间 |

反馈工作流成功后仍返回 `PROPOSAL_READY`，不能直接把行动改成完成。只有用户确认后，主后端才能在事务里应用反馈 Proposal。

## 6. 响应状态如何处理

### 6.1 主图谱工作流

| 状态 | 含义 | 主后端动作 |
| --- | --- | --- |
| `PROPOSAL_READY` | Proposal 和预览通过全部校验 | 保存 Proposal，返回确认页，不修改正式图谱 |
| `NO_CHANGE` | 证据重复或语义上没有变化 | 标记任务完成，不创建新主题、不重复推送 |
| `NEEDS_CONFIRMATION` | 目标主题存在歧义 | 展示候选让用户选择，带同一业务幂等键重新运行 |
| `STALE` | 图谱版本已变化 | 丢弃旧快照，读取最新图谱后重新运行 |
| `BLOCKED` | Policy 拒绝越权或不安全结果 | 不自动重试；记录原因，必要时转人工或降级 |
| `REJECTED` | 请求字段、版本或幂等来源冲突 | 修复调用方数据；不能原样重试 |
| `FAILED` | 模型或内部执行失败 | 仅当 `error.retryable=true` 时按相同幂等键重试 |

### 6.2 反馈工作流

| 状态 | 含义 | 主后端动作 |
| --- | --- | --- |
| `PROPOSAL_READY` | 反馈 Patch 已通过校验 | 保存并进入用户确认 |
| `NO_CHANGE` | 同一反馈已经处理 | 返回已有结果，不重复关闭行动 |
| `STALE` | 图谱版本变化 | 读取最新图谱后重跑 |
| `BLOCKED` | 行动不存在、已关闭、归属不符或反馈冲突 | 不重试，不改图谱 |
| `REJECTED` | 请求合同非法 | 修正请求 |
| `FAILED` | 可恢复或内部失败 | 按 `error.retryable` 决定是否重试 |

所有工作流响应现在都有顶层 `error` 和 `observation`。后端读取顶层字段即可，不需要遍历每个节点寻找失败原因。节点响应仍保留，用于审计和定位问题。

## 7. 四个关键 ID 的生成规则

### `runId`

代表“一次物理执行”。每次真正调用 Agent 生成一个新的 UUID/ULID。一次重试是新的执行，所以可以有新的 `runId`。

### `idempotencyKey`

代表“同一个逻辑业务动作”。同一线索或同一反馈重试时必须保持不变。建议形式：

```text
clue:{clueId}:cognition-graph-workflow-v1
body-record:{recordId}:cognition-graph-workflow-v1
feedback:{feedbackId}:action-feedback-workflow-v1
```

数据库应对 `(user_id, workflow_version, idempotency_key)` 建唯一约束。

### `contextSnapshotId`

代表“本次 Agent 看见的不可变上下文”。主后端要保存它对应的：

```text
userId
graphId
graphVersion
evidenceIds
候选输入 ID
快照创建时间
```

同一快照重试可复用；图谱版本或证据集合变化后必须创建新快照 ID。

### `baseGraphVersion`

由 Agent 从输入图谱写入 Proposal。用户确认时，主后端必须再次检查：

```text
proposal.baseGraphVersion == 数据库当前 graphVersion
```

不相等就将 Proposal 标记为 `STALE`，禁止覆盖新版本。

## 8. 建议的数据库结构

表名可按现有后端规范调整，但职责和唯一约束不能丢。

| 逻辑表 | 必须保存的内容 | 关键约束 |
| --- | --- | --- |
| `cognition_graph` | 用户当前图谱、schemaVersion、graphVersion | `user_id` 唯一；`graph_id` 唯一 |
| `cognition_graph_node` | 节点字段和 nodeVersion | `(graph_id, node_id)` 唯一 |
| `cognition_graph_edge` | 边字段、active、edgeVersion | `(graph_id, edge_id)` 唯一 |
| `cognition_evidence` | 规范证据、来源、指纹、时间、事实等级 | `(user_id, source_type, source_id)` 唯一 |
| `cognition_proposal` | Proposal JSON、状态、baseGraphVersion、用户决定 | `proposal_id` 唯一；幂等键唯一 |
| `cognition_proposal_operation` | 每条 Patch 操作和顺序 | `(proposal_id, operation_index)` 唯一 |
| `cognition_graph_history` | 每次提交前后版本、Proposal、操作者 | `(graph_id, graph_version)` 唯一 |
| `cognition_context_snapshot` | 本次传给 Agent 的不可变上下文索引 | `context_snapshot_id` 唯一 |
| `cognition_agent_run` | 工作流终态、耗时、版本、错误、模型信息 | `run_id` 唯一 |
| `cognition_agent_node_run` | 各节点 observation | `(run_id, node_id)` 唯一 |
| `cognition_action_feedback` | 原始反馈、行动 ID、结果、处理状态 | `(user_id, feedback_id)` 唯一 |
| `outbox_event` | 提交后需要发送的刷新、通知、推送事件 | 事件 ID 唯一，可重试发布 |

不要把所有内容只塞进一个覆盖式 JSON 字段。可以保留完整 JSON 作为审计副本，但节点、边、版本、证据、Proposal 状态和幂等字段必须可以单独查询和约束。

## 9. 用户确认后如何原子应用 Patch

Agent 不实现这一步，必须由 Athena 主后端完成。

严格顺序：

```text
1. 接收用户对 proposalId 的决定
2. 从登录态取得 userId，不信任请求体中的用户身份
3. 开启数据库事务
4. 按 userId + graphId 锁定当前图谱行
5. 检查 Proposal 属于该用户且状态仍为 READY_FOR_CONFIRMATION
6. 检查 baseGraphVersion 等于当前 graphVersion
7. 检查同一决定是否已经成功处理，保证确认接口幂等
8. 按白名单逐条应用 operations
9. 验证最终节点、边、主题归属和每主题最多一个待办行动
10. graphVersion 只增加 1
11. 保存 graph history、证据关联和 Proposal 最终状态
12. 写入 Outbox 事件
13. 提交事务
```

如果任何一步失败，整个事务回滚。不能出现“节点写入成功、边写入失败、图谱版本已经增加”的半成品。

关系型数据库可使用等价的行锁策略，例如：

```sql
SELECT graph_id, graph_version
FROM cognition_graph
WHERE user_id = ? AND graph_id = ?
FOR UPDATE;
```

具体 SQL 要根据后端数据库方言实现，但目标相同：同一用户图谱同一时刻只能提交一个版本更新。

操作白名单：

| 操作 | 后端执行要求 |
| --- | --- |
| `ADD_NODE` | ID 必须不存在；非主题节点必须属于目标主题 |
| `UPDATE_NODE` | `targetId` 必须存在；节点版本必须是旧版本 + 1 |
| `ADD_EDGE` | 两端节点必须已存在；边 ID 和活动关系不能重复 |
| `SUPERSEDE_NODE` | 旧节点与替代节点类型一致，保留历史，不物理删除 |
| `DEACTIVATE_EDGE` | 只把已有边设为 inactive，不物理删除 |
| `NO_OP` | 不修改图谱；只能单独存在于无变化提案 |

用户决定处理：

| 决定 | Proposal 状态 | 是否应用 Patch |
| --- | --- | --- |
| 接受为主题/接受更新 | `ACCEPTED` | 是 |
| 只保存为知识 | `KEPT_AS_KNOWLEDGE` | 否 |
| 拒绝 | `REJECTED` | 否 |

`graphPreview` 只能展示差异和供审计比对。正式提交必须使用 `proposal.operations` 加当前数据库版本重新执行，不能把预览 JSON 整体写回。

## 10. 服务部署

### 10.1 构建

要求 Java 17。Agent 根目录包含 Maven Wrapper：

```powershell
cd "D:\aa\athena-zyj (2)\Back_End\athena-cognition-agent"
.\mvnw.cmd test
.\mvnw.cmd package
```

不依赖 IntelliJ IDEA，CI 和服务器都必须能通过 Wrapper 构建。

### 10.2 生产环境变量

```text
SPRING_PROFILES_ACTIVE=prod
ATHENA_MODEL_PROVIDER=openai-compatible
ATHENA_MODEL_API_KEY=<由密钥系统注入>
ATHENA_MODEL_BASE_URL=<千问 OpenAI 兼容 Base URL>
ATHENA_MODEL_NAME=<实际模型 ID>
ATHENA_MODEL_ENDPOINT_PATH=/chat/completions
ATHENA_MODEL_TIMEOUT_MS=30000
ATHENA_MODEL_JSON_MODE=true
```

如果 Base URL 不包含 `/compatible-mode/v1`，应按实际兼容地址调整；如果填写的是完整 `/chat/completions` 地址，则 `ATHENA_MODEL_ENDPOINT_PATH` 应为空，禁止重复拼接路径。

真实 API Key 不能写入：

```text
Java 源码
application.yml
Android 客户端
Git 仓库
交接文档
普通日志
```

`prod` Profile 下如果 Provider 仍是 `mock`，或 Key、Base URL、Model Name 缺失，服务会启动失败。这是故意的生产保护。

### 10.3 启动和健康检查

```powershell
java -jar target/athena-cognition-agent-0.0.1-SNAPSHOT.jar
```

健康检查：

```http
GET /actuator/health
```

`/internal/**` 和 `/actuator/**` 当前没有业务用户鉴权。正式部署必须放在内网，通过网关、服务身份、mTLS 或现有服务间认证保护；不得直接暴露公网。

## 11. 超时、重试和任务状态

当前 Agent 工作流是同步 HTTP。主后端应把一次调用保存为任务，而不是让 Android 长时间持有连接并承担重试。

建议流程：

```text
Android 提交标记或反馈
-> 主后端先持久化业务输入
-> 创建 Agent 任务并立即返回任务状态
-> 后端工作线程调用 Agent
-> 保存 Proposal 或错误
-> Android 查询/接收结果
```

是否采用消息队列取决于现有后端规模；即使第一版使用线程池，也必须保留任务表、幂等键和可重试状态。

重试规则：

- 只在 `error.retryable=true` 时重试。
- `MODEL_TIMEOUT`、`MODEL_UNAVAILABLE` 可退避重试。
- `BLOCKED`、`REJECTED`、`IDEMPOTENCY_CONFLICT` 不能原样自动重试。
- `STALE` 不是网络重试；必须重新读取图谱并生成新快照。
- 重试使用同一 `idempotencyKey`，但使用新的 `runId`。
- 后端必须设置最大重试次数和死信/人工处理状态，不能无限循环。

用户确认成功后，通过 Outbox 发布“图谱已更新”“健康首页刷新”“下一步行动可用”等事件。不能在数据库事务提交前直接推送，否则事务回滚后用户会收到不存在的结果。

## 12. 可观测性

节点指标：

```text
athena.cognition.graph.node.runs
athena.cognition.graph.node.duration
athena.cognition.graph.patch.operations
```

工作流终态指标：

```text
athena.cognition.workflow.runs
athena.cognition.workflow.duration
```

工作流指标只使用低基数标签：

```text
workflow
status
trigger_type
```

禁止把 `runId`、`graphId`、用户备注或文章原文放进指标标签。

每次运行应持久化：

```text
runId
triggerType
workflowVersion
contextSnapshotId
最终 status 和 error
节点 nodeId/nodeVersion
promptVersion
modelProvider/modelName
schemaResult/policyResult/modelPolicyResult
modelCallStatus/modelErrorCode
latencyMs
inputTokens/outputTokens/totalTokens
estimatedCost
retryCount
evidenceIds
operationCount
baseGraphVersion/previewGraphVersion
用户最终决定
```

Agent 日志已经避免记录完整文章摘录、完整身体记录、完整 Prompt 和完整图谱。主后端也必须保持同样的脱敏规则。

至少建立以下告警：

- `FAILED` 比例持续升高。
- `MODEL_TIMEOUT` 或 `MODEL_UNAVAILABLE` 持续升高。
- `BLOCKED` 突然异常升高，可能是合同漂移。
- `STALE` 持续升高，可能是并发控制有问题。
- P95/P99 工作流耗时超过业务阈值。
- Token 或成本异常增长。
- Proposal 长时间停留在 `READY_FOR_CONFIRMATION`。

## 13. 测试和 CI 验收

固定评估集：

```text
src/test/resources/fixtures/cognition-graph-workflow-v1/evaluation-cases.json
src/test/resources/fixtures/action-feedback-workflow-v1/evaluation-cases.json
```

主流程 9 条基线覆盖：

```text
首次创建
增量更新
重复证据
用户指定主题
歧义确认
上下文隔离
非法身体事实
sourceId 内容冲突
相同文本不同时间的身体记录
```

反馈 8 条基线覆盖四种反馈、重复、已关闭行动、未知行动和反馈 ID 冲突。

CI 必须执行：

```powershell
.\mvnw.cmd test
```

本次冻结基线：

```text
Tests run: 100
Failures: 0
Errors: 0
Skipped: 0
JSON Schema parsed: 35
BUILD SUCCESS
```

后端联调还必须增加以下集成测试：

1. Android 重复提交同一线索，只产生一个逻辑任务。
2. 两个请求并发更新同一图谱，只允许一个版本提交成功。
3. 用户确认旧 Proposal 时返回 `STALE`，不能覆盖新图谱。
4. `graphPreview` 不会直接写数据库。
5. 跨用户 graphId、topicId、actionId 全部被拒绝。
6. 用户拒绝或只保存知识时不应用 Patch。
7. 事务中任一边写入失败时节点、版本和 Proposal 状态全部回滚。
8. 同一反馈重复提交不会重复关闭行动或新增反馈节点。
9. Agent 超时后按同一幂等键重试，不产生两个 Proposal。
10. Outbox 只在图谱事务提交后发布。

## 14. 安全与数据隔离

主后端必须完成：

- 从登录态获取 `userId`，不接受 Android 自报用户 ID。
- 加载图谱、证据、主题和行动时全部带 `userId` 条件。
- 调用 Agent 前验证 `userSelectedTopicId` 和 `actionId` 属于当前用户。
- 保存 Proposal 时绑定 `userId`、`graphId` 和 `runId`。
- 用户确认时再次验证 Proposal 所有权。
- 限制请求体大小、节点数、边数和调用频率。
- 对内部接口做服务鉴权和网络隔离。
- 对模型 API Key 使用部署平台密钥管理。
- 设置健康数据访问审计和保留期限。

Agent 的上下文白名单只能减少模型可见数据，不能代替主后端的用户权限校验。主后端传错用户数据时，Agent 无法知道登录身份，因此用户隔离必须在调用 Agent 之前完成。

## 15. 代码和合同位置

| 内容 | 路径 |
| --- | --- |
| Agent 工程 | `Back_End/athena-cognition-agent` |
| 总合同常量 | `src/main/java/.../graph/contract/GraphContract.java` |
| 图谱合同 | `src/main/java/.../graph/contract/PersonalCognitionGraph.java` |
| Proposal 合同 | `src/main/java/.../graph/contract/GraphUpdateProposal.java` |
| 主工作流 | `src/main/java/.../workflow/service/CognitionGraphWorkflow.java` |
| 反馈工作流 | `src/main/java/.../feedbackworkflow/service/ActionFeedbackWorkflow.java` |
| 完整 Guard | `src/main/java/.../guard/service/GraphPatchGuardService.java` |
| Scope 防越权 | `src/main/java/.../scope/policy/GraphUpdateScopePolicyValidator.java` |
| JSON Schema | `src/main/resources/schemas` |
| 固定评估集 | `src/test/resources/fixtures` |
| Agent 开发说明 | `docs/cognition-agent-v1.md` |
| 第一次前后端合同 | `docs/cognition-contract-v1.md` |

Java 包路径前缀统一为：

```text
com/whu/software/athena/cognitionagent
```

## 16. 当前仍未完成的内容

以下不是遗漏，而是明确属于后端部署和联调阶段：

- 正式数据库表和数据迁移。
- 主后端到 Agent 的服务鉴权。
- Android Mock 数据替换为主后端接口。
- Agent 任务队列、重试、死信和并发控制。
- Proposal 人工确认接口和事务应用器。
- 图谱历史、运行记录和上下文快照持久化。
- Outbox、推送和健康首页刷新事件。
- 真实千问凭证下的在线固定评估。
- 生产告警、限流、容量和成本阈值。

真实千问 Provider 的代码和生产配置保护已经存在，但本次交接不能声称真实线上模型质量已经验收；仓库测试使用 Mock 和模拟 HTTP，不包含真实密钥调用结果。

## 17. 后端最终验收清单

- [ ] Java 17 环境使用 `mvnw` 构建通过。
- [ ] `prod` Profile 使用真实 `openai-compatible` Provider，Mock 无法启动。
- [ ] Agent 只部署在受保护的内部网络。
- [ ] 主后端完成节点 1、主图谱工作流、反馈工作流三个入口接入。
- [ ] 节点 2-10 不被主后端自行编排。
- [ ] 图谱、证据、Proposal、运行记录、快照和历史均已持久化。
- [ ] 用户隔离和资源所有权在调用 Agent 前完成。
- [ ] 幂等键和数据库唯一约束生效。
- [ ] 用户确认前正式图谱不变化。
- [ ] Proposal 在单事务内应用，版本冲突返回 `STALE`。
- [ ] `graphPreview` 仅展示和审计，不直接落库。
- [ ] 拒绝和只保存知识不会应用 Patch。
- [ ] 反馈重复提交不会重复关闭行动。
- [ ] Outbox 在事务提交后可靠发布。
- [ ] 节点和工作流指标可查询，日志不含健康原文和密钥。
- [ ] 100 个 Agent 测试及后端集成测试全部通过。
- [ ] 使用真实千问配置跑完固定评估并人工抽查输出。

完成以上清单后，才算第二次后端交接真正完成。仅仅把 JAR 启动起来，不算完成部署。
