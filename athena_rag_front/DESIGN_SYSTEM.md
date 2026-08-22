# Athena RAG 前端设计系统

## 设计理念

Athena RAG 采用**暖黄色主题**，营造温暖、舒适、专业的智能对话体验。设计灵感来自：
- 🍯 蜂蜜的温润质感
- 🌅 日出时分的暖色调
- 📜 羊皮纸的古典韵味
- ☕ 焦糖拿铁的温暖氛围

## 核心设计原则

1. **温暖而专业** - 暖色调传递亲切感，同时保持专业可信赖
2. **层次清晰** - 通过色彩深浅和阴影营造视觉层次
3. **微妙动效** - 柔和的过渡和悬浮动画增强互动体验
4. **可访问性优先** - 确保色彩对比度符合 WCAG AA 标准

## 色彩系统

### 主色调 (Primary Colors)

**蜜糖色系 (Honey)**
```css
honey-50:  #FFF9F0  /* 背景基础色 */
honey-100: #FFF5E6  /* 次级背景 */
honey-200: #FFEFD5  /* 边框/分割线 */
honey-300: #FFE8C5  /* 悬停状态 */
honey-400: #FFDEAD  /* 激活状态 */
honey-500: #FFB84D  /* 主要强调色 */
honey-600: #F59E0B  /* 按钮/链接 */
honey-700: #D97706  /* 按钮悬停 */
honey-800: #B45309  /* 深色文字 */
honey-900: #92400E  /* 最深文字 */
```

**温暖色系 (Warm)**
```css
warm-50:  #FFFAF4  /* 超浅背景 */
warm-100: #FFF4E0  /* 浅背景 */
warm-200: #FFEFD5  /* 卡片背景 */
warm-300: #FFE4B5  /* 边框 */
warm-400: #FFD699  /* 悬停边框 */
warm-500: #FFCF40  /* 辅助强调 */
warm-600: #C77700  /* 焦糖色 */
warm-700: #A36200  /* 深焦糖 */
warm-800: #7F4E00  /* 深棕色 */
warm-900: #5C3900  /* 最深棕色 */
```

### 语义色彩 (Semantic Colors)

```css
/* 成功 */
--success: #059669
--success-light: #D1FAE5

/* 警告 */
--warning: #F59E0B
--warning-light: #FEF3C7

/* 错误 */
--error: #DC2626
--error-light: #FEE2E2

/* 信息 */
--info: #D97706
--info-light: #FFEFD5
```

### 文字色彩 (Text Colors)

```css
--text-primary: #3E2723     /* 主要文字 - 深棕色 */
--text-secondary: #5D4037   /* 次要文字 */
--text-tertiary: #8D6E63    /* 三级文字 */
--text-muted: #A1887F       /* 弱化文字 */
--text-on-accent: #FFF9F0   /* 强调色上的文字 */
--text-warm: #6D4C41        /* 温暖色调文字 */
```

## 渐变系统

### 主要渐变

**日落渐变 (Gradient Sunset)**
```css
linear-gradient(135deg, #FFB84D 0%, #F59E0B 50%, #D97706 100%)
```
- 用途：主要按钮、品牌元素、图标背景
- 特点：充满活力的三色渐变，模拟日落色彩

**蜜糖渐变 (Gradient Honey)**
```css
linear-gradient(180deg, #FFF9F0 0%, #FEF3C7 100%)
```
- 用途：页面背景、大面积区域
- 特点：柔和的垂直渐变，营造温暖氛围

**温暖渐变 (Gradient Warm)**
```css
linear-gradient(135deg, #FFE8C5 0%, #FFD699 100%)
```
- 用途：卡片背景、悬浮层
- 特点：对角渐变，增加视觉深度

## 排版系统

### 字体家族

```css
/* 标题字体 - Fraunces (衬线) */
font-display: 'Fraunces', 'Georgia', serif

/* 正文字体 - Inter (无衬线) */
font-body: 'Inter Variable', 'Inter', ui-sans-serif, system-ui

/* 等宽字体 - JetBrains Mono */
font-mono: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace
```

### 字体大小

