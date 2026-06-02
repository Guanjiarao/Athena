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
| 缓存 | Redis | 5.3.0 |
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

### 6.8 athena-rag

**核心职责：** 提供 AI 智能问答科普

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



---

## 9. 数据库设计

Athena 系统采用 MySQL 8.0 作为主数据库，各业务模块按服务拆分独立的数据库表。

### 9.1 数据库设计原则

**垂直拆分：** 按业务模块拆分表，每个服务管理自己的表
**读写分离：** 支持主从架构（配置在 Nacos）
**字段冗余：** 适当冗余以减少跨服务查询（如 NoteDO 中的 topicName）

### 9.2 笔记核心表设计

#### 9.2.1 笔记基础表（tb_note_basic）

**说明：** 存储笔记的基础信息和审核状态，用于笔记列表查询和审核流程。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| note_id | BIGINT | 笔记ID（主键） | 雪花算法生成 |
| user_id | BIGINT | 作者ID | 外键关联 tb_user.id |
| title | VARCHAR(100) | 笔记标题 | 非空 |
| cover_url | VARCHAR(500) | 封面图URL | 列表展示用 |
| type | TINYINT | 笔记类型 | 1-图文，2-视频，3-0~12岁，4-0~12岁视频，5-12~22岁，6-12~22岁视频，7-22~55岁，8-22~55岁视频，9-55+，10-55+视频 |
| status | TINYINT | 审核状态 | 0-待审核，1-审核通过，2-审核拒绝 |
| review_remark | VARCHAR(500) | 审核备注 | 审核拒绝时填写原因 |
| review_time | DATETIME | 审核时间 | |
| reviewer_id | BIGINT | 审核员ID | 外键关联 tb_user.id |
| channel_id | INT | 频道ID | 1-健身指南，2-避孕指南，3-个性化护肤，4-科学养护生理期，5-私处护理 |
| channel_name | VARCHAR(50) | 频道名称 | 冗余字段 |
| create_time | DATETIME | 创建时间 | 默认当前时间 |
| update_time | DATETIME | 更新时间 | 自动更新 |

**索引设计：**
- PRIMARY KEY (`note_id`)
- INDEX `idx_user_id` (`user_id`) - 查询用户笔记列表
- INDEX `idx_status_create_time` (`status`, `create_time`) - 广场列表查询（审核通过 + 时间倒序）
- INDEX `idx_channel_status` (`channel_id`, `status`) - 按频道筛选
- INDEX `idx_type_status` (`type`, `status`) - 按类型筛选

#### 9.2.2 笔记详细表（tb_note）

**说明：** 存储笔记的详细内容信息，与 tb_note_basic 是 1:1 关系。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 笔记ID（主键） | 与 tb_note_basic.note_id 相同 |
| title | VARCHAR(100) | 笔记标题 | 冗余字段 |
| user_id | BIGINT | 作者ID | 冗余字段 |
| topic_id | BIGINT | 话题ID | 外键关联 tb_topic.id |
| topic_name | VARCHAR(50) | 话题名称 | 冗余字段，减少联表查询 |
| is_top | BOOLEAN | 是否置顶 | 默认 false |
| type | TINYINT | 笔记类型 | 同 tb_note_basic.type |
| img_urls | TEXT | 图片URL列表 | JSON 数组格式存储 |
| video_url | VARCHAR(500) | 视频URL | 当类型为视频时有值 |
| visible | TINYINT | 可见范围 | 0-私密，1-公开 |
| status | TINYINT | 审核状态 | 冗余字段，同 tb_note_basic.status |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**设计考量：**
- **双表设计**：tb_note_basic 用于列表查询，tb_note 用于详情查询
- **字段冗余**：title、user_id、status 等字段在两表都存在，避免联表查询

**索引设计：**
- PRIMARY KEY (`id`)
- INDEX `idx_topic_id` (`topic_id`) - 按话题查询

#### 9.2.3 笔记内容表（tb_note_content）

