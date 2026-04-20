# RAGent 实战教学大纲

> 面向对象：了解 RAG 基础理念，但没有实战经验的 Java 后端开发者
> 教学方式：每节课围绕 ragent 项目源码，理论 + 源码阅读 + 动手实践
> 项目地址：Back_End/ragent

---

## 第一阶段：基础认知（第 1-5 课）

### 第 1 课：RAG 全景图 — 从理论到工程

- RAG 的核心思想回顾：检索增强生成 vs 纯 LLM
- 一个 RAG 系统的完整链路：文档 → 分块 → 向量化 → 存储 → 检索 → 生成
- ragent 项目的定位：一个生产级 RAG 平台
- 动手：把 ragent 跑起来，体验一次完整的问答流程

### 第 2 课：ragent 项目架构总览

- 四大模块的职责：bootstrap（业务）、framework（基础设施）、infra-ai（AI 能力）、mcp-server（工具调用）
- 包结构与分层设计：controller → service → core → dao
- 配置文件解读：application.yaml 里的每一项配置是干什么的
- 动手：画出 ragent 的模块依赖图，标注每个模块的核心类

### 第 3 课：framework 基础设施层

- 统一响应体 `Result<T>` 和 `Results` 工具类
- 全局异常处理体系：ClientException / ServiceException / RemoteException
- RocketMQ 生产者封装：普通消息 vs 事务消息
- MyBatis-Plus 配置与分页插件
- 分布式锁（Redisson）和用户上下文（UserContext + TTL）
- 动手：对比 athena 的 framework，找出设计理念的异同

### 第 4 课：infra-ai — AI 模型抽象层

- 为什么需要一个 AI 抽象层？多模型路由 + 故障转移
- 三大能力接口：ChatService / EmbeddingService / RerankService
- Provider 体系：百炼（通义千问）、SiliconFlow、Ollama
- `ModelRoutingExecutor`：优先级路由 + 健康检查 + 自动降级
- 配置解读：candidates 列表、priority、default-model 的含义
- 动手：配置你自己的 API Key，调通 Embedding 和 Chat 接口

### 第 5 课：向量数据库基础

- 什么是向量？什么是 Embedding？维度是什么意思？
- 余弦相似度 vs 欧氏距离 vs 内积
- PgVector：PostgreSQL 的向量扩展，SQL 就能做向量检索
- Milvus：专业向量数据库，HNSW 索引原理
- ragent 的双向量库支持：`@ConditionalOnProperty` 切换
- `VectorStoreService` 接口：upsert / search / delete
- 动手：用 PgVector 手动插入一条向量，用 SQL 做一次相似度检索

---

## 第二阶段：文档处理链路（第 6-12 课）

### 第 6 课：文档解析 — 从文件到文本

- Apache Tika 是什么？为什么用它？
- `DocumentParser` 接口设计：parse / extractText / supports
- `TikaDocumentParser`：PDF/Word/Excel/PPT 的解析实现
- `MarkdownDocumentParser`：为什么 Markdown 要单独处理
- `DocumentParserSelector`：策略模式选择解析器
- 动手：上传一个 PDF 文件，断点跟踪解析过程，看输出的文本长什么样

### 第 7 课：文本分块 — 切得好才能检索得准

- 为什么要分块？太长 LLM 放不下，太短丢失上下文
- 分块的核心矛盾：粒度 vs 语义完整性
- `FixedSizeTextChunker`：简单粗暴，按字符数切，overlap 保证上下文
- `StructureAwareTextChunker`：识别 Markdown 结构（标题/段落/代码块），在语义边界切分
- `ChunkingOptions`：chunkSize / overlapSize / minSize / targetSize / maxSize
- `ChunkingStrategyFactory`：工厂模式 + 自动注册
- 动手：同一篇文档分别用两种策略分块，对比结果差异

### 第 8 课：Embedding 向量化 — 文本变向量

