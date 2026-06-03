# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Athena 是一个基于 Spring Boot 3 和 Spring Cloud 的微服务架构后端系统，包含多个业务模块和一个独立的 RAG 智能体子项目（athena-rag）。

## 核心架构

### 主项目结构

这是一个 Maven 多模块项目，采用微服务架构：

- **athena-framework**: 基础框架层，提供通用能力
  - `athena-framework-basic`: 基础工具类、统一响应、常量定义
  - `athena-framework-filter`: 过滤器和拦截器
  - `athena-framework-mq`: 消息队列封装（RocketMQ）

- **业务服务模块**（每个模块分为 -api 和 -biz 两层）:
  - `athena-userauth`: 用户认证与授权服务
  - `athena-ground`: 基础服务（具体业务待确认）
  - `athena-record`: 记录管理服务
  - `athena-relation`: 关系管理服务
  - `athena-comment`: 评论服务
  - `athena-insight`: 洞察分析服务
  - `athena-oss`: 对象存储服务

- **athena-gateway**: API 网关（基于 Spring Cloud Gateway）

- **athena-rag**: 独立的 RAG 智能体项目（详见下方）

### athena-rag 子项目（Ragent AI）

这是一个企业级 RAG（检索增强生成）智能体平台，包含两个独立系统：

1. **RAG 问答系统**：文档检索与智能问答
2. **医疗预分诊系统**：多轮对话收集症状并生成分诊报告

#### 模块结构

- `framework`: 基础设施层（异常处理、幂等、分布式ID、SSE封装、限流等）
- `infra-ai`: AI 基础设施层（模型路由、向量数据库、Embedding 等）
- `bootstrap`: 业务应用层
  - `rag`: RAG 问答相关代码
  - `triage`: 医疗预分诊系统
  - `ingestion`: 文档入库流水线
  - `knowledge`: 知识库管理
  - `admin`: 管理后台 API
  - `user`: 用户管理

#### 关键特性

- **多路检索引擎**: 意图定向检索 + 全局向量检索并行
- **医疗预分诊**: 基于多 Agent 架构的对话式症状收集与分诊
- **模型路由与容错**: 多模型优先级调度、健康检查、自动降级
- **文档入库 Pipeline**: 节点编排式 ETL 流程
- **队列式并发限流**: 基于 Redis 的分布式排队限流
- **全链路追踪**: 每个环节都有 Trace 记录

## 技术栈

### 主项目

- **Java 17** + **Spring Boot 3.0.2**
- **Spring Cloud 2022.0.0** + **Spring Cloud Alibaba 2022.0.0.0**
- **Nacos**: 服务发现与配置中心
- **MySQL 8.0**: 关系数据库
- **Redis**: 缓存与会话
- **MyBatis Plus 3.5.7**: ORM 框架
- **Druid**: 数据库连接池
- **Sa-Token 1.38.0**: 认证鉴权
- **RocketMQ 2.2.3**: 消息队列
- **Hutool 5.7.17**: 工具库

### athena-rag 子项目

- **Java 17** + **Spring Boot 3.5.7**
- **Milvus 2.6**: 向量数据库
- **Apache Tika 3.2**: 文档解析
- **Redisson 4.0**: Redis 分布式客户端
- **Sa-Token 1.43.0**: 认证鉴权
- **Transmittable Thread Local 2.14.5**: 跨线程上下文传递
- **React 18** + **TypeScript**: 前端（frontend 目录）

## 构建与运行

### 主项目构建

```bash
# 完整构建
mvn clean install

# 跳过测试构建
mvn clean install -DskipTests

# 构建特定模块
mvn clean install -pl athena-gateway -am
```

### athena-rag 子项目构建

```bash
cd athena-rag

# 完整构建
mvn clean install

# 运行测试
mvn test

# 运行分诊评测（烟雾测试 - 前10个用例）
mvn test -Dtest=TriageEvalRunnerTests#runSmokeCases -pl bootstrap

# 并行运行分诊评测（效率优化）
# Group A: 用例 001-005
mvn test -Dtest=TriageEvalRunnerTests#runGroupA -pl bootstrap

# Group B: 用例 006-010
mvn test -Dtest=TriageEvalRunnerTests#runGroupB -pl bootstrap

# 运行完整评测（全部100个用例）
mvn test -Dtest=TriageEvalRunnerTests#runAllCases -pl bootstrap
```

### 服务启动

项目提供统一的服务管理脚本 `service.sh`：

```bash
# 启动所有服务
./service.sh start

# 启动特定服务（支持模糊匹配）
./service.sh start gateway
./service.sh start rag

# 停止服务
./service.sh stop
./service.sh stop gateway

# 重启服务
./service.sh restart
./service.sh restart rag

# 查看服务状态
./service.sh status
./service.sh status insight

# 查看日志
./service.sh logs
./service.sh logs rag
```

