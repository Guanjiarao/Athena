# 上传 HTML 文件最终开发文档

> 目标：将 `Athena note` 中进入知识库范围的 HTML 笔记，改为走 `ragent` 标准 `document upload` 链路，并优先使用 Athena 专用 `pipelineId`。

## 一、最终结论

本次改造的最终方案已经明确：

- `type == 0 / 1 / 2`：**完全不走这里**
- 这些类型属于其他业务（当前可视为视频类等非知识笔记场景）
- 不进入本次知识库上传链路
- 不需要继续旧 `dispatch`
- 本次开发只处理 **知识型 note** 的上传与入库

也就是说，这次的正确分流规则不是：

- `0/1/2` 继续旧 dispatch

而是：

- `0/1/2` 直接跳过本链路
- `非 0/1/2` 才进入本次 `ragent document upload` 链路

本次最终目标是：

- 把知识型 Athena note 作为标准 HTML document 上传到 `ragent`
- 进入 `ragent` 文档体系
- 享受文档列表、文档详情、分块日志、chunk 调试、重建向量等全链路能力

---

## 二、本次只处理的范围

### 1. 纳入本次改造的 note

仅处理：

- `type != 0 && type != 1 && type != 2`

并且已确认：

- 这些 note 的 `noteContent` 一定是 HTML

因此本次方案确定为：

- 将 `noteContent` 包装为 `.html` 文件
- 通过 multipart 上传到 `ragent`
- 使用 Athena 专用 `pipelineId`

### 2. 不纳入本次改造的 note

不处理：

- `type == 0 / 1 / 2`

这些类型：

- 不属于当前知识库接入范围
- 不进入本次 HTML 文档上传链路
- 不需要继续旧知识同步 dispatch
- 也不需要为了本次功能专门兼容

---

## 三、已确认可复用的旧资产

顺着昨天跑通过的旧同步链路，已经确认以下内容可以复用。

### 1. 可复用：type → kbCode 路由规则

旧链路中，`ragent` 已有按 `type` 路由知识库编码的实现。

当前业务规则：

- `10-29` → `kbchild`
- `30-49` → `kbteen`
- `50-69` → `kbadult`
- `70-89` → `kbsenior`
- `127` → `kbcommon`

这套规则已经存在于：

- 文档 `rag-integration-plan.md`
- `ragent` 的 `AthenaKnowledgeSyncProperties`
- `KnowledgeBaseRoutingServiceImpl`

结论：

- 本次不需要重新设计 type 路由规则
- 只需要把这套规则在 Athena 侧也收口成稳定组件

### 2. 可复用：HTML parser

昨天已确认 `ragent` 中已经有专用的 HTML parser：

- `HtmlDocumentParser`

同时 `ParserNode` 已支持：

- 根据 `.html` / `text/html` 命中 `ParserType.HTML`

结论：

- 本次不需要重新开发 HTML 解析器
- 只要上传的文件是 `.html`，并走正确 pipeline，就能复用这套解析能力

### 3. 可复用：昨天已验证的 Athena note 专用 pipeline

昨天的旧同步链路里，`AthenaNoteIngestionServiceImpl` 已经构造并跑通过一条 Athena note 专用 pipeline，固定标识为：

- `athena-note-ingestion`

它的核心节点已经明确为：

- `fetcher`
- `parser`
- `chunker`
- `indexer`

并且：

- parser 强制使用 HTML parser
- chunk 策略使用 `structure_aware`
- metadata 字段已经约定过

本次最终结论不是“重新设计一条新 pipeline”，而是：

- **优先复用昨天已经验证过的 `athena-note-ingestion` pipeline 配置与处理逻辑**
- **本次主要替换的是执行入口：从“MQ 直进 ingestion engine”切到“标准 document upload + pipelineId”**

结论：

- 本次优先复用昨天已经验证过的 Athena note pipeline
- 新方案中的 Athena 专用 `pipelineId` 默认就应对齐 `athena-note-ingestion`
- 若 `ragent` 后台已存在这条持久化 pipeline，则直接使用它
- 若目前仍是代码内动态构造版本，则需要将其等价迁移为可供 document upload 使用的标准 pipeline 配置

