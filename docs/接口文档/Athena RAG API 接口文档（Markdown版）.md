# Athena RAG API 接口文档（Markdown版）

# 基础信息

- **OpenAPI版本**：3\.1\.0

- **接口标题**：Athena RAG API

- **接口描述**：Athena RAG 模块接口文档

- **联系信息**：Athena Team

- **许可证**：Apache 2\.0

- **接口版本**：v1\.0\.0

- **服务地址**：http://localhost:9090/api/ragent（Generated server url）

# 接口分类及详情

## 一、用户接口（tags: 用户接口）

### 1\. 更新用户

- **请求方式**：PUT

- **接口路径**：/users/\{id\}

- **摘要**：更新用户

- **operationId**：update

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/UserUpdateRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 2\. 删除用户

- **请求方式**：DELETE

- **接口路径**：/users/\{id\}

- **摘要**：删除用户

- **operationId**：delete

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 3\. 修改当前用户密码

- **请求方式**：PUT

- **接口路径**：/user/password

- **摘要**：修改当前用户密码

- **operationId**：changePassword

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/ChangePasswordRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 4\. 分页查询用户列表

- **请求方式**：GET

- **接口路径**：/users

- **摘要**：分页查询用户列表

- **operationId**：pageQuery

- **查询参数**：
        

    - name: requestParam，in: query，required: true，schema: $ref: \#/components/schemas/UserPageRequest

- **响应（200 OK）**：
       

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultIPageUserVO

### 5\. 创建用户

- **请求方式**：POST

- **接口路径**：/users

- **摘要**：创建用户

- **operationId**：create

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/UserCreateRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultString

### 6\. 用户登出

- **请求方式**：POST

- **接口路径**：/auth/logout

- **摘要**：用户登出接口，清除用户的认证信息和会话

- **operationId**：logout

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 7\. 用户登录

- **请求方式**：POST

- **接口路径**：/auth/login

- **摘要**：用户登录接口

- **operationId**：login

- **请求体**：

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/LoginRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultLoginVO

### 8\. 获取当前登录用户信息

- **请求方式**：GET

- **接口路径**：/user/me

- **摘要**：获取当前登录用户信息

- **operationId**：currentUser

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultCurrentUserVO

## 二、RAG接口（tags: RAG接口）

### 1\. 查询示例问题详情

- **请求方式**：GET

- **接口路径**：/sample\-questions/\{id\}

- **摘要**：查询示例问题详情

- **operationId**：queryById

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultSampleQuestionVO

### 2\. 更新示例问题

- **请求方式**：PUT

- **接口路径**：/sample\-questions/\{id\}

- **摘要**：更新示例问题

- **operationId**：update\_1

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/SampleQuestionUpdateRequest

    - required: true

- **响应（200 OK）**：


    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 3\. 删除示例问题

- **请求方式**：DELETE

- **接口路径**：/sample\-questions/\{id\}

- **摘要**：删除示例问题

- **operationId**：delete\_1

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 4\. 查询映射规则详情

- **请求方式**：GET

- **接口路径**：/mappings/\{id\}

- **摘要**：查询映射规则详情

- **operationId**：queryById\_1

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultQueryTermMappingVO

### 5\. 更新映射规则

- **请求方式**：PUT

- **接口路径**：/mappings/\{id\}

- **摘要**：更新映射规则

- **operationId**：update\_2

- **路径参数**：
       

    - name: id，in: path，required: true，schema: string

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/QueryTermMappingUpdateRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 6\. 删除映射规则

- **请求方式**：DELETE

- **接口路径**：/mappings/\{id\}

- **摘要**：删除映射规则

- **operationId**：delete\_2

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 7\. 更新意图节点

- **请求方式**：PUT

- **接口路径**：/intent\-tree/\{id\}

- **摘要**：更新意图节点

- **operationId**：updateNode

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/IntentNodeUpdateRequest

    - required: true

- **响应（200 OK）**：description: OK

### 8\. 删除意图节点

- **请求方式**：DELETE

- **接口路径**：/intent\-tree/\{id\}

- **摘要**：删除意图节点

- **operationId**：deleteNode

- **路径参数**：
        

    - name: id，in: path，required: true，schema: string

- **响应（200 OK）**：description: OK

### 9\. 重命名会话

- **请求方式**：PUT

- **接口路径**：/conversations/\{conversationId\}

- **摘要**：重命名会话

- **operationId**：rename

- **路径参数**：
        

    - name: conversationId，in: path，required: true，schema: string

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/ConversationUpdateRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 10\. 删除会话

- **请求方式**：DELETE

