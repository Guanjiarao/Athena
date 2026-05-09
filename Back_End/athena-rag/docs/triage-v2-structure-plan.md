# Plan: Triage v2 Structure Refactor

> 这份文件现在保留为单文件摘要入口。
>
> 更完整的分主题展开版已整理到：`docs/重构计划/`

## 推荐阅读顺序

1. `docs/重构计划/README.md`
2. `docs/重构计划/00-总纲.md`
3. `docs/重构计划/01-source-of-truth-决策.md`
4. `docs/重构计划/02-complaint-memory-and-correction.md`
5. `docs/重构计划/03-risk-semantics-and-planner.md`
6. `docs/重构计划/04-module-split-蓝图.md`
7. `docs/重构计划/05-phase-migration-plan.md`
8. `docs/重构计划/06-validation-and-guardrails.md`

## 这套文档解决什么问题

这组文档用于回答 triage v2 当前为什么不能继续边修边堆，以及下一步应如何以增量方式完成结构重构。

核心结论有三条：

1. session 级 `primaryComplaint` 的 source of truth 应该是 reducer 持有的 complaint memory，而不是 turn-level `primaryComplaint` 或单纯的 `slotState.PRIMARY_SYMPTOM`
2. correction 必须拆成 phrase parsing、state-aware target resolution、reducer consumption 三段，不能继续塞在 `TurnUnderstandingWorker`
3. risk 必须分成 signal / semantic / action 三层，planner 不应再把 unresolved risk 回退到 `PRIMARY_SYMPTOM`

## 目录分工

- `00-总纲.md`：整体问题与方向
- `01-source-of-truth-决策.md`：所有关键 truth owner 的拍板
- `02-complaint-memory-and-correction.md`：主诉记忆与纠正拆法
- `03-risk-semantics-and-planner.md`：风险语义层与 planner 输入边界
- `04-module-split-蓝图.md`：模块职责、输入、输出、禁区
- `05-phase-migration-plan.md`：Phase 1/2/3 增量迁移顺序
- `06-validation-and-guardrails.md`：保护样本、结构验收、红线

## 建议实现窗口先做什么

先做 `Phase 1`：
- 抽出 `ComplaintCandidateExtractor`
- 抽出 `FollowUpAnswerResolver`
- 抽出 `CorrectionPhraseParser`
- 把 complaint carry 收回 reducer 的 `ComplaintMemoryPolicy`

这是后续 correction 收口和胸部风险链收口的地基。