- Embedding 模型做了什么？文本 → 高维浮点数组
- 通义千问 text-embedding-v4：1536 维，怎么调用
- `EmbeddingService.embed()` 和 `embedBatch()` 的区别
- 批量 Embedding 的性能考量：batch size、并发、限流
- `ChunkEmbeddingService`：分块后批量向量化的实现
- 动手：把一段文本 Embedding，打印向量，观察维度和数值范围

### 第 9 课：向量存储 — 写入和索引

- `VectorStoreService.upsert()`：向量 + metadata 一起存
- metadata 的设计：存什么？docId、chunkIndex、原文、标题...
- PgVector 的存储结构：`embedding vector(1536)` 列 + HNSW 索引
- Milvus 的 Collection / Partition / Index 概念
- `VectorStoreAdmin.createCollection()`：建集合时发生了什么
- 动手：上传一篇文档，在数据库里查看生成的向量记录和 metadata

### 第 10 课：知识库管理 — CRUD 全流程

- 知识库的数据模型：knowledge_base → document → chunk 三层结构
- `KnowledgeBaseServiceImpl`：创建知识库时自动创建向量集合
- `KnowledgeDocumentServiceImpl`：文档上传 → S3 存储 → MQ 异步分块
- `KnowledgeDocumentChunkConsumer`：MQ 消费者触发分块流水线
- 分块流水线：下载 → 解析 → 分块 → Embedding → 写入向量库
- 动手：通过 API 创建一个知识库，上传文档，观察完整的异步处理链路

### 第 11 课：摄取流水线 — 可编排的数据管道

- Pipeline 的设计思想：节点化、可配置、可扩展
- 六大节点：Fetcher → Parser → Enhancer → Chunker → Enricher → Indexer
- `IngestionEngine`：拓扑排序 + 链式执行 + 日志记录
- 数据源抓取策略：S3 / HTTP / 本地文件 / 飞书
- Enhancer vs Enricher：文档级增强 vs 分块级增强
- 动手：创建一个自定义 Pipeline，配置不同的分块策略和增强节点

### 第 12 课：阶段实战 — 构建你的第一个知识库

- 综合前面所学，完成一个完整的知识库构建
- 准备 3-5 篇不同格式的文档（PDF/Markdown/Word）
- 创建知识库 → 上传文档 → 观察分块结果 → 手动检索验证
- 调优：调整分块参数，对比检索效果
- 总结：文档处理链路的性能瓶颈在哪里

---

## 第三阶段：检索与问答（第 13-20 课）

### 第 13 课：向量检索基础 — 从 SQL 到语义搜索

- 传统关键词搜索 vs 向量语义搜索
- `RetrieverService.retrieve()`：输入问题向量，输出 topK 相似分块
- PgVector 的检索 SQL：`ORDER BY embedding <=> ?::vector LIMIT ?`
- Milvus 的检索：`SearchReq` + `FloatVec` + HNSW 参数
- topK 的选择：太小漏信息，太大引入噪声
- 动手：同一个问题，分别用 topK=3/5/10 检索，对比结果质量

### 第 14 课：查询改写 — 让检索更精准

- 用户的问题往往不适合直接检索：口语化、模糊、多意图
- `QueryRewriteService`：调用 LLM 改写用户问题
- `MultiQuestionRewriteService`：一个问题拆成多个子问题
- 会话上下文改写：结合历史对话消除指代不明
- Prompt 模板：怎么让 LLM 输出结构化的改写结果
- 动手：输入一个口语化的问题，观察改写前后的检索结果差异

### 第 15 课：意图解析 — 理解用户真正想问什么

- 意图树的三层结构：DOMAIN → CATEGORY → TOPIC
- `IntentNode`：每个节点关联知识库集合或 MCP 工具
- `DefaultIntentClassifier`：用 LLM 做意图分类
- `IntentResolver`：递归遍历意图树，匹配最佳意图
- 意图缓存：`IntentTreeCacheManager` 避免重复加载
- 动手：定义一个简单的意图树，测试不同问题的意图分类结果