- **接口路径**：/conversations/\{conversationId\}

- **摘要**：删除会话

- **operationId**：delete\_5

- **路径参数**：
        

    - name: conversationId，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 11\. 分页查询示例问题列表

- **请求方式**：GET

- **接口路径**：/sample\-questions

- **摘要**：分页查询示例问题列表

- **operationId**：pageQuery\_1

- **查询参数**：
        

    - name: requestParam，in: query，required: true，schema: $ref: \#/components/schemas/SampleQuestionPageRequest

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultIPageSampleQuestionVO

### 12\. 创建示例问题

- **请求方式**：POST

- **接口路径**：/sample\-questions

- **摘要**：创建示例问题

- **operationId**：create\_1

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/SampleQuestionCreateRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultString

### 13\. 发起 SSE 流式对话

- **请求方式**：POST

- **接口路径**：/rag/v3/stop

- **摘要**：发起 SSE 流式对话

- **operationId**：stop

- **查询参数**：
        

    - name: taskId，in: query，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 14\. 分页查询映射规则

- **请求方式**：GET

- **接口路径**：/mappings

- **摘要**：分页查询映射规则

- **operationId**：pageQuery\_2

- **查询参数**：
        

    - name: requestParam，in: query，required: true，schema: $ref: \#/components/schemas/QueryTermMappingPageRequest

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultIPageQueryTermMappingVO

### 15\. 创建映射规则

- **请求方式**：POST

- **接口路径**：/mappings

- **摘要**：创建映射规则

- **operationId**：create\_2

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/QueryTermMappingCreateRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultString

### 16\. 创建意图节点

- **请求方式**：POST

- **接口路径**：/intent\-tree

- **摘要**：创建意图节点

- **operationId**：createNode

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/IntentNodeCreateRequest

    - required: true

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultString

### 17\. 批量启用节点

- **请求方式**：POST

- **接口路径**：/intent\-tree/batch/enable

- **摘要**：批量启用节点

- **operationId**：batchEnable\_1

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/IntentNodeBatchRequest

    - required: true

- **响应（200 OK）**：description: OK

### 18\. 批量停用节点

- **请求方式**：POST

- **接口路径**：/intent\-tree/batch/disable

- **摘要**：批量停用节点

- **operationId**：batchDisable\_1

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/IntentNodeBatchRequest

    - required: true

- **响应（200 OK）**：description: OK

### 19\. 批量删除节点

- **请求方式**：POST

- **接口路径**：/intent\-tree/batch/delete

- **摘要**：批量删除节点

- **operationId**：batchDelete

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/IntentNodeBatchRequest

    - required: true

- **响应（200 OK）**：description: OK

### 20\. 提交点赞/踩反馈

- **请求方式**：POST

- **接口路径**：/conversations/messages/\{messageId\}/feedback

- **摘要**：提交点赞/踩反馈（异步，通过 MQ 持久化）

- **operationId**：submitFeedback

- **路径参数**：
        

    - name: messageId，in: path，required: true，schema: string

- **请求体**：
        

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/MessageFeedbackRequest

    - required: true

- **响应（200 OK）**：


    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultVoid

### 21\. 接口操作（已废弃）

- **请求方式**：POST

- **接口路径**：/athena/rag/ask

- **摘要**：接口操作

- **operationId**：ask

- **请求体**：

    - content\-type: application/json

    - schema: $ref: \#/components/schemas/AthenaAskRequest

    - required: true

- **响应（200 OK）**：


    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultAthenaAskVO

- **备注**：deprecated: true

### 22\. SSE 流式对话

- **请求方式**：GET

- **接口路径**：/rag/v3/chat

- **operationId**：chat

- **查询参数**：
        

    - name: question，in: query，required: true，schema: string

    - name: conversationId，in: query，required: false，schema: string

    - name: deepThinking，in: query，required: false，schema: boolean，default: false

- **响应（200 OK）**：
        

    - content\-type: text/event\-stream;charset=UTF\-8

    - schema: $ref: \#/components/schemas/SseEmitter

### 23\. 分页查询链路运行记录

- **请求方式**：GET

- **接口路径**：/rag/traces/runs

- **摘要**：分页查询链路运行记录

- **operationId**：pageRuns

- **查询参数**：
        

    - name: request，in: query，required: true，schema: $ref: \#/components/schemas/RagTraceRunPageRequest

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultIPageRagTraceRunVO

### 24\. 查询链路详情（包含节点）

- **请求方式**：GET

- **接口路径**：/rag/traces/runs/\{traceId\}

- **摘要**：查询链路详情（包含节点）

