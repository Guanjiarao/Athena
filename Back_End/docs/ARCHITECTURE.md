# Athena 后端系统架构文档

> 版本：v1.2  
> 更新时间：2026-06-02

---

## 1. 系统概述

Athena 是一个基于 Spring Boot 3 的后端系统，采用微服务架构来打造女性的专属社区。

---

## 2. 应用核心功能

主项目包含以下服务模块：

1. **athena-userauth** - 用户认证与授权
2. **athena-ground** - 笔记服务
3. **athena-record** - 记录管理
4. **athena-relation** - 关系管理
5. **athena-comment** - 评论服务
6. **athena-insight** - 数据洞察
7. **athena-oss** - 对象存储服务
8. **athena-gateway** - API 网关
9. **athena-framework** - 框架层（提供通用能力）
10. **athena-rag** - rag服务

---

## 3. 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 编程语言 | Java | 17 |
| 核心框架 | Spring Boot | 3.0.2 |
| 微服务框架 | Spring Cloud | 2022.0.0 |
| 微服务框架 | Spring Cloud Alibaba | 2022.0.0.0 |
| 服务注册与发现 | Nacos | 2.2.3 |
| API 网关 | Spring Cloud Gateway | 4.0.0 |
| 数据库 | MySQL | 8.0.32 |
| ORM 框架 | MyBatis Plus | 3.5.7 |
| 数据库连接池 | Druid | 1.2.23 |
| 缓存 | Redis | - |
| 认证鉴权 | Sa-Token | 1.38.0 |
| 消息队列 | RocketMQ | 2.2.3 |
| 工具库 | Hutool | 5.7.17 |
| 跨线程传递 | Transmittable Thread Local | 2.14.2 |

## 4. 项目结构

### 4.1 主项目模块结构

```
Back_End/
├── athena-framework/          # 框架层
│   ├── athena-framework-basic    # 基础工具
│   ├── athena-framework-filter   # 过滤器
│   └── athena-framework-mq       # 消息队列封装
├── athena-gateway/            # API 网关
├── athena-userauth/           # 用户认证授权
├── athena-ground/             # 基础服务
├── athena-record/             # 记录管理
├── athena-relation/           # 关系管理
├── athena-comment/            # 评论服务
├── athena-insight/            # 数据洞察
├── athena-oss/                # 对象存储
└── athena-rag/                # RAG 智能体
```

### 4.2 athena-rag 模块结构

```
athena-rag/
├── framework/                 # 基础设施层
├── infra-ai/                  # AI 基础设施层
├── bootstrap/                 # 业务应用层
│   ├── rag/                     # RAG 问答
│   ├── ingestion/               # 文档入库
│   ├── knowledge/               # 知识库管理
│   ├── admin/                   # 管理后台
│   └── user/                    # 用户管理
└── frontend/                  # React 前端
```

---

## 5. 微服务架构

### 5.1 架构设计

Athena 采用基于 Spring Cloud 的微服务架构，各服务独立部署、独立扩展。

**架构图：**

![](attachments\Athena微服务架构图-简化版.png)

### 5.2 Spring Cloud Gateway 统一网关

#### 5.2.1 核心职责

**1. 统一路由转发**

