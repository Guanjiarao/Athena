# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**重要提示：与用户沟通时始终使用中文。**

## 项目概述

Ragent AI 是一个企业级 RAG（检索增强生成）智能体平台，基于 Java 17 + Spring Boot 3 构建。该系统提供完整的文档入库、多路检索、意图分类、问题重写、会话记忆等核心能力。

## 架构设计

### 模块结构

项目采用分层架构，包含 4 个 Maven 模块：

1. **framework**（基础设施层）
   - 异常处理体系：ClientException、ServiceException、RemoteException
   - 分布式 ID 生成（Snowflake）
   - 用户上下文和追踪上下文（TTL 跨线程传播）
   - SSE（Server-Sent Events）封装
   - 统一响应格式
   - MyBatis-Plus 配置
   - RocketMQ 生产者封装
   - Redisson 分布式锁和限流

2. **infra-ai**（AI 能力抽象层）
   - 多模型路由和故障转移
   - 提供商实现：百炼（阿里云）、SiliconFlow、Ollama
   - 三大核心服务：ChatService、EmbeddingService、RerankService
   - 模型健康检查和熔断器（CLOSED → OPEN → HALF_OPEN）
   - 基于优先级的模型选择

3. **bootstrap**（业务逻辑层）
   - 主应用入口：`RagentApplication.java`
   - 业务模块：
     - `admin`：仪表板和系统管理
     - `core`：核心 RAG 组件（解析、分块）
     - `ingestion`：文档入库流水线
     - `knowledge`：知识库管理
     - `rag`：RAG 查询和检索
     - `user`：用户管理

4. **mcp-server**（MCP 工具集成）
   - 基于 JSON-RPC 2.0 的工具执行
   - 工具注册表和自动发现
   - 独立应用入口：`MCPServerApplication.java`

### 核心业务模块

#### RAG 流水线
完整的文档到答案流程：
1. **文档入库**：上传 → 解析（Apache Tika）→ 分块 → 向量化 → 存储（Milvus/PgVector）
2. **查询处理**：重写 → 意图分类 → 多路检索 → 重排序 → Prompt 组装 → LLM 生成
3. **多路检索**：向量全局搜索 + 意图定向搜索（并行执行）
4. **后处理**：去重 → 重排序（qwen3-rerank）

核心包结构（`bootstrap/rag/core`）：
- `guidance`：用户引导（处理模糊查询）
- `intent`：意图分类（树形结构）
- `mcp`：MCP 工具集成
- `memory`：会话记忆管理（滑动窗口）
- `prompt`：Prompt 模板
- `retrieve`：多路检索引擎
  - `channel`：搜索通道实现（策略模式）
  - `postprocessor`：结果后处理流水线（责任链模式）
- `rewrite`：查询重写
- `vector`：向量存储操作（Milvus/PgVector）

#### 入库流水线
基于节点的文档处理流水线：
- **流水线编排**：`domain/pipeline` 包
- **执行引擎**：`engine` 包
- **节点实现**：`node` 包（模板方法模式）
- **文档获取**：`strategy/fetcher` 包（策略模式）

## 技术栈

### 后端
- **Java**：17
- **框架**：Spring Boot 3.5.7
- **数据库**：MySQL（20+ 张表）、PostgreSQL with pgvector
- **向量数据库**：Milvus 2.6.x 或 PostgreSQL pgvector
- **缓存**：Redis + Redisson
- **消息队列**：RocketMQ 5.x
- **ORM**：MyBatis-Plus 3.5.14
- **对象存储**：S3 兼容（RustFS）
- **文档解析**：Apache Tika 3.2
- **认证**：Sa-Token 1.43.0
- **工具库**：Hutool 5.8.37、Guava
- **代码格式化**：Spotless（编译时自动应用）

### 前端
- **框架**：React 18 + TypeScript
- **构建工具**：Vite 5
- **UI**：Radix UI + Tailwind CSS
- **状态管理**：Zustand
- **表单**：React Hook Form + Zod
- **路由**：React Router v6

## 开发命令

### 后端

```bash
# 构建项目（包含 Spotless 格式化）
./mvnw clean compile

# 运行测试
./mvnw test

# 打包应用
./mvnw clean package

# 运行应用（dev 配置）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 使用特定配置运行
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# 手动应用代码格式化
./mvnw spotless:apply

# 检查格式化（不应用）
./mvnw spotless:check

# 运行单个测试类
./mvnw test -Dtest=ClassName

# 运行单个测试方法
./mvnw test -Dtest=ClassName#methodName
```

