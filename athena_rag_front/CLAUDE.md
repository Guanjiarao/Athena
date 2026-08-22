# Athena RAG Frontend 技术文档

> 版本：v1.0  
> 更新时间：2026-06-10

---

## 1. 项目概述

Athena RAG Frontend 是基于 React + TypeScript 的 RAG 系统前端，提供智能对话和知识库管理功能。

### 核心功能

- 智能对话（流式响应、深度思考模式）
- 会话管理
- 知识库管理
- 数据摄取（流水线）
- 链路追踪
- 用户管理

### 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.3.1 |
| 语言 | TypeScript | 5.5.4 |
| 构建 | Vite | 5.4.3 |
| 路由 | React Router | 6.26.2 |
| 状态 | Zustand | 4.5.5 |
| 样式 | Tailwind CSS | 3.4.10 |
| UI 组件 | Radix UI + shadcn/ui | - |
| HTTP | Axios | 1.7.5 |
| 表单 | React Hook Form + Zod | 7.71.1 + 4.3.6 |

---

## 2. 项目结构

```
athena_rag_front/rag-frontend/frontend/
├── src/
│   ├── components/        # 组件
│   │   ├── chat/         # 聊天组件
│   │   ├── admin/        # 管理后台组件
│   │   ├── ui/           # 基础 UI 组件
│   │   └── common/       # 通用组件
│   ├── pages/            # 页面
│   │   ├── LoginPage.tsx
│   │   ├── ChatPage.tsx
│   │   └── admin/        # 管理后台页面
│   ├── services/         # API 服务
│   ├── stores/           # Zustand 状态
│   ├── types/            # 类型定义
│   ├── utils/            # 工具函数
│   └── styles/           # 样式
├── vite.config.ts
├── tailwind.config.cjs
└── package.json
```

---

## 3. 核心架构

### 3.1 路由架构

```
/ → /login 或 /chat
/login → 登录页
/chat → 聊天欢迎页
/chat/:sessionId → 对话页
/admin → 管理后台
  ├── /admin/dashboard → 仪表板
  ├── /admin/knowledge → 知识库列表
  ├── /admin/knowledge/:kbId → 知识库文档
  ├── /admin/knowledge/:kbId/docs/:docId → 文档分块
  ├── /admin/intent-tree → 意图树
  ├── /admin/intent-list → 意图列表
  ├── /admin/ingestion → 数据摄取
  ├── /admin/mappings → 关键词映射
  ├── /admin/traces → 链路追踪
  ├── /admin/sample-questions → 示例问题
  ├── /admin/settings → 系统设置
  └── /admin/users → 用户管理
```

### 3.2 状态管理

**authStore**:
- `user`, `token`, `isAuthenticated`
- `login()`, `logout()`, `checkAuth()`

**chatStore**:
- `sessions`, `messages`, `isStreaming`
- `sendMessage()`, `fetchSessions()`, `cancelGeneration()`

**themeStore**:
- `theme`
- `setTheme()`

### 3.3 流式响应

使用 SSE（Server-Sent Events）实现：

```typescript
EventSource('/api/ragent/chat/stream')
  ├─ meta → conversationId, taskId
  ├─ thinking_delta → 思考过程
  ├─ delta → 回复内容
  └─ completion → 完成信号
```

---

## 4. 开发指南

### 4.1 安装依赖

```bash
cd athena_rag_front/rag-frontend/frontend
npm install
```

### 4.2 启动开发

```bash
npm run dev  # http://localhost:5173
```

### 4.3 构建生产

```bash
npm run build  # 输出到 dist/
```

### 4.4 环境变量

`.env`:
```env
VITE_API_BASE_URL=/api/ragent
VITE_APP_NAME=Athena RAG
```

### 4.5 代理配置

`vite.config.ts`:
```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:9090',  # 后端地址
      changeOrigin: true
    }
  }
}
```

---

## 5. 部署

### Nginx 配置

```nginx
server {
    listen 80;
    root /var/www/athena-rag-frontend/dist;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ragent {
        proxy_pass http://localhost:9090;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_read_timeout 300s;
    }
}
```

---

## 6. 代码规范

- 组件使用 PascalCase
- 文件按功能模块组织
- 所有 API 调用通过 service 层
- 使用 TypeScript 类型注解
- 样式使用 Tailwind CSS

---

**维护者**: Athena Team  
**更新**: 2026-06-10
