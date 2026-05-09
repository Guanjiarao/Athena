# Development Report

## 1. 改了哪些文件

本轮直接修改的文件：

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/worker/ComplaintFallbackResolver.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/triage/worker/ComplaintFallbackResolverCompatibilityFallbackTest.java`

本轮明确保留、不改动的相关文件：

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/worker/TurnComplaintSemanticsCoordinator.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/triage/worker/SlotManager.java`
- judged artifact closure 相关文件

## 2. `035` 为什么还会失败

本轮 residual `TRIAGE-EXT-V2-035` 的失败原因不是：

- `SlotManager` 再次清掉了 `PRIMARY_SYMPTOM`
- `TurnComplaintSemanticsCoordinator` 没有从 `slotState` carry-forward

上一轮这两个方向已经恢复，而且这轮保留不推翻。

`035` 之所以仍失败，是因为它和已恢复的 `048` 看似相近，但第一轮文本并不完全相同：

- `035` 首轮：`胸口有点不舒服`
- `048` 首轮：`胸口不舒服`

当前 `ComplaintFallbackResolver.resolvePrimaryComplaint(...)` 能识别：

- `胸口不舒服 -> 胸部不适`

但不能识别：

- `胸口有点不舒服 -> 胸部不适`

于是 `035` 在第一轮并没有建立起主诉 `胸部不适`。

这样到了第二轮 `说不清是不是喘不上气` 时，即便：

- `TurnComplaintSemanticsCoordinator` 已经会从上下文 carry-forward
- `SlotManager` 也不会再把空 truth 时的主诉删掉

系统依然没有可 carry-forward 的胸部主诉来源，于是最终表现为：

- `expected primaryComplaint=胸部不适, actual=null`

所以 `035` 的 residual 根因是：

- 首轮胸部弱变体文本没有命中现有 complaint fallback 词形
- 不是上下文回填职责不够大
- 也不是要继续扩大 `complaintFromContext()`

## 3. 你这次改的是哪一条最小 carry-forward / complaint path

本轮改的是一个更小的 complaint entry path，而不是去扩大 carry-forward 逻辑：

- 在 `ComplaintFallbackResolver.resolvePrimaryComplaint(...)` 中补齐一个极小词形变体：
  - `胸口有点不舒服 -> 胸部不适`

也就是说，这轮没有去改：

- `TurnComplaintSemanticsCoordinator` 的上下文来源集合
- `SlotManager` 的主诉保留策略
- reducer / planner / risk 的行为线

本轮真正移动的边界是：

- 让 `035` 的首轮胸部 complaint 能进入与 `048` 同一条已恢复的主诉建立与后续 carry-forward 路径

这是一个真实边界移动，因为它改变的是：

- `035` 首轮是否能建立 `primaryComplaint=胸部不适`
- 从而决定第二轮 uncertain dyspnea follow-up 能否沿用既有主诉

不是重命名，也不是函数搬家。

## 4. 为什么这个修复不会打坏腹痛保护线

不会打坏腹痛保护线，原因是：

- 本轮没有改腹痛相关 fallback 规则
- 没有改 `resolveWeakSymptomWithBodyCue(...)`
- 没有改 `TurnComplaintSemanticsCoordinator` 的腹痛上下文回填路径
- 没有改 `SlotManager` 的“空 complaintTruth 不删除 PRIMARY_SYMPTOM”恢复结果

腹痛保护线依赖的恢复面保持不变。

## 5. 为什么这个修复不会打坏 `034 / 048 / 049`

不会打坏 `034 / 048 / 049`，原因分别是：

### 对 `034 / 049`

- 这两条依赖的是 `胸口闷 / 胸闷 -> 胸闷` 的既有识别
- 本轮没有删除也没有降级这条识别
- 对应测试仍保留并通过

### 对 `048`

- `048` 依赖的是 `胸口不舒服 -> 胸部不适`
- 本轮只是补了 `胸口有点不舒服` 这个更窄的同类词形
- 没有改变 `胸口不舒服` 的既有映射结果

