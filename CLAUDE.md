# Athena 项目文档

## 项目概述

Athena 是一个全栈 AI 应用平台，包含后端服务和多个前端应用。项目采用微服务架构，提供 RAG（检索增强生成）系统、内容管理、移动应用等功能。

## 项目结构

```
athena/
├── Back_End/                    # 后端服务
│   ├── athena-rag/             # RAG 系统后端（主要服务）
│   │   ├── bootstrap/          # 应用启动模块
│   │   ├── framework/          # 框架核心代码
│   │   └── frontend/           # RAG 前端源码（开发版本）
│   ├── athena-comment/         # 评论服务
│   ├── athena-framework/       # 公共框架
│   ├── athena-gateway/         # API 网关
│   ├── athena-ground/          # 基础服务
│   ├── athena-insight/         # 数据分析服务
│   ├── athena-oss/            # 对象存储服务
│   ├── athena-record/         # 记录服务
│   ├── athena-relation/       # 关系服务
│   └── athena-userauth/       # 用户认证服务
│
├── admin_web_front/           # 管理后台 Web 前端
│   ├── src/
│   │   ├── api/              # API 调用
│   │   ├── components/       # React 组件
│   │   ├── contexts/         # React Context
│   │   ├── layouts/          # 布局组件
│   │   ├── lib/              # 工具库
│   │   └── pages/            # 页面组件
│   └── package.json
│
├── athena_app_front/         # Android 移动应用
│   └── (Kotlin + Android SDK)
│
└── athena_rag_front/         # RAG 系统前端（部署版本）
    └── rag-frontend/frontend/
        ├── src/              # 源代码
        │   ├── components/  # React 组件
        │   ├── hooks/       # 自定义 Hooks
        │   ├── pages/       # 页面组件
        │   ├── services/    # API 服务
        │   ├── stores/      # Zustand 状态管理
        │   ├── styles/      # 样式文件
        │   ├── types/       # TypeScript 类型
        │   └── utils/       # 工具函数
        └── package.json
```

## 技术栈

### 后端技术栈
- **语言**: Java
- **框架**: Spring Boot
- **构建工具**: Maven
- **架构模式**: 微服务架构

### 前端技术栈

#### RAG 前端 (athena_rag_front)
- **框架**: React 18.3.1 + TypeScript
- **构建工具**: Vite 5.4.3
- **路由**: React Router DOM 6.26.2
- **状态管理**: Zustand 4.5.5
- **UI 组件**: 
  - Radix UI（无障碍组件库）
  - shadcn/ui 设计系统
  - Tailwind CSS 3.4.10
- **表单**: React Hook Form 7.71.1 + Zod 4.3.6
- **数据可视化**: Recharts 3.7.0
- **Markdown**: React Markdown 9.0.1
- **HTTP 客户端**: Axios 1.7.5
- **其他**: 
  - React Dropzone（文件上传）
  - React Virtuoso（虚拟列表）
  - Date-fns（日期处理）
  - Lucide React（图标）

#### 管理后台 (admin_web_front)
- **框架**: React 19.2.4 + TypeScript 6.0.2
- **构建工具**: Vite 8.0.4
- **路由**: React Router DOM 7.9.6
- **UI 组件**: 
  - Radix UI
  - Tailwind CSS 4.1.18
- **富文本编辑**: React Quill New 3.8.3
- **动画**: Framer Motion 12.23.12
- **HTTP 客户端**: Axios 1.15.0

## RAG 前端应用详解

### 主要功能模块

#### 1. 聊天界面 (/chat)
- **文件**: `src/pages/ChatPage.tsx`
- **功能**: 
  - 实时对话交互
  - 流式响应显示
  - 会话管理
  - Markdown 渲染
  - 代码高亮

#### 2. 管理后台 (/admin)
- **布局**: `src/pages/admin/AdminLayout.tsx`
- **子页面**:
  - **仪表板** (`/admin/dashboard`): 系统概览、数据统计
  - **知识库管理** (`/admin/knowledge`): CRUD 操作、文档管理、chunk 查看
  - **意图树管理** (`/admin/intent-tree`): 意图配置、树形结构编辑
  - **数据摄取** (`/admin/ingestion`): 文档上传、批量导入
  - **链路追踪** (`/admin/traces`): RAG 调用链追踪、性能分析
  - **示例问题** (`/admin/sample-questions`): 预设问题管理
  - **查询映射** (`/admin/mappings`): 查询词映射配置
  - **系统设置** (`/admin/settings`): 系统参数配置
  - **用户管理** (`/admin/users`): 用户 CRUD