### 前端

```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview

# 代码检查
npm run lint

# 代码格式化
npm run format
```

### Docker 服务

```bash
# 启动 Milvus 向量数据库
cd resources/docker
docker-compose -f milvus-stack-2.6.6.compose.yaml up -d

# 启动 RocketMQ
docker-compose -f rocketmq-stack-5.2.0.compose.yaml up -d

# 停止服务
docker-compose -f milvus-stack-2.6.6.compose.yaml down
docker-compose -f rocketmq-stack-5.2.0.compose.yaml down
```

### 数据库设置

```bash
# 初始化 PostgreSQL schema（包含 pgvector）
psql -U postgres -d ragent -f resources/database/schema_pg.sql

# 加载初始数据
psql -U postgres -d ragent -f resources/database/init_data_pg.sql

# 应用迁移（v1.0 到 v1.1）
psql -U postgres -d ragent -f resources/database/upgrade_v1.0_to_v1.1.sql
```

## 配置

### 应用配置
- 主配置：`bootstrap/src/main/resources/config/application.yaml`
- Dev 配置：`application-dev.yaml`
- Prod 配置：`application-prod.yaml`
- 服务器运行在端口 **9090**，上下文路径 `/api/ragent`

### 环境变量
- `SPRING_PROFILES_ACTIVE`：设置活动配置（默认：dev）

### API 文档
- Swagger UI：http://localhost:9090/api/ragent/swagger-ui.html
- OpenAPI 文档：http://localhost:9090/api/ragent/v3/api-docs

### 关键配置项

**RAG 配置**（在 application-dev.yaml 中）：
- 向量存储类型：`pg`（PostgreSQL with pgvector）或 `milvus`
- 集合：`rag_default_store`，维度：1536，度量：COSINE
- 查询重写：启用，最多 4 条历史消息
- 限流：1 个并发请求，3 秒最大等待，30 秒租约
- 记忆：保留 4 轮，5 轮后摘要，60 分钟 TTL
- 搜索通道：vector-global（0.6 阈值）、intent-directed（0.4 最小分数）
- 追踪：启用

**AI 提供商**：
1. **百炼（阿里云）**：qwen-plus-latest、qwen3-max（支持思考）、qwen3-rerank
2. **SiliconFlow**：GLM-4.7（支持思考）、Qwen3-Embedding-8B（1536 维）
3. **Ollama（本地）**：qwen3:8b-fp16、qwen3-embedding:8b-fp16

## 关键设计模式

代码库广泛使用设计模式以提高可扩展性：

- **策略模式**：SearchChannel、PostProcessor、MCPToolExecutor（可插拔组件）
- **工厂模式**：IntentTreeFactory、StreamCallbackFactory
- **注册表模式**：MCPToolRegistry、IntentNodeRegistry（自动发现）
- **模板方法**：IngestionNode 基类
- **装饰器模式**：ProbeBufferingCallback（首包检测）
- **责任链模式**：后处理器链、模型故障转移链
- **观察者模式**：StreamCallback（SSE 事件）
- **AOP**：@RagTraceNode（追踪）、@ChatRateLimit（限流）

## 线程池架构

系统使用 8 个专用线程池，均使用 TTL（TransmittableThreadLocal）包装：
- MCP 批量执行
- RAG 上下文组装
- 多路检索
- 内部检索
- 意图分类
- 记忆摘要
- 模型流式输出
- 会话入口

所有线程池都保留用户上下文和追踪信息跨异步边界。

## 扩展系统

### 添加新的搜索通道
1. 实现 `SearchChannel` 接口
2. 使用 `@Component` 注册为 Spring Bean
3. 自动被 `MultiChannelRetrievalEngine` 发现

### 添加新的 MCP 工具
1. 实现 `MCPToolExecutor` 接口
2. 使用 `@Component` 注册为 Spring Bean
3. 自动被 `DefaultMCPToolRegistry` 发现

### 添加新的入库节点
1. 继承 `IngestionNode` 基类
2. 实现节点特定逻辑（模板方法模式）
3. 通过数据库配置到流水线（`t_ingestion_pipeline_node`）

