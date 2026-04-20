# 教学对话纪要：Session 03

## 会话背景

本轮会话主要围绕第 3 课 `framework` 基础设施层的内容沉淀与教学方式固化展开。

## 本轮完成内容

### 1. 调整了课程编号结构

为了让教学顺序与实际理解过程更一致，已将原先作为“第三节课”的配置文件讲解内容重新定位为：

- `第 2.1 课：从 application.yaml 反推系统能力边界`

对应文件已调整为：

- `lessons/lesson-02.1-application-config-overview.md`

同时，正式的第 3 课恢复为：

- `第 3 课：framework 基础设施层`

对应文件为：

- `lessons/lesson-03-framework-infrastructure.md`

### 2. 将第 3 课改写为更正式的讲义风格

本轮已将第 3 课内容从“对话式讲解稿”整理为更正式的 Markdown 课程讲义，强化了以下部分：

- 课程信息
- 学习目标
- 课前提醒
- 模块定位
- 分节结构
- 本课小结
- 课后思考
- 后续教学记录约定

整体风格已更接近正式课程讲义，方便用户直接在文件中持续阅读。

### 3. 固化了后续教学内容交付方式

用户明确提出：

- 后续课程优先直接写入课程文件
- 不需要每次在对话里再完整重复发送讲义内容
- 只有在有问题或需要修改时，再回到课程文件进行调整
- 课程内容应以 Markdown 文件形式为主，因为可读性更好

基于此，后续教学统一遵循以下规则：

1. 课程内容优先直接写入 `docs/teaching/lessons/`。
2. 对话中默认只提示：已更新哪个文件、当前讲到哪一课。
3. 如用户提出补充、修订、风格调整，再对现有课程文件进行修改。
4. 阶段性教学策略、互动反馈与课程编排变化，写入 `docs/teaching/conversations/`。

## 本轮涉及的文件

### 课程文件

- `docs/teaching/lessons/lesson-02.1-application-config-overview.md`
- `docs/teaching/lessons/lesson-03-framework-infrastructure.md`

### 索引文件

- `docs/teaching/README.md`

### 对话记录文件

- `docs/teaching/conversations/session-03.md`

## 下一步建议

后续可以直接进入：

- `第 4 课：infra-ai —— AI 模型抽象层`

并继续沿用“先直接写课程文件，再在对话里简要提示”的方式推进。
