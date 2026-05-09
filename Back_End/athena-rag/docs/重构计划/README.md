# Triage v2 重构计划总览

本目录用于承接 triage v2 当前这批结构性问题的重构方案，供后续实现窗口直接执行。

这不是实现代码目录，也不是 case 调整目录，而是结构设计与迁移设计目录。

## 目录说明

### 1. `00-总纲.md`

主文档。回答这轮最核心的问题：
- 为什么现在必须先做结构而不是继续补规则
- 当前实际架构哪里已经长歪
- 哪些 source of truth 必须拍板
- 建议的模块拆分是什么
- 增量迁移顺序是什么
- 验证策略是什么
- 下一步明确不做什么

### 2. `01-source-of-truth-决策.md`

把最容易反复摇摆的 source-of-truth 问题一次说透：
- primaryComplaint
- `slotState.PRIMARY_SYMPTOM`
- complaint memory
- correction target
- risk semantic object
- planner input

这份文档的目标是：后续实现窗口不再靠猜。

### 3. `02-complaint-memory-and-correction.md`

专门讲 complaint memory 与 correction 的结构拆法：
- establish / carry / correct / replace 如何分层
- correction phrase parsing、target resolution、reducer consumption 如何拆
- 哪些逻辑必须迁出 `TurnUnderstandingWorker`

### 4. `03-risk-semantics-and-planner.md`

专门讲胸部风险链暴露出的结构问题：
- risk signal
- risk semantic object
- risk decision action
- risk history persistence
- 为什么 unresolved risk 不能回退到 `PRIMARY_SYMPTOM`
- planner 未来到底应消费什么对象

### 5. `04-module-split-蓝图.md`

按模块给出更细的拆分蓝图。每个模块都明确：
- Responsibilities
- Inputs
- Outputs
- What it must NOT do
- 与其他模块的依赖关系

### 6. `05-phase-migration-plan.md`

按阶段给出现实可执行的迁移计划：
- Phase 1 先做什么
- Phase 2 再做什么
- Phase 3 最后做什么
- 每一阶段的进入条件、完成条件、风险点

### 7. `06-validation-and-guardrails.md`

定义重构期间的保护验证：
- 腹痛保护样本
- 胸部风险样本
- 如何判断没有重新走回 v1 规则泥球
- 如何判断模块化是真的发生，而不是只换了函数名

## 使用方式

建议后续实现窗口按以下顺序阅读：

1. `00-总纲.md`
2. `01-source-of-truth-决策.md`
3. `02-complaint-memory-and-correction.md`
4. `03-risk-semantics-and-planner.md`
5. `04-module-split-蓝图.md`
6. `05-phase-migration-plan.md`
7. `06-validation-and-guardrails.md`

## 与旧文件的关系

原先的 `docs/triage-v2-structure-plan.md` 可以保留为单文件摘要版。

本目录中的文件是展开版、执行版、分主题版。后续如果要继续补结构方案，优先补这里，不建议再把所有内容继续堆进单一长文档。

## 这一组文档的设计原则

1. 不做 complaint-domain specific 方案
2. 不做 case-driven 方案
3. 不把所有逻辑继续堆在 `TurnUnderstandingWorker`
4. 不只讲理想终态，必须讲增量演进路径
5. 不混入实现代码与半实现产物
