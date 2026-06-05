# Athena

## 项目介绍

## 系统架构


#### 介绍
# Athena 后端
本仓库包含一个后端端项目：

# Athena 前端项目集合
 
本仓库包含三个前端项目：

## 项目结构

### 1. admin_web_front - 管理后台 Web 前端
- **技术栈**: React + TypeScript + Vite + Tailwind CSS
- **功能**: 内容管理、文章发布、审核系统
- **启动命令**: 
  ```bash
  cd admin_web_front
  npm install
  npm run dev
  ```

### 2. athena_app_front - Android 移动应用
- **技术栈**: Kotlin + Android SDK + Gradle
- **功能**: Android 原生应用前端
- **构建命令**:
  ```bash
  cd athena_app_front
  ./gradlew build
  ```

### 3. athena_front/rag-frontend - RAG 系统前端
- **技术栈**: React + TypeScript + Vite
- **功能**: RAG（检索增强生成）系统交互界面
- **启动命令**:
  ```bash
  cd athena_front/rag-frontend/frontend
  npm install
  npm run dev
  ```

## .gitignore 说明

本仓库已配置完善的 .gitignore，排除了以下内容：
- Node.js 依赖 (node_modules)
- 构建输出 (dist, build)
- IDE 配置 (.idea, .vscode)
- 环境变量文件 (.env, .env.local)
- Gradle 缓存 (.gradle, .gradle-user)
- Android 缓存 (.android-user)
- 日志文件 (*.log)

## 提交信息

- **初始提交**: 包含三个前端项目的完整源代码
- **文件数量**: 583 个文件
- **代码行数**: 74,551 行

