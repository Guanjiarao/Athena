# Athena 认知图谱管线：前端对接说明 V1

> 面向对象：Athena Android 前端开发者
> 后端状态：已部署开发环境（121.41.200.73），真实千问在线，全链路已验证
> 写于：2026-08-28

## 0. 先看这里：这次多了什么

之前的认知闭环（线索→整理草稿→主题→行动）继续有效，**一个接口都没有变**。

这次后端在**旁边新增了一条 AI 图谱管线**：用户每个账号有一份持续演化的"个人认知图谱"，AI 根据新线索对图谱提增量修改建议（Proposal），**用户确认后才生效**。你的工作是给这条新管线做页面。

和旧流程的关系：

```text
旧流程（不动）：线索 → fixed-v1 整理草稿 → 用户决定 → 主题
新流程（本次）：线索 → AI 图谱提案 → 用户确认 → 图谱版本+1
```

两条流程共用 `POST /athena/cognition/clues` 入口——你创建线索的代码不用改，后端会自动同时触发两条管线。

## 1. 五个核心概念

| 概念 | 通俗解释 | 对应页面 |
| --- | --- | --- |
| Agent 任务 `agentTask` | 一次 AI 处理的后台任务，异步执行 | 状态轮询/等待页 |
| 提案 `proposal` | AI 对图谱的增量修改建议，待用户确认 | 提案确认页（核心页面） |
| 图谱 `graph` | 用户的个人认知图谱（节点+边+版本号） | 图谱浏览页 |
| 图谱行动 `graph action` | 图谱里的"下一步观察任务"节点 | 行动反馈页 |
| 运行状态 | 任务/提案各自的状态机 | 见第 4 节 |

**铁律：提案确认之前，图谱不会变。** `PROPOSAL_READY` 只表示 AI 校验通过，不代表生效。确认页展示的 `graphPreview` 是模拟预览，不是真实图谱。

## 2. 接口一览

Base：`{网关}/athena/cognition`，开发网关 `http://121.41.200.73:13715`
鉴权：与现有接口相同，`Authorization: Bearer <sa-token>`，响应统一 `Result<T>` 信封（`code/message/data/total`）。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/clues` | （旧接口不变）创建线索，RELATED 类型自动触发 AI 任务 |
| POST | `/graph-update-tasks` | 手动触发图谱整理（等价"帮我整理"；也用于歧义时带用户选择的主题重跑） |
| GET | `/agent-tasks?limit=20` | 当前用户的 AI 任务列表（新的在前） |
| GET | `/agent-tasks/{taskId}` | 单个任务状态 |
| GET | `/proposals?status=&page=&pageSize=` | 提案列表 |
| GET | `/proposals/{proposalId}` | 提案详情（含 operations 和 graphPreview） |
| POST | `/proposals/{proposalId}/decision` | 提案决定：接受/只存知识/拒绝 |
| POST | `/graph-actions/{actionId}/feedback` | 对图谱行动提交反馈 |
| GET | `/graph` | 当前用户图谱（节点+边+版本） |

## 3. 页面流程

### 3.1 主流程：线索 → 提案 → 确认

```text
用户标记文章（POST /clues，不变）
  -> 后端自动创建 AI 任务（异步）
  -> 前端轮询 GET /agent-tasks/{taskId}（建议 2-3s 一次，最多等 60s）
  -> 任务 SUCCEEDED 且返回 proposalId
  -> 打开提案确认页：GET /proposals/{proposalId}
  -> 用户选择：接受 / 只存知识 / 拒绝（POST .../decision）
  -> 接受后图谱版本+1，可展示 GET /graph
```

提案详情里对确认页有用的字段：

| 字段 | 说明 |
| --- | --- |
| `route` | `CREATE_BRANCH`（新主题）或 `UPDATE_EXISTING`（更新已有主题） |
| `changeSummary` | AI 的一句话变更说明，可直接展示 |
| `operations[]` | 增量操作列表：`ADD_NODE/UPDATE_NODE/ADD_EDGE` 等，每项含节点类型（`TOPIC/SELF_REPORTED_FACT/PATTERN_HYPOTHESIS/OPEN_QUESTION/ACTION/SOURCE_EVIDENCE`）和内容 |
| `graphPreview` | 模拟应用后的完整图谱预览，**只能用于展示 diff，不是真实图谱** |
| `baseGraphVersion` | 提案基于的图谱版本（展示用，无需处理） |

`decision` 请求体：`{"decision": "ACCEPT" | "KEEP_AS_KNOWLEDGE" | "REJECT"}`

### 3.2 歧义流程：NEEDS_CONFIRMATION（必须做，不是异常）

AI 拿不准该更新哪个主题时，任务停在 `NEEDS_CONFIRMATION`。**这是正常的保守设计，不是失败**——系统故意停下来问人，而不是让 AI 瞎猜。

```text
任务 NEEDS_CONFIRMATION
  -> 页面提示："这条线索该归到哪个主题？"
  -> 展示候选：现有主题列表（从 GET /graph 取 TOPIC 节点）+ "开一个新主题"
  -> 用户选定后重新提交：
     POST /graph-update-tasks
     {"triggerType":"USER_REQUEST","clueIds":["clue_xxx"],
      "userSelectedTopicId":"topic_xxx"}   // 选了已有主题
     或不带 userSelectedTopicId 但带 suggestedTopicTitle  // 开新主题
  -> 新任务重新走 3.1 流程