- **operationId**：detail

- **路径参数**：
       

    - name: traceId，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultRagTraceDetailVO

### 25\. 仅查询链路节点

- **请求方式**：GET

- **接口路径**：/rag/traces/runs/\{traceId\}/nodes

- **摘要**：仅查询链路节点

- **operationId**：nodes

- **路径参数**：
        

    - name: traceId，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultListRagTraceNodeVO

### 26\. 获取系统 RAG、AI 模型等配置信息

- **请求方式**：GET

- **接口路径**：/rag/settings

- **摘要**：获取系统 RAG、AI 模型等配置信息

- **operationId**：settings

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultSystemSettingsVO

### 27\. 随机获取示例问题列表

- **请求方式**：GET

- **接口路径**：/rag/sample\-questions

- **摘要**：随机获取示例问题列表

- **operationId**：listSampleQuestions

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultListSampleQuestionVO

### 28\. 获取会话列表

- **请求方式**：GET

- **接口路径**：/conversations

- **摘要**：获取会话列表

- **operationId**：listConversations

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultListConversationVO

### 29\. 获取会话消息列表

- **请求方式**：GET

- **接口路径**：/conversations/\{conversationId\}/messages

- **摘要**：获取会话消息列表

- **operationId**：listMessages

- **路径参数**：
       

    - name: conversationId，in: path，required: true，schema: string

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultListConversationMessageVO

### 30\. 获取完整的意图节点树

- **请求方式**：GET

- **接口路径**：/intent\-tree/trees

- **摘要**：获取完整的意图节点树

- **operationId**：tree

- **响应（200 OK）**：
        

    - content\-type: \*/\*

    - schema: $ref: \#/components/schemas/ResultListIntentNodeTreeVO

# Athena RAG API \- 知识库接口

# 三、知识库接口（tags: 知识库接口）

## 3\.1 查询知识库详情

### 接口信息

- 请求路径：`/knowledge\-base/\{kb\-id\}`

- 请求方法：GET

- 标签：知识库接口

- 摘要：查询知识库详情

- 操作ID：queryKnowledgeBase

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|kb\-id|path|是|string|知识库ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultKnowledgeBaseVO`

## 3\.2 重命名知识库

### 接口信息

- 请求路径：`/knowledge\-base/\{kb\-id\}`

- 请求方法：PUT

- 标签：知识库接口

- 摘要：重命名知识库

- 操作ID：renameKnowledgeBase

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|kb\-id|path|是|string|知识库ID|

### 请求体

内容类型：application/json

schema：`$ref: \#/components/schemas/KnowledgeBaseUpdateRequest`

是否必填：是

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.3 删除知识库

### 接口信息

- 请求路径：`/knowledge\-base/\{kb\-id\}`

- 请求方法：DELETE

- 标签：知识库接口

- 摘要：删除知识库

- 操作ID：deleteKnowledgeBase

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|kb\-id|path|是|string|知识库ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.4 查询文档详情

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{docId\}`

- 请求方法：GET

- 标签：知识库接口

- 摘要：查询文档详情

- 操作ID：get

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|docId|path|是|string|文档ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultKnowledgeDocumentVO`

## 3\.5 更新文档信息

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{docId\}`

- 请求方法：PUT

- 标签：知识库接口

- 摘要：更新文档信息

- 操作ID：update\_3

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|docId|path|是|string|文档ID|

### 请求体

内容类型：application/json

schema：`$ref: \#/components/schemas/KnowledgeDocumentUpdateRequest`

是否必填：是

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.6 删除文档

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}`

- 请求方法：DELETE

- 标签：知识库接口

- 摘要：删除文档：逻辑删除。可选同时删除向量库中该文档的所有 chunk

- 操作ID：delete\_6

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.7 更新 Chunk 内容

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks/\{chunk\-id\}`

- 请求方法：PUT

- 标签：知识库接口

- 摘要：更新 Chunk 内容

- 操作ID：update\_4

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|
|chunk\-id|path|是|string|Chunk ID|

### 请求体

内容类型：application/json

schema：`$ref: \#/components/schemas/KnowledgeChunkUpdateRequest`

是否必填：是

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.8 删除 Chunk

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks/\{chunk\-id\}`

- 请求方法：DELETE

- 标签：知识库接口

- 摘要：删除 Chunk

- 操作ID：delete\_3

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|
|chunk\-id|path|是|string|Chunk ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.9 分页查询知识库列表

### 接口信息

- 请求路径：`/knowledge\-base`

- 请求方法：GET

- 标签：知识库接口

- 摘要：分页查询知识库列表

- 操作ID：pageQuery\_3

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|requestParam|query|是|object|分页查询参数，schema：`$ref: \#/components/schemas/KnowledgeBasePageRequest`|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultIPageKnowledgeBaseVO`

