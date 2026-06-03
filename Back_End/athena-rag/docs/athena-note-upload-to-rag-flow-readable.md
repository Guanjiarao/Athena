# Athena 笔记审核通过后上传到 RAG 的流程

## 1. 这条链路解决什么问题

Athena 里的笔记在用户提交后，需要经过后台审核。只有审核通过且符合权威的笔记，才会进入 RAG 知识库，后续才能被智能问答、知识检索等能力使用。

所以这条链路的核心问题是：

> 当一篇 Athena 笔记审核通过后，系统如何把这篇笔记转换成 RAG 可以检索的知识文档？

当前实现中，上传 RAG 的触发点不是用户发布笔记时，而是后台管理员点击“审核通过”之后。

---

## 2. 总体流程

整体可以理解为两大阶段：

1. **Athena 侧：审核通过后，把笔记作为 document 上传给 RAG。**
2. **RAG 侧：接收 document 后，解析、分块、向量化，最终写入向量库。**

流程图如下：

```text
管理员审核通过笔记
        │
        ▼
Athena 更新笔记状态为“审核通过”
        │
        ▼
事务提交成功后，提交异步上传任务到线程池
        │
        ▼
线程池后台执行上传任务
        │
        ▼
把笔记 HTML 内容作为 document 上传到 athena-rag
        │
        ▼
athena-rag 创建文档记录，并保存原始文件
        │
        ▼
Athena 调用 RAG 的 startChunk 接口
        │
        ▼
RAG 内部发送 MQ 消息，异步执行分块任务
        │
        ▼
解析文档内容、分块、生成 embedding
        │
        ▼
写入 chunk 表和向量库
        │
        ▼
笔记可以被 RAG 检索使用
```

---

## 3. 涉及哪些系统

| 系统 | 作用 |
| --- | --- |
| `athena-ground` | 负责笔记审核、读取笔记内容、判断是否需要上传 RAG、发起 document 上传 |
| `athena-rag` | 负责接收文档、保存文件、解析分块、生成向量、写入向量库 |
| 线程池 | 在 Athena 侧异步执行 RAG 上传，避免审核接口被 RAG 上传拖慢 |
| RocketMQ | 在 RAG 内部异步执行耗时的文档分块和向量化任务 |
| 向量库 | 存储文档分块后的 embedding，用于后续相似度检索 |

---

## 4. 第一步：后台审核通过

后台审核入口是：

```text
POST /athena/admin/blog/review/approve
```

对应控制器：

```java
@PostMapping("/approve")
public Result approve(@RequestBody NoteApproveDTO request) {
    return noteReviewService.approve(request);
}
```

也就是说，管理员点击审核通过后，会进入 `NoteReviewServiceImpl.approve` 方法。

---

## 5. 第二步：更新笔记审核状态

在 `approve` 方法里，系统首先会做这些事情：

1. 校验 `noteId` 是否存在。
2. 查询笔记基础信息。
3. 判断当前笔记是否仍处于“待审核”状态。
4. 将笔记状态更新为“审核通过”。
5. 查询笔记正文内容。
6. 组装后续上传 RAG 需要的数据。

笔记审核状态大致是：

```text
0 = 待审核
1 = 审核通过
2 = 审核拒绝
```

审核通过时，状态会从：

```text
待审核 -> 审核通过
```

---

## 6. 第三步：判断这篇笔记是否需要进入 RAG

不是所有笔记都会上传到 RAG。

当前代码里通过笔记类型 `type` 判断：

```java
private boolean shouldUploadAsRagentDocument(Byte type) {
    return type != null && type != 0 && type != 1 && type != 2;
}
```

可以理解为：

| 笔记类型 | 是否上传 RAG |
| --- | --- |
| `null` | 不上传 |
| `0 / 1 / 2` | 不上传 |
| 其他类型 | 上传 |

这样做的目的，是只把需要进入知识库的笔记同步给 RAG。

---

## 7. 第四步：提交异步上传任务

以前的逻辑是：审核接口里直接调用 RAG 上传接口。

现在的逻辑改成了：

```text
审核状态更新成功
  -> 等事务提交成功
  -> 再把上传 RAG 的任务放入线程池
  -> 后台线程异步上传
```