### 4. 可复用：metadata 字段设计

旧链路里已经使用过的 metadata 字段：

- `noteId`
- `title`
- `type`
- `authorId`
- `source`

结论：

- 本次虽然不扩展 `ragent` document 表结构
- 但 Athena 专用 pipeline 应尽量继续保住这些 metadata
- 后续引用补查、重建、删除清理都会更顺

### 5. 可复用：文件命名思路

旧链路已有：

- `athena-note-<noteId>.html`

结论：

- 本次标准 document upload 也建议文件名中带 `noteId`
- 因为当前不改 `document` 表结构，也不能直接指定 `documentId = noteId`
- 文件名是当前最便宜、最稳的 note 身份保留方式之一

---

## 四、本次不继续复用的旧方式

虽然昨天的旧链路能跑通，但本次不继续采用以下方式：

### 1. 不继续走旧 MQ 主链路作为最终方案

旧链路是：

- Athena 发布 note
- 发 `note-knowledge-sync`
- `ragent` 消费后直接构造 `IngestionContext`
- 直接跑 ingestion engine

这条路的问题是：

- 不会进入标准 document 体系
- 没有标准 document 记录
- 不能自然享受文档列表 / 分块调试 / 文档视图能力

所以本次结论是：

- 旧链路里的规则、parser、pipeline 内容可以复用
- 但执行方式不能再是“直接 ingestion，不落标准 document”

### 2. 不在本轮扩展 ragent 表结构

本轮明确不做：

- 不给 `KnowledgeDocumentDO` 增字段
- 不给 upload request 加 `bizType / bizId`
- 不强行把 `documentId = noteId`
- 不改 document 表结构

本轮只使用现有标准 document upload 能力。

---

## 五、最终技术方案

## 步骤 1：在 Athena 提交笔记后做分流

修改 `GroundServiceImpl.submitNote()` 主流程。

正确分流逻辑：

- `type == 0 / 1 / 2`：直接跳过本链路
- `type != 0 / 1 / 2`：进入新的 `AthenaNoteDocumentUploadService`

这里要特别注意：

