# PR 记录（PR1 - PR5.2）

## PR1：配置骨架

### 目标
为 Athena 与 RAG 集成建立最小配置骨架，不引入业务逻辑。

### 变更
- `ragent` 新增 `AthenaKnowledgeSyncProperties`
- `ragent` 的 `application.yaml` 增加 `athena.knowledge-sync` 配置
- `athena` 新增 `AthenaRagAskRoutingProperties`
- `athena` 的 `bootstrap-dev.yml` / `bootstrap-prod.yml` 增加 `athena.rag.ask-routing` 配置

### 边界
- 只做配置类与配置项
- 不做 MQ
- 不做同步逻辑
- 不做问答逻辑

---

## PR2：路由服务

### 目标
把配置骨架变成可调用的路由能力。

### 变更
- `ragent` 新增：
  - `KnowledgeBaseRoutingService`
  - `KnowledgeBaseRoutingServiceImpl`
- `athena` 新增：
  - `RagAskRoutingService`
  - `RagAskRoutingServiceImpl`

### 作用
- `ragent` 支持 `type -> kbCode`
- `athena` 支持 `age -> kbCodes`

### 边界
- 仍不接 MQ
- 不做入库
- 不做问答接口

---

## PR3：Athena 笔记摄取服务

### 目标
在不新开平行处理流的前提下，复用 `ragent` 现有 ingestion pipeline / node / 监控体系，增加 Athena HTML 笔记摄取入口。

### 设计原则
- 不重写 parser / chunk / index 流程
- 不新增 MySQL fetcher
- 通过预置 `rawBytes` 复用现有 `fetcher -> parser -> chunker -> indexer` 流水线
- 通过 `IngestionContext.metadata` 注入 `noteId` 等业务字段，为后续引用回传打基础

### 变更
- 新增 DTO：
  - `AthenaNoteSyncRequest`
  - `AthenaNoteSyncResult`
- 新增服务：
  - `AthenaNoteIngestionService`
  - `AthenaNoteIngestionServiceImpl`

### 当前实现
- 根据 `type` 调用 `KnowledgeBaseRoutingService` 获取目标 `kbCode`
- 查询知识库并获取 `collectionName`
- 构造 `IngestionContext`
- 组装最小 pipeline：
  - `fetcher`
  - `parser`
  - `chunker`
  - `indexer`
- metadata 注入：
  - `noteId`
  - `title`
  - `type`
  - `authorId`
  - `source=athena-note`

### 代码复查结论
本轮复查时额外发现并修正了一个配置问题：
- `ragent/bootstrap/src/main/resources/application.yaml` 中，`athena.knowledge-sync` 原本错误插入到了 `rag` 配置块内部
- 现已调整为独立顶层配置，避免影响现有 `rag.mcp`、`rag.search`、`rag.trace` 结构

### 当前边界
- 只完成服务层入口
- 不接 MQ Consumer
- 不接 Controller
- 不接 Athena 发消息
- 不处理问答返回

---

## PR4：Athena 笔记同步 MQ Consumer

### 目标
把 PR3 中完成的 `AthenaNoteIngestionService` 挂到 RocketMQ 消费入口上，形成真正可被 `athena` 调用的异步同步通道。

### 变更
- 新增 MQ 事件：
  - `AthenaNoteSyncEvent`
- 新增 MQ Consumer：
  - `AthenaNoteSyncConsumer`

### 当前实现
- 最终监听固定 topic：`note-knowledge-sync`
- 使用固定 consumer group：`note-knowledge-sync_cg`
- 消费 `MessageWrapper<AthenaNoteSyncEvent>`
- 在 consumer 中将消息转换为 `AthenaNoteSyncRequest`
- 调用 `AthenaNoteIngestionService.ingest(...)`

### 设计取舍
- 继续复用 `ragent` 现有 RocketMQ 抽象与消费模式
- 不在 consumer 内写业务流程，只做消息适配与服务委托
- 不在 PR4 引入幂等增强、失败补偿、人工重试面板等附加能力
- 原本尝试过 `${unique-name}` 形式的 topic / group，但联调阶段为避免收发不一致，先收敛为固定 topic 与固定 consumer group

### 当前边界
- 已接入 MQ 消费入口
- 已能和 `athena` 发送端形成稳定闭环
- 仍未接问答接口