**说明：** 存储笔记的正文内容，采用读写分离设计，提升大文本查询性能。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| content_id | BIGINT | 内容ID（主键） | 自增 |
| note_id | BIGINT | 笔记ID | 外键关联 tb_note.id，唯一索引 |
| content | TEXT | 笔记正文 | 富文本内容 |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**设计考量：**
- **垂直拆分**：将大文本字段独立存储，tb_note_basic 和 tb_note 只存基础信息，提升列表查询性能
- **1:1 关系**：一个笔记对应一条内容记录

**索引设计：**
- PRIMARY KEY (`content_id`)
- UNIQUE KEY `uk_note_id` (`note_id`) - 保证一对一关系

#### 9.2.4 笔记统计表（tb_note_count）

**说明：** 存储笔记的互动统计数据（点赞、收藏、评论数），采用独立表设计支持高并发更新。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 统计ID（主键） | 自增 |
| note_id | BIGINT | 笔记ID | 外键关联 tb_note.id，唯一索引 |
| like_total | BIGINT | 点赞总数 | 默认 0 |
| collect_total | BIGINT | 收藏总数 | 默认 0 |
| comment_total | BIGINT | 评论总数 | 默认 0 |

**设计考量：**
- **计数器独立表**：避免频繁更新 tb_note 表，减少行锁竞争
- **异步更新**：通过 RocketMQ 异步更新统计数据
- **最终一致性**：允许短暂的统计延迟

**索引设计：**
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_note_id` (`note_id`)

#### 9.2.5 笔记点赞表（tb_note_like）

**说明：** 记录用户对笔记的点赞行为。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 点赞ID（主键） | 自增 |
| user_id | BIGINT | 用户ID | 外键关联 tb_user.id |
| note_id | BIGINT | 笔记ID | 外键关联 tb_note.id |
| status | TINYINT | 点赞状态 | 1-已点赞，0-已取消 |
| create_time | DATETIME | 创建时间 | |

**业务逻辑：**
- 用户点赞 → status=1
- 用户取消点赞 → status=0（软删除，保留记录）
- 同一用户对同一笔记只有一条记录

**索引设计：**
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_user_note` (`user_id`, `note_id`) - 防止重复点赞
- INDEX `idx_note_id` (`note_id`) - 查询笔记的点赞列表

#### 9.2.6 笔记收藏表（tb_note_collection）

**说明：** 记录用户对笔记的收藏行为。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 收藏ID（主键） | 自增 |
| user_id | BIGINT | 用户ID | 外键关联 tb_user.id |
| note_id | BIGINT | 笔记ID | 外键关联 tb_note.id |
| status | TINYINT | 收藏状态 | 1-已收藏，0-已取消 |
| create_time | DATETIME | 创建时间 | |

**索引设计：**
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_user_note` (`user_id`, `note_id`)
- INDEX `idx_note_id` (`note_id`)
- INDEX `idx_user_status_time` (`user_id`, `status`, `create_time`) - 用户收藏列表查询

#### 9.2.7 用户浏览记录表（tb_user_view_record）

**说明：** 记录用户浏览笔记的行为数据，用于推荐算法和数据分析。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 记录ID（主键） | 自增 |
| user_id | BIGINT | 用户ID | 外键关联 tb_user.id |
| note_id | BIGINT | 笔记ID | 外键关联 tb_note.id |
| first_view_time | DATETIME | 首次浏览时间 | |
| last_view_time | DATETIME | 最近浏览时间 | |
| view_count | INT | 浏览次数 | 同一笔记重复浏览累加 |
| duration | INT | 停留时长（秒） | 累计停留时长 |

**业务逻辑：**
- 用户浏览笔记 ≥ 3 秒后记录
- 同一用户重复浏览同一笔记，更新 `last_view_time`、`view_count`、`duration`

**索引设计：**
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_user_note` (`user_id`, `note_id`)
- INDEX `idx_user_last_view` (`user_id`, `last_view_time`) - 用户浏览历史查询
- INDEX `idx_note_id` (`note_id`) - 笔记热度统计

### 9.3 评论系统表设计

#### 9.3.1 评论主表（tb_comment）

