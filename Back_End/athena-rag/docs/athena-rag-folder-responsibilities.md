# athena-rag 文件夹作用说明（排除 triage）

> 本文用于快速了解 `athena-rag` 项目中各主要文件夹的职责。已按要求排除 `triage` 相关目录和包；同时跳过了 `node_modules`、`target`、IDE 临时目录、备份目录中的依赖展开内容。说明基于当前目录结构、`pom.xml` 模块定义和已阅读的关键代码整理。

## 1. 项目顶层目录

| 文件夹 | 作用 |
|---|---|
| `.claude/` | Claude/Agent 相关本地配置目录，通常用于保存 AI 辅助开发配置，不属于业务运行代码。 |
| `.cursor/` | Cursor IDE 相关配置目录，包含项目内自定义 skills 等辅助开发资产；其中部分 skill 名称包含 triage，本文不展开。 |
| `.idea/` | IntelliJ IDEA 项目配置目录，例如代码样式、数据源、检查规则等；不属于业务运行代码。 |
| `.mvn/` | Maven Wrapper 配置目录，用于固定 Maven 包装器运行方式。 |
| `assets/` | 项目静态素材或说明文档中引用的图片/资源目录。当前未展开业务逻辑。 |
| `bootstrap/` | Spring Boot 启动模块，也是后端业务主模块。包含 RAG、知识库、用户、管理、文档摄入等主要业务代码。 |
| `docs/` | 项目文档目录，存放 RAG 流程、上传流程、架构说明等 Markdown 文档和文档示例素材。 |
| `eval-reports/` | 评测报告输出目录，用于保存 RAG/检索/问答等评测结果。 |
| `framework/` | 通用框架模块，提供上下文、统一返回、异常、缓存、幂等、MQ、Trace、Web/SSE 等基础能力。 |
| `frontend/` | 前端主项目，基于 React/Vite，提供用户聊天、后台管理、知识库、配置、Trace 等页面。 |
| `frontend-backup-20260523-133100/` | 前端备份目录，保留某一时刻的前端代码和依赖，不建议作为主开发入口。 |
| `infra-ai/` | AI 基础设施模块，封装 LLM、Embedding、Rerank、Token 统计、HTTP 客户端等模型基础能力。 |
| `mcp-server/` | MCP Server 模块，用于提供 MCP 工具服务；根 `pom.xml` 中该模块当前被注释，说明暂未纳入主 Maven 构建。 |
| `resources/` | 部署、数据库、样例知识文档、评测数据、格式文件、Nginx 分发包等项目运行/演示资源。 |
| `scripts/` | 脚本目录，用于项目辅助操作、部署或维护；当前未展开具体脚本职责。 |

## 2. Maven 模块关系

根 `pom.xml` 定义了当前主构建模块：

| 模块目录 | 作用 |
|---|---|
| `bootstrap/` | 主业务与应用启动模块。 |
| `framework/` | 通用基础框架模块。 |
| `infra-ai/` | AI 模型与算法基础设施模块。 |
| `mcp-server/` | MCP 服务模块，目前在根 `pom.xml` 中被注释，暂不参与主构建。 |

## 3. `bootstrap/` 后端主业务模块

### 3.1 `bootstrap/src/main/java/com/nageoffer/ai/ragent/`

| 文件夹 | 作用 |
|---|---|
| `admin/` | 后台管理相关接口与服务，例如系统管理、配置管理或管理端聚合能力。 |
| `core/` | 文档解析、切片等核心底层处理能力，偏通用算法/处理逻辑。 |
| `ingestion/` | 文档摄入/入库流水线，包括上传、抓取、解析、切片、向量化、状态流转等。 |
| `knowledge/` | 知识库管理模块，包括知识条目、文件、状态、MQ 事件、定时任务和后台管理接口。 |
| `rag/` | RAG 问答核心模块，包括聊天、检索、意图、Prompt、会话记忆、Trace、评测等。 |
| `user/` | 用户模块，包括登录/认证、用户信息、用户 DAO 和用户服务。 |

### 3.2 `admin/`

| 文件夹 | 作用 |
|---|---|
| `admin/controller/` | 后台管理 HTTP 接口入口。 |
| `admin/controller/vo/` | 管理端接口返回视图对象。 |
| `admin/service/` | 管理端业务服务接口。 |
| `admin/service/impl/` | 管理端业务服务实现。 |

### 3.3 `core/`

