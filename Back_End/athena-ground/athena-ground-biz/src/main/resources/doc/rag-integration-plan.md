# RAG 知识库集成说明

> 状态：PR6 / PR7 已联调通过
> 更新时间：2026-04-08

## 一、目标

将 `ragent`（RAG 智能体平台）集成到 `athena` 微服务体系中，实现：
- 知识类笔记发布后自动同步到对应 RAG 知识库
- 用户提问时基于年龄自动路由到对应知识库
- 返回 AI 综合答案与引用文章信息

本轮落地聚焦“先跑通闭环”：
- 已完成新增笔记同步
- 已完成博客问答主链路
- 已完成年龄路由、引用回传、基础日志补齐
- 暂未实现编辑/删除同步、权限隔离增强、流式联调优化等增强能力

## 二、当前实现结论

### 1. 微服务职责划分

| 系统 | 职责 |
|------|------|
| `athena` | 业务主数据、笔记发布、问答入口、结果返回 |
| `ragent` | HTML 解析、分块、Embedding、向量检索、RAG 问答、引用生成 |

结论：
- `ragent` 作为独立微服务部署
- `athena` 的 MySQL 仍然是业务真源
- `ragent` 维护 PostgreSQL / PgVector 或 Milvus 中的知识副本与检索索引

### 2. 数据形态

- `athena` 发送给 `ragent` 的原始内容是 HTML
- `ragent` 内部负责 HTML 解析、文本清洗、分块、索引
- 向量库 metadata 当前至少保存：
  - `noteId`
  - `title`
  - `type`
  - `authorId`
  - `source`
  - `collection_name`
  - `doc_id`
  - `chunk_index`

### 3. 已完成的关键修复

本轮联调过程中已解决以下问题：
- `athena` 与 `ragent` 的统一返回结构不一致，改为 `athena` 手动解析 `ragent` 返回体
- `ragent` 的登录拦截会阻断服务间调用，已将 `/athena/rag/ask` 加入白名单
- 问答链路日志不足，已在 `athena` 与 `ragent` 两侧补充关键日志
- 引用信息不能再从 `chunkId` 推断，改为从向量 metadata 读取 `noteId/title`
- 摄取链路中 metadata 在入库前被丢失，已在 `IndexerNode` 修复回填逻辑
- `Gson JsonObject` 与 `Jackson` 混用导致摄取失败，已改为使用 `Gson` 原生转 `Map`
- Athena 富文本 HTML 不适合直接复用 Tika 结果，已在 parser 策略模式中补充专用 `HTML` 解析器
- `text/html` 曾被误识别为 `TEXT`，已修正 MIME 识别顺序

## 三、业务映射规则

### 1. 笔记 type 到知识库路由

当前约定如下：

| 范围 / 值 | 业务含义 | 目标知识库 |
|-----------|----------|------------|
| `10-29` | 0~12 岁 | `kbchild` |
| `30-49` | 12~22 岁 | `kbteen` |
| `50-69` | 22~55 岁 | `kbadult` |
| `70-89` | 55 岁+ | `kbsenior` |
| `127` | 通用知识 | `kbcommon` |

说明：
- `1`、`2` 类型不进入知识库
- 知识笔记同步时由 `ragent` 根据 type 路由到目标知识库

### 2. 用户问答年龄路由

当前问答路由规则：
- `age < 12` → `kbchild`
- `12 <= age <= 22` → `kbteen`
- `23 <= age <= 55` → `kbadult`
- `age > 55` → `kbsenior`
- 每次都追加 `kbcommon` 作为公共知识库

年龄缺失或非法时：
- 使用兜底年龄 `30`
- 因此默认落到 `kbadult + kbcommon`

## 四、已落地链路

### 1. 问答链路

```text
前端
  │
  └─ POST /athena/blog/ask
            │
            ▼
athena-ground
  │
  ├─ 校验 question / age
  ├─ 调用 ragent: POST /api/ragent/athena/rag/ask
  │
  ▼
ragent
  │
  ├─ 年龄兜底与知识库路由
  ├─ 分知识库检索（目标库 + kbcommon）
  ├─ 去重、排序、截取 TopK
  ├─ 调用 LLM 生成答案
  └─ 基于 metadata 生成 references
  │
  ▼
athena-ground
  │
  ├─ 解析 ragent 返回
  └─ 返回 answer / resolvedAge / kbCodes / references
```