### 第 16 课：多通道检索 — 并行搜索，合并结果

- 为什么需要多通道？不同检索策略各有优劣
- `MultiChannelRetrievalEngine`：并行执行 + 结果合并
- `VectorGlobalSearchChannel`：全局向量检索，广撒网
- `IntentDirectedSearchChannel`：意图定向检索，精准打击
- 通道的启用条件：`isEnabled(context)` 的判断逻辑
- 动手：开启/关闭不同通道，对比检索结果的召回率和精确度

### 第 17 课：后处理 — 去重与重排序

- 多通道检索的问题：结果重复、排序不一致
- `DeduplicationPostProcessor`：基于内容哈希去重
- `RerankPostProcessor`：调用 Rerank 模型重新打分排序
- Rerank 模型（qwen3-rerank）vs 向量相似度：谁更准？
- 后处理器链的执行顺序：先去重再 Rerank
- 动手：观察 Rerank 前后的排序变化，理解 relevance_score

### 第 18 课：Prompt 工程 — 组装最终的提示词

- `RAGPromptService`：根据场景选择 Prompt 模板
- 四种场景：KB_ONLY / MCP_ONLY / MIXED / EMPTY
- `ContextFormatter`：把检索到的分块格式化为 LLM 能理解的文本
- 模板变量：`{{context}}`、`{{question}}`、`{{mcp_context}}`
- System Prompt vs User Prompt 的分工
- 动手：修改 Prompt 模板，观察 LLM 回答风格的变化

### 第 19 课：会话记忆 — 多轮对话的上下文

- 为什么需要记忆？LLM 本身是无状态的
- `JdbcConversationMemorySummaryService`：JDBC 持久化消息
- 滑动窗口：保留最近 N 轮，避免 token 超限
- 自动摘要：消息太多时，LLM 生成摘要替代原始消息
- Redis 分布式锁：并发写入的安全保障
- 动手：进行一段多轮对话，观察记忆的加载和摘要触发

### 第 20 课：RAG 问答全链路 — 串起来

- `RAGChatServiceImpl`：核心入口，串联所有组件
- 完整链路：接收问题 → 查询改写 → 意图解析 → 多通道检索 → 后处理 → Prompt 组装 → LLM 生成 → 流式返回
- SSE 流式输出：`SseEmitterSender` 的实现
- 错误处理和降级策略
- RAG Trace：`RagTraceContext` 全链路追踪
- 动手：发起一次问答，在日志中追踪完整链路的每个节点耗时

---

## 第四阶段：高级特性（第 21-25 课）

### 第 21 课：MCP 工具调用 — RAG 的能力扩展

- MCP 协议是什么？Model Context Protocol
- JSON-RPC 2.0：请求/响应格式
- `MCPToolExecutor` 接口：定义工具 + 执行逻辑
- `MCPToolRegistry`：工具注册表，自动发现 Bean
- `MCPDispatcher`：方法分发（tools/list、tools/call、initialize）
- `MCPEndpoint`：REST 端点，POST /mcp
- 动手：写一个自定义的 MCP 工具（比如查询笔记信息），注册并调用

### 第 22 课：MCP 与 RAG 的融合

- `RetrievalEngine`：在检索层集成 MCP 工具调用
- 意图节点的两种 kind：RAG（知识库检索）vs MCP（工具调用）
- 参数提取：LLM 从用户问题中提取工具参数
- KB 结果 + MCP 结果的合并策略
- MIXED 场景的 Prompt 组装
- 动手：定义一个意图节点关联 MCP 工具，测试混合问答

### 第 23 课：意图引导 — 处理模糊问题

- `IntentGuidanceService`：歧义检测 + 追问决策
- 什么时候该追问？多个高分意图、问题太模糊
- `GuidanceDecision`：追问建议的生成
- 前端交互：展示追问选项，用户选择后重新检索
- 动手：构造一个歧义问题，观察引导流程