| 文件夹 | 作用 |
|---|---|
| `core/chunk/` | 文档切片核心逻辑，负责将解析后的文本切分为适合向量化和检索的 chunk。 |
| `core/chunk/strategy/` | 切片策略实现，例如按长度、标题、段落或语义规则拆分。 |
| `core/parser/` | 文档解析能力，负责从文件中提取文本内容或结构化内容。 |

### 3.4 `ingestion/` 文档摄入模块

| 文件夹 | 作用 |
|---|---|
| `ingestion/controller/` | 文档摄入相关 HTTP 接口入口。 |
| `ingestion/controller/request/` | 摄入接口请求参数对象。 |
| `ingestion/controller/vo/` | 摄入接口返回视图对象。 |
| `ingestion/dao/` | 摄入模块数据访问层聚合目录。 |
| `ingestion/dao/entity/` | 摄入模块数据库实体对象。 |
| `ingestion/dao/mapper/` | 摄入模块 MyBatis/MyBatis-Plus Mapper。 |
| `ingestion/domain/` | 摄入领域模型聚合目录，承载上下文、枚举、流水线结果和配置模型。 |
| `ingestion/domain/context/` | 摄入流水线运行上下文，记录文档、任务、阶段状态等过程数据。 |
| `ingestion/domain/enums/` | 摄入模块枚举，例如任务状态、节点类型、处理阶段等。 |
| `ingestion/domain/pipeline/` | 摄入流水线抽象或阶段编排模型。 |
| `ingestion/domain/result/` | 摄入节点或流水线执行结果对象。 |
| `ingestion/domain/settings/` | 摄入相关配置/设置模型。 |
| `ingestion/engine/` | 摄入执行引擎，负责驱动各处理节点串联执行。 |
| `ingestion/node/` | 摄入流水线节点实现，例如解析、切片、向量化、索引写入等步骤。 |
| `ingestion/prompt/` | 摄入过程可能使用的 Prompt 或提示词构造逻辑。 |
| `ingestion/service/` | 摄入模块业务服务接口。 |
| `ingestion/service/impl/` | 摄入模块业务服务实现。 |
| `ingestion/strategy/` | 摄入策略聚合目录，用于按不同来源或类型选择处理方式。 |
| `ingestion/strategy/fetcher/` | 文档内容获取策略，例如从 URL、文件、外部来源抓取内容。 |
| `ingestion/util/` | 摄入模块工具类。 |

### 3.5 `knowledge/` 知识库模块

| 文件夹 | 作用 |
|---|---|
| `knowledge/config/` | 知识库模块配置类。 |
| `knowledge/controller/` | 知识库相关 HTTP 接口入口。 |
| `knowledge/controller/request/` | 知识库接口请求对象。 |
| `knowledge/controller/vo/` | 知识库接口返回视图对象。 |
| `knowledge/dao/` | 知识库数据访问层聚合目录。 |
| `knowledge/dao/entity/` | 知识库数据库实体对象，例如文档、知识条目、任务等。 |
| `knowledge/dao/handler/` | 数据库字段类型处理器或持久化辅助 handler。 |
| `knowledge/dao/mapper/` | 知识库 MyBatis/MyBatis-Plus Mapper。 |
| `knowledge/enums/` | 知识库模块枚举，例如状态、类型、来源等。 |
| `knowledge/filter/` | 知识库相关过滤逻辑，例如查询条件、权限或内容过滤。 |
| `knowledge/handler/` | 知识库业务处理器，承接特定事件或流程节点。 |
| `knowledge/mq/` | 知识库 MQ 消息处理聚合目录。 |
| `knowledge/mq/event/` | 知识库相关消息事件对象。 |
| `knowledge/schedule/` | 知识库定时任务，例如状态扫描、重试、同步等。 |
| `knowledge/service/` | 知识库业务服务接口。 |
| `knowledge/service/dto/` | 知识库服务层 DTO。 |
| `knowledge/service/impl/` | 知识库业务服务实现。 |

### 3.6 `rag/` RAG 核心模块（不含 triage）