### 2. 笔记同步链路

```text
Athena 发布知识类笔记
  │
  └─ 发送 note-knowledge-sync 消息
            │
            ▼
ragent MQ Consumer
  │
  ├─ 按 type 选择目标 knowledge base
  ├─ HTML -> 文本解析
  ├─ 分块
  ├─ Embedding 向量化
  └─ 写入向量库并保存 metadata
```

## 五、接口说明

### 1. `athena` 对前端接口

#### `POST /athena/blog/ask`

请求体：

```json
{
  "question": "肚子好疼是为什么",
  "age": 15
}
```

`age` 可选；为空时走兜底年龄 `30`。

返回示例：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "answer": "...",
    "resolvedAge": 15,
    "kbCodes": ["kbteen", "kbcommon"],
    "references": [
      {
        "noteId": 366,
        "title": "示例文章标题",
        "snippet": "示例引用片段",
        "score": 0.91
      }
    ]
  },
  "total": null
}
```

### 2. `athena` 调 `ragent` 接口

#### `POST /api/ragent/athena/rag/ask`

说明：
- 该接口用于服务间调用
- 已从 `ragent` 登录拦截中放行
- `athena` 当前通过 `RestTemplate` 调用本地 `9090` 端口

`ragent` 返回结构采用其自身统一格式：
- `code` 为字符串
- 成功值为 `"0"`

## 六、日志说明

### 1. `athena` 侧日志关键字

关键前缀：`[BlogAsk]`

当前会输出：
- 开始调用 `ragent`
- `ragent` HTTP 响应状态与 body 长度
- `ragent` 业务返回 `code/message`
- 最终 `resolvedAge / kbCodes / referenceCount / answerLength`

### 2. `ragent` 侧日志关键字

关键前缀：`[AthenaRagAsk]`

当前会输出：
- 收到问答请求
- 年龄路由结果
- 每个知识库的检索开始/结束
- 总命中 `chunkIds`
- LLM 调用开始
- 最终 `answerLength / referenceCount`

## 七、联调结果

本轮已验证通过：
- Athena 笔记摄取成功
- 向量 metadata 正常写入
- 博客问答接口调用成功
- 年龄路由命中正确知识库
- `references` 可从 metadata 解析返回
- Athena HTML 笔记已能通过专用 HTML parser 转为纯文本
- 4000+ 字的长文已能按预期分为多块

## 八、注意事项

### 1. 旧向量数据

如果是修复前写入的旧向量数据，可能缺失完整 metadata。
出现引用为空时，需要：
- 删除旧向量数据
- 重新同步对应笔记

### 2. 当前 `athena` 到 `ragent` 地址

当前代码中调用地址固定为：
- `http://localhost:9090/api/ragent/athena/rag/ask`

本地联调需保证：
- `ragent` 启动在 `9090`
- context path 为 `/api/ragent`

### 3. 重复同步

当前流程已支持修复后重新同步并写入完整 metadata。
若后续需要彻底避免同一 note 的历史向量残留，可继续补“同步前自动删旧向量”能力。

## 九、架构决策更新（已达成一致）

### 1. 总体方向

在当前 PR6 / PR7 已跑通的基础上，下一阶段不建议继续在 `athena` 内部重复建设聊天、SSE、trace、对话存储等能力；而是将 `ragent` 正式纳入 `athena` 微服务体系，作为智能问答与知识库能力中心。

当前一致意见如下：
- `ragent` 应正式接入 `Nacos + 网关`
- `ragent` 保留其原有的 chat、SSE、trace、会话存储、知识库前端等能力
- `athena` 不再重复建设一套对话系统
- Athena 场景仍需保留业务边界，不建议完全退化为裸调用 `ragent` 通用 chat

### 2. 为什么不建议由 `athena` 继续充当 SSE 中转层

如果继续采用：
- 前端 -> `athena`
- `athena` -> `ragent`