### 第 24 课：性能优化与调优

- Embedding 批量化：减少 API 调用次数
- 向量索引调优：HNSW 的 M 和 efConstruction 参数
- 检索缓存：相同问题的结果缓存
- 分块参数调优：chunkSize 和 overlap 对检索质量的影响
- LLM 调用优化：流式输出、超时控制、重试策略
- 动手：用不同参数配置做 A/B 测试，量化检索质量

### 第 25 课：可观测性 — Trace 与监控

- `RagTraceContext`：traceId + taskId + nodeStack
- `RagTraceNode`：每个节点的耗时、输入、输出记录
- 数据库持久化：`t_rag_trace_run` + `t_rag_trace_node`
- 链路分析：哪个节点最慢？检索质量怎么量化？
- 动手：查询 trace 表，分析一次问答的完整链路性能

---

## 第五阶段：集成实战（第 26-30 课）

### 第 26 课：集成规划 — ragent 接入 athena

- 回顾 athena 的微服务架构
- ragent 作为独立微服务的部署方案
- 通信方式：Feign / HTTP 直连
- 数据同步：RocketMQ 异步推送笔记内容
- 参考文档：`doc/rag-integration-plan.md`

### 第 27 课：知识同步 — 笔记自动入库

- athena 侧：submitNote() 发 MQ 消息（type != 1, 2）
- ragent 侧：消费消息 → 解析 → 分块 → 向量化
- metadata 设计：noteId / type / title / userId
- 按 type 分知识库：儿童科普库 / 青年女性库 / 通用库
- 动手：发表一篇知识笔记，验证自动同步到 ragent 知识库

### 第 28 课：问答接口 — 从问题到答案 + 引用文章

- athena 新增 `/blog/ask` 接口
- 调用 ragent 的 RAG 问答 API
- 从返回的 chunk metadata 中提取 noteId
- 批量查询 NoteBasicDO，组装 BlogListDTO
- 返回结构：AI 回答 + 引用文章卡片列表
- 动手：实现完整的问答接口，用 Apifox 测试

### 第 29 课：MCP 工具 — 笔记查询工具

- 实现一个 `NoteQueryMCPExecutor`
- 功能：根据关键词/类型/作者查询笔记
- 注册到 ragent 的 MCP Server
- 在意图树中配置 MCP 类型的意图节点
- 测试：用户问"有哪些关于经期护理的文章" → MCP 工具查询 → 返回文章列表
- 动手：完整实现并测试

### 第 30 课：总结与展望

- 回顾整个 RAG 系统的架构和实现
- 你学到了什么：文档处理、向量检索、Prompt 工程、多通道检索、MCP 工具
- ragent 的设计亮点：模型路由、故障转移、可编排流水线、全链路追踪
- 可以继续探索的方向：
  - 混合检索（向量 + BM25 关键词）
  - GraphRAG（知识图谱增强）
  - Agent 编排（多步推理）
  - 评估体系（Ragas / 自动化评测）
- 你的 athena + ragent 集成还可以做什么：
  - 个性化推荐（基于浏览记录 + 向量相似度）
  - 智能客服（MCP 工具 + 多轮对话）
  - 内容审核（AI 辅助判断笔记质量）

---

## 附录

### 环境准备清单

| 组件 | 用途 | 安装方式 |
|------|------|----------|
| JDK 17 | 运行环境 | 已有 |
| PostgreSQL 16+ | ragent 主数据库 + PgVector | Docker / 直装 |
| pgvector 扩展 | 向量存储 | `CREATE EXTENSION vector` |
| Redis | 缓存 + 分布式锁 | 已有 |
| RocketMQ 5.2 | 异步消息 | 已有 |
| 通义千问 API Key | Chat + Embedding + Rerank | 阿里云百炼平台申请 |

### 每节课预计时长

- 理论讲解：15-20 分钟
- 源码阅读：15-20 分钟
- 动手实践：20-30 分钟
- 总计：约 50-70 分钟/课