## 3\.10 创建知识库

### 接口信息

- 请求路径：`/knowledge\-base`

- 请求方法：POST

- 标签：知识库接口

- 摘要：创建知识库

- 操作ID：createKnowledgeBase

### 请求体

内容类型：application/json

schema：`$ref: \#/components/schemas/KnowledgeBaseCreateRequest`

是否必填：是

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultString`

## 3\.11 上传文档

### 接口信息

- 请求路径：`/knowledge\-base/\{kb\-id\}/docs/upload`

- 请求方法：POST

- 标签：知识库接口

- 摘要：上传文档：入库记录 \+ 文件落盘，返回文档ID

- 操作ID：upload

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|kb\-id|path|是|string|知识库ID|

### 请求体

内容类型：multipart/form\-data

schema：

- file：string（binary），文件

- requestParam：`$ref: \#/components/schemas/KnowledgeDocumentUploadRequest`

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultKnowledgeDocumentVO`

## 3\.12 分页查询 Chunk 列表

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks`

- 请求方法：GET

- 标签：知识库接口

- 摘要：分页查询 Chunk 列表

- 操作ID：pageQuery\_4

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|
|requestParam|query|是|object|分页查询参数，schema：`$ref: \#/components/schemas/KnowledgeChunkPageRequest`|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultIPageKnowledgeChunkVO`

## 3\.13 新增 Chunk

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks`

- 请求方法：POST

- 标签：知识库接口

- 摘要：新增 Chunk

- 操作ID：create\_3

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|

### 请求体

内容类型：application/json

schema：`$ref: \#/components/schemas/KnowledgeChunkCreateRequest`

是否必填：是

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultKnowledgeChunkVO`

## 3\.14 启用单条 Chunk

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks/\{chunk\-id\}/enable`

- 请求方法：POST

- 标签：知识库接口

- 摘要：启用单条 Chunk

- 操作ID：enable

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|
|chunk\-id|path|是|string|Chunk ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.15 禁用单条 Chunk

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks/\{chunk\-id\}/disable`

- 请求方法：POST

- 标签：知识库接口

- 摘要：禁用单条 Chunk

- 操作ID：disable

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|
|chunk\-id|path|是|string|Chunk ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.16 重建文档向量

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks/rebuild`

- 请求方法：POST

- 标签：知识库接口

- 摘要：重建文档向量（以数据库 enabled=1 的 chunk 为准）

- 操作ID：rebuild

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.17 批量启用 Chunk

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks/batch\-enable`

- 请求方法：POST

- 标签：知识库接口

- 摘要：批量启用 Chunk

- 操作ID：batchEnable

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|

### 请求体

内容类型：application/json

schema：`$ref: \#/components/schemas/KnowledgeChunkBatchRequest`

是否必填：是

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.18 批量禁用 Chunk

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunks/batch\-disable`

- 请求方法：POST

- 标签：知识库接口

- 摘要：批量禁用 Chunk

- 操作ID：batchDisable

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|

### 请求体

内容类型：application/json

schema：`$ref: \#/components/schemas/KnowledgeChunkBatchRequest`

是否必填：是

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.19 开始分块

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{doc\-id\}/chunk`

- 请求方法：POST

- 标签：知识库接口

- 摘要：开始分块：抽取文本 \-\&gt; 分块 \-\&gt; 嵌入并写入向量库

- 操作ID：startChunk

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.20 启用/禁用文档

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{docId\}/enable`

- 请求方法：PATCH

- 标签：知识库接口

- 摘要：启用/禁用文档

- 操作ID：enable\_1

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|docId|path|是|string|文档ID|
|value|query|是|boolean|启用为true，禁用为false|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultVoid`

## 3\.21 分页查询文档列表（支持状态/关键字过滤）

### 接口信息

- 请求路径：`/knowledge\-base/\{kb\-id\}/docs`

- 请求方法：GET

- 标签：知识库接口

- 摘要：分页查询文档列表（支持状态/关键字过滤）

- 操作ID：page\_2

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|kb\-id|path|是|string|知识库ID|
|requestParam|query|是|object|分页查询参数，schema：`$ref: \#/components/schemas/KnowledgeDocumentPageRequest`|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultIPageKnowledgeDocumentVO`

## 3\.22 查询文档分块日志列表

### 接口信息