#### 3. 登录认证 (/login)
- **文件**: `src/pages/LoginPage.tsx`
- **功能**: 用户登录、Token 管理

### 状态管理

使用 Zustand 管理全局状态：
- **authStore**: 用户认证状态、Token、角色权限
- **chatStore**: 聊天消息、会话列表、流式响应
- **themeStore**: 主题配置（暗色/亮色模式）

### API 服务层

所有 API 调用集中在 `src/services/` 目录：
- `authService.ts`: 登录、注销、Token 刷新
- `chatService.ts`: 发送消息、流式响应
- `sessionService.ts`: 会话 CRUD
- `knowledgeService.ts`: 知识库管理
- `intentTreeService.ts`: 意图树管理
- `ingestionService.ts`: 文档上传
- `ragTraceService.ts`: 链路追踪
- 等等

### 路由守卫

- **RequireAuth**: 验证用户登录状态
- **RequireAdmin**: 验证管理员权限
- **RedirectIfAuth**: 已登录用户重定向到聊天页面

## 开发指南

### 环境准备

#### 后端服务
```bash
cd Back_End
mvn clean install
mvn spring-boot:run
```

#### RAG 前端
```bash
cd athena_rag_front/rag-frontend/frontend
npm install
npm run dev
```

#### 管理后台
```bash
cd admin_web_front
npm install
npm run dev
```

### 代理配置

RAG 前端开发服务器已配置代理（`vite.config.ts`）：
- 所有 `/api` 请求转发到 `http://localhost:9090`
- 后端服务需在 9090 端口运行

管理后台需根据实际后端端口配置代理。

### 构建部署

```bash
# RAG 前端
cd athena_rag_front/rag-frontend/frontend
npm run build
# 输出目录: dist/

# 管理后台
cd admin_web_front
npm run build
# 输出目录: dist/
```

## 代码规范

### 前端代码规范

- **组件命名**: PascalCase（如 `ChatInput.tsx`）
- **文件组织**: 按功能模块分组
- **样式**: 使用 Tailwind CSS 工具类
- **类型定义**: 所有组件和函数都应有完整的 TypeScript 类型
- **API 调用**: 统一通过 service 层，不在组件中直接调用
- **状态管理**: 全局状态使用 Zustand，局部状态使用 React useState
- **错误处理**: 使用 ErrorBoundary 包裹，API 错误在 service 层统一处理

### 组件结构示例

```tsx
import * as React from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { useChatStore } from "@/stores/chatStore";

interface ComponentProps {
  title: string;
  onAction?: () => void;
}

export function Component({ title, onAction }: ComponentProps) {
  const [state, setState] = React.useState(false);
  const navigate = useNavigate();
  
  // 组件逻辑
  
  return (
    <div className="flex flex-col gap-4">
      {/* JSX 内容 */}
    </div>
  );
}
```

## 常见问题

### Q: RAG 前端提示 "No static resource api/ragent/knowledge-base"
**A**: 确保后端服务在 9090 端口运行，前端代理已配置。重启开发服务器。

### Q: 如何创建管理员账号？
**A**: 在数据库中设置用户的 role 字段为 'admin'：
```sql
UPDATE t_user SET role = 'admin' WHERE username = 'your_username';
```

### Q: 前端端口被占用？
**A**: Vite 会自动尝试下一个端口（5174、5175...）

### Q: 如何调试 API 请求？
**A**: 打开浏览器开发者工具（F12）-> Network 标签查看请求详情

## Git 工作流

- **主分支**: `master`
- **提交规范**: 
  - `feat:` 新功能
  - `fix:` 修复 bug
  - `docs:` 文档更新
  - `refactor:` 代码重构
  - `style:` 代码格式调整
  - `test:` 测试相关

## 注意事项

1. **源码同步**: `athena_rag_front` 和 `Back_End/athena-rag/frontend` 是同一个前端项目，后者是开发版本，前者是部署版本
2. **依赖安装**: 首次克隆项目后，需要在每个前端项目目录执行 `npm install`
3. **环境变量**: 不要提交 `.env` 文件，使用 `.env.example` 作为模板
4. **node_modules**: 已在 `.gitignore` 中排除，不提交到仓库

## 资源链接

- [React 文档](https://react.dev/)
- [TypeScript 文档](https://www.typescriptlang.org/)
- [Tailwind CSS](https://tailwindcss.com/)
- [Radix UI](https://www.radix-ui.com/)
- [shadcn/ui](https://ui.shadcn.com/)
- [Zustand](https://zustand-demo.pmnd.rs/)
- [React Router](https://reactrouter.com/)