### 环境变量配置

```bash
# 设置 Spring Profile
SPRING_PROFILE=dev ./service.sh start

# 设置 JVM 参数
JAVA_OPTS="-Xms512m -Xmx1024m" ./service.sh start
```

## 分诊系统架构（athena-rag/triage）

### 核心组件

- **TriageController**: 分诊入口 (`POST /triage/analyze`)
- **TriageOrchestratorService**: 分诊编排服务，协调整个流程
- **TriageStateMachine**: 状态机引擎，管理对话状态转换
- **TurnUnderstandingExecutionEngine**: 轮次理解引擎，解析用户输入

### 多 Agent 架构

```
Normalization Agent (输入规范化)
  ↓
Parallel: Risk Agent / Rule Agent / Slot Agent (并行分析)
  ↓
Context Reducer (上下文精简)
  ↓
Question Planner (问题规划)
  ↓
Response Agent (响应生成)
```

### 分诊评测框架

位于 `src/test/java/com/nageoffer/ai/ragent/triage/eval/`:

- **TriageCaseLoader**: 从 Markdown 加载 100 个测试用例（20 个医疗系统 × 5 个用例）
- **TriageInvoker**: 模拟多轮对话调用分诊系统
- **TriageLLMJudge**: 使用 LLM 对 6 个维度打分（风险等级、建议科室、主诉提炼等）
- **TriageReportWriter**: 生成 JSON 和文本格式报告
- **TriageEvalRunnerTests**: 测试运行器

评分维度（总分 100 分）：
- 风险等级（20 分）
- 建议科室（15 分）
- 主诉提炼（15 分）
- 症状提取（20 分）
- 风险分析（15 分）
- 行动建议（15 分）

## 代码规范

### 包命名规范

主项目使用 `athena.*` 包名，athena-rag 使用 `com.nageoffer.ai.ragent.*`。

### 模块分层规范

业务模块采用 API-BIZ 分层：
- `-api`: 对外暴露的接口、DTO、常量
- `-biz`: 业务实现、Controller、Service、Mapper

### 设计模式应用

athena-rag 中大量使用设计模式：
- **策略模式**: SearchChannel、PostProcessor、MCPToolExecutor
- **工厂模式**: IntentTreeFactory、StreamCallbackFactory
- **注册表模式**: MCPToolRegistry、IntentNodeRegistry
- **模板方法**: IngestionNode 基类
- **装饰器模式**: ProbeBufferingCallback
- **责任链模式**: 后处理器链、模型降级链
- **观察者模式**: StreamCallback

### 并发编程

athena-rag 使用 8 个专用线程池（MCP 调用、RAG 上下文、多路检索等），所有线程池都通过 `TtlExecutors` 包装以支持上下文传递。

## 重要配置文件位置

- **服务启动脚本**: `./service.sh`
- **主 POM**: `./pom.xml`
- **athena-rag POM**: `./athena-rag/pom.xml`
- **athena-rag 配置**: `./athena-rag/bootstrap/src/main/resources/application*.yml`
- **网关配置**: `./athena-gateway/src/main/resources/application*.yml`
- **日志目录**: `./logs/`（由 service.sh 创建）

## 开发注意事项

### athena-rag 子项目

1. **Redis 连接必需**: 分诊系统依赖 Redis，测试前确保 Redis 可用
2. **LLM 配置**: 评测需要配置 LLM API 密钥
3. **并行测试**: 使用 `runGroupA` 和 `runGroupB` 可将测试时间从 15 分钟降到 8 分钟
4. **测试用例位置**: `bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/预分诊测试用例/`
5. **评测报告位置**: `./eval-reports/`

### 主项目

1. **Nacos 依赖**: 大多数服务需要 Nacos 运行
2. **数据库初始化**: 首次运行需初始化 MySQL 数据库
3. **网关端口**: athena-gateway 通常作为统一入口
4. **服务发现**: 服务间通过 Nacos 进行服务发现

## 故障排查

### 服务启动失败

1. 检查日志: `./service.sh logs <service-name>`
2. 查看端口占用: `./service.sh status`
3. 确认依赖服务（Nacos、MySQL、Redis）是否运行

### athena-rag 测试失败

1. **Redis 连接失败**: 检查 Redis 是否运行，配置是否正确
2. **LLM 调用失败**: 检查 API 密钥配置，网络连接
3. **测试超时**: 考虑使用并行测试（runGroupA/runGroupB）

## 相关文档

- **athena-rag 详细文档**: `./athena-rag/CLAUDE.md`
- **athena-rag README**: `./athena-rag/README.md`
- **分诊架构文档**: `./athena-rag/docs/架构文档/`
- **RAG 流程文档**: `./athena-rag/docs/rag-chat-flow.md`
- **Athena Note 上传流程**: `./athena-rag/docs/athena-note-upload-to-rag-flow.md`