- 请求路径：`/knowledge\-base/docs/\{docId\}/chunk\-logs`

- 请求方法：GET

- 标签：知识库接口

- 摘要：查询文档分块日志列表

- 操作ID：getChunkLogs

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|docId|path|是|string|文档ID|
|page|query|是|object|分页参数，schema：`$ref: \#/components/schemas/PageKnowledgeDocumentChunkLogVO`|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：`$ref: \#/components/schemas/ResultIPageKnowledgeDocumentChunkLogVO`

# Athena RAG API 接口文档（3\.23及以后）

## 3\.23 搜索文档（全局检索建议）

### 接口信息

- 请求路径：\`/knowledge\-base/docs/search\`

- 请求方法：GET

- 标签：知识库接口

- 摘要：搜索文档（全局检索建议）

- 操作ID：search

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|keyword|query|否|string|检索关键词|
|limit|query|否|integer\(int32\)|检索结果数量限制，默认值为8|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultListKnowledgeDocumentSearchVO\`

## 3\.24 查询支持的分块策略列表

### 接口信息

- 请求路径：\`/knowledge\-base/chunk\-strategies\`

- 请求方法：GET

- 标签：知识库接口

- 摘要：查询支持的分块策略列表

- 操作ID：listChunkStrategies

### 请求参数

无请求参数

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultListChunkStrategyVO\`

## 3\.25 获取完整的意图节点树

### 接口信息

- 请求路径：\`/intent\-tree/trees\`

- 请求方法：GET

- 标签：RAG接口

- 摘要：获取完整的意图节点树

- 操作ID：tree

### 请求参数

无请求参数

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultListIntentNodeTreeVO\`

## 3\.26 根据任务ID获取任务详情

### 接口信息

- 请求路径：\`/ingestion/tasks/\{id\}\`

- 请求方法：GET

- 标签：数据摄取接口

- 摘要：根据任务 ID 获取任务详情

- 操作ID：get\_2

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|id|path|是|string|任务ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultIngestionTaskVO\`

## 3\.27 根据任务ID获取任务节点运行记录

### 接口信息

- 请求路径：\`/ingestion/tasks/\{id\}/nodes\`

- 请求方法：GET

- 标签：数据摄取接口

- 摘要：根据任务 ID 获取任务节点运行记录

- 操作ID：nodes\_1

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|id|path|是|string|任务ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultListIngestionTaskNodeVO\`

## 3\.28 获取会话列表

### 接口信息

- 请求路径：\`/conversations\`

- 请求方法：GET

- 标签：RAG接口

- 摘要：获取会话列表

- 操作ID：listConversations

### 请求参数

无请求参数

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultListConversationVO\`

## 3\.29 获取会话消息列表

### 接口信息

- 请求路径：\`/conversations/\{conversationId\}/messages\`

- 请求方法：GET

- 标签：RAG接口

- 摘要：获取会话消息列表

- 操作ID：listMessages

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|conversationId|path|是|string|会话ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultListConversationMessageVO\`

## 3\.30 管理面板趋势查询

### 接口信息

- 请求路径：\`/admin/dashboard/trends\`

- 请求方法：GET

- 标签：管理面板接口

- 摘要：接口操作

- 操作ID：trends

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|metric|query|是|string|指标类型|
|window|query|否|string|时间窗口|
|granularity|query|否|string|时间粒度|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultDashboardTrendsVO\`

## 3\.31 管理面板性能查询

### 接口信息

- 请求路径：\`/admin/dashboard/performance\`

- 请求方法：GET

- 标签：管理面板接口

- 摘要：接口操作

- 操作ID：performance

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|window|query|否|string|时间窗口|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultDashboardPerformanceVO\`

## 3\.32 管理面板概览查询

### 接口信息

- 请求路径：\`/admin/dashboard/overview\`

- 请求方法：GET

- 标签：管理面板接口

- 摘要：接口操作

- 操作ID：overview

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|window|query|否|string|时间窗口|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultDashboardOverviewVO\`

## 3\.33 删除文档

### 接口信息

- 请求路径：\`/knowledge\-base/docs/\{doc\-id\}\`

- 请求方法：DELETE

- 标签：知识库接口

- 摘要：删除文档：逻辑删除。可选同时删除向量库中该文档的所有 chunk

- 操作ID：delete\_6

### 请求参数

|参数名|位置|是否必填|数据类型|描述|
|---|---|---|---|---|
|doc\-id|path|是|string|文档ID|

### 响应信息

#### 响应码：200 OK

响应内容类型：\*/\*

响应 schema：\`$ref: \#/components/schemas/ResultVoid\`










