# Athena RAG 前端暖黄色主题 - 快速启动指南

## 🎨 主题特色

你的 Athena RAG 前端现在采用了全新的**暖黄色主题设计**，灵感来自：
- 🍯 蜂蜜般的温润质感
- 🌅 日出的温暖色调  
- 📜 羊皮纸的古典韵味
- ☕ 焦糖拿铁的舒适氛围

## ✨ 设计亮点

### 1. 温暖舒适的色彩系统
- **蜜糖色系** (Honey): 从浅到深的黄色渐变，营造温暖氛围
- **焦糖色系** (Warm): 深棕色调，提供视觉层次
- **日落渐变**: 三色渐变按钮，充满活力

### 2. 精致的视觉细节
- **柔和阴影**: 带有暖色调的阴影系统
- **光晕效果**: 聚焦时的温暖光晕
- **渐变背景**: 多层次背景营造深度感
- **装饰性光球**: 漂浮动画的背景装饰

### 3. 流畅的动画效果
- **淡入上浮**: 元素加载时的优雅入场
- **悬浮效果**: 卡片和按钮的微妙位移
- **脉冲动画**: 状态指示器的呼吸效果
- **光晕脉动**: 强调元素的闪烁动画

### 4. 优雅的字体搭配
- **标题**: Fraunces - 现代衬线字体，优雅而醒目
- **正文**: Inter - 清晰易读的无衬线字体
- **代码**: JetBrains Mono - 专业等宽字体

## 🚀 启动项目

### 1. 安装依赖

```bash
cd athena_rag_front/rag-frontend/frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

开发服务器将在 `http://localhost:5173` 启动（如果端口被占用，会自动尝试下一个端口）。

### 3. 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist/` 目录。

## 🎯 主要页面预览

### 登录页面 (`/login`)
- 渐变背景配合浮动光球
- 日落渐变的品牌图标
- 温暖的卡片设计
- 流畅的输入框交互

### 欢迎界面 (`/chat`)
- 居中的大标题，采用日落渐变
- 温暖的输入框设计
- 预设问题卡片，悬浮效果
- 深度思考模式切换

### 聊天界面 (`/chat/:sessionId`)
- 蜜糖色系的消息气泡
- 渐变背景的输入区域
- 流畅的消息滚动
- 优雅的加载动画

### 管理后台 (`/admin/*`)
- 温暖的侧边栏设计
- 蜜糖色调的数据卡片
- 清晰的表格布局
- 专业的数据可视化

## 🎨 自定义主题

### 修改主色调

编辑 `src/styles/globals.css`：

```css
:root {
  /* 调整主色调的色相 */
  --primary: 38 92% 50%;  /* HSL 格式，可调整色相值 */
  
  /* 或直接修改 CSS 变量 */
  --accent-primary: #F59E0B;  /* 改为你喜欢的颜色 */
}
```

### 修改字体

编辑 `index.html` 和 `tailwind.config.cjs`：

```html
<!-- index.html -->
<link href="https://fonts.googleapis.com/css2?family=YourFont&display=swap" rel="stylesheet" />
```

```javascript
// tailwind.config.cjs
fontFamily: {
  display: ["'YourFont'", "serif"],
  // ...
}
```

### 调整圆角

编辑 `src/styles/globals.css`：

```css
:root {
  --radius-sm: 8px;   /* 增大圆角 */
  --radius-md: 16px;
  --radius-lg: 24px;
}
```

## 📱 响应式设计

设计已针对不同设备优化：

- **手机** (< 640px): 单列布局，大号触摸目标
- **平板** (640px - 1024px): 双列布局，优化间距
- **桌面** (> 1024px): 多列布局，完整功能

## ♿ 可访问性

主题已考虑可访问性：

- ✅ 色彩对比度符合 WCAG AA 标准
- ✅ 支持键盘导航
- ✅ 提供屏幕阅读器标签
- ✅ 聚焦状态清晰可见

## 🔧 技术栈

- **框架**: React 18.3.1 + TypeScript
- **构建工具**: Vite 5.4.3
- **样式**: Tailwind CSS 3.4.10
- **UI组件**: Radix UI + shadcn/ui
- **图标**: Lucide React
- **状态管理**: Zustand 4.5.5
- **路由**: React Router DOM 6.26.2

## 📁 项目结构

```
athena_rag_front/rag-frontend/frontend/
├── src/
│   ├── components/          # React 组件
│   │   ├── chat/           # 聊天相关组件
│   │   ├── layout/         # 布局组件
│   │   └── ui/             # UI 基础组件
│   ├── pages/              # 页面组件
│   │   ├── ChatPage.tsx    # 聊天页面
│   │   ├── LoginPage.tsx   # 登录页面
│   │   └── admin/          # 管理后台页面
│   ├── styles/             # 样式文件
│   │   └── globals.css     # 全局样式和主题变量
│   ├── services/           # API 服务
│   ├── stores/             # Zustand 状态管理
│   └── utils/              # 工具函数
├── index.html              # HTML 入口
├── tailwind.config.cjs     # Tailwind 配置
└── vite.config.ts          # Vite 配置
```

## 🎓 设计系统文档

完整的设计系统文档请查看：
- `DESIGN_SYSTEM.md` - 详细的设计规范、色彩系统、组件库

## 🐛 已知问题

1. **初次加载字体闪烁**: Google Fonts 加载时可能有短暂的字体切换，可以通过自托管字体解决
2. **Safari 渐变渲染**: 某些 Safari 版本可能渐变显示略有差异

## 💡 使用建议

### 开发环境
- 建议使用 Chrome 或 Firefox 开发者工具
- 启用 React Developer Tools 扩展
- 使用 Tailwind CSS IntelliSense 插件（VS Code）

### 代码编辑器
- VS Code (推荐)
- WebStorm
- Cursor

### 浏览器支持
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## 🤝 贡献指南

如果你想调整设计：

1. 修改 `src/styles/globals.css` 中的 CSS 变量
2. 更新 `tailwind.config.cjs` 中的配置
3. 在组件中使用新的样式类
4. 测试在不同设备上的表现
5. 更新 `DESIGN_SYSTEM.md` 文档

## 📞 获取帮助

遇到问题？查看：
- `DESIGN_SYSTEM.md` - 完整设计系统文档
- `CLAUDE.md` - 项目整体文档
- `TESTING.md` - 测试和故障排除

## 🎉 开始使用

现在你可以：

1. **启动项目**: `npm run dev`
2. **访问页面**: `http://localhost:5173`
3. **登录系统**: 使用管理员账号登录
4. **体验聊天**: 在温暖舒适的界面中开始对话
5. **探索后台**: 查看管理后台的数据可视化

祝你使用愉快！✨

---

**主题版本**: 1.0.0 - Honey Sunset  
**设计日期**: 2026-06-05  
**设计师**: Claude (Anthropic)
