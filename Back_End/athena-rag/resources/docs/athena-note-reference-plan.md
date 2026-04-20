# Athena Note 引用补查改动方案

> 目标：确认当前标准 document 主链路是否已传 `noteId`，并给出“基于 `noteId` 回查 Athena 业务详情”的可落地方案。
> 更新时间：2026-04-14

## 一、结论先说

### 1. 当前新主链路 **没有** 传 `noteId`

当前 Athena 发布知识笔记后，走的是标准 document 上传链路：

- `AthenaNoteDocumentUploadServiceImpl`
- `POST /api/ragent/knowledge-base/{kbId}/docs/upload`
- `POST /api/ragent/knowledge-base/docs/{docId}/chunk`

这条链路当前只传了：

- `file`
- `sourceType=file`
- `processMode=pipeline`
- `pipelineId`

没有传：

- `noteId`
- `title`
- `authorId`
- `type`
- `source`

因此，**当前标准 document 新主链路里，不能确认向量 metadata 中稳定存在 `noteId`**。

### 2. 旧 MQ / ingestion 方案里，确实加过 metadata

旧方案 `AthenaNoteIngestionServiceImpl` 中，metadata 明确包含：

- `noteId`
- `title`
- `type`
- `authorId`
- `source`

但这不是当前主链路。

### 3. TODO 8 视角下，当前状态是：**没做完**

如果目标是：

- 检索结果只稳定依赖 `noteId`
- 再回查 Athena 主数据补标题 / 封面 / 频道 / 作者

那么这件事在当前标准 document 主链路里还没有真正闭环。

### 4. 当前阶段 **不建议直接上 Feign**

原先设想的 `ragent -> athena-ground` Feign 方案，在当前仓库结构下不适合立刻推进。

原因是：

- `athena` 主工程基于 `Spring Boot 3.0.2 + Spring Cloud 2022.0.0 + Spring Cloud Alibaba 2022.0.0.0`
- `athena-rag` 当前单独工程基于 `Spring Boot 3.5.7`
- 两边 Spring Boot / Spring Cloud / Nacos 版本栈并不一致
- 当前 `ragent` 虽然已有 Nacos discovery 依赖，但要无痛复用 Athena 现有 Feign/Nacos 调用方式，存在较高兼容风险

因此，**当前最稳妥的方案不是 Feign，而是先走 HTTP 接口调用（RestTemplate / OkHttp）**。

---

## 二、为什么现在不能直接做引用补查

当前引用链路依赖检索结果 metadata。

如果新主链路没有把 `noteId` 稳定写入 metadata，那么后续就无法保证：

- `ragent` 检索结果一定带 `noteId`
- 引用阶段能按 `noteId` 去 Athena 查详情

所以第一步不是直接补查，而是先保证：

> **标准 document → chunk → vector metadata** 这条链路里，`noteId` 一定存在。

---

## 三、当前推荐方案：HTTP 补查，不走 Feign / MCP

## 结论

当前阶段推荐：

- **用 HTTP 内部接口补查**
- `ragent` 通过 `RestTemplate` 或 `OkHttp` 调 Athena / Gateway 提供的引用详情接口
- **不用 MCP**
- **暂不落 Feign**

## 原因

这个场景是一个典型的内部确定性 RPC：

1. `ragent` 检索得到 `noteId`
2. `ragent` 调 Athena 内部接口
3. Athena 返回该 `noteId` 对应的业务展示信息
4. `ragent` 组装最终 references 返回前端

这不是模型自由调用工具的场景，不需要 MCP 参与推理或决策。

同时，Feign 依赖服务发现和版本栈兼容；当前 `athena-rag` 与 Athena 主工程版本栈不一致，直接推 Feign 会增加额外风险。

因此，当前更务实的落地方式是：

- **先用 HTTP 补查把功能跑通**
- 等后续 `ragent` 真正并入 Athena 微服务体系、版本栈统一后，再评估是否收敛到 Feign

---

## 四、总体改造目标

最终演进目标建议如下：

### 1. 向量 metadata 最小化

长期稳定只要求保留：

- `noteId`

可选保留：

- `snippet`
- `score`（这个通常来自检索结果，不一定存在 metadata 中）

不再把以下字段作为长期依赖：

- `title`
- `author`
- `channel`
- `cover`

这些展示信息应以 Athena 主数据为准。

### 2. 引用组装改为二段式

#### 第一段：检索阶段
`ragent` 检索后只拿最小引用候选：

- `noteId`
- `snippet`
- `score`

#### 第二段：补查阶段
`ragent` 通过 HTTP 调 Athena：

- 按 `noteId` 批量查业务详情
- 组装前端最终引用卡片

---

## 五、分步改造方案

## Phase 1：确认并补齐 `noteId` 写入新主链路

### 目标
确保当前标准 document 主链路最终写入向量库的 metadata 一定包含 `noteId`。

### 要做的事

#### 1. Athena 上传 document 时补充业务元信息
当前 `AthenaNoteDocumentUploadServiceImpl` 只传了 upload 所需基本字段。

需要确认 `ragent` 的标准 document upload / pipeline / chunk 流程，是否支持额外业务 metadata 透传。

若支持，则在 upload 请求中补：

- `noteId`
- `type`
- `authorId`
- 可选 `title`
- 固定 `source=athena-note`

#### 2. 如果 upload 接口当前不支持自定义 metadata
则需要在 `ragent` 的 document 模型或 pipeline 上新增一条能力：

- 文档上传时保存扩展 metadata
- chunk / index 时把该 metadata 带入向量记录

#### 3. 验证点
验证新上传的一篇 Athena note，其向量 metadata 中至少稳定存在：

- `noteId`

完成标准：