且下一步要支持流式对话，那么 `athena` 必须同时承担：
- SSE 接入端
- SSE 下游调用端
- 长连接转发
- 流式异常处理
- 取消/断流传播

这会让 `athena` 逐步演化为一个“流式中转代理层”，复杂度明显上升，而大量能力本质上只是对 `ragent` 的重复包装。因此，从实现成本与长期维护看，不建议让 `athena` 作为正式的 SSE relay。

### 3. 对话存储归属

关于 conversation / message / trace 等对话相关数据，当前一致意见为：
- 优先复用 `ragent` 现有对话存储模型
- 不在 `athena` 内重复建设会话与消息存储
- 对话历史、流式输出、trace 等智能问答资产，以 `ragent` 为主存储位置

这意味着：
- 如果前端要直接使用流式对话、多轮上下文、trace 等能力，直接对接 `ragent` 更自然
- `athena` 更适合保留业务数据、文章详情、引用落地与知识同步职责

### 4. Athena 知识内容在 `ragent` 中的定位

当前一致意见为：
- Athena note 应逐步升级为 `ragent` 中的标准 document 对象，而不仅仅是内部摄取生成的向量数据
- 一篇 Athena note 应能在 `ragent` 文档视角下被查看、追踪、查看分块结果
- 这样才能真正复用 `ragent` 已有的文档管理、分块调试、知识库前端能力

建议文档 metadata 长期稳定仅保留：
- `noteId`

文章标题、作者、频道等基础展示信息不再作为向量 metadata 的长期主载荷，而是在引用回传阶段基于 `noteId` 再通过 `feign` 或 `mcp` 查询 Athena 业务侧详情。

这样做的原因是：
- 向量 metadata 保持最小化，减少冗余与更新成本
- 文章标题、频道、作者等字段应以 Athena 业务库为准，避免索引副本与主数据漂移
- 前端所需的文章基础展示信息可由 Athena 侧统一组装，例如复用 `BlogListDTO` 这类已有业务 DTO

补充约定：引用筛选与补查规则如下：
- `ragent` 回传的分块引用候选只保留最小字段：`noteId`、`score`，必要时可附带 `snippet`
- 候选结果按 `score` 从高到低排序
- 默认最多选择前 `3` 个候选
- 这 `3` 个候选必须全部大于等于可信度阈值，才一起返回
- 如果前 `3` 个中任意一个低于阈值，则不再硬凑 `3` 个，而是只返回 `score` 最高的第 `1` 个候选
- `athena` 在拿到最终 `noteId` 列表后，再通过 `feign` 或 `mcp` 按 `noteId` 补查文章基础信息
- 最终前端展示所需的标题、封面、频道、作者等字段，由 Athena 业务侧统一组装返回

### 5. 前端入口边界建议

当前一致意见不是“所有入口都改成直连 `ragent`”，而是分层处理：

#### 业务内容入口继续归 `athena`
用于：
- 笔记发布
- 笔记详情
- 文章跳转
- 业务主数据查询
- 知识同步触发

#### 智能问答入口逐步下沉到 `ragent`
用于：
- conversation
- message history
- SSE streaming
- trace
- chat UI / chat API

换句话说：
- `athena` 负责业务内容
- `ragent` 负责智能问答体验

### 6. 为什么不建议“完全前端直打裸 `ragent` 通用 chat”

虽然前端直连 `ragent` 能显著降低 SSE 与对话存储复杂度，但 Athena 场景仍有明确业务约束，因此不建议完全放弃业务边界。

需要继续保留的约束包括：
- 年龄分层路由 / 用户画像路由
- 只允许进入 Athena 指定知识域
- 引用必须能稳定映射回 Athena 文章
- 返回 metadata 中至少稳定包含 `noteId`
- 引用展示所需的标题、作者、频道等信息由 Athena 业务侧按 `noteId` 补查
- 引用候选默认最多取 `3` 个，且必须全部达到可信度阈值；否则仅返回分数最高的 `1` 个

因此更推荐的方向不是“前端直接裸连通用 chat”，而是：
- 将 `ragent` 正式产品化接入 `athena` 微服务体系
- 对前端开放受 Athena 业务约束的 `ragent` 智能问答能力

### 7. 推荐的融合架构