| 文件夹 | 作用 |
|---|---|
| `rag/aop/` | RAG 相关 AOP 切面，例如 Trace 节点采集、调用链增强。 |
| `rag/config/` | RAG 模块配置类，包括检索通道、默认参数、线程池、验证等配置。 |
| `rag/config/validation/` | RAG 配置校验逻辑，确保启动或运行时配置合法。 |
| `rag/constant/` | RAG 常量定义，例如 Prompt 路径、阈值、特殊 key 等。 |
| `rag/controller/` | RAG HTTP 接口入口，例如聊天、会话、管理、Trace 查询等。 |
| `rag/controller/request/` | RAG 接口请求对象。 |
| `rag/controller/vo/` | RAG 接口返回视图对象。 |
| `rag/core/` | RAG 核心算法与领域能力聚合目录。 |
| `rag/core/guidance/` | 歧义识别与引导追问逻辑，用于在问题不清时短路返回澄清提示。 |
| `rag/core/intent/` | 意图树、意图分类、节点分数、意图过滤等逻辑。 |
| `rag/core/memory/` | 会话记忆服务，负责加载和追加多轮对话历史。 |
| `rag/core/prompt/` | Prompt 模板加载、上下文格式化、RAG Prompt 组装。 |
| `rag/core/retrieve/` | 检索编排核心，负责按子问题和意图组织 KB/MCP 上下文。 |
| `rag/core/retrieve/channel/` | 检索通道抽象与实现，例如全局检索、意图定向检索等。 |
| `rag/core/retrieve/channel/strategy/` | 检索通道内部策略，例如不同召回方式、查询构造或通道选择策略。 |
| `rag/core/retrieve/postprocessor/` | 检索结果后处理链，例如去重、排序、截断、rerank 等。 |
| `rag/core/rewrite/` | Query rewrite 和多问题拆分逻辑，将用户原问题改写为更适合检索的问题。 |
| `rag/core/vector/` | 向量相关核心能力，例如向量索引、向量查询、向量存储适配。 |
| `rag/dao/` | RAG 模块数据访问层聚合目录。 |
| `rag/dao/entity/` | RAG 数据库实体对象，例如会话、消息、意图节点、Trace 等。 |
| `rag/dao/mapper/` | RAG MyBatis/MyBatis-Plus Mapper。 |
| `rag/dto/` | RAG 内部或跨层数据传输对象。 |
| `rag/enums/` | RAG 模块枚举，例如 SSE 事件类型、意图类型、状态类型等。 |
| `rag/eval/` | RAG 评测逻辑，例如样本运行、指标计算、输出评测报告。 |
| `rag/mq/` | RAG 消息队列处理聚合目录。 |
| `rag/mq/event/` | RAG 相关 MQ 事件对象。 |
| `rag/service/` | RAG 应用服务接口，承接聊天、会话、后台配置等业务能力。 |
| `rag/service/bo/` | RAG 服务层业务对象。 |
| `rag/service/handler/` | 流式回调、SSE 事件处理、任务生命周期 handler。 |
| `rag/service/impl/` | RAG 应用服务实现。 |
| `rag/service/pipeline/` | RAG Chat pipeline 编排逻辑，例如加载记忆、改写、意图、检索、Prompt、LLM 调用。 |
| `rag/service/ratelimit/` | RAG 对话限流/排队逻辑，控制用户并发或请求节奏。 |
| `rag/trace/` | RAG Trace 运行器、上下文和追踪数据处理。 |
| `rag/util/` | RAG 模块工具类。 |

### 3.7 `user/` 用户模块

| 文件夹 | 作用 |
|---|---|
| `user/config/` | 用户模块配置，例如认证、拦截器或登录相关配置。 |
| `user/controller/` | 用户相关 HTTP 接口入口。 |
| `user/controller/request/` | 用户接口请求对象。 |
| `user/controller/vo/` | 用户接口返回视图对象。 |
| `user/dao/` | 用户数据访问层聚合目录。 |
| `user/dao/entity/` | 用户数据库实体对象。 |
| `user/dao/mapper/` | 用户 MyBatis/MyBatis-Plus Mapper。 |
| `user/enums/` | 用户模块枚举，例如角色、状态、登录类型等。 |
| `user/service/` | 用户业务服务接口。 |
| `user/service/impl/` | 用户业务服务实现。 |

### 3.8 `bootstrap/src/main/resources/`

| 文件夹 | 作用 |
|---|---|
| `config/` | Spring Boot 配置文件或分环境配置。 |
| `lua/` | Redis Lua 脚本，例如限流、幂等或分布式控制脚本。 |
| `META-INF/` | Java/Spring 标准元信息目录。 |
| `prompt/` | Prompt 模板目录，例如系统问答、RAG 知识库问答、上下文格式化模板等。 |
| `sql/` | 数据库初始化或迁移 SQL 脚本。 |

### 3.9 `bootstrap/src/test/`

