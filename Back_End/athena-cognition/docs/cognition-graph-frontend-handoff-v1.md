# Athena 认知图谱管线：前端对接说明 V1.1

> 面向对象：Athena Android 前端开发者
> 后端状态：已部署开发环境（121.41.200.73），真实千问在线，全链路已验证
> 写于：2026-08-28；V1.1 修订：2026-08-29（补 agentTaskId 直连、by-clue 反查、真实 JSON、OpenAPI 文件、测试账号）
> **OpenAPI 文件：`Back_End/athena-cognition/openapi-graph-v1.yaml`（可直接导入 Apifox/Postman/Swagger）**

## 0. 先看这里：这次多了什么

之前的认知闭环（线索→整理草稿→主题→行动）继续有效，**一个接口都没有变**。

这次后端在**旁边新增了一条 AI 图谱管线**：每个账号有一份持续演化的"个人认知图谱"，AI 根据新线索对图谱提增量修改建议（Proposal），**用户确认后才生效**。

```text
旧流程（不动）：线索 → fixed-v1 整理草稿 → 用户决定 → 主题
新流程（本次）：线索 → AI 图谱提案 → 用户确认 → 图谱版本+1
```

两条流程共用 `POST /athena/cognition/clues` 入口——创建线索的代码不用改，后端会自动同时触发两条管线。

**铁律：提案确认之前，图谱不会变。** `PROPOSAL_READY` 只表示 AI 校验通过。确认页展示的 `graphPreview` 是模拟预览，不是真实图谱。

## 1. 测试账号与环境

- 开发网关：`http://121.41.200.73:13715`（与现有接口同一入口）
- 登录接口（与现有 App 相同）：
  1. `POST /athena/login/code?phone=<手机号>` 发验证码
  2. `POST /athena/login` body `{"phone": "<手机号>", "code": "<验证码>"}` 拿 token
- 联调用现成测试账号：userId 1024（图谱里已有 1 个主题和若干证据，方便直接看到效果）。当前有效 token：
  `Authorization: Bearer HPZTciG1qFwjU8q5wwmCgcsJuEYsKLlH`
  （过期了按上面登录流程重新拿即可）
- 一次 AI 任务约 5-30 秒，轮询间隔建议 2-3 秒，最多等 60 秒

## 2. 接口一览

Base：`{网关}/athena/cognition`。鉴权 `Authorization: Bearer <sa-token>`，响应统一 `Result<T>` 信封。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/clues` | （旧接口）创建线索；RELATED 时**响应直接带 `data.agentTask.taskId`** |
| GET | `/agent-tasks/by-clue/{clueId}` | **新增：按 clueId 精确反查 Agent 任务** |
| POST | `/graph-update-tasks` | 手动触发整理 / 歧义时带用户选择重跑 |
| GET | `/agent-tasks?limit=20` | 任务列表（新的在前） |
| GET | `/agent-tasks/{taskId}` | 单个任务状态 |
| GET | `/proposals?status=&page=&pageSize=` | 提案列表 |
| GET | `/proposals/{proposalId}` | 提案详情（operations + graphPreview） |
| POST | `/proposals/{proposalId}/decision` | 提案决定：接受/只存知识/拒绝 |
| POST | `/graph-actions/{actionId}/feedback` | 图谱行动反馈 |
| GET | `/graph` | 当前用户真实图谱 |

**前端不需要从任务列表猜任务**：创建线索的响应里直接有 `agentTask.taskId`；万一丢失，用 `GET /agent-tasks/by-clue/{clueId}` 按 clueId 精确反查（不存在返回 `COGNITION_NOT_FOUND`）。

## 3. 页面流程

### 3.1 主流程：线索 → 提案 → 确认

```text
POST /clues（不变）
  -> 响应 data.agentTask.taskId 直接拿到任务 ID
  -> 轮询 GET /agent-tasks/{taskId}（2-3s 一次）
  -> status=SUCCEEDED 且有 proposalId
  -> GET /proposals/{proposalId} 打开确认页
  -> POST .../decision（ACCEPT / KEEP_AS_KNOWLEDGE / REJECT）
  -> 接受后 GET /graph 看到图谱版本+1