---

## PR4.1：Athena MQ 框架封装与浏览记录迁移

### 目标
先把 `athena` 侧 RocketMQ 能力沉到 framework 层，避免后续笔记同步、问答日志等场景继续在业务模块里手写 producer / consumer / 手动配置。

### 背景
- `athena-ground` 中原有浏览记录 MQ 是一个已验证可用、但实现较草率的版本
- 生产者直接依赖 `RocketMQTemplate`
- 消费者通过手动 `DefaultMQPushConsumer` 注册
- 消息体使用 JSON 字符串与 `Map` 解析
- `RocketMQConfig` 放在业务模块里，不利于后续复用

### 变更
- `athena-framework` 新增模块：
  - `athena-framework-mq`
- framework-mq 新增：
  - `MessageWrapper`
  - `MessageQueueProducer`
  - `RocketMQProducerAdapter`
  - `RocketMQAutoConfiguration`
- framework-basic 补充：
  - `JsonUtils.parseObject(...)`
- `athena-ground-biz` 新增：
  - `ViewRecordEvent`
  - `ViewRecordProducer`
  - `ViewRecordConsumer`
  - `RocketMQConsumerConfig`
- 删除：
  - 旧的业务侧 `RocketMQConfig`

### 当前实现
- 通过 `athena-framework-mq` 提供统一发送接口与消息包装器
- framework 中显式注册：
  - `DefaultMQProducer`
  - `RocketMQTemplate`
  - `MessageQueueProducer`
- 生产侧统一发送 `MessageWrapper + JSON String payload`
- 浏览记录生产侧改为发送 `ViewRecordEvent`
- 浏览记录消费侧保留手动 `DefaultMQPushConsumer` 注册，但业务处理收敛到 `ViewRecordConsumer`
- 消费链路改为：
  - 收字符串
  - 反序列化 `MessageWrapper`
  - 再反序列化 `ViewRecordEvent`

### 关键修正
这一轮实际把多个兼容性问题一并踩平并修掉：
- `rocketmq-spring-boot-starter` 在当前环境下没有自动提供 `RocketMQTemplate`
- `DefaultMQProducer` 不能重复启动，否则会出现 `RUNNING` 状态异常
- 当前版本组合下，直接发送对象 payload 不稳定，最终统一改成字符串 JSON 传输
- `@RocketMQMessageListener` 在 `athena` 当前环境下兼容性不稳，因此消费者最终回到手动注册方案

### 设计取舍
- 先只抽取普通消息发送能力，不提前引入事务消息、回查、复杂 listener 抽象
- producer 统一 framework 化
- consumer 采用已验证稳定的手动注册方式，而不是强推注解监听
- 保持和 `ragent` 的消息协议方向一致，但不照搬全部实现

### 当前边界
- 已完成 `athena` 侧 MQ 基础设施沉淀
- 已完成浏览记录链路迁移，作为第一条落地样例
- 还未接入 Athena 笔记发布同步消息

---

## PR5：Athena 发布笔记后发送知识同步 MQ

### 目标
在 Athena 笔记发布成功后，向 `ragent` 发送 `note-knowledge-sync` 消息，打通 `athena -> ragent` 的异步同步链路。

### 变更
- `athena-ground-biz` 新增：
  - `AthenaNoteSyncEvent`
  - `AthenaNoteSyncProducer`
  - `AthenaNoteSyncDispatchService`
- `GroundServiceImpl.submitNote(...)` 接入同步消息发送

### 当前实现
- 笔记发布成功后发送 topic：`note-knowledge-sync`
- 发送数据包含：
  - `noteId`
  - `title`
  - `contentHtml`
  - `type`
  - `authorId`
- `submitNote(...)` 成功返回 `Result.ok(noteId)`
- 派发逻辑收敛到独立的 `AthenaNoteSyncDispatchService`

### 设计取舍
- 只改发布成功后的发送动作，不在这一轮引入事务消息与失败补偿
- 同步服务独立，避免把 MQ 发送逻辑继续堆到 `GroundServiceImpl`
- 传输层继续沿用 PR4.1 已验证的 framework producer 能力

### 当前边界
- 已完成 Athena 发送端
- 已和 PR4 的 `ragent` consumer 形成闭环
- 尚未做问答接口与引用回传

