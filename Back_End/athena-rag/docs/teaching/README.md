# RAGent 项目教学讲义

## 说明

这个目录用于沉淀基于 `ragent` 项目的教学内容，分为三部分：

- `lessons/`：每节课的正式讲义
- `conversations/`：师生对话纪要与阶段总结
- `pr/`：按阶段记录集成 / 改造 PR 的设计与边界

## 当前安排

### 正式课程序列

- `lessons/lesson-01-rag-to-engineering.md`
- `lessons/lesson-02-project-architecture-overview.md`
- `lessons/lesson-03-framework-infrastructure.md`

### 课程补充与加深阅读

- `lessons/lesson-02.1-application-config-overview.md`
  - 定位：第 2 课的补充阅读
  - 作用：通过 `application.yaml` 反推系统能力边界，作为进入第 3 课前的源码预热
- `lessons/lesson-03.1-framework-code-walkthrough.md`
  - 定位：第 3 课的代码补读
  - 作用：结合源码片段拆解统一响应、异常处理、上下文、MQ 抽象与 SSE 封装

### PR 记录

- `pr/pr-01-to-pr-03.md`

## 使用方式

1. 每完成一节正式课程，新增对应讲义文件。
2. 如果出现适合插入主线的补充阅读，可单独记录，但需标注其与正式课程的关系。
3. 每轮有价值的教学对话，整理到 `conversations/` 下。
4. 课程内容默认优先直接写入对应 Markdown 文件，对话中不再重复完整发送。
5. 后续可以再补充：
   - 源码阅读索引
   - 每课练习答案
   - 实战任务记录