```

### 3.2 歧义流程：NEEDS_CONFIRMATION（正常业务态，不是错误）

AI 拿不准该更新哪个主题时，任务停在 `NEEDS_CONFIRMATION`——系统故意停下来问人，不让 AI 瞎猜。

此时任务响应里可用的字段：

| 字段 | 内容 |
| --- | --- |
| `clueIds` | 本次处理的线索 |
| `suggestedTopicTitle` | 建议的新主题标题 |
| `candidates` | **当前恒为 null**——Agent 合同（v1）不返回候选列表。请用 `GET /graph` 中 `type=TOPIC` 且 `status=ACTIVE` 的节点作为候选主题列表（与 AI 看到的候选是同一集合） |

"需要用户选择的原因"是固定语义：建议标题没有精确命中唯一主题，且 AI 无法在候选中确定目标分支。页面上统一按这个话术展示即可，然后：

```text
展示：现有主题列表（GET /graph 的 TOPIC 节点）+ "开一个新主题"
  -> 用户选定后重新提交：
     POST /graph-update-tasks
     {"triggerType":"USER_REQUEST","clueIds":["clue_xxx"],"userSelectedTopicId":"topic_xxx"}
     // 开新主题则不带 userSelectedTopicId，带 suggestedTopicTitle
  -> 新任务重新走 3.1
```

### 3.3 行动反馈流程

```text
GET /graph 找 type=ACTION 且 actionStatus=PENDING 的节点
  -> POST /graph-actions/{actionId}/feedback
     {"result":"OCCURRED|NOT_OCCURRED|UNCERTAIN|SKIPPED","note":"可选","occurredAt":null}
  -> 响应是一个 Agent 任务（workflowVersion=action-feedback-workflow-v1）
  -> 轮询到 SUCCEEDED 拿到 proposalId，走同一个 proposals/decision 确认流程
  -> 确认后行动变为 COMPLETED/SKIPPED
```

同一行动只能反馈一次，重复提交返回同一任务（幂等），不会产生重复数据。

## 4. 真实响应示例（2026-08-29 开发环境实拍）

### 4.1 POST /athena/cognition/clues

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "clue": {
      "id": "clue_108",
      "type": "ARTICLE_HIGHLIGHT",
      "intent": "RELATED",
      "relationType": "CURRENT",
      "helpRequestType": "OBSERVE",
      "articleId": "doc-demo-114946",
      "articleTitle": "联调文档示例文章",
      "articleType": 100,
      "selectedText": "这周三次睡前用暖光台灯替代顶灯阅读……",
      "cycleRelation": "NO_RELATION",
      "source": "KNOWLEDGE_ARTICLE",
      "status": "PENDING",
      "suggestedTopicTitle": "late-night screen time manual",
      "originalLabel": "和我有关",
      "createdAt": "2026-08-28T19:49:46.334Z",
      "updatedAt": "2026-08-28T19:49:46.334Z"
    },
    "digestTask": { "triggered": false },
    "agentTask": {
      "taskId": "task_234b3e05-e731-4750-9c70-e73c1de74b68",
      "workflowVersion": "cognition-graph-workflow-v1",
      "idempotencyKey": "clue:clue_108:cognition-graph-workflow-v1",
      "triggerType": "CLUE_CREATED",
      "status": "PENDING",
      "retryCount": 0,
      "maxRetry": 3,
      "createdAt": "2026-08-28T19:49:46.408Z",
      "updatedAt": "2026-08-28T19:49:46.408Z",
      "clueIds": ["clue_108"]
    }
  }
}
```

注意：`agentTask` 只在 RELATED 线索时出现；QUESTION / KNOWLEDGE_ONLY 线索不触发图谱管线，该字段不存在（null）。

### 4.2 GET /athena/cognition/agent-tasks/{taskId}

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "taskId": "task_234b3e05-e731-4750-9c70-e73c1de74b68",
    "workflowVersion": "cognition-graph-workflow-v1",
    "idempotencyKey": "clue:clue_108:cognition-graph-workflow-v1",
    "triggerType": "CLUE_CREATED",
    "status": "SUCCEEDED",
    "retryCount": 0,
    "maxRetry": 3,
    "proposalId": "proposal_85ad6fdf34f6cc88a2846ab8",
    "createdAt": "2026-08-28T19:49:46.408Z",
    "updatedAt": "2026-08-28T19:49:58.067Z",
    "clueIds": ["clue_108"]
  }
}
```

失败的任务长这样（`errorCode`/`errorRetryable` 只在失败时出现）：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "taskId": "task_4b4fefb4-eb76-4b01-89e2-b89b048549f6",
    "workflowVersion": "cognition-graph-workflow-v1",
    "idempotencyKey": "clue:clue_106:cognition-graph-workflow-v1",
    "triggerType": "CLUE_CREATED",
    "status": "FAILED",
    "retryCount": 0,
    "maxRetry": 3,
    "errorCode": "MODEL_OUTPUT_INVALID",
    "errorRetryable": false,
    "createdAt": "2026-08-28T19:43:35.482Z",
    "updatedAt": "2026-08-28T19:43:47.484Z",
    "clueIds": ["clue_106"]
  }
}
```

