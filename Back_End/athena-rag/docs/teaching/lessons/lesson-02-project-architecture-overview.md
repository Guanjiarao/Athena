# 第 2 课：`ragent` 项目架构总览

## 本课目标

本课不再泛讲 RAG 基础，而是回答一个更实战的问题：

> 第一次接手 `ragent` 这样的项目，应该如何建立项目地图？

## 四大模块总览

根据根目录和根 `pom.xml`，`ragent` 当前的核心模块为：

- `bootstrap`
- `framework`
- `infra-ai`
- `mcp-server`

可以先建立如下认知：

- `bootstrap`：RAG 主业务编排层
- `framework`：基础设施层
- `infra-ai`：AI 能力抽象层
- `mcp-server`：工具调用协议层

## 1. `framework`：系统地基

这个模块主要沉淀与具体 RAG 业务无关的公共能力，例如：

- 统一响应
- 异常体系
- Redis / Redisson
- MyBatis Plus
- Sa-Token
- RocketMQ
- 用户上下文透传

学习这个模块的重点，不是逐类背代码，而是看它如何统一基础设施能力，避免业务层重复造轮子。

## 2. `infra-ai`：模型能力抽象层

这个模块的职责是屏蔽不同模型供应商之间的差异，对上层暴露统一接口。根据现有目录，可以预期它会覆盖：

- `chat`
- `embedding`
- `rerank`
- `config`
- `http`
- `token`
- `util`

这意味着业务层不应该直接依赖具体模型供应商，而是通过统一抽象完成：

- Chat 调用
- Embedding 调用
- Rerank 调用
- 模型路由与降级

## 3. `bootstrap`：RAG 业务编排中心

`bootstrap` 同时依赖 `framework` 与 `infra-ai`，并引入：

- Tika
- Milvus
- PostgreSQL / pgvector
- JDBC
- S3
- Validation

说明它是整条业务主链路的汇合点，核心业务预计都在这里编排，包括：

- 知识库管理
- 文档上传与解析
- 文本分块
- Embedding
- 向量存储
- 检索与问答
- 意图识别
- Prompt 组装
- 会话记忆
- Trace

另外，`bootstrap/src/main/resources/prompt/` 下已经存在多份 Prompt 模板，说明 Prompt 也是工程中的正式资产，而不是散落在代码里的字符串常量。

## 4. `mcp-server`：工具协议层

这个模块相对独立，依赖较轻，职责更偏向协议实现与工具调用分发，预计包括：

- MCP 协议接入
- JSON-RPC 处理
- 工具注册
- 工具发现
- 工具调用分发

它和知识库检索并不是上下级关系，而是并列能力域。

## 第一次读项目的推荐顺序

### 第 1 步：看根 `pom.xml`

确认：

- 多模块结构
- 各模块依赖方向
- 哪层是基础层，哪层是应用层

### 第 2 步：看 `bootstrap/application.yaml`

通过配置反推系统边界，例如：

- 模型供应商
- 向量库配置
- S3 存储
- RocketMQ
- 会话记忆
- Prompt 与 Trace 能力

### 第 3 步：看启动入口与对外 API

目标是确认：

- 系统的核心入口在哪里
- 对外暴露了哪些能力
- 管理后台接口与问答接口如何区分

### 第 4 步：先读问答主链路

优先打通这条链：

- 用户提问
- 问题改写
- 意图解析
- 检索
- 后处理
- Prompt 组装
- LLM 生成
- SSE 返回

### 第 5 步：再读文档入库链路

包括：

- 文档上传
- 解析
- 分块
- 向量化
- 写向量库

## 本课总结

这套架构的核心价值，不在于“分模块更优雅”，而在于：

- 业务和模型供应商解耦
- 基础设施和业务编排解耦
- 知识检索与工具调用解耦
- 后续演进空间更大

## 课后建议

后续正式进入源码时，优先从 `bootstrap/application.yaml` 开始，利用配置文件快速反推整个系统的能力边界与主链路结构。