### 联调验证结果
本轮已完成真实环境联调验证，主链路已经跑通：
- `athena` 发布笔记成功
- `athena` 成功发送 `note-knowledge-sync` MQ 消息
- `ragent` 成功注册并启动 `AthenaNoteSyncConsumer`
- `ragent` 成功消费历史与新增同步消息
- `AthenaNoteIngestionService` 成功执行 `fetcher -> parser -> chunker -> indexer`
- 向量成功写入目标知识库 `kbchild`

### 已验证通过的行为
- `type=10` 的笔记能够正确路由到 `kbchild`
- 同步消息可以稳定消费，不再出现 producer / consumer topic 对不上的问题
- ingestion pipeline 可以复用现有能力完成 HTML 笔记解析、分块和入库
- 同一批历史积压消息可以被 consumer 正常拉取并完成补消费

### 暂缓项
- 当前已观察到某篇解析后文本长度约 4607 的内容只分出 1 个 chunk
- 该现象暂不阻塞主链路验证，先不在 PR5 阶段展开处理
- 后续在主链路完成后，再专项分析 chunker 策略与 HTML 解析后的文本结构是否符合预期

---

## PR5.1：GroundServiceImpl 互动逻辑拆分

### 目标
缩小 `GroundServiceImpl` 体积，把点赞、收藏、互动计数相关逻辑从大类中剥离出去。

### 变更
- 新增接口：
  - `NoteInteractionService`
- 新增实现：
  - `NoteInteractionServiceImpl`
- `GroundServiceImpl` 改为委托互动服务处理：
  - 点赞 / 收藏
  - 是否点赞 / 是否收藏
  - 点赞列表 / 收藏列表
  - 点赞数 / 收藏数 / 评论数变更

### 结果
- `GroundServiceImpl` 不再承载大量互动细节
- 互动相关职责更集中，后续更容易继续拆分和测试

---

## PR5.2：发布逻辑拆分与 service 规范化

### 目标
继续缩小 `GroundServiceImpl`，把笔记发布落库逻辑抽成独立服务，并统一 service 层结构为“接口在 `service`，实现放 `service.impl`”。

### 变更
- 新增接口：
  - `NotePublishService`
  - `AthenaNoteSyncDispatchService`
- 新增实现：
  - `NotePublishServiceImpl`
  - `AthenaNoteSyncDispatchServiceImpl`
  - `GroundServiceImpl` 移入 `service.impl`
- `GroundServiceImpl.submitNote(...)` 调整为：
  - 参数校验
  - `notePublishService.publish(...)`
  - `athenaNoteSyncDispatchService.dispatch(...)`

### 当前结构
- `service` 包只放接口：
  - `GroundService`
  - `NoteInteractionService`
  - `NotePublishService`
  - `AthenaNoteSyncDispatchService`
- `service.impl` 包放实现：
  - `GroundServiceImpl`
  - `NoteInteractionServiceImpl`
  - `NotePublishServiceImpl`
  - `AthenaNoteSyncDispatchServiceImpl`

### 结果
- `GroundServiceImpl` 更接近编排层
- 发布、互动、同步派发各自有清晰边界
- service 层结构更规范，后续继续扩展更容易

---

## 当前总体状态

### 已打通的链路
- `athena` 发布笔记
- `athena` 发送 `note-knowledge-sync` MQ
- `ragent` 消费消息并执行 Athena 笔记 ingestion
- ingestion 结果成功写入目标知识库

### 已完成的基础能力
- `ragent` 知识库路由
- `athena` 年龄路由
- `ragent` Athena 笔记摄取服务
- `ragent` Athena 同步 MQ consumer
- `athena` MQ framework 化
- `athena` 浏览记录链路迁移
- `athena` 发布同步消息派发
- `athena-ground-biz` service 层职责拆分与规范化

---

## 下一步建议

### PR6
- `ragent` 提供面向 `athena` 的问答接口
- 问答结果中支持回传引用笔记信息
- 优先先把面向 `athena` 的主问答链路打通，再回头优化 chunk 数异常等非阻塞问题

### PR7
- `athena` 增加 `/blog/ask` 闭环
- 接入年龄路由与问答展示