```css
text-xs: 12px
text-sm: 13px
text-base: 14px
text-lg: 15px
text-xl: 18px
text-2xl: 24px
text-3xl: 32px
text-4xl: 48px
```

### 字体权重

```css
font-normal: 400
font-medium: 500
font-semibold: 600
font-bold: 700
```

## 阴影系统

### 标准阴影

```css
--shadow-xs: 0 1px 2px rgba(121, 85, 72, 0.06)
--shadow-sm: 0 2px 8px rgba(121, 85, 72, 0.08)
--shadow-md: 0 4px 12px rgba(121, 85, 72, 0.10)
--shadow-lg: 0 8px 20px rgba(121, 85, 72, 0.12)
--shadow-xl: 0 12px 28px rgba(121, 85, 72, 0.15)
```

### 特殊阴影

```css
/* 温暖阴影 - 用于按钮和卡片 */
--shadow-warm: 0 4px 16px rgba(245, 158, 11, 0.15)

/* 光晕效果 - 用于聚焦状态 */
--shadow-glow: 0 0 20px rgba(255, 191, 64, 0.3)
```

## 圆角系统

```css
--radius-sm: 6px
--radius-md: 12px
--radius-lg: 16px
--radius-xl: 20px
--radius-full: 9999px
```

## 间距系统

```css
--spacing-xs: 4px
--spacing-sm: 8px
--spacing-md: 12px
--spacing-lg: 16px
--spacing-xl: 24px
--spacing-2xl: 32px
--spacing-3xl: 48px
--spacing-4xl: 64px
```

## 动画系统

### 关键帧动画

**淡入上浮 (Fade Up)**
```css
@keyframes fade-up {
  0% { opacity: 0; transform: translateY(10px); }
  100% { opacity: 1; transform: translateY(0); }
}
animation: fade-up 0.35s ease-out;
```

**光晕脉冲 (Glow)**
```css
@keyframes glow {
  0%, 100% { opacity: 0.5; filter: drop-shadow(0 0 8px rgba(255, 191, 64, 0.5)); }
  50% { opacity: 1; filter: drop-shadow(0 0 16px rgba(255, 191, 64, 0.8)); }
}
animation: glow 2.6s ease-in-out infinite;
```

**漂浮 (Float)**
```css
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
animation: float 6s ease-in-out infinite;
```

**闪烁 (Shimmer)**
```css
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
animation: shimmer 3s linear infinite;
```

## 组件设计规范

### 按钮 (Button)

**主要按钮**
```tsx
<button className="bg-gradient-sunset text-white px-6 py-2.5 rounded-full
                   shadow-warm hover:opacity-90 transition-opacity">
  确认
</button>
```

**次要按钮**
```tsx
<button className="bg-warm-100 text-warm-800 px-6 py-2.5 rounded-full
                   border border-honey-200 hover:bg-warm-200 transition-colors">
  取消
</button>
```

### 输入框 (Input)

```tsx
<input className="w-full px-4 py-3 rounded-xl border border-honey-200
                  bg-honey-50/95 text-warm-900 placeholder:text-warm-500
                  focus:border-honey-400 focus:ring-2 focus:ring-honey-300/20
                  transition-all" />
```

### 卡片 (Card)

```tsx
<div className="rounded-2xl border border-honey-200/80 bg-honey-50/80
                backdrop-blur p-6 shadow-sm hover:shadow-warm
                transition-shadow">
  {/* 卡片内容 */}
</div>
```

### 标签 (Badge)

```tsx
<span className="inline-flex items-center gap-2 px-3 py-1 rounded-full
                 bg-honey-200 text-warm-800 text-xs font-medium
                 border border-honey-300">
  标签
</span>
```

## 布局规范

### 页面背景

```css
background: linear-gradient(180deg, #FFF9F0 0%, #FEF3C7 100%);
```

### 内容容器

```css
max-width: 800px;
margin: 0 auto;
padding: 0 24px;
```

### 侧边栏

```css
background: #FFF5E6;
width: 280px;
border-right: 1px solid #F5DEB3;
```

## 交互状态

### 悬停 (Hover)