### 添加新的模型提供商
1. 在 `infra-ai` 层实现 `ChatClient` 接口
2. 添加到配置中的候选列表
3. 自动参与路由和故障转移

### 添加新的后处理器
1. 实现 `PostProcessor` 接口
2. 使用 `@Component` 和 `@Order` 注解注册为 Spring Bean
3. 自动添加到后处理链

## 重要注意事项

### 代码风格
- **Spotless** 在编译时自动格式化代码
- 版权头从 `resources/format/copyright.txt` 自动应用
- 遵循现有的包结构和命名约定
- 使用 Lombok 减少样板代码

### 测试
- 单元测试使用 Mockito with Java agent（在 maven-surefire-plugin 中配置）
- 集成测试可能需要运行服务（MySQL、Redis、Milvus、RocketMQ）
- 测试位置：`bootstrap/src/test/java`
- 运行单个测试：`./mvnw test -Dtest=ClassName#methodName`

### 并发
- 用户上下文传播使用 TTL（TransmittableThreadLocal）
- 所有异步操作必须使用 TTL 包装的执行器
- 关键部分使用 Redisson 分布式锁
- 永远不要直接使用标准 `ExecutorService` - 使用 8 个预配置的 TTL 包装线程池

### RAG 追踪
- 通过 `RagTraceContext` 进行完整流水线追踪
- 每个节点记录：持续时间、输入、输出、错误
- 追踪数据持久化到 `t_rag_trace_run` 和 `t_rag_trace_node` 表
- 通过 `ragent.rag.trace.enabled` 配置启用/禁用

### 记忆管理
- 会话记忆使用滑动窗口（最近 N 轮）
- 接近 token 限制时自动摘要
- Redis 分布式锁防止并发写入
- 记忆 TTL：60 分钟（可配置）

### 限流
- 基于 Redis 的队列式分布式限流
- 信号 + ZSET + Pub/Sub 用于跨实例协调
- SSE 推送队列位置更新给客户端
- 每用户配置：最大并发请求、最大等待时间、租约持续时间

### 模型故障转移
- 每个模型提供商的熔断器（CLOSED → OPEN → HALF_OPEN）
- 首包探测实现无缝故障转移
- 基于优先级的模型选择
- 失败阈值：2 次连续失败
- 熔断器打开持续时间：30 秒

## 常见工作流程

### 本地运行完整堆栈
1. 启动 PostgreSQL with pgvector 扩展
2. 启动 Redis
3. 启动 Milvus：`docker-compose -f resources/docker/milvus-stack-2.6.6.compose.yaml up -d`
4. 启动 RocketMQ：`docker-compose -f resources/docker/rocketmq-stack-5.2.0.compose.yaml up -d`
5. 初始化数据库 schema：`psql -U postgres -d ragent -f resources/database/schema_pg.sql`
6. 在 `application-dev.yaml` 中配置 API keys
7. 运行后端：`./mvnw spring-boot:run`
8. 运行前端：`cd frontend && npm run dev`

### 添加新知识库
1. 通过 admin API 创建知识库（`POST /api/ragent/admin/knowledge`）
2. 通过 ingestion API 上传文档（PDF/Word/Markdown）
3. 文档异步处理：解析 → 分块 → 向量化 → 存储到 Milvus/PgVector
4. 通过追踪日志或 `t_rag_trace_run` 表监控入库

### 调试 RAG 流水线
1. 检查 `t_rag_trace_run` 获取追踪 ID
2. 查询 `t_rag_trace_node` 获取详细节点执行
3. 查看每个阶段的日志：rewrite → intent → retrieval → rerank → generation
4. 使用 Swagger UI 测试单个组件
5. 启用调试日志：`logging.level.com.nageoffer.ai.ragent=DEBUG`

### 测试 SSE 流式传输
使用提供的测试脚本：
```bash
bash scripts/sse_queue_test.sh
```

## 文档

- **主 README**：`README.md` - 全面的项目介绍（中文）
- **学习指南**：`ragentlearn.md` - 30 课程大纲
- **数据库文档**：`resources/database/` - Schema 和迁移脚本
- **架构图**：`docs/assets/` - 可视化架构文档
- **示例文档**：`resources/docs/knowledge/` - 示例知识库文档

## Git 工作流

- 主分支：`master`
- Git 用户：xiaoxiaolanfeng
- 最近工作：数据库文档、后端重构
