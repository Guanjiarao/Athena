# RAG 对话记录前端接入文档

> 适用模块：`athena-rag`
> 更新时间：2026-04-14

## 一、目标

本文档用于给前端同学接入 RAG 聊天能力，重点覆盖：

- 会话列表查询
- 历史消息查询（对话记录）
- SSE 流式问答
- 会话重命名与删除
- 消息点赞 / 点踩
- 任务停止

如果前端要实现一个完整聊天页，至少需要接入以下 3 个接口：

1. `GET /conversations`
2. `GET /conversations/{conversationId}/messages`
3. `GET /rag/v3/chat`

---

## 二、统一返回结构

除 SSE 流式接口外，普通接口都采用统一返回结构：

```json
{
  "code": "0",
  "message": null,
  "data": {}
}
```

说明：

- `code = "0"` 表示成功
- 业务数据在 `data` 字段中
- `message` 失败时会返回错误信息

---

## 三、接口总览

| 功能 | 方法 | 路径 |
|------|------|------|
| 获取会话列表 | `GET` | `/conversations` |
| 获取某个会话的历史消息 | `GET` | `/conversations/{conversationId}/messages` |
| 重命名会话 | `PUT` | `/conversations/{conversationId}` |
| 删除会话 | `DELETE` | `/conversations/{conversationId}` |
| 发起 / 继续流式对话 | `GET` | `/rag/v3/chat` |
| 停止当前生成任务 | `POST` | `/rag/v3/stop` |
| 消息点赞 / 点踩 | `POST` | `/conversations/messages/{messageId}/feedback` |

---

## 四、会话相关接口

### 1. 获取会话列表

#### 请求

```http
GET /conversations
```

#### 用途

获取当前登录用户的会话列表，用于前端左侧会话栏展示。

#### 返回示例