- 按钮：`opacity: 0.9` 或 颜色变深
- 卡片：`translate-y: -2px` + `shadow-warm`
- 链接：下划线 + 颜色变化

### 聚焦 (Focus)

- 输入框：`ring-2 ring-honey-300/20`
- 按钮：`outline-2 outline-honey-500`

### 激活 (Active)

- 按钮：`scale: 0.98`
- 卡片：背景色变深

### 禁用 (Disabled)

- 透明度：`opacity: 0.6`
- 光标：`cursor: not-allowed`

## 可访问性

### 色彩对比度

所有文字与背景的对比度符合 WCAG AA 标准：
- 普通文字：最小对比度 4.5:1
- 大号文字：最小对比度 3:1

### 键盘导航

- 所有交互元素支持 Tab 键导航
- 聚焦状态有明显视觉反馈
- 支持快捷键操作（Enter 发送、Shift+Enter 换行）

### 屏幕阅读器

- 使用语义化 HTML 标签
- 为图标和按钮提供 `aria-label`
- 表单元素有对应的 `<label>`

## 响应式设计

### 断点

```css
sm: 640px
md: 768px
lg: 1024px
xl: 1280px
2xl: 1536px
```

### 移动端优化

- 触摸目标最小 44x44px
- 字体大小不小于 14px
- 增加按钮和卡片间距
- 简化复杂布局

## 性能优化

### 动画性能

- 优先使用 CSS `transform` 和 `opacity`
- 避免在动画中修改 `width`、`height`
- 使用 `will-change` 提示浏览器优化

### 图片优化

- 使用 WebP 格式
- 提供多种尺寸
- 懒加载非首屏图片

### 渐变优化

- 使用 CSS 渐变而非图片
- 合理使用背景虚化 `backdrop-filter`

## 品牌元素

### Logo

- 使用日落渐变背景
- 圆角矩形容器 (rounded-2xl)
- 白色图标

### 加载动画

使用蜜糖色点阵跳动动画：
```tsx
<div className="flex gap-1">
  <div className="w-2 h-2 rounded-full bg-honey-600 animate-bounce" />
  <div className="w-2 h-2 rounded-full bg-honey-600 animate-bounce" style={{animationDelay: '0.1s'}} />
  <div className="w-2 h-2 rounded-full bg-honey-600 animate-bounce" style={{animationDelay: '0.2s'}} />
</div>
```

## 使用示例

### 登录页面

```tsx
<div className="min-h-screen bg-gradient-honey flex items-center justify-center">
  {/* 装饰性光晕 */}
  <div className="absolute top-20 left-20 w-96 h-96 bg-honey-400 
                  rounded-full mix-blend-multiply filter blur-3xl 
                  opacity-20 animate-float" />
  
  <div className="w-full max-w-md rounded-3xl border border-honey-300/50 
                  bg-honey-50/90 p-8 shadow-warm backdrop-blur">
    {/* 登录表单 */}
  </div>
</div>
```

### 聊天界面

```tsx
<div className="flex h-full flex-col bg-honey-50">
  {/* 消息列表 */}
  <div className="flex-1 overflow-y-auto">
    {messages.map(msg => (
      <div className={cn(
        "rounded-2xl p-4 shadow-sm",
        msg.role === 'user' 
          ? "bg-honey-200/80 text-warm-900 ml-auto max-w-[80%]"
          : "bg-white/80 text-warm-900"
      )}>
        {msg.content}
      </div>
    ))}
  </div>
  
  {/* 输入区域 */}
  <div className="bg-gradient-to-t from-honey-50 via-honey-50 to-transparent">
    <ChatInput />
  </div>
</div>
```

## 设计文件

- Figma: [链接]
- 色板下载: [链接]
- 图标库: Lucide React
- 字体文件: Google Fonts

## 维护更新

设计系统应保持动态更新：
- 定期审查色彩对比度
- 收集用户反馈优化交互
- 随技术发展更新最佳实践
- 保持与品牌形象一致

---

**设计系统版本**: 1.0.0  
**最后更新**: 2026-06-05  
**设计师**: Claude (Anthropic)