```

注意：真模型下"开新主题"也可能再次被 AI 判为歧义（它会保守），多给一次重试引导即可。

### 3.3 行动反馈流程

```text
GET /graph 找到 type=ACTION 且 actionStatus=PENDING 的节点
  -> 展示行动（title/description）和四个反馈选项
  -> POST /graph-actions/{actionId}/feedback
     {"result":"OCCURRED|NOT_OCCURRED|UNCERTAIN|SKIPPED","note":"可选","occurredAt":null}
  -> 产生一个新的反馈提案（走 3.1 的确认流程）
  -> 确认后行动变为 COMPLETED/SKIPPED，图谱生成反馈证据
```

同一行动只能反馈一次，重复提交返回已有任务，不会产生重复数据。

### 3.4 手动整理入口

`POST /graph-update-tasks`：`{"triggerType":"USER_REQUEST","clueIds":["clue_a","clue_b"],"suggestedTopicTitle":"可选标题","userSelectedTopicId":null}`。响应就是任务对象，后续轮询同 3.1。

## 4. 状态与错误处理

### 任务状态（`agentTask.status`）

| 状态 | 含义 | 前端动作 |
| --- | --- | --- |
| `PENDING` / `RUNNING` | 排队/执行中 | 继续轮询 |
| `SUCCEEDED` | 成功，`proposalId` 有值 | 打开提案确认页 |
| `NO_CHANGE` | 内容重复或无变化 | 静默结束，不打扰用户 |
| `NEEDS_CONFIRMATION` | AI 拿不准目标主题 | 走 3.2 选主题流程 |
| `FAILED` | 失败（`errorCode` 有原因） | 提示稍后重试；`errorRetryable=true` 的可自动重试 |
| `DEAD` | 重试耗尽 | 提示失败，不再重试 |

### 提案决定可能返回的错误（都在 `data.errorCode`，HTTP 200）

| errorCode | 含义 | 前端动作 |
| --- | --- | --- |
| `COGNITION_STATE_CONFLICT` | 该提案已处理过（重复点击） | 刷新提案状态即可 |
| `COGNITION_VERSION_CONFLICT` | 图谱已被更新（提案过期 STALE） | 提示"内容已更新"，重新走整理流程 |
| `COGNITION_PROPOSAL_NOT_READY` | 提案当前状态不能确认 | 刷新 |
| `COGNITION_RATE_LIMITED` | 触发限流（每用户每分钟 5 个 AI 任务） | 提示"操作过于频繁，请稍后再试" |
| `COGNITION_NOT_FOUND` | 对象不存在或不属于当前用户 | 按普通错误处理 |

### 图谱节点/边类型（GET /graph 展示用）

节点：`TOPIC`（主题）、`SOURCE_EVIDENCE`（证据）、`SELF_REPORTED_FACT`（用户自述事实）、`PATTERN_HYPOTHESIS`（待验证猜想）、`OPEN_QUESTION`（待确认问题）、`ACTION`（行动）。
边：`ABOUT`（证据→主题）、`GROUNDS`（证据→事实/问题）、`SUPPORTS`、`CHALLENGES`、`NEXT_STEP_FOR`（行动→主题）、`FEEDBACK_FOR`（反馈证据→行动）。
节点状态：`ACTIVE/SUPERSEDED/ARCHIVED`，展示时一般只看 ACTIVE。

## 5. 环境信息

- 开发网关：`http://121.41.200.73:13715`（与现有接口同一入口）
- 登录用现有 sa-token 流程，token 获取方式不变
- 当前 AI 用真实千问（qwen-plus），一次任务约 5-30 秒，轮询间隔建议 2-3 秒
- AI 输出内容（阶段理解、开放问题、行动文案）是模型生成的，展示时保留"AI 生成，待确认"的语义，不要当成确定结论渲染

## 6. 明确不要做的事

- 不要把 `graphPreview` 当作真实图谱持久化或覆盖展示状态，它只用于确认页 diff
- 不要跳过一个任务的轮询直接假设提案存在；提案只能从 SUCCEEDED 的任务来
- 不要对 `NEEDS_CONFIRMATION` 报错，它是正常业务态
- 不要对同一行动重复提交反馈并期望两次生效（幂等返回同一任务）
- 不要在前端缓存提案决定结果后阻止用户刷新——服务端以 409 冲突兜底，以服务端状态为准

## 7. 联调建议顺序

1. 提案确认页（核心）：建线索 → 轮询 → 确认 → 看图谱
2. 行动反馈页：反馈 → 确认 → 行动关闭
3. NEEDS_CONFIRMATION 选主题流程
4. 错误态（重复决定、限流、过期）
5. 图谱浏览页