**说明：** 存储评论的基础信息，支持一级评论和二级回复。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 评论ID（主键） | 雪花算法生成 |
| note_id | BIGINT | 笔记ID | 外键关联 tb_note.id |
| user_id | BIGINT | 评论者ID | 外键关联 tb_user.id |
| is_content_empty | BOOLEAN | 内容是否为空 | 纯图片评论为 true |
| image_url | VARCHAR(500) | 图片URL | 可选 |
| level | INT | 评论层级 | 1-一级评论，2-二级回复 |
| reply_total | BIGINT | 回复总数 | 仅一级评论有值 |
| like_total | BIGINT | 点赞总数 | |
| parent_id | BIGINT | 父评论ID | 二级回复时指向一级评论ID |
| reply_comment_id | BIGINT | 回复的评论ID | 二级回复时指向被回复的评论ID |
| reply_user_id | BIGINT | 回复的用户ID | 二级回复时指向被回复的用户ID |
| is_top | BOOLEAN | 是否置顶 | 默认 false |
| first_reply_comment_id | BIGINT | 首条回复ID | 一级评论的第一条回复 |
| heat | BIGINT | 热度值 | 用于排序（点赞数 + 回复数） |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**评论层级设计：**
```
一级评论（level=1, parent_id=null）
  ├─ 二级回复 A（level=2, parent_id=一级评论ID, reply_comment_id=一级评论ID）
  ├─ 二级回复 B（level=2, parent_id=一级评论ID, reply_comment_id=二级回复A的ID）
  └─ 二级回复 C（level=2, parent_id=一级评论ID, reply_comment_id=二级回复B的ID）
```

**索引设计：**
- PRIMARY KEY (`id`)
- INDEX `idx_note_level_heat` (`note_id`, `level`, `heat`) - 笔记评论列表（按热度排序）
- INDEX `idx_parent_time` (`parent_id`, `create_time`) - 二级回复列表（按时间倒序）
- INDEX `idx_user_id` (`user_id`) - 用户评论列表

#### 9.3.2 评论内容表（tb_comment_content）

**说明：** 存储评论的文本内容，垂直拆分设计。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 内容ID（主键） | 自增 |
| comment_id | BIGINT | 评论ID | 外键关联 tb_comment.id，唯一索引 |
| content | TEXT | 评论内容 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**索引设计：**
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_comment_id` (`comment_id`)

### 9.4 用户系统表设计

#### 9.4.1 用户基础表（tb_user）

**说明：** 存储用户的账号信息。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 用户ID（主键） | 自增 |
| phone | VARCHAR(20) | 手机号 | 唯一索引 |
| nick_name | VARCHAR(50) | 昵称 | |
| icon | VARCHAR(500) | 头像URL | |
| priority | BOOLEAN | 是否VIP | 默认 false |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**索引设计：**
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_phone` (`phone`) - 手机号登录

#### 9.4.2 用户信息表（tb_user_info）

**说明：** 存储用户的扩展信息和统计数据。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| user_id | BIGINT | 用户ID（主键） | 外键关联 tb_user.id |
| city | VARCHAR(50) | 城市 | |
| introduction | VARCHAR(500) | 个人简介 | |
| fans_total | INT | 粉丝总数 | 默认 0 |
| following_total | INT | 关注总数 | 默认 0 |
| gender | TINYINT | 性别 | 0-未知，1-男，2-女 |
| birthday | DATE | 生日 | |
| credits | INT | 积分 | 默认 0 |
| level | TINYINT | 等级 | 默认 1 |
| content_total | BIGINT | 笔记总数 | 默认 0 |
| like_total | BIGINT | 获赞总数 | 默认 0 |
| collect_total | BIGINT | 获藏总数 | 默认 0 |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**索引设计：**
- PRIMARY KEY (`user_id`)

### 9.5 关系系统表设计

#### 9.5.1 关注表（tb_follow）

**说明：** 记录用户之间的关注关系。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 关注ID（主键） | 自增 |
| user_id | BIGINT | 关注者ID | 我关注了谁 |
| follow_user_id | BIGINT | 被关注者ID | |
| create_time | DATETIME | 关注时间 | |

