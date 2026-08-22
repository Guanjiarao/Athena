# Athena - 女性健康智能服务平台

<div align="center">

![Athena Logo](docs/attachments/Athena_Architecture.png)

**一个集内容管理、社区互动、AI 问答、健康记录于一体的综合女性健康服务平台**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3.1-61dafb.svg)](https://reactjs.org/)
[![Android](https://img.shields.io/badge/Android-SDK%2036-3DDC84.svg)](https://developer.android.com/)

</div>

---

## 📋 目录

- [项目简介](#-项目简介)
- [系统架构](#-系统架构)
- [核心功能](#-核心功能)
- [技术栈](#-技术栈)
- [项目结构](#-项目结构)
- [部署说明](#-部署说明)
- [开发指南](#-开发指南)
- [文档链接](#-文档链接)
- [贡献指南](#-贡献指南)
- [许可证](#-许可证)

---

## 🎯 项目简介

Athena 是一个面向女性健康场景的全栈智能服务平台，提供三端应用支持：

- **管理后台 (Web)**：内容审核、发布管理、数据治理
- **移动应用 (Android)**：内容浏览、社区互动、健康记录、AI 咨询
- **RAG 前端 (Web)**：智能问答、知识库管理、链路追踪

平台采用微服务架构，集成 RAG（检索增强生成）技术，为用户提供专业的健康咨询和知识服务。

### ✨ 核心特性

- 🤖 **AI 智能问答**：基于 RAG 技术的流式对话，支持深度思考模式
- 📚 **知识库管理**：文档上传、分块处理、向量检索
- 📝 **内容管理**：文章/视频发布、审核流程、内容治理
- 💬 **社区互动**：评论、点赞、收藏、关注
- 📊 **健康记录**：经期管理、身体状况记录、分析报告
- 🔒 **隐私保护**：本地脱敏、隐私守护、私密图库
- 📈 **数据洞察**：个性化推荐、健康趋势分析

---

## 🏗️ 系统架构

### 总体架构

 ![](D:\athenaworktwo\athena\docs\attachments\总架构图.png)

## 🚀 核心功能

### 1. 管理后台功能

#### 内容审核
- 待审核内容列表与筛选
- 审核详情查看（标题、正文、图片、视频）
- 审核操作：通过、驳回（需填写原因）、删除
- 审核记录与日志追踪

#### 内容管理
- 按类型、状态、分类筛选内容
- 内容详情查看与编辑
- 违规内容处理与下架
- 内容数据统计

#### 内容发布
- 文章发布：富文本编辑、封面上传、分类选择
- 视频发布：视频上传、封面设置、简介编辑
- 草稿保存与定时发布

### 2. 移动端功能

#### 内容浏览
- **推荐页**：个性化内容推荐、智能分发
- **知识模块**：按年龄阶段/主题分类（0-12岁、12-22岁、22-55岁、55+）
- **广场模块**：社区图文/视频动态流

#### 社区互动
- 点赞、收藏、评论、回复
- 关注用户、查看关注/粉丝列表
- 用户主页与个人内容展示

#### AI 智能咨询
- 自然语言问答，支持多轮对话
- 流式响应，实时展示生成过程
- 深度思考模式（可选）
- 引用来源展示，支持跳转原文

#### 健康记录
- **经期管理**：记录经期、周期预测、月视图展示
- **身体状况**：症状、情绪、睡眠、体温、体重等记录
- **备孕记录**：排卵试纸、同房记录、孕期扩展
- **分析报告**：周期分析、健康趋势、个性化建议

#### 隐私保护
- 私密图库：本地图片管理
- 图片脱敏：OCR 识别敏感信息 + 遮挡
- 隐私 AI：上传前本地处理

### 3. RAG 前端功能

- **仪表板**：知识库统计、查询量趋势
- **知识库管理**：创建/删除知识库、文档上传、分块查看
- **数据摄取**：批量上传、切分参数配置、任务队列
- **意图管理**：意图树编辑、触发关键词配置
- **链路追踪**：RAG 调用记录、性能分析、调试工具
- **系统设置**：LLM 配置、向量模型、检索参数、Prompt 模板
- **用户管理**：用户 CRUD、角色权限

---

## 🛠️ 技术栈

### 后端技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 编程语言 | Java | 17 |
| 核心框架 | Spring Boot | 3.0.2 |
| 微服务框架 | Spring Cloud | 2022.0.0 |
|  | Spring Cloud Alibaba | 2022.0.0.0 |
| 服务注册发现 | Nacos | 2.2.3 |
| API 网关 | Spring Cloud Gateway | 4.0.0 |
| 认证鉴权 | Sa-Token | 1.38.0 |
| 数据库 | MySQL | 8.0.32 |
| ORM 框架 | MyBatis Plus | 3.5.7 |
| 缓存 | Redis | 5.3.0 |
| 消息队列 | RocketMQ | 2.2.3 |
| 向量数据库 | PostgreSQL (PGVector) | - |
| 文档解析 | Apache Tika | - |

### 前端技术栈

#### 管理后台 (admin_web_front)
- React 19.2.4 + TypeScript 6.0.2
- Vite 8.0.4
- React Router 7.9.6
- Tailwind CSS 4.1.18
- React Quill（富文本编辑）
- Axios 1.15.0

#### RAG 前端 (athena_rag_front)
- React 18.3.1 + TypeScript 5.5.4
- Vite 5.4.3
- React Router 6.26.2
- Zustand 4.5.5（状态管理）
- Radix UI + shadcn/ui
- Tailwind CSS 3.4.10
- Axios 1.7.5

#### Android 移动端 (athena_app_front)
- Android SDK 36 (minSdk 24)
- Java + Gradle Kotlin DSL
- OkHttp（网络请求）
- Glide（图片加载）
- GSYVideoPlayer（视频播放）
- Markwon（Markdown 渲染）
- ML Kit（OCR 识别）

---

## 📁 项目结构

```
athena/
├── Back_End/                       # 后端微服务
│   ├── athena-gateway/            # API 网关
│   ├── athena-userauth/           # 用户认证服务
│   ├── athena-ground/             # 内容管理服务
│   ├── athena-comment/            # 评论服务
│   ├── athena-relation/           # 关系服务
│   ├── athena-record/             # 记录服务
│   ├── athena-insight/            # 洞察服务
│   ├── athena-oss/                # 对象存储服务
│   ├── athena-rag/                # RAG 服务
│   │   ├── bootstrap/             # 业务应用层
│   │   ├── framework/             # 基础设施层
│   │   ├── infra-ai/              # AI 基础设施
│   │   └── frontend/              # RAG 前端（开发版）
│   └── athena-framework/          # 公共框架
│
├── admin_web_front/               # 管理后台前端
│   ├── src/
│   │   ├── api/                  # API 调用层
│   │   ├── components/           # 可复用组件
│   │   ├── contexts/             # React Context
│   │   ├── layouts/              # 布局组件
│   │   ├── lib/                  # 工具函数
│   │   └── pages/                # 页面组件
│   └── package.json
│
├── athena_rag_front/             # RAG 前端（部署版）
│   └── rag-frontend/frontend/
│       ├── src/
│       │   ├── components/       # UI 组件
│       │   ├── hooks/            # 自定义 Hooks
│       │   ├── pages/            # 页面组件
│       │   ├── services/         # API 服务
│       │   ├── stores/           # Zustand 状态
│       │   ├── styles/           # 样式文件
│       │   ├── types/            # TypeScript 类型
│       │   └── utils/            # 工具函数
│       └── package.json
│
├── athena_app_front/             # Android 移动端
│   └── app/src/main/
│       ├── java/com/whu/software/athena/
│       │   ├── MainActivity.java
│       │   ├── config/           # API 配置
│       │   ├── core/             # AI 核心
│       │   ├── db/               # 数据库
│       │   ├── entity/           # 实体类
│       │   ├── features/         # 功能模块
│       │   ├── net/              # 网络层
│       │   └── utils/            # 工具类
│       └── res/                  # Android 资源
│
├── docs/                         # 文档目录
│   ├── 架构文档.md
│   ├── 功能设计.md
│   ├── 使用手册.md
│   ├── 需求规格.md
│   ├── 安装维护.md
│   ├── 接口文档/
│   ├── 数据库文档/
│   └── 测试文档/
│
├── CLAUDE.md                     # Claude Code 项目说明
└── README.md                     # 本文件
```

### 目录说明

#### 后端服务 (`Back_End/`)
每个微服务独立部署，通过 Nacos 服务发现互相调用。

#### 管理后台 (`admin_web_front/`)
基于 React 的 SPA 应用，提供内容审核、发布、管理等后台功能。

#### RAG 前端 (`athena_rag_front/`)
独立的 RAG 系统前端，支持智能对话和知识库管理。

#### Android 应用 (`athena_app_front/`)
原生 Android 应用，面向最终用户提供完整的移动端体验。

#### 文档 (`docs/`)
完整的项目文档，包括架构、功能、接口、数据库等说明。

---

## 🚀 快速开始

### 环境要求

#### 后端
- **JDK**: 17+
- **Maven**: 3.6+
- **MySQL**: 8.0+
- **Redis**: 5.0+
- **Nacos**: 2.2.3
- **RocketMQ**: 5.1.0（可选）
- **PostgreSQL**: 13+（RAG 向量库）

#### 前端
- **Node.js**: 18+
- **npm**: 9+

#### Android
- **Android Studio**: Hedgehog | 2023.1.1+
- **Gradle**: 8.0+
- **Android SDK**: 36（minSdk 24）

### 后端启动

#### 1. 启动基础设施

```bash
# 启动 MySQL
mysql -u root -p
CREATE DATABASE athena_ground CHARACTER SET utf8mb4;
CREATE DATABASE athena_userauth CHARACTER SET utf8mb4;
CREATE DATABASE athena_record CHARACTER SET utf8mb4;
CREATE DATABASE athena_insight CHARACTER SET utf8mb4;
CREATE DATABASE athena_rag CHARACTER SET utf8mb4;

# 启动 Redis
redis-server

# 启动 Nacos
cd nacos/bin
sh startup.sh -m standalone

# 启动 RocketMQ（可选）
cd rocketmq/bin
sh mqnamesrv
sh mqbroker -n localhost:9876
```

#### 2. 配置 Nacos

访问 `http://localhost:8848/nacos`（用户名/密码：nacos/nacos）

创建命名空间（如 `dev`），上传以下配置文件：
- `base.yaml` - 基础配置
- `redis.yaml` - Redis 配置
- `sa-token.yaml` - 认证配置
- `athena-gateway.yaml` - 网关配置
- 各微服务配置文件

#### 3. 启动微服务

```bash
cd Back_End

# 按顺序启动服务
cd athena-gateway && mvn spring-boot:run &
cd athena-userauth && mvn spring-boot:run &
cd athena-ground && mvn spring-boot:run &
cd athena-comment && mvn spring-boot:run &
cd athena-relation && mvn spring-boot:run &
cd athena-record && mvn spring-boot:run &
cd athena-insight && mvn spring-boot:run &
cd athena-oss && mvn spring-boot:run &
cd athena-rag && mvn spring-boot:run &
```

#### 4. 验证服务

访问 Nacos 控制台，确认所有服务状态为"健康"。

### 前端启动

#### 管理后台

```bash
cd admin_web_front
npm install
npm run dev
# 访问 http://localhost:5173
```

#### RAG 前端

```bash
cd athena_rag_front/rag-frontend/frontend
npm install
npm run dev
# 访问 http://localhost:5174
```

### Android 构建

```bash
cd athena_app_front
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/
```

或在 Android Studio 中直接运行。

---

## 📦 部署说明

### 后端部署

#### 1. 打包微服务

```bash
cd Back_End
mvn clean package -DskipTests

# 每个服务生成 target/*.jar
```

#### 2. 部署到服务器

```bash
# 使用 systemd 管理服务
sudo vi /etc/systemd/system/athena-gateway.service

[Unit]
Description=Athena Gateway Service
After=network.target

[Service]
Type=simple
User=athena
WorkingDirectory=/opt/athena
ExecStart=/usr/bin/java -jar /opt/athena/athena-gateway.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target

# 启动服务
sudo systemctl start athena-gateway
sudo systemctl enable athena-gateway
```

#### 3. 配置 Nginx 反向代理

```nginx
server {
    listen 80;
    server_name api.athena.com;

    location /api/ {
        proxy_pass http://localhost:9090/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # SSE 支持
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}
```

### 前端部署

#### 管理后台

```bash
cd admin_web_front
npm run build

# 部署到 Nginx
cp -r dist/* /var/www/admin.athena.com/
```

Nginx 配置：

```nginx
server {
    listen 80;
    server_name admin.athena.com;
    root /var/www/admin.athena.com;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://localhost:9090/;
    }
}
```

#### RAG 前端

```bash
cd athena_rag_front/rag-frontend/frontend
npm run build

cp -r dist/* /var/www/rag.athena.com/
```

配置与管理后台类似。

### Android 发布

```bash
# 生成签名 APK
./gradlew assembleRelease

# 上传到应用商店或内部分发平台
```

---

## 💡 开发指南

### 后端开发

#### 新增微服务

1. 创建 Spring Boot 模块
2. 配置 Nacos 服务发现
3. 在网关添加路由规则
4. 定义 Feign Client API
5. 编写业务逻辑

#### 数据库规范

- 表名：`tb_*`
- 主键：雪花算法生成的 `BIGINT`
- 时间字段：`create_time`、`update_time`
- 软删除：`deleted` 字段（0-未删除，1-已删除）

#### 接口规范

统一返回格式：

```java
{
    "code": 200,
    "message": "success",
    "data": { ... }
}
```

### 前端开发

#### 管理后台

- 页面组件放在 `src/pages/`
- API 调用放在 `src/api/`
- 全局状态使用 Context
- 样式使用 Tailwind CSS

#### RAG 前端

- 状态管理使用 Zustand
- 页面组件放在 `src/pages/`
- API 服务放在 `src/services/`
- 类型定义放在 `src/types/`

#### Android

- 新增页面：Activity + Fragment + XML Layout
- 网络请求：使用 ApiConfig + OkHttp
- 图片加载：使用 Glide
- 本地存储：SQLite + SharedPreferences

### 代码提交规范

- `feat:` 新功能
- `fix:` Bug 修复
- `docs:` 文档更新
- `refactor:` 代码重构
- `style:` 代码格式调整
- `test:` 测试相关
- `chore:` 构建/工具配置

---

## 📚 文档链接

- [架构文档](docs/架构文档.md) - 系统总体架构和技术细节
- [功能设计](docs/功能设计.md) - 功能模块划分和设计说明
- [使用手册](docs/使用手册.md) - 各端使用说明和操作指南
- [需求规格](docs/需求规格.md) - 系统需求和功能规格
- [安装维护](docs/安装维护.md) - 部署和运维指南
- [接口文档](docs/接口文档/) - 各服务 API 接口说明
- [数据库文档](docs/数据库文档/) - 数据库表结构设计

---

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议！

### 贡献流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

### 问题反馈

遇到问题？请通过 [GitHub Issues](https://github.com/your-org/athena/issues) 提交。

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

---

## 👥 团队

**Athena Team**  
武汉大学软件工程学院

---

## 📞 联系方式

- 项目主页: [GitHub](https://github.com/your-org/athena)
- 邮箱: athena-team@whu.edu.cn

---

<div align="center">

**Built with ❤️ by Athena Team**

</div>