关键代码如下：

```java
if (shouldUploadAsRagentDocument(payload.type())) {
    athenaNoteRagAsyncUploadService.submitAfterCommit(
            payload.noteId(),
            payload.title(),
            payload.content(),
            payload.type(),
            payload.authorId()
    );
}
```

这里有一个重要点：

> RAG 上传不是立即执行，而是在数据库事务提交成功之后才提交到线程池。

这样可以避免一种错误情况：

```text
笔记审核事务最终回滚了，但 RAG 已经提前上传了文档。
```

---

## 8. 为什么要用线程池

RAG 上传本身涉及 HTTP 调用、文件上传、RAG 文档创建和触发分块。如果这些都放在审核接口里同步执行，会带来两个问题：

1. **审核接口变慢。**
2. **如果 RAG 慢或者短暂不可用，审核流程容易被影响。**

因此现在使用线程池异步处理。

线程池配置如下：

```java
executor.setCorePoolSize(8);
executor.setMaxPoolSize(8);
executor.setQueueCapacity(1000);
executor.setThreadNamePrefix("athena-note-rag-upload-");
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
```

可以简单理解为：

| 配置 | 含义 |
| --- | --- |
| `corePoolSize = 8` | 固定 8 个线程并发上传 |
| `maxPoolSize = 8` | 最多也是 8 个线程 |
| `queueCapacity = 1000` | 最多排队 1000 个上传任务 |
| `AbortPolicy` | 队列满了就拒绝任务，并记录日志，不拖慢审核接口 |

这样可以控制对 RAG 的并发压力，避免短时间大量审核通过时把 RAG 打爆。

---

## 9. 第五步：Athena 把笔记包装成 document

线程池中的后台任务最终会调用：

```java
athenaNoteDocumentUploadService.upload(noteId, title, contentHtml, type, authorId);
```

这个方法会把笔记正文包装成一个 HTML 文件。

文件名格式类似：

```text
athena-note-{noteId}-{title}.html
```

例如：

```text
athena-note-12345-高血压饮食建议.html
```

同时，还会带上一些元数据 metadata：

```json
{
  "noteId": 12345,
  "title": "高血压饮食建议",
  "type": 50,
  "authorId": 10001,
  "source": "athena-note"
}
```

这些 metadata 的作用是：后续 RAG 分块、检索、溯源时，可以知道这个知识来自哪篇 Athena 笔记。

---

## 10. 第六步：选择目标知识库和 Pipeline

不同类型的笔记，可能要进入不同的知识库，也可能使用不同的 RAG 处理 Pipeline。

Athena 通过 `type` 做路由：

```text
笔记 type
  -> 找到对应的知识库 kbId
  -> 找到对应的 pipelineId
```

核心逻辑是：

```java
AthenaKnowledgeRouteService.KnowledgeTarget target =
        knowledgeRouteService.resolveTarget(Integer.valueOf(type));
```

最终会得到：

| 字段 | 含义 |
| --- | --- |
| `kbId` | RAG 知识库 ID |
| `kbCode` | 业务知识库编码 |
| `pipelineId` | RAG 处理文档时使用的数据处理 Pipeline |

---

## 11. 第七步：调用 RAG 上传接口

Athena 会调用 RAG 的 document upload 接口：

```text
POST /api/ragent/knowledge-base/{kbId}/docs/upload
```

上传时主要传这些参数：

| 参数 | 说明 |
| --- | --- |
| `file` | 笔记 HTML 文件 |
| `sourceType` | 固定为 `file` |
| `processMode` | 固定为 `pipeline` |
| `pipelineId` | 前面路由出来的 Pipeline ID |
| `metadata` | noteId、title、type、authorId 等信息 |

RAG 收到后，会创建一条文档记录。

这时 RAG 文档状态是：

```text
PENDING
```

表示文档已经上传，但还没有完成分块和向量化。

---

## 12. 第八步：触发 RAG 文档分块

RAG 上传接口返回成功后，会返回一个 `docId`。

Athena 拿到 `docId` 后，会继续调用 RAG 的分块接口：