**业务逻辑：**
- 关注 → 插入记录
- 取消关注 → 删除记录（物理删除）

**索引设计：**
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`) - 防止重复关注
- INDEX `idx_follow_user` (`follow_user_id`) - 查询粉丝列表

#### 9.5.2 粉丝表（tb_fans）

**说明：** 冗余设计，加速粉丝列表查询。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 粉丝ID（主键） | 自增 |
| user_id | BIGINT | 被关注者ID | 谁关注了我 |
| fans_user_id | BIGINT | 关注者ID | |
| create_time | DATETIME | 关注时间 | |

**设计考量：**
- tb_follow 和 tb_fans 存储同一关系的两个视角
- 插入 tb_follow 时同步插入 tb_fans
- 空间换时间，避免复杂的双向查询

**索引设计：**
- PRIMARY KEY (`id`)
- INDEX `idx_user_fans` (`user_id`, `create_time`) - 粉丝列表查询

### 9.6 健康记录表设计

#### 9.6.1 经期周期表（tb_menstruation_cycle）

**说明：** 记录用户的经期周期数据，支持预测功能。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 周期ID（主键） | 自增 |
| user_id | BIGINT | 用户ID | 外键关联 tb_user.id |
| start_date | DATE | 经期开始日期 | |
| end_date | DATE | 经期结束日期 | 可为空（未结束） |
| duration_days | INT | 经期持续天数 | end_date - start_date + 1 |
| cycle_length | INT | 周期长度（天） | 距离上次经期的天数 |
| is_predicted | INT | 是否预测数据 | 0-实际记录，1-预测数据 |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**业务逻辑：**
- 用户记录经期开始 → 插入记录（end_date=null）
- 用户记录经期结束 → 更新 end_date 和 duration_days
- 系统根据历史周期预测未来经期（is_predicted=1）

**索引设计：**
- PRIMARY KEY (`id`)
- INDEX `idx_user_start` (`user_id`, `start_date`) - 查询用户经期历史

#### 9.6.2 日常健康记录表（tb_daily_record）

**说明：** 记录用户每日的健康数据（体温、心情、症状等）。

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 记录ID（主键） | 自增 |
| user_id | BIGINT | 用户ID | 外键关联 tb_user.id |
| record_date | DATE | 记录日期 | |
| mode_type | INT | 模式类型 | 0-正常模式，1-备孕模式，2-怀孕模式 |
| record_item_id | INT | 记录项ID | 外键关联数据字典表 |
| record_value | VARCHAR(500) | 记录值 | JSON 格式存储 |

**设计考量：**
- 采用 EAV 模型（实体-属性-值）存储灵活的健康数据
- record_item_id 关联数据字典，定义不同的健康指标
- record_value 存储 JSON 格式数据，支持复杂结构

**索引设计：**
- PRIMARY KEY (`id`)
- INDEX `idx_user_date` (`user_id`, `record_date`) - 查询某日记录

### 9.7 数据库设计要点总结

#### 9.7.1 读写分离设计

- **大文本垂直拆分**：笔记内容、评论内容独立存储
- **统计数据独立表**：点赞数、收藏数、评论数独立表，减少锁竞争

#### 9.7.2 高并发优化

- **软删除 vs 物理删除**：点赞/收藏采用软删除（status字段），关注采用物理删除
- **冗余字段**：topic_name 等冗余字段减少联表查询
- **异步更新**：统计数据通过 RocketMQ 异步更新

#### 9.7.3 索引设计原则

- **唯一索引**：防止重复数据（点赞、收藏、关注）
- **复合索引**：覆盖常用查询场景（user_id + status + create_time）
- **外键约束**：代码层面维护，不使用数据库外键约束

#### 9.7.4 数据类型选择

- **主键**：BIGINT（雪花算法生成分布式ID）
- **时间字段**：DATETIME（MySQL 8.0 性能优化）
- **状态字段**：TINYINT（节省空间）
- **JSON 字段**：TEXT + JSON 格式（灵活存储，适合变化频繁的字段）

---

**待补充章节：**

- 部署架构
- 开发规范
- 监控与运维