推荐演进后的形态：

```text
前端
  ├─ 业务内容相关 -> athena
  │       ├─ 笔记发布
  │       ├─ 笔记详情
  │       └─ 引用跳转
  │
  └─ 智能问答相关 -> ragent
          ├─ conversation
          ├─ SSE / chat
          ├─ trace
          └─ 知识库前端与调试能力

athena
  ├─ 业务主数据（MySQL）
  ├─ note 发布与同步触发
  └─ 引用文章落地

ragent
  ├─ 标准 document 管理
  ├─ chunk / vector / retrieve
  ├─ stream chat / conversation / trace
  └─ Athena 知识域问答
```

### 8. 下一阶段建议迁移顺序

基于当前一致意见，建议下一阶段按以下顺序推进：

1. 先将 `ragent` 正式接入 `Nacos + 网关`
2. 将 Athena note 从“内部摄取对象”逐步升级为 `ragent` 标准 document
3. 梳理并固化 Athena 问答所需的最小 metadata、引用筛选规则与补查协议
4. 评估并开放面向 Athena 场景的 `ragent` 流式问答入口
5. 最后再决定是否保留 `athena/blog/ask` 作为兼容入口或逐步收缩为业务辅助接口

## 十、身份与网关方案（当前定案）

### 1. 网关层结论

当前一致意见为：
- 保留现有 `athena-gateway` 作为统一入口网关
- 不再为 `ragent` 单独建设新的网关服务
- `ragent` 直接作为被 `athena-gateway` 路由转发的下游服务接入

原因：
- `athena-gateway` 已经具备 `Sa-Token` 登录校验能力
- `athena-gateway` 已具备将当前登录用户 `userId` 透传到下游服务的基础能力
- `ragent` 当前并不存在真正独立的网关层，只有应用端口与 `context-path`
- 因此没有必要再额外引入一层 `ragent gateway`

推荐关系：

```text
前端 -> athena-gateway -> ragent
```

而不是：

```text
前端 -> athena-gateway -> ragent-gateway -> ragent
```

### 2. 用户身份链路定案

当前已确定采用以下最小可行方案：

```text
前端登录 Athena
  -> athena-gateway 做 Sa-Token 校验
  -> athena-gateway 透传 userId
  -> 请求进入 ragent
  -> ragent 根据 userId 查找本地用户
  -> 若不存在则自动创建 role=user 的本地用户
  -> ragent 使用该本地用户承接 conversation / message / trace
```

该方案的核心原则：
- Athena 是主登录入口与身份真源
- `athena-gateway` 负责统一鉴权与 `userId` 透传
- `ragent` 不再要求普通用户显式登录其自身系统
- `ragent` 本地用户主要用于承接会话、消息、trace、权限边界，不作为独立对外账号体系

### 3. 为什么采用该方案

当前阶段不希望引入过重的统一身份改造，因此选择最小改造路径：
- 不重做一套 SSO / IAM
- 不让 `athena` 自己实现 conversation / SSE / trace
- 不给普通用户暴露 `ragent` 独立登录流程
- 保持前端认知简单：用户只需要登录 Athena

该方案能够同时满足：
- 复用 `ragent` 已有对话体系
- 复用 `athena-gateway` 已有 `Sa-Token` 校验能力
- 将改造范围控制在“网关透传 + 本地用户映射”层面

### 4. ragent 本地用户的用途