```text
POST /api/ragent/knowledge-base/docs/{docId}/chunk
```

这个接口不会直接在当前请求里完成所有分块和向量化，而是会在 RAG 内部发送 MQ 消息。

也就是说：

```text
Athena 触发 startChunk
  -> RAG 把文档状态改为 RUNNING
  -> RAG 发送 MQ 消息
  -> RAG 消费者异步处理文档
```

---

## 13. 第九步：RAG 内部异步处理文档

RAG 内部消费者收到 MQ 消息后，会开始真正处理文档。

主要步骤是：

```text
读取原始 HTML 文件
  -> 解析成纯文本或结构化文本
  -> 根据 Pipeline 做清洗、增强等处理
  -> 将长文本切成多个 chunk
  -> 为每个 chunk 生成 embedding
  -> 保存 chunk
  -> 写入向量库
```

可以理解为：

> 上传 document 只是把原始材料交给 RAG；真正让它能被检索，还需要分块和向量化。

---

## 14. RAG 里的文档状态变化

一篇文档在 RAG 中大致会经历这些状态：

```text
PENDING -> RUNNING -> SUCCESS
                 \-> FAILED
```

含义如下：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 文档刚上传，还没开始分块 |
| `RUNNING` | 正在分块、向量化 |
| `SUCCESS` | 分块和向量写入完成，可以用于检索 |
| `FAILED` | 分块或向量化失败，需要排查日志 |

---

## 15. 当前架构设计特点

这条链路采用“审核主流程”和“知识库处理流程”分阶段执行的方式。

审核通过时，系统优先完成笔记审核状态更新；事务提交成功后，再由后台线程池负责把笔记上传到 RAG。RAG 收到文档后，再通过内部异步任务完成分块和向量化。

这样设计有几个好处：

| 设计点 | 好处 |
| --- | --- |
| 事务提交后再上传 | 保证只有审核真正通过的笔记才会进入 RAG 上传流程 |
| Athena 侧线程池异步上传 | 审核接口响应更快，用户操作体验更稳定 |
| 固定 8 个上传线程 | 控制上传并发，避免短时间大量请求冲击 RAG 服务 |
| 有界队列 1000 | 上传任务可以排队处理，同时避免任务无限堆积 |
| RAG 侧异步分块 | 文档解析、分块、embedding 等耗时操作在后台完成 |
| RAG 文档状态流转 | 可以通过 `PENDING / RUNNING / SUCCESS / FAILED` 观察文档处理进度 |

整体上，这个设计把“内容审核”和“知识库加工”拆成两个阶段：

```text
审核阶段：快速完成审核状态更新
知识库阶段：后台完成 document 上传、分块、向量化
```

这样既保证审核流程清晰，也让 RAG 的文档处理更加可控。

---

## 16. Athena 侧和 RAG 侧的异步分工

当前链路里有两处异步处理，各自承担不同职责：

| 位置 | 异步方式 | 主要作用 |
| --- | --- | --- |
| Athena 侧 | 线程池 | 负责把审核通过的笔记上传为 RAG document |
| RAG 侧 | RocketMQ | 负责文档解析、分块、embedding、向量写入 |

可以理解为：

```text
Athena 侧线程池：负责“把材料送进 RAG”
RAG 侧 MQ：负责“把材料加工成可检索知识”
```

这种分工让两个系统职责更清楚：

- `athena-ground` 专注于笔记审核和上传任务调度；
- `athena-rag` 专注于知识库文档处理和向量检索准备。

## 17. 总结

Athena 笔记进入 RAG 的过程可以概括为：

> 管理员审核通过笔记后，Athena 在事务提交成功后把上传任务交给线程池异步执行；线程池把笔记内容作为 HTML document 上传给 RAG；RAG 保存文档后通过 MQ 异步完成解析、分块、embedding 和向量库写入，最终让这篇笔记可以被 RAG 检索使用。

这条链路通过“审核事务提交后上传 + Athena 侧线程池 + RAG 侧异步分块”的方式，把笔记审核和知识库加工两个阶段清晰拆开，提高了审核接口响应速度，也让 RAG 文档处理过程更加可控。
