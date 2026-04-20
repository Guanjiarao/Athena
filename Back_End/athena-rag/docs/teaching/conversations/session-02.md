# 教学对话纪要：Session 02

## 会话目标

补充第 2 课内容，采用“配置驱动的源码阅读法”，从 `bootstrap/src/main/resources/application.yaml` 反推 `ragent` 的系统边界与工程设计重点。

## 本轮完成内容

### 1. 读取并分析了 `application.yaml`

重点识别出以下能力域：

- 服务对外入口：`server`、`spring.application`
- 基础依赖：PostgreSQL、Redis、RocketMQ、Milvus、S3 兼容存储
- RAG 核心配置：`rag.vector`、`rag.query-rewrite`、`rag.memory`、`rag.search.channels`、`rag.trace`
- AI 抽象配置：`ai.providers`、`ai.chat`、`ai.embedding`、`ai.rerank`、`ai.selection`
- 安全与会话：`sa-token`

### 2. 提炼出的关键工程判断

通过配置可反推出：

1. `ragent` 是独立部署的 HTTP RAG 服务。
2. 当前默认向量后端是 `pgvector`，但系统具备 `Milvus` 切换能力。
3. 问答链路包含查询改写、会话记忆、多通道检索、后处理和 trace。
4. AI 能力不是直连单模型，而是 provider 抽象 + 候选模型路由 + 熔断降级。
5. 文档处理链路存在并发控制与后台调度机制。
6. MCP 工具调用已经是正式能力域。

### 3. 教学上的进一步聚焦

本轮继续强化了后续教学策略：

- 不泛讲概念
- 优先从配置和主链路切入
- 讲清楚每一处设计背后的实战动机
- 让源码阅读有明确验证目标，而不是随机浏览

## 已新增讲义

- `lessons/lesson-03-application-config-overview.md`

## 建议的下一步源码阅读方向

基于本轮配置分析，后续优先验证三件事：

1. 向量存储抽象与 `pg/milvus` 条件切换
2. `infra-ai` 中的统一能力接口与模型路由
3. 问答主链路入口：改写、检索、Prompt、SSE