| 文件夹 | 作用 |
|---|---|
| `bootstrap/src/test/java/` | 后端测试代码根目录。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/index/` | 索引相关测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/knowledge/` | 知识库模块测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/knowledge/schedule/` | 知识库定时任务测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/` | RAG 模块测试聚合目录。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/` | RAG core 层测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/vector/` | 向量能力测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/embedding/` | Embedding 相关测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/eval/` | RAG 评测相关测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/Intent/` | 意图识别相关测试。注意当前目录名首字母大写。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/rewrite/` | Query rewrite 相关测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/service/` | 服务层通用或跨模块测试。 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/vector/` | 向量检索或向量存储相关测试。 |
| `bootstrap/src/test/resources/` | 测试资源目录。 |
| `bootstrap/src/test/resources/mockito-extensions/` | Mockito 测试扩展配置。 |

## 4. `framework/` 通用基础框架模块

| 文件夹 | 作用 |
|---|---|
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/cache/` | 缓存相关基础能力。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/config/` | 通用框架配置类。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/context/` | 用户上下文、请求上下文等线程上下文能力。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/convention/` | 通用约定对象，例如统一返回、ChatMessage、ChatRequest、RetrievedChunk 等跨模块模型。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/database/` | 数据库通用配置或基础能力。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/distributedid/` | 分布式 ID 生成能力。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/errorcode/` | 错误码定义。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/exception/` | 通用异常体系。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/exception/kb/` | 知识库相关异常类型。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/idempotent/` | 幂等控制能力，例如重复提交拦截。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/mq/` | MQ 基础抽象或通用配置。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/mq/producer/` | MQ 生产者封装。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/` | RAG Trace 通用注解、上下文或节点模型。 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/web/` | Web 层通用工具，例如统一响应、SSE 发送封装等。 |
| `framework/src/main/resources/lua/` | framework 模块自带 Lua 脚本资源。 |

## 5. `infra-ai/` AI 基础设施模块

| 文件夹 | 作用 |
|---|---|
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/` | LLM 聊天接口、流式回调、取消句柄、具体模型调用封装。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/config/` | AI 模型相关配置，例如模型供应商、API Key、超时、流式参数等。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/` | Embedding 向量生成能力。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/enums/` | AI 基础设施枚举，例如模型类型、供应商类型等。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/http/` | HTTP 客户端封装，用于调用模型 API 或外部 AI 服务。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/` | AI 模型请求/响应对象或模型描述对象。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/rerank/` | Rerank 重排序模型能力，用于检索结果重排。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/token/` | Token 估算、统计或截断相关工具。 |
| `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/util/` | AI 基础设施工具类。 |

## 6. `mcp-server/` MCP 服务模块

> 根 `pom.xml` 中 `mcp-server` 模块当前被注释，说明它存在源码，但暂不参与主 Maven 构建。

| 文件夹 | 作用 |
|---|---|
| `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/` | MCP Server Java 代码根包。 |
| `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/config/` | MCP Server 配置类。 |
| `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/` | MCP 工具执行器，负责具体工具调用逻辑。 |
| `mcp-server/src/main/resources/` | MCP Server 资源配置目录。 |

## 7. `frontend/` 前端主项目

| 文件夹 | 作用 |
|---|---|
| `frontend/public/` | Vite/React 静态资源目录，构建时原样复制。 |
| `frontend/src/` | 前端源代码根目录。 |
| `frontend/src/components/` | 通用和业务组件聚合目录。 |
| `frontend/src/components/admin/` | 后台管理相关组件。 |
| `frontend/src/components/chat/` | 聊天界面相关组件，例如消息、输入框、流式展示等。 |
| `frontend/src/components/common/` | 跨页面通用组件。 |
| `frontend/src/components/layout/` | 页面布局组件，例如侧边栏、顶栏、主布局。 |
| `frontend/src/components/session/` | 会话相关组件。 |
| `frontend/src/components/ui/` | 基础 UI 组件库封装。 |
| `frontend/src/hooks/` | React 自定义 hooks。 |
| `frontend/src/lib/` | 前端基础库封装或第三方工具适配。 |
| `frontend/src/pages/` | 页面级组件目录。 |
| `frontend/src/pages/admin/` | 后台管理页面聚合目录。 |
| `frontend/src/pages/admin/dashboard/` | 管理端仪表盘页面。 |
| `frontend/src/pages/admin/ingestion/` | 文档摄入管理页面。 |
| `frontend/src/pages/admin/intent-tree/` | 意图树管理页面。 |
| `frontend/src/pages/admin/knowledge/` | 知识库管理页面。 |
| `frontend/src/pages/admin/query-term-mapping/` | Query 术语映射管理页面。 |
| `frontend/src/pages/admin/sample-questions/` | 样例问题管理页面。 |
| `frontend/src/pages/admin/settings/` | 系统/RAG 配置页面。 |
| `frontend/src/pages/admin/traces/` | RAG Trace 查询与展示页面。 |
| `frontend/src/pages/admin/traces/components/` | Trace 页面内部组件。 |
| `frontend/src/pages/admin/users/` | 用户管理页面。 |
| `frontend/src/services/` | 前端 API 请求封装。 |
| `frontend/src/stores/` | 前端状态管理。 |
| `frontend/src/styles/` | 全局样式和主题样式。 |
| `frontend/src/types/` | TypeScript 类型定义。 |
| `frontend/src/utils/` | 前端工具函数。 |
| `frontend/@/components/` | 可能是别名路径或生成组件目录，用于兼容 `@/components` 引用。 |
| `frontend/@/components/ui/` | `@/components/ui` 基础 UI 组件。 |
| `frontend/node_modules/` | 前端依赖安装目录，不属于项目源码。 |