当前一致意见为：
- `ragent` 保留 `t_user` 表
- 该表继续作为 `ragent` 本地用户、会话归属、trace 归属、角色控制的载体
- 普通 Athena 用户首次进入 `ragent`` 时，若本地不存在映射用户，则自动补建
- 自动补建用户默认角色固定为 `user`

其用途主要是：
- 标识同一个用户的 conversation 归属
- 标识 message / trace / session 的归属关系
- 为后续普通用户与管理员能力隔离提供基础角色信息

### 5. 权限边界

当前权限边界约定如下：

#### 普通用户
- 身份来源：Athena 登录用户
- 进入方式：前端通过 `athena-gateway` 访问 `ragent`
- `ragent` 本地角色：`user`
- 可使用能力：问答、conversation、message history、SSE、trace（若前端开放）
- 不可使用能力：后台管理、用户管理、知识库管理、模型设置等管理域功能

#### 管理员
- 由内部人员单独维护 `ragent` 的 `admin` 账号
- 管理后台能力只开放给 `admin`
- 普通 Athena 用户不会自动升级为 `admin`

### 6. 实现约束

为保证第一阶段方案简单且不失控，当前约定以下约束：
- 自动创建逻辑只创建 `role=user`
- 普通用户不走 `ragent` 原生登录页
- 管理后台与普通问答域要继续隔离
- `ragent` 普通用户侧能力依赖网关透传 `userId`
- conversation / message / trace 的归属以 `ragent` 本地映射用户为准

### 7. 下一步实施建议

围绕该方案，下一步建议按以下顺序推进：
1. 在 `athena-gateway` 中正式增加到 `ragent` 的路由配置
2. 统一并固化 `userId` 透传请求头约定
3. 在 `ragent` 中新增基于 `userId` 的本地用户查找 / 自动创建逻辑
4. 将普通问答链路改为从透传 `userId` 构造用户上下文
5. 将后台管理链路与普通问答链路的认证方式明确分开

### 8. 明日开发 TODO（细化版）

以下任务按推荐顺序排列，可作为下一阶段的直接执行清单。

#### TODO 1：将 `ragent` 正式接入 `Nacos + athena-gateway`

目标：让 `ragent` 从本地直连服务切换为正式微服务。

子任务：
- 梳理 `ragent` 当前启动配置，确认服务名、端口、`context-path`
- 在 `ragent` 中补齐 `Nacos` 注册发现所需依赖与配置
- 明确 `ragent` 在注册中心中的正式服务名
- 在 `athena-gateway` 中新增到 `ragent` 的路由规则
- 明确外部访问路径前缀，建议统一归入 `ragent` 专属前缀
- 验证通过网关访问 `ragent` 普通接口是否成功
- 清理 `athena` 侧现有写死的 `localhost:9090` 直连依赖，改为走网关或服务发现

完成标准：
- `ragent` 能成功注册到注册中心
- 通过 `athena-gateway` 可以访问 `ragent` 接口
- 本地联调不再依赖固定写死地址

#### TODO 2：固化 `userId` 透传协议

目标：确保普通用户身份在网关到 `ragent` 的链路中稳定传递。

子任务：
- 确认网关当前透传的请求头名称
- 统一普通用户态接口仅认这一套 `userId` 透传头
- 明确该请求头仅允许由 `athena-gateway` 注入
- 检查前端直连网关时是否总是携带 `Authorization`
- 验证普通请求与后续 SSE 请求都能经过该透传逻辑
- 在文档中固定透传字段含义，避免后续命名漂移

完成标准：
- 已登录请求进入 `ragent` 时都能稳定拿到 `userId`
- 普通接口与流式接口在透传规则上保持一致

#### TODO 3：在 `ragent` 中实现本地用户查找 / 自动创建

目标：建立 Athena 用户与 `ragent.t_user` 的本地映射。

子任务：
- 梳理 `ragent` 当前 `t_user` 表和用户服务代码
- 确定按透传 `userId` 查找本地映射用户的实现方式
- 若本地不存在映射用户，则自动创建 `role=user` 的用户记录
- 明确自动创建用户的用户名生成规则
- 明确自动创建用户的密码填充值规则
- 保证同一个 Athena 用户不会重复创建多条本地用户记录
- 明确 `admin` 用户不进入自动创建流程

完成标准：
- 首次访问 `ragent` 的 Athena 用户会自动补建本地用户
- 再次访问时可稳定复用同一条本地用户记录
- 自动创建用户角色恒定为 `user`

#### TODO 4：改造 `ragent` 普通问答链路的用户上下文构建

目标：让普通问答链路依赖网关透传身份，而不是依赖 `ragent` 本地显式登录。

子任务：
- 梳理当前 `Sa-Token` 校验与 `UserContext` 构建逻辑
- 为普通问答域接口增加“从透传 `userId` 构建上下文”的处理逻辑
- 将透传 `userId` 映射到本地 `ragent user`
- 将映射后的用户信息注入 `UserContext`
- 确认 conversation / message / trace 读取的仍是本地用户 ID
- 校验异步场景下的上下文清理和线程安全

完成标准：
- 普通用户不登录 `ragent` 也能被正确识别
- conversation / message / trace 能稳定归属到正确本地用户

#### TODO 5：明确后台管理域与普通问答域的认证边界

目标：防止普通透传用户碰到管理后台能力。

子任务：
- 梳理 `ragent` 当前所有用户态接口与管理态接口
- 标记哪些接口属于普通问答域
- 标记哪些接口属于后台管理域
- 普通问答域保留网关透传身份模式
- 后台管理域继续保留 `admin` 校验逻辑
- 检查用户管理、知识库管理、模型设置、系统配置等接口是否仍需 `admin`
- 验证普通 Athena 用户即使能访问 `ragent`，也无法进入后台管理能力

完成标准：
- 普通用户只能使用问答相关能力
- 管理域接口仍然只对 `admin` 开放

#### TODO 6：打通最小可用的 `conversation / SSE` 链路

目标：验证通过 `athena-gateway` 访问 `ragent` 时，流式问答可以正常工作。

子任务：
- 选定一个最小可用的 chat / stream 接口做联调入口
- 验证前端流式请求经过 gateway 时能够携带登录态
- 验证 gateway 不会阻断或提前关闭 SSE 长连接
- 验证 `userId` 透传在 SSE 请求中仍然有效
- 验证同一用户发起多次流式请求时，会话能落到自己的 conversation 下
- 观察是否存在超时、中断、断流或首包异常问题
- 如有必要，再针对 gateway 和下游服务补充超时配置

完成标准：
- 通过 gateway 的 SSE 请求可正常建立和持续输出
- 同一用户的 conversation 能正常积累历史记录

#### TODO 7：改造引用链路为“最小 metadata + 业务侧补查”

目标：让 `ragent` 只负责返回可信 `noteId`，由 Athena 侧补文章业务信息。

子任务：
- 调整 `ragent` 引用候选输出结构，仅保留 `noteId`、`score`，必要时保留 `snippet`
- 在 `ragent` 内实现引用候选按 `score` 降序排序
- 增加可信度阈值配置
- 默认只看前 `3` 个候选
- 若前 `3` 个全部达到阈值，则返回 `3` 个
- 若前 `3` 个任意一个未达到阈值，则仅返回最高分的 `1` 个
- 在 Athena 侧补充按 `noteId` 查询文章基础信息的能力
- 明确最终前端引用对象的组装位置，优先放在 Athena 业务侧

完成标准：
- `ragent` 不再依赖标题、作者、频道等 metadata 返回最终引用
- Athena 能根据 `noteId` 组装出完整业务引用对象

#### TODO 8：补齐知识同步生命周期，先做“删旧再建”

目标：避免同一篇 note 重复同步后残留旧向量数据。

子任务：
- 梳理当前笔记同步链路中的新增逻辑
- 增加“同步前删除旧向量 / 旧文档数据”的处理步骤
- 明确删除条件应至少覆盖同一 `noteId` 的历史索引数据
- 验证重复同步后不会残留旧 chunk
- 为后续“编辑后重建索引”预留扩展点
- 为后续“删除笔记后清理向量”预留扩展点

完成标准：
- 同一篇笔记重复同步后不会产生历史脏数据残留
- 后续编辑重建与删除清理具备延伸空间

## 十一、后续建议

本轮闭环完成后，建议下一阶段按优先级继续推进：
1. 将 `ragent` 正式接入 Nacos / 网关，去掉本地直连依赖
2. 将 Athena note 升级为 `ragent` 标准 document，对接文档视图与分块调试能力
3. 梳理 Athena 场景下的 conversation / stream / trace 使用边界
4. 以 `athena-gateway` + `userId` 透传 + `ragent` 本地用户映射方式打通正式用户链路
5. 同步前自动删除旧向量，避免重复摄取残留历史数据
6. 支持笔记编辑后的重建索引
7. 支持笔记删除后的向量清理
8. 视前端需求决定是否将正式智能问答入口逐步切到 `ragent`