- **不要再写成 `0/1/2` 继续旧 dispatch`**
- 这是错误结论，已经排除

本次建议新增判断方法：

- `shouldUploadAsRagentDocument(Byte type)`

判断规则：

- `type != null && type != 0 && type != 1 && type != 2`

需要做的事：

1. 在 `publish(noteSubmitDTO)` 成功后拿到 `noteId`
2. 判断当前 `type` 是否属于知识型 note
3. 若是，则调用新的 document upload 服务
4. 若不是，则直接结束本次知识库上传流程

---

## 步骤 2：新增 Athena 专用的 ragent document upload 客户端服务

建议新增：

- `AthenaNoteDocumentUploadService`
- `AthenaNoteDocumentUploadServiceImpl`

职责只做以下几件事：

1. 接收 `noteId`、`title`、`contentHtml`、`type`、`authorId`
2. 根据 `type` 找到目标知识库
3. 根据目标知识库找到对应 `kbId`
4. 根据目标知识库找到对应 `pipelineId`
5. 将 HTML 内容包装为 `.html` 文件
6. 调用 `ragent` 标准 document upload 接口
7. 视情况决定是否立即触发 `startChunk`

注意：

- 不要把 document upload 再塞回旧 `AthenaNoteSyncDispatchService`
- 新链路应该是一个独立服务

---

## 步骤 3：收口 type → kbCode → kbId / pipelineId

虽然 type → kbCode 的规则已经存在，但本次开发需要 Athena 侧能独立完成以下三段解析：

1. `type -> kbCode`
2. `kbCode -> kbId`
3. `kbCode -> pipelineId`

建议新增 Athena 侧路由组件：

- `AthenaKnowledgeRouteService`

建议统一集中维护以下配置：

### 1. type → kbCode

- `10-29` → `kbchild`
- `30-49` → `kbteen`
- `50-69` → `kbadult`
- `70-89` → `kbsenior`
- `127` → `kbcommon`

### 2. kbCode → kbId

需要维护：

- `kbchild -> <真实 kbId>`
- `kbteen -> <真实 kbId>`
- `kbadult -> <真实 kbId>`
- `kbsenior -> <真实 kbId>`
- `kbcommon -> <真实 kbId>`

### 3. kbCode → pipelineId

需要维护：

- `kbchild -> <Athena child pipelineId>`
- `kbteen -> <Athena teen pipelineId>`
- `kbadult -> <Athena adult pipelineId>`
- `kbsenior -> <Athena senior pipelineId>`
- `kbcommon -> <Athena common pipelineId>`

说明：

- `ragent` 的 document upload 需要的是 `kbId`
- 不是 `kbCode`
- `pipelineId` 也要一起可配置化，不要散落写死

---

## 步骤 4：默认优先复用昨天那条 Athena note pipeline

本次 document upload 的默认目标不是重新拍脑袋创建一条新 pipeline，而是：

- **优先复用昨天已经验证通过的 `athena-note-ingestion`**

因此默认执行策略为：

1. Athena 将 HTML note 上传为标准 document
2. 上传参数中指定：
   - `sourceType=file`
   - `processMode=pipeline`
   - `pipelineId=athena-note-ingestion`（或它在后台对应的正式持久化 ID）
3. 后续由 `ragent` 标准 document 流程继续跑 parser / chunk / index

如果当前后台还没有持久化的这条 pipeline，则需要做的不是重新设计，而是：

- 将昨天 `AthenaNoteIngestionServiceImpl` 中那套 pipeline 节点配置，等价迁移成 `ragent` 标准 document upload 可用的 pipeline 配置

也就是说：

- 复用 pipeline 内容
- 替换执行入口

---

## 步骤 5：将 note HTML 包装为 multipart 文件

在 `AthenaNoteDocumentUploadServiceImpl` 中，将 `contentHtml` 包装成一个内存 `.html` 文件。

建议规则：

1. 文件扩展名固定为 `.html`
2. 文件内容直接使用 `contentHtml`
3. 文件名中必须带 `noteId`
4. 文件名可带标题，但需要清洗非法字符

推荐文件名：

- `athena-note-<noteId>.html`
- 或 `athena-note-<noteId>-<sanitized-title>.html`

建议保留 `noteId` 的原因：

- 当前不改 `ragent` 表结构
- 当前不能直接指定 `documentId = noteId`
- 文件名是当前最简单可用的 note 身份保留方式

---

## 步骤 6：调用 ragent 标准 document upload 接口

Athena 侧调用：

- `POST /api/ragent/knowledge-base/{kbId}/docs/upload`

请求方式：

- `multipart/form-data`

请求参数建议固定为：

- `file=<html 文件>`
- `sourceType=file`
- `processMode=pipeline`
- `pipelineId=<Athena 专用 pipelineId>`

本轮不建议传：

- `chunkStrategy`
- `chunkConfig`

因为本次明确走 pipeline 模式。

上传成功后，至少记录：

- `docId`
- `docName`
- `status`

同时补关键日志：

- `noteId`
- `type`
- `kbCode`
- `kbId`
- `pipelineId`
- `docId`

---

## 步骤 7：优先尝试 upload 成功后立即触发 chunk

当前 `ragent upload()` 只负责：

- 建立 document 记录
- 存文件

不会自动开始分块。

因此 Athena 侧 upload 成功后，建议继续调用：

- `POST /api/ragent/knowledge-base/docs/{docId}/chunk`

第一版建议策略：

1. upload 成功
2. 拿到 `docId`
3. 立刻调用 `startChunk`

原因：

- `ragent startChunk` 自身已经是异步化入口
- Athena 第一版没必要再额外加一层 MQ
- 这样最利于快速验证 document 主链路是否可用

本阶段需要重点验证：

- Athena 专用 `pipelineId` 是否能稳定工作
- 文档状态是否能从 `pending -> running -> success`
- chunk 日志是否正常生成
- chunk 调试页是否能看到结果

---

## 步骤 8：若立即 startChunk 不稳定，再退到 MQ 异步

如果后续验证发现：

- upload 后立刻触发 chunk 存在时序问题
- pipeline 执行不稳定，需要重试
- Athena 接口响应时间不希望再受第二次调用影响

则再补 Athena 侧异步派发。

备选方案：

1. Athena 先只 upload
2. upload 成功后发一条“document chunk start”事件
3. 异步消费者再调用 `ragent startChunk`

但这是备选方案，不是第一版默认方案。

当前默认优先顺序：

1. 先做 upload + startChunk 直连
2. 若验证不稳定，再补 MQ

---

## 六、Athena 侧最终开发拆分

### 1. 修改现有提交流程

修改：

- `GroundServiceImpl.submitNote()`

改造内容：

- 增加知识型 note 判断
- 只有 `type != 0/1/2` 才进入 upload 流程
- `0/1/2` 直接跳过本链路

### 2. 新增 document 上传服务

新增：

- `AthenaNoteDocumentUploadService`
- `AthenaNoteDocumentUploadServiceImpl`

### 3. 新增知识路由服务

新增：

- `AthenaKnowledgeRouteService`

负责：

- `type -> kbCode`
- `kbCode -> kbId`
- `kbCode -> pipelineId`

### 4. 新增/整理配置

建议统一配置：

- `ragent.base-url`
- 各知识库对应 `kbId`
- 各知识库对应 `pipelineId`
- 必要时再补 Athena note 文件名策略

避免这些值硬编码在 service 中。

---

## 七、ragent 侧本轮怎么用旧资产

本轮 ragent 侧原则：

- 不改表结构
- 不改标准 document 上传协议
- 尽量复用昨天已经验证过的资产

具体复用项：

1. `HtmlDocumentParser`
2. `ParserNode` 对 HTML 的识别逻辑
3. `athena-note-ingestion` 这条 Athena note 专用 pipeline
4. 旧 pipeline 中 parser/chunker/indexer 的节点配置
5. 旧 metadata 字段设计
6. 旧 type → kbCode 规则

不继续复用的部分：

- 旧 MQ 消费后直接 `ingestionEngine.execute(...)` 的执行方式

因为这会绕过标准 document 体系。

---

## 八、第一版验收标准

第一版完成后，至少要验证：

1. 提交 `type != 0 / 1 / 2` 的 note 后，Athena 会走标准 document upload 链路
2. `type == 0 / 1 / 2` 的 note 完全不进入本次知识库链路
3. Athena 能成功把 HTML 上传到正确 `kbId`
4. 上传时能命中正确的 Athena 专用 `pipelineId`
5. 该 `pipelineId` 默认复用昨天的 `athena-note-ingestion`
6. `ragent` 文档列表中能看到该 note 对应的 document
7. upload 后能够正常触发 chunk
8. chunk 日志中能看到记录
9. chunk 调试页可以查看结果

---

## 九、建议的验证顺序

### 验证 1：只验证 upload

先验证：

- document 是否创建成功
- 文件名是否符合预期
- 是否进入正确知识库
- `0/1/2` 是否确实未进入本链路

### 验证 2：验证 pipeline chunk

再验证：

- `startChunk` 是否成功
- pipeline 是否产出 chunk
- 文档状态是否进入 `success`
- 实际执行的 pipeline 是否就是昨天那条 Athena note pipeline

### 验证 3：验证文档视图能力

最后验证：

- 文档列表
- 文档详情
- 分块日志
- chunk 调试

---

## 十、后续扩展（本轮先不做）

本轮跑通后，后续可以继续做：

1. 同一 `noteId` 重复上传前先删旧 document / 旧向量
2. 笔记编辑后自动重建 document
3. 笔记删除后清理 `ragent document`
4. 若后续需要，再考虑更正式的业务 metadata 表达方式

---

## 十一、一句话决策

本次最终决策是：

- **只有 `type != 0/1/2` 的知识型 Athena note 才进入本次链路；这些 note 会被包装为 HTML 文件，走 `ragent` 标准 document upload，并默认复用昨天已经验证通过的 `athena-note-ingestion` pipeline 完成后续处理；`type == 0/1/2` 完全不走这里。**