## 8. `docs/` 文档目录

| 文件夹 | 作用 |
|---|---|
| `docs/assets/` | 文档引用的图片、图表或附件。 |
| `docs/examples/` | 文档示例内容。 |
| `docs/�ܹ��ĵ�/` | 目录名疑似中文编码异常，可能原意为“架构文档”。建议后续确认并重命名。 |

当前已知文档包括：

- `docs/rag-chat-flow.md`：RAG Chat 全流程说明。
- `docs/athena-note-upload-to-rag-flow.md`：笔记上传到 RAG 的链路说明。
- `docs/athena-rag-folder-responsibilities.md`：本文档。

## 9. `resources/` 项目资源目录

| 文件夹 | 作用 |
|---|---|
| `resources/database/` | 数据库相关资源，例如初始化脚本、备份或示例数据库文件。 |
| `resources/database/backups/` | 数据库备份文件。 |
| `resources/docker/` | Docker 部署资源。 |
| `resources/docker/lightweight/` | 轻量化 Docker 部署配置。 |
| `resources/docs/` | 示例知识文档或演示文档根目录。 |
| `resources/docs/knowledge/` | 知识库示例文档。 |
| `resources/docs/knowledge/biz/` | 业务类知识文档。 |
| `resources/docs/knowledge/biz/biz-ins/` | 保险业务相关示例知识文档。 |
| `resources/docs/knowledge/biz/biz-oa/` | OA 业务相关示例知识文档。 |
| `resources/docs/knowledge/group/` | 集团内部知识文档。 |
| `resources/docs/knowledge/group/group-finance/` | 财务相关集团知识文档。 |
| `resources/docs/knowledge/group/hr/` | HR 相关集团知识文档。 |
| `resources/docs/knowledge/group/it/` | IT 相关集团知识文档。 |
| `resources/eval/` | 评测输入、评测配置或评测数据。 |
| `resources/eval/outputs/` | 评测输出结果。 |
| `resources/format/` | 格式模板或格式化示例资源。 |
| `resources/nageoffer-nginx-2.0.1/` | Nginx 分发包和前端静态发布目录，用于本地或演示部署。 |

## 10. 建议忽略或谨慎修改的目录

| 文件夹 | 原因 |
|---|---|
| `frontend/node_modules/` | 依赖安装目录，不应手工维护。 |
| `frontend-backup-20260523-133100/` | 备份目录，除非回滚或对比，不应作为主开发目录。 |
| `.idea/` | IDE 配置，团队协作时应谨慎修改。 |
| `.cursor/` / `.claude/` | AI/IDE 辅助配置，修改前应确认是否影响个人或团队开发流程。 |
| `resources/nageoffer-nginx-2.0.1/__MACOSX/` | macOS 压缩包产生的元数据目录，通常无业务价值。 |
| `target/` | Maven 构建产物目录，本文已跳过，不应纳入源码职责分析。 |

## 11. 一句话总结

`athena-rag` 是一个多模块 RAG 应用：`bootstrap` 承载后端业务，`framework` 提供通用基础设施，`infra-ai` 封装模型能力，`frontend` 提供交互界面，`resources` 提供部署/数据/样例资源，`docs` 承载说明文档；其中 RAG Chat、知识库、文档摄入、用户与后台管理是当前主业务核心。