```json
{
  "code": "0",
  "message": null,
  "data": [
    {
      "conversationId": "2002713020947939330",
      "title": "如何缓解焦虑",
      "lastTime": "2026-04-14T09:21:35.000+08:00"
    },
    {
      "conversationId": "2002713020947939331",
      "title": "新对话",
      "lastTime": "2026-04-14T09:25:12.000+08:00"
    }
  ]
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `conversationId` | `string` | 会话 ID |
| `title` | `string` | 会话标题 |
| `lastTime` | `date` | 最后活跃时间 |

---

### 2. 获取会话消息列表（对话记录）

#### 请求

```http
GET /conversations/{conversationId}/messages
```

示例：

```http
GET /conversations/2002713020947939330/messages
```

#### 用途

获取某个会话下的完整消息历史，用于聊天窗口回显历史记录。

#### 返回示例

```json
{
  "code": "0",
  "message": null,
  "data": [
    {
      "id": "2002713020947939401",
      "conversationId": "2002713020947939330",
      "role": "user",
      "content": "最近总是睡不好怎么办？",
      "vote": null,
      "createTime": "2026-04-14T09:20:01.000+08:00"
    },
    {
      "id": "2002713020947939402",
      "conversationId": "2002713020947939330",
      "role": "assistant",
      "content": "如果最近睡眠不佳，可以先从作息、情绪和饮食三个方面观察……",
      "vote": 1,
      "createTime": "2026-04-14T09:20:06.000+08:00"
    }
  ]
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 消息 ID |
| `conversationId` | `string` | 所属会话 ID |
| `role` | `string` | 角色，常见值：`user` / `assistant` |
| `content` | `string` | 消息内容 |
| `vote` | `number \| null` | 反馈值：`1`=点赞，`-1`=点踩，`null`=未反馈 |
| `createTime` | `date` | 消息创建时间 |

---

### 3. 重命名会话

#### 请求

```http
PUT /conversations/{conversationId}
Content-Type: application/json
```

请求体：

```json
{
  "title": "睡眠问题咨询"
}
```

#### 返回示例

```json
{
  "code": "0",
  "message": null,
  "data": null
}
```

---

### 4. 删除会话

#### 请求

```http
DELETE /conversations/{conversationId}
```

#### 返回示例

```json
{
  "code": "0",
  "message": null,
  "data": null
}
```

---

## 五、聊天主链路接口

### 1. 发起 / 继续流式对话

#### 请求

```http
GET /rag/v3/chat?question=你好&conversationId=2002713020947939330&deepThinking=false
Accept: text/event-stream
```

#### 请求参数

| 参数 | 是否必填 | 类型 | 说明 |
|------|----------|------|------|
| `question` | 是 | `string` | 用户输入的问题 |
| `conversationId` | 否 | `string` | 会话 ID；不传表示创建新会话 |
| `deepThinking` | 否 | `boolean` | 是否开启深度思考，默认 `false` |

#### 用途

这是聊天主接口，采用 SSE 流式返回。

- 不传 `conversationId`：创建新会话
- 传 `conversationId`：继续当前会话
- 对话完成后，消息会被写入会话历史

---

## 六、SSE 事件约定

前端接入 `GET /rag/v3/chat` 时，需要监听以下事件：

| 事件名 | 说明 | 数据结构 |
|--------|------|----------|
| `meta` | 返回会话与任务元信息 | `{ conversationId, taskId }` |
| `message` | 增量消息片段 | `{ type, delta }` |
| `finish` | 本轮回复完成 | `{ messageId, title }` |
| `done` | 整个流结束 | `"[DONE]"` |
| `cancel` | 任务被取消 | 取消相关数据 |
| `reject` | 请求被拒绝 | 拒绝相关数据 |

### 1. `meta`

示例：

```text
event: meta
data: {"conversationId":"2002713020947939330","taskId":"2002713020947939999"}
```

说明：

- `conversationId`：会话 ID
- `taskId`：本次流式任务 ID

前端注意：

- 如果是新建会话，前端必须从 `meta` 事件中拿到新的 `conversationId`
- 后续查询历史消息、继续对话、删除会话，都依赖这个 `conversationId`
- 停止生成时需要用到 `taskId`

### 2. `message`

示例：

```text
event: message
data: {"type":"response","delta":"如果最近睡眠不佳，可以先从"}
```

或：

```text
event: message
data: {"type":"think","delta":"我先分析一下用户的问题"}
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `string` | 片段类型，当前可能为 `response` 或 `think` |
| `delta` | `string` | 本次增量文本 |

前端一般只需要把 `type = response` 的文本拼接到回答区域。

### 3. `finish`

示例：

```text
event: finish
data: {"messageId":"2002713020947939402","title":"睡眠问题咨询"}
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `messageId` | `string \| null` | 本次 assistant 消息 ID |
| `title` | `string \| null` | 会话标题，可能为空 |

### 4. `done`

示例：

```text
event: done
data: [DONE]
```

表示流式输出已经结束。

---

## 七、停止生成接口

### 请求

```http
POST /rag/v3/stop?taskId=2002713020947939999
```

#### 参数说明

| 参数 | 是否必填 | 类型 | 说明 |
|------|----------|------|------|
| `taskId` | 是 | `string` | SSE `meta` 事件返回的任务 ID |

#### 返回示例

```json
{
  "code": "0",
  "message": null,
  "data": null
}
```

---

## 八、消息反馈接口

### 请求

```http
POST /conversations/messages/{messageId}/feedback
Content-Type: application/json
```

请求体：

```json
{
  "vote": 1,
  "reason": "回答有帮助",
  "comment": "讲得比较清楚"
}
```

#### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `vote` | `number` | 是 | `1`=点赞，`-1`=点踩 |
| `reason` | `string` | 否 | 反馈原因 |
| `comment` | `string` | 否 | 补充说明 |

#### 返回示例

```json
{
  "code": "0",
  "message": null,
  "data": null
}
```

---

## 九、推荐前端接入流程

### 场景一：新建对话

1. 用户输入问题
2. 前端调用：`GET /rag/v3/chat?question=xxx`
3. 监听 `meta`，拿到 `conversationId` 和 `taskId`
4. 监听 `message`，实时拼接回答内容
5. 收到 `finish` / `done` 后，将本地会话状态更新为完成
6. 使用 `conversationId` 调用 `GET /conversations/{conversationId}/messages` 刷新历史
7. 使用 `GET /conversations` 刷新左侧会话列表

### 场景二：继续已有对话

1. 页面初始化调用 `GET /conversations`
2. 用户点击某个会话
3. 调用 `GET /conversations/{conversationId}/messages` 加载历史记录
4. 用户继续提问时调用：
   `GET /rag/v3/chat?question=xxx&conversationId=当前会话ID`
5. 收到流式结果后刷新该会话的消息列表

### 场景三：停止回答

1. 前端从 `meta` 事件中缓存 `taskId`
2. 用户点击“停止生成”
3. 调用 `POST /rag/v3/stop?taskId=xxx`
4. 前端结束当前生成态，必要时重新查询消息列表

### 场景四：点赞 / 点踩

1. 前端从消息列表中拿到 assistant 消息的 `id`
2. 用户点击点赞 / 点踩
3. 调用 `POST /conversations/messages/{messageId}/feedback`
4. 提交成功后刷新当前会话消息，或直接本地更新 `vote`

---

## 十、前端注意事项

### 1. 新会话必须从 `meta` 中拿 `conversationId`

因为首次发起聊天时，前端可能还没有会话 ID。后端会在 SSE `meta` 事件中返回新建出来的 `conversationId`。

### 2. 不要把 SSE 聊天结果当成最终历史数据源

SSE 用于流式展示，真正的历史记录建议以后端落库结果为准，即：

- 流式过程中：前端本地拼接展示
- 流式结束后：重新请求 `/conversations/{conversationId}/messages`

### 3. `message.type` 可能有两类

当前已知值：

- `response`：正式回答内容
- `think`：思考过程内容

前端可以根据产品需求决定是否展示 `think`。

### 4. 普通接口成功判断

建议统一按以下规则判断：

- `code === "0"` 视为成功

### 5. 点赞 / 点踩只对消息生效

反馈接口作用对象是单条消息，因此必须先拿到 `messageId`。

---

## 十一、最小接入清单

如果只是先把聊天页跑通，建议按以下顺序对接：

### 必接

1. `GET /conversations`
2. `GET /conversations/{conversationId}/messages`
3. `GET /rag/v3/chat`

### 次要增强

4. `POST /rag/v3/stop`
5. `PUT /conversations/{conversationId}`
6. `DELETE /conversations/{conversationId}`
7. `POST /conversations/messages/{messageId}/feedback`

---

## 十二、补充说明

### 1. `/athena/rag/ask` 不是聊天记录接口

该接口是 Athena 侧的一次性问答接口，返回的是同步问答结果，不是当前 RAG 聊天页的会话记录接口。

如果要做“可追溯历史消息”的聊天页，请以前述会话接口与 SSE 接口为准。

### 2. 登录态

会话相关接口依赖当前登录用户上下文，前端调用时应确保带上正常登录态信息。

---

## 十三、建议联调顺序

建议前后端联调时按下面顺序验证：

1. `GET /conversations` 是否能拿到当前用户会话
2. `GET /conversations/{conversationId}/messages` 是否能正确回显历史
3. `GET /rag/v3/chat` 是否能收到 `meta -> message -> finish -> done`
4. 新会话场景下是否能正确获取新的 `conversationId`
5. `POST /rag/v3/stop` 是否可以中断生成
6. 点赞 / 点踩是否能成功持久化
7. 重命名 / 删除会话是否同步生效