### 对胸部 uncertain follow-up carry-forward 主线

- 本轮没有改 `TurnComplaintSemanticsCoordinator` 的 `complaintFromContext()` 来源范围
- 没有继续往里面混入更多 truth 来源
- 所以不会把已恢复的胸部 carry-forward 路径扩成更大的不稳定面

## 6. 为什么 judged artifact closure 仍然被保留

本轮没有动 judged artifact closure 的任何输出 contract：

- 没动 judged JSON complaint-truth 顶层字段
- 没动 judged markdown complaint-truth lines
- 没动 extended judged report generator / writer / support 的结构

同时，本轮实际重跑了上一轮用于证明 closure 的真实入口：

- `TriageEvalRunnerTest#shouldGenerateRegressionExtendedJudgementReportIntoFixedOutputsDirectory`

并且通过。

所以本轮 residual cleanup 没有打坏：

- judged artifact closure

## 7. 你有没有继续扩大 `complaintFromContext()` 的角色

没有扩大。

这轮我明确没有继续扩大 `complaintFromContext()` 的角色。

具体来说，本轮没有新增这些来源：

- 更多历史 turn understanding 来源
- 更多 reducer complaint truth 来源
- 更多 planner / risk / suppressed gap 相关来源
- 任何新的多跳 complaint 推断来源

`complaintFromContext()` 仍然保持上一轮已经恢复的最小职责范围，没有继续放大。

这轮真正的修复点在更前面的 complaint entry path：

- 让 `胸口有点不舒服` 能像 `胸口不舒服` 一样进入同一条已恢复主线

因此这仍然是最小修改，而不是新的 complaint truth 设计扩张。

## 8. 本轮还顺手修了什么

本轮只顺手修了一个与当前恢复边界一致的测试期望：

- `ComplaintFallbackResolverCompatibilityFallbackTest`

之前其中一条旧断言仍要求：

- `胸口闷 -> null`
- `胸闷 -> null`

这与已经恢复并明确需要保留的 `034 / 049` 现状冲突。

我把它收敛为与当前保留边界一致的测试：

- `胸口闷 -> 胸闷`
- `胸闷 -> 胸闷`
- `不是胸口痛，是胃这边不舒服 -> null`

这不是扩 scope，而是让兼容测试与当前已接受恢复边界保持一致。

## 9. 哪些东西我刻意没动

本轮刻意没有改：

- `TurnComplaintSemanticsCoordinator` 的上下文来源范围
- `SlotManager` 的主诉保留策略
- complaint truth ownership 设计
- observation / export closure 设计
- risk semantic layer
- planner / suppressedGaps 主线
- judged artifact closure 相关 writer / support / generator

## 10. 当前编辑文件的窄诊断状态

当前窄诊断结果：

- `ComplaintFallbackResolver.java`：无 linter 错误
- `ComplaintFallbackResolverCompatibilityFallbackTest.java`：无 linter 错误

本轮窄测试结果：

- `ComplaintFallbackResolverCompatibilityFallbackTest`：通过
- `TurnUnderstandingWorkerSemanticBoundaryTest`：通过
- `TriageEvalRunnerTest#shouldGenerateRegressionExtendedJudgementReportIntoFixedOutputsDirectory`：通过
- 总体命令结果：`BUILD SUCCESS`

## 11. 结论

本轮完成的是一个 residual regression cleanup cut：

1. 没有推翻上一轮 `SlotManager` / `TurnComplaintSemanticsCoordinator` 的恢复方向
2. 识别出 `035` 的 residual 根因是首轮文本 `胸口有点不舒服` 没命中现有 complaint fallback
3. 用一个极小的 complaint 词形补齐收掉 `035`
4. 没有扩大 `complaintFromContext()` 的角色
5. 保住了：
   - judged artifact closure
   - 腹痛保护线已恢复结果
   - 胸部 `034 / 048 / 049` 已恢复结果