所有外部请求通过网关统一入口，网关根据路径规则转发到后端服务：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: ground
          uri: lb://athena-ground
          predicates:
            - Path=/athena/blog/**,/athena/admin/**
        - id: relation
          uri: lb://athena-relation
          predicates:
            - Path=/athena/relation/**
        - id: comment
          uri: lb://athena-comment
          predicates:
            - Path=/athena/comment/**
        - id: oss
          uri: lb://athena-oss
          predicates:
            - Path=/athena/file/**
        - id: record
          uri: lb://athena-record
          predicates:
            - Path=/athena/record/**,/athena/menstruation/**
        - id: userauth
          uri: lb://athena-userauth
          predicates:
            - Path=/athena/login/**,/athena/user/**

      discovery:
        locator:
          enabled: true
          lower-case-service-id: true

```

**路由规则说明：**
- `lb://service-name`：通过 Nacos 服务发现，自动负载均衡
- `predicates`：路径匹配规则，支持多个路径

**2. Sa-Token 统一认证**

网关层统一进行身份验证，验证通过后将 userId 注入请求头传递给后端服务。

```java
@Configuration
public class SaTokenConfigure {
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")              // 拦截所有路径
            .setAuth(obj -> {
                SaRouter.match("/**")
                    .notMatch("/athena/login/**")  // 排除登录接口
                    .check(r -> StpUtil.checkLogin()); // 验证登录状态
            })
            .setError(e -> Result.fail(e.getMessage()));
    }
}
```

**认证流程：**
```
1. 前端请求携带 Token（Header: Authorization: Bearer <token>）
2. 网关验证 Token 有效性（查询 Redis）
3. 验证成功 → 提取 userId → 注入到请求头（Header: userId）
4. 转发请求到后端服务
5. 后端服务直接从请求头获取 userId，无需再次验证
```

**关键代码（AddUserId2HeaderFilter）：**
```java
@Component
@Order(-90)
public class AddUserId2HeaderFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 从请求头获取 Token
        String token = getTokenFromHeader(exchange);
        
        // 2. 从 Redis 查询 userId
        String userId = stringRedisTemplate.opsForValue()
            .get("sa:token:token:" + token);
        
        // 3. 将 userId 注入到新的请求头
        ServerWebExchange newExchange = exchange.mutate()
            .request(builder -> builder.header("userId", userId))
            .build();
        
        return chain.filter(newExchange);
    }
}
```

**白名单配置：**
- `/athena/login/**`：登录接口，无需验证
- 其他所有接口均需携带有效 Token

### 5.3 Nacos 服务注册与配置管理

#### 5.3.1 服务注册与发现

**服务注册：**

- 每个微服务启动时自动注册到 Nacos
- 注册信息：服务名、IP、端口、健康状态
- 心跳上报：定期向 Nacos 发送心跳，保持在线状态

**服务发现：**

- Gateway 通过 `lb://service-name` 动态获取服务实例列表
- 实时感知：服务上下线自动推送更新

#### 5.3.2 配置中心

**配置分层管理：**

![nacos](attachments/nacos配置列表.png)

```yaml
如：gateway的开发环境
# bootstrap.yml
spring:
  cloud:
    nacos:
      config:
        server-addr: 121.41.200.73:8848
        namespace: 71e163a1-9eae-4a2f-9c57-491089e5cc5d  # 环境隔离
        file-extension: yaml
        shared-configs:           # 共享配置
          - data-id: base.yaml       # 基础配置
            refresh: true
          - data-id: redis.yaml      # Redis 配置
            refresh: true
          - data-id: sa-token.yaml   # 认证配置
            refresh: true
```

**配置层级：**
```
共享配置（base.yaml, redis.yaml 等）
    ↓
服务配置（athena-userauth.yaml）
    ↓
环境配置（通过 namespace 区分）
    ↓
本地配置（application.yml）
```

**环境隔离：**
- **开发环境（dev）**：namespace = `71e163a1-9eae-4a2f-9c57-491089e5cc5d`
- **生产环境（prod）**：使用不同的 namespace
- 通过环境变量切换：`SPRING_PROFILES_ACTIVE=dev/prod`

**动态配置刷新：**

- 配置变更后自动推送到服务实例
- 无需重启服务即可生效
- `refresh: true` 开启动态刷新

### 5.4 OpenFeign 服务间调用

#### 5.4.1 Feign 客户端定义

服务间通信使用 OpenFeign 进行 RPC 远程调用。

**示例：athena-comment 调用 athena-userauth**

**1. API 层定义接口（athena-userauth-api）：**

```java
@FeignClient(name = "athena-userauth")  // 服务名
public interface UserFeignApi {
    String PREFIX = "/athena/user";
    
    @GetMapping(value = PREFIX + "/findById")
    Result<UserDTO> findById(@RequestParam Long id);
    
    @GetMapping(value = PREFIX + "/findByUserIds")
    Result<List<UserDTO>> findByUserIds(@RequestParam List<Long> userIds);
}
```

**2. 业务层调用（athena-comment-biz）：**
```java
@Component
public class UserAuthFeignApi {
    @Resource
    private UserFeignApi userFeignApi;  // 注入 Feign 客户端
    
    public UserDTO findByUserId(Long userId) {
        Result<UserDTO> result = userFeignApi.findById(userId);
        if (result == null || result.getCode() != 200) {
            return null;
        }
        return result.getData();
    }
}
```

**调用流程：**
```
athena-comment
    ↓
调用 UserFeignApi.findById(userId)
    ↓
Feign 客户端发起 HTTP 请求
    ↓
Nacos 解析服务地址（athena-userauth）
    ↓
负载均衡选择实例
    ↓
发送 GET /athena/user/findById?id=123
    ↓
athena-userauth 处理请求并返回
```

#### 5.4.2 Feign 请求拦截器

**上下文传递：**

网关层注入的 `userId` 需要在服务间调用时继续传递。

```java
@Component
public class FeignRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        // 从当前线程上下文获取 userId
        String userId = UserContext.getUserId();
        
        // 添加到 Feign 请求头
        if (userId != null) {
            template.header("userId", userId);
        }
    }
}
```

### 5.5 Sa-Token 统一认证鉴权

#### 5.5.1 认证流程

**登录流程：**
```
1. 用户提交用户名密码
2. athena-userauth 验证通过
3. 生成 Token：StpUtil.login(userId)
4. Token 存入 Redis：
   Key: sa:token:token:<token>
   Value: <userId>
   TTL: 7天
5. 返回 Token 给前端
```

**访问流程：**
```
1. 前端请求携带 Token（Header: Authorization: Bearer <token>）
2. Gateway 拦截请求
3. Sa-Token 验证：StpUtil.checkLogin()
4. 从 Redis 查询 userId
5. 注入 userId 到请求头
6. 转发到后端服务
```

#### 5.5.2 Token 管理

**Token 存储：**
- 存储位置：Redis
- Key 格式：`sa:token:token:<token>`
- Value：userId
- 过期时间：7 天（可配置）

**优势：**
- 无状态：Token 本身不携带用户信息，只是一个 key
- 可撤销：删除 Redis 中的 Token 即可强制用户下线
- 跨服务共享：所有服务共用同一个 Redis，Token 全局有效

---

## 6. 各服务详细功能与职责

### 6.1 athena-userauth（用户认证与授权）

**核心职责：** 用户身份管理与认证

**主要功能：**
- 用户登录（手机号 + 验证码）
- 发送验证码
- 登录状态查询
- 用户信息查询（根据 ID、手机号等）
- 用户信息更新

**对外接口（UserFeignApi）：**
```java
@FeignClient(name = "athena-userauth")
public interface UserFeignApi {
    Result<UserDTO> findById(@RequestParam Long id);
    Result<List<UserDTO>> findByUserIds(@RequestParam List<Long> userIds);
}
```

**被依赖情况：** 几乎所有服务都需要调用 userauth 获取用户信息

---

### 6.2 athena-ground（广场/内容管理）

**核心职责：** 用户发布的笔记内容管理与展示

**主要功能：**
- **笔记发布**：用户提交笔记内容（`submitNote`）
- **笔记审核**：管理员审核待发布笔记（通过/拒绝）
- **广场列表**：分页查询已审核通过的笔记列表
- **笔记详情**：查询笔记完整内容
- **浏览记录**：记录用户浏览历史
- **笔记搜索**：搜索笔记内容

**关键路径：**
- `/athena/blog/list` - 广场笔记列表
- `/athena/blog/Detail` - 笔记详情
- `/athena/admin/blog/review/*` - 管理员审核接口

**说明：** ground 是内容广场，用户发布的健康笔记、经验分享等都在这里展示和管理。

---

### 6.3 athena-record（个人健康记录）

**核心职责：** 用户的私密健康数据记录

**主要功能：**

**1. 经期管理（MenstruationCycleController）：**
- 新增经期周期记录
- 编辑经期记录
- 删除经期记录
- 查询经期历史
- 经期预测
- 经期统计分析
- 月视图展示

**2. 日常记录（DailyRecordController）：**
- 创建日常健康记录
- 更新健康记录
- 删除记录
- 查询某日详情
- 查询月度标记（哪些日期有记录）

**关键路径：**
- `/athena/menstruation/*` - 经期管理
- `/athena/record/*` - 日常记录

**说明：** record 是私密的个人健康数据，与 ground 的公开笔记不同。

---

### 6.4 athena-comment（评论服务）

**核心职责：** 笔记的评论与互动

**主要功能：**
- 发布评论（支持一级评论、二级回复）
- 评论分页列表
- 展开二级评论
- 评论点赞
- 查询点赞状态

**关键路径：**
- `/athena/comment/publish` - 发布评论
- `/athena/comment/listPage` - 评论列表
- `/athena/comment/commentLike` - 点赞评论

**依赖服务：**
- athena-userauth：获取评论用户信息
- athena-ground：验证笔记是否存在

---

### 6.5 athena-relation（用户关系管理）

**核心职责：** 用户之间的社交关系

**主要功能：**
- 关注用户
- 取消关注
- 查询是否已关注
- 关注列表查询
- 粉丝列表查询
- 关注数/粉丝数统计

**关键路径：**
- `/athena/relation/follow` - 关注
- `/athena/relation/unfollow` - 取消关注
- `/athena/relation/followList` - 我的关注
- `/athena/relation/fanList` - 我的粉丝

---

### 6.6 athena-oss（对象存储）

**核心职责：** 文件上传与管理

**主要功能：**
- 文件上传（图片、视频等）
- 返回文件访问 URL

**关键路径：**
- `/athena/file/upload` - 文件上传

---

### 6.7 athena-insight（数据洞察分析）

**核心职责：** 用户行为分析与个性化推荐

**主要功能：**

**1. 个性化推荐：**

- 推荐笔记列表

2**. 数据报告：**

- 生成用户健康分析报告

**关键路径：**

- `/athena/insight/report` - 分析报告
- `/athena/insight/recommend/*` - 推荐接口

**说明：** insight 负责用户画像、个性化推荐等能力。

---

### 6.8 athena-rag（RAG 智能体）

**核心职责：** 提供 AI 智能问答与医疗辅助能力

athena-rag 是一个独立的 AI 应用子项目，与主项目通过 HTTP API 集成。

#### 6.8.1 两大核心系统

**1. RAG 问答系统**

为用户提供基于知识库的智能问答服务。

**主要能力：**
- 文档知识检索：从知识库中检索相关信息
- 多轮对话：支持上下文理解的连续对话
- 意图识别：理解用户提问意图，提供精准回答
- 问题重写：补全对话上下文，理解省略表达
- 会话记忆：记住对话历史，支持追问

**典型场景：**

- 用户提问："更年期怎么护肤？"
- 系统检索相关知识并生成回答
- 用户追问："我要怎么给小朋友传授性教育知识？"
- 系统结合上下文继续回答

#### 6.8.2 技术架构

**两层架构设计：**

- **infra-ai 层**：AI 基础设施（模型调用封装、向量数据库封装）
- **bootstrap 层**：业务应用（RAG 问答、知识库管理）

**关键技术：**

- 向量数据库：PostgreSQL (PGVector) 存储文档向量
- 文档解析：Apache Tika 支持多种文档格式
- LLM 模型：支持多模型路由与降级

**说明：** 关于 athena-rag 的详细架构设计、技术实现，请参考 RAG 架构文档。

---

## 7. 服务间 RPC 调用关系

### 7.1 调用依赖图

```
athena-comment
    ├─> athena-userauth (获取用户信息)
    └─> athena-ground (增加笔记的评论数量)

athena-ground
    ├─> athena-userauth (获取用户信息(头像名称))
    └─> athena-insight (广场推荐笔记)

athena-insight
    └─> athena-ground (获取笔记主题)

athena-relation
    └─> athena-userauth (获取用户信息)

athena-record
    └─> athena-userauth (获取用户信息)
```

### 7.2 典型 RPC 调用示例

**评论服务获取用户信息**

```java
// athena-comment-biz
@Component
public class UserAuthFeignApi {
    @Resource
    private UserFeignApi userFeignApi;  // Feign 客户端
    
    public UserDTO findByUserId(Long userId) {
        Result<UserDTO> result = userFeignApi.findById(userId);
        return result.getData();
    }
}
```

调用链路：
```
athena-comment
    ↓ (Feign 调用)
GET /athena/user/findById?id=123
    ↓
athena-userauth
    ↓
返回用户信息
```

### 7.3 服务间调用特点

**1. 统一的返回格式**
```java
public class Result<T> {
    private Integer code;    // 200 表示成功
    private String message;
    private T data;
}
```

**2. 上下文自动传递**
- userId 在网关层注入请求头
- Feign 拦截器自动携带 userId 到下游服务
- 下游服务通过 `UserIdHolder.getUserId()` 获取

---

## 8. 数据流转示例

### 8.1 用户发布笔记流程

```
1. 用户编辑笔记内容
2. POST /athena/blog/submitNote → athena-ground
3. athena-ground 保存笔记（状态：待审核）
4. 返回笔记 ID

5. 管理员审核
6. POST /athena/admin/blog/review/approve → athena-ground
7. 更新笔记状态为"已通过"
8. 返回审核结果
```

**说明：** 当前代码中 ground 服务支持 RocketMQ 消息队列，包含以下生产者：
- `NoteInteractionProducer`：笔记互动事件（点赞、收藏等）
- `ViewRecordProducer`：浏览记录事件
- `AthenaNoteSyncProducer`：笔记同步到 RAG 系统

这些消息会被相应的消费者处理，实现异步解耦。

### 8.2 用户查看笔记并评论

```
1. GET /athena/blog/list → athena-ground
2. athena-ground 查询已审核笔记
3. Feign 调用 athena-userauth 批量获取用户信息
4. 返回笔记列表（包含作者信息）

5. 用户点击查看详情
6. GET /athena/blog/Detail?noteId=123 → athena-ground
7. 记录浏览历史（ViewRecordService）
8. 返回笔记详情
```

### 8.3 用户查看个性化推荐

```
1. GET /athena/insight/recommend/list → athena-insight
2. athena-insight 获取用户特征
3. 基于用户特征计算推荐笔记
4. Feign 调用 athena-ground 获取笔记内容
5. Feign 调用 athena-userauth 获取作者信息
6. 返回推荐列表
```



**待补充章节：**

- 各服务详细职责
- 数据库设计
- 部署架构
- 开发规范