### 4.3 GET /athena/cognition/proposals/{proposalId}（节选，实际 operations 更多）

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "proposalId": "proposal_85ad6fdf34f6cc88a2846ab8",
    "status": "READY_FOR_CONFIRMATION",
    "route": "UPDATE_EXISTING",
    "targetTopicId": "topic_1319b6d33ae89435b84d9538",
    "baseGraphVersion": 6,
    "changeSummary": "Added one new self-reported fact and one new pattern hypothesis based on clue_108; no existing nodes were revised.",
    "evidenceIds": ["clue_108"],
    "operations": [
      {
        "operationIndex": 0,
        "operationType": "ADD_NODE",
        "node": {
          "id": "source_203d71c2744000bd5b6da903",
          "type": "SOURCE_EVIDENCE",
          "status": "ACTIVE",
          "content": "这周三次睡前用暖光台灯替代顶灯阅读……",
          "topicId": "topic_1319b6d33ae89435b84d9538",
          "version": 1
        },
        "evidenceIds": ["clue_108"],
        "reason": "Add the canonical source evidence used by this proposal."
      },
      {
        "operationIndex": 1,
        "operationType": "UPDATE_NODE",
        "targetId": "topic_1319b6d33ae89435b84d9538",
        "node": {
          "id": "topic_1319b6d33ae89435b84d9538",
          "type": "TOPIC",
          "title": "late-night screen time manual",
          "content": "The user reports that using warm-light desk lamps instead of overhead lighting …（AI 生成的阶段理解）",
          "version": 6
        },
        "evidenceIds": ["clue_108"],
        "reason": "Refresh only the target topic stage understanding."
      }
    ],
    "graphPreview": { "graphSchemaVersion": "personal-cognition-graph-v1", "graphVersion": 7, "nodes": ["……"], "edges": ["……"] }
  }
}
```

### 4.4 POST /athena/cognition/proposals/{proposalId}/decision

请求：`{"decision":"ACCEPT"}`

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "proposalId": "proposal_85ad6fdf34f6cc88a2846ab8",
    "status": "ACCEPTED",
    "userDecision": "ACCEPT",
    "appliedGraphVersion": 7
  }
}
```

### 4.5 GET /athena/cognition/graph（节选）

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "graphSchemaVersion": "personal-cognition-graph-v1",
    "graphId": "graph_994a5211-0c52-4d68-a65f-361730f3b86b",
    "graphVersion": 7,
    "nodes": [
      {
        "id": "topic_1319b6d33ae89435b84d9538",
        "type": "TOPIC",
        "status": "ACTIVE",
        "title": "late-night screen time manual",
        "content": "The user reports that using warm-light desk lamps …",
        "evidenceIds": ["clue_90", "clue_94", "clue_102", "clue_103", "clue_105", "clue_108"],
        "version": 6,
        "createdAt": "2026-08-27T18:56:58.567Z",
        "updatedAt": "2026-08-28T19:51:53.439Z"
      },
      {
        "id": "action_b9a69c97e714f08f73abb9d2",
        "type": "ACTION",
        "status": "ACTIVE",
        "actionType": "RECORD_BODY",
        "actionStatus": "PENDING",
        "title": "Record one related body change",
        "feedbackOptions": ["OCCURRED", "NOT_OCCURRED", "UNCERTAIN", "SKIPPED"]
      }
    ],
    "edges": [
      { "id": "edge_xxx", "type": "ABOUT", "fromNodeId": "source_xxx", "toNodeId": "topic_xxx", "active": true }
    ]
  }
}
```

### 4.6 POST /athena/cognition/graph-actions/{actionId}/feedback

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "taskId": "task_0a49d8cf-fc7c-44c6-92ca-754f1d8c28e4",
    "workflowVersion": "action-feedback-workflow-v1",
    "idempotencyKey": "feedback:action_b9a69c97e714f08f73abb9d2:action-feedback-workflow-v1",
    "triggerType": "ACTION_FEEDBACK",
    "status": "PENDING",
    "retryCount": 0,
    "maxRetry": 3,
    "createdAt": "2026-08-28T19:52:59.015Z",
    "updatedAt": "2026-08-28T19:52:59.015Z",
    "clueIds": []
  }
}
```