- 新主链路检索结果可稳定取到 `noteId`

---

## Phase 2：Athena 暴露“按 noteId 批量查引用详情”接口

### 目标
给 `ragent` 提供一个专用、稳定、轻量的引用补查接口。

### 推荐接口形态
由 `athena-ground` 提供一个批量查询接口，例如：

```text
POST /athena/blog/reference/detail/batch
```

请求体示意：

```json
{
  "noteIds": [101, 102, 103]
}
```

返回体示意：

```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "noteId": 101,
      "title": "文章标题",
      "cover": "https://...",
      "channelId": 3,
      "channelName": "科学养护",
      "authorId": 2001,
      "authorName": "小雅"
    }
  ]
}
```

### 这个接口的特点
- 面向内部服务调用
- 只返回引用展示所需字段
- 支持批量，避免逐条 RPC
- 不依赖前端展示 DTO 的历史形态

---

## Phase 3：`ragent` 用 HTTP 调 Athena / Gateway 补查

### 目标
在不引入 Feign 兼容风险的前提下，把引用补查功能跑通。

### 推荐两种方式

#### 方案 A：`ragent -> athena-gateway`
`ragent` 通过配置好的网关地址调用：

```text
POST {gatewayBaseUrl}/athena/blog/reference/detail/batch
```

优点：
- 不依赖 Feign / Nacos 版本兼容
- 复用现有网关入口
- 接入成本低

缺点：
- 比服务内 RPC 多一层网关

#### 方案 B：`ragent -> athena-ground` 直连 HTTP
由 `ragent` 通过配置好的内部地址调用 `athena-ground` 的批量详情接口。

优点：
- 路径更短
- 不依赖 gateway 转发

缺点：
- 需要额外维护内部地址配置

### 当前建议
优先采用：

- **方案 A：先经 gateway 调 Athena**

因为当前网关链路已经在跑，联调更容易。

### 技术实现建议
使用：

- `RestTemplate`
- 或 `OkHttp`

不引入 Feign。

---

## Phase 4：`ragent` 引用组装切换到 noteId 补查

### 目标
不再长期依赖 metadata 中的标题等展示字段。

### 新流程

1. 检索阶段拿到若干候选 chunk
2. 从 chunk metadata 中取 `noteId`
3. 去重后按得分排序
4. 依据已有规则筛选最终候选
5. 批量调用 Athena 查询详情
6. 拼接最终 references 返回前端

### 最终返回给前端的引用对象建议

```json
{
  "noteId": 101,
  "title": "文章标题",
  "snippet": "命中的片段摘要",
  "score": 0.91,
  "cover": "https://...",
  "channelName": "科学养护",
  "authorName": "小雅"
}
```

---

## 六、推荐的实际实施顺序

推荐按以下顺序推进：

### Step 1
先确认并补齐：

- **标准 document 新主链路中 `noteId` 的 metadata 写入**

这是当前最优先项。

### Step 2
在 `athena-ground` 中补一个：

- 按 `noteId` 批量查引用详情的内部接口

### Step 3
给 `ragent` 增加一个 HTTP 客户端配置，支持调 Gateway / Athena

### Step 4
在 `ragent` 中把引用组装切到：

- `noteId -> HTTP 批量补查`

### Step 5
逐步收缩 metadata 中对 `title/author/channel` 的长期依赖

---

## 七、当前代码状态核验结论

### 已确认

#### 当前标准 document 上传主链路未传 `noteId`
文件：`athena-ground/.../AthenaNoteDocumentUploadServiceImpl.java`

当前 body 只有：

- `file`
- `sourceType`
- `processMode`
- `pipelineId`

#### 旧 ingestion 链路确实传过 metadata
文件：`athena-rag/.../AthenaNoteIngestionServiceImpl.java`

metadata 包含：

- `noteId`
- `title`
- `type`
- `authorId`
- `source`

但它不是当前主链路。

#### Athena 现有工程已有 Feign + Nacos 使用先例
例如：

- `athena-insight` 已启用 `@EnableFeignClients`
- 已存在 `InsightGroundFeignApi` 这类通过服务名调用 Athena 业务服务的模式

但当前 `athena-rag` 版本栈与 Athena 主工程不一致，**不适合直接复用该方案**。

#### 当前 `athena-rag` 与 Athena 主工程存在版本栈差异

- Athena 主工程：`Spring Boot 3.0.2`
- `athena-rag`：`Spring Boot 3.5.7`

因此，当前直接推进 Feign + Nacos，不是最稳的选择。

---

## 八、最终建议

### 结论

当前推荐方案是：

1. **先补 `noteId` 进新 document 主链路 metadata**
2. **再由 Athena 暴露批量引用详情接口**
3. **`ragent` 通过 HTTP 调用该接口完成补查**
4. **不使用 MCP 作为引用补查主方案**
5. **暂不推进 Feign，等后续版本栈统一后再评估**

### 原因

- HTTP 调用更容易立即落地
- 不受当前 Spring Boot / Spring Cloud / Nacos 版本不兼容影响
- MCP 不适合固定业务补查链路
- 功能先跑通比过早统一技术形态更重要

---

## 九、下一步最小落地任务清单

### Task 1
确认 `ragent` 标准 document upload / chunk / index 链路中，如何传入并持久化扩展 metadata。

### Task 2
把 `noteId`、`type`、`authorId`、`source` 接到新主链路里。

### Task 3
在 `athena-ground` 设计并实现“按 `noteId` 批量查引用详情”接口。

### Task 4
给 `ragent` 增加 HTTP 调用 Athena / Gateway 的客户端实现。

### Task 5
将 references 生成逻辑切换为：

- 检索拿 `noteId`
- HTTP 批量补查
- 组装最终返回