### 4.7 失败响应示例：errorCode 的确切位置

重复决定同一提案（HTTP 状态也是 200，业务错误看 `code` 和 `data.errorCode`）：

```json
{
  "code": 409,
  "message": "提案已处理",
  "data": {
    "errorCode": "COGNITION_STATE_CONFLICT",
    "objectId": "proposal_85ad6fdf34f6cc88a2846ab8",
    "currentStatus": "ACCEPTED"
  }
}
```

规律：业务错误 HTTP 恒 200；语义码在外层 `code`；稳定错误码在 `data.errorCode`；`objectId`/`currentStatus` 视错误类型可选。未登录则是 `code=401`、无 `data.errorCode`；网关层无 token/假 token 直接拦截返回非 200 HTTP。

## 5. 状态与错误处理

### 任务状态（`agentTask.status`）

| 状态 | 含义 | 前端动作 |
| --- | --- | --- |
| `PENDING` / `RUNNING` | 排队/执行中 | 继续轮询 |
| `SUCCEEDED` | 成功，`proposalId` 有值 | 打开提案确认页 |
| `NO_CHANGE` | 内容重复或无变化 | 静默结束，不打扰用户 |
| `NEEDS_CONFIRMATION` | AI 拿不准目标主题 | 走 3.2 选主题流程 |
| `FAILED` | 失败（`errorCode` 有原因） | 提示稍后重试；`errorRetryable=true` 可自动重试 |
| `DEAD` | 重试耗尽 | 提示失败，不再重试 |

### errorCode 速查（都在 `data.errorCode`，HTTP 200）

| errorCode | code | 含义 | 前端动作 |
| --- | --- | --- | --- |
| `COGNITION_INVALID_ARGUMENT` | 400 | 字段/枚举非法 | 检查请求体 |
| `COGNITION_NOT_FOUND` | 404 | 对象不存在或不属于当前用户 | 按普通错误处理 |
| `COGNITION_STATE_CONFLICT` | 409 | 提案已处理/行动已反馈（重复点击） | 刷新状态即可 |
| `COGNITION_VERSION_CONFLICT` | 409 | 图谱已更新，提案过期（STALE） | 提示"内容已更新"，重新走整理流程 |
| `COGNITION_PROPOSAL_NOT_READY` | 409 | 提案当前状态不能确认 | 刷新 |
| `COGNITION_RATE_LIMITED` | 409 | 限流（每用户每分钟 5 个 Agent 任务） | 提示"操作过于频繁，请稍后再试" |
| `COGNITION_AGENT_TASK_FAILED` | 500 | Agent 任务执行失败 | 提示稍后重试 |

### 图谱节点/边类型（展示用）

节点：`TOPIC`（主题）、`SOURCE_EVIDENCE`（证据）、`SELF_REPORTED_FACT`（用户自述事实）、`PATTERN_HYPOTHESIS`（待验证猜想）、`OPEN_QUESTION`（待确认问题）、`ACTION`（行动）。
边：`ABOUT`（证据→主题）、`GROUNDS`（证据→事实/问题）、`SUPPORTS`、`CHALLENGES`、`NEXT_STEP_FOR`（行动→主题）、`FEEDBACK_FOR`（反馈证据→行动）。
展示一般只看 `status=ACTIVE` 的节点。

## 6. 明确不要做的事

- 不要把 `graphPreview` 当作真实图谱持久化或覆盖展示，它只用于确认页 diff
- 不要跳过轮询直接假设提案存在；提案只能从 SUCCEEDED 的任务来
- 不要对 `NEEDS_CONFIRMATION` 报错，它是正常业务态
- 不要对同一行动重复提交反馈并期望两次生效（幂等返回同一任务）
- 不要把 AI 生成内容（阶段理解、开放问题、行动文案）渲染成确定结论，保留"AI 生成，待确认"语义

## 7. 联调建议顺序

1. 提案确认页（核心）：建线索（拿 agentTask.taskId）→ 轮询 → 确认 → 看图谱
2. 行动反馈页：反馈 → 轮询 → 确认 → 行动关闭
3. NEEDS_CONFIRMATION 选主题流程
4. 错误态（重复决定、限流、过期）
5. 图谱浏览页
