# 认知闭环 V1：契约 §15 测试用例验证记录（TC-01 ~ TC-23）

> 契约：`Back_End/docs/newfunction/cognition-contract-v1.md` §15
> 代码：`athena-cognition/athena-cognition-biz`（P1 ~ P4-2 已实现全部接口与规则）
> 验证日期：2026-08-21　全量测试：`mvn -q -pl athena-cognition/athena-cognition-biz -am test` 全绿（91 个测试，0 失败）

状态说明：**PASS** = 本卡新增或已有自动化测试通过；**COVERED** = 前面卡片已有自动化测试覆盖（本卡指认）；
**MANUAL** = 当前环境无法自动化，附手动验证步骤。

测试类位置（均在 `athena-cognition-biz/src/test/java/athena/cognition/biz/`）：

- `service/CognitionServiceTest.java`（嵌套组：MarkerSaveRules / RevokeRules / DigestDecisions / DigestTasks / AutoThreshold / FailureAndRetry / ActionFeedbackRules / UserIsolation / HomeSummaryStates）
- `controller/CognitionExceptionHandlerTest.java`
- `domain/MaturityCalculatorTest.java`、`domain/CognitionStateMachineTest.java`
- `generator/FixedDigestGeneratorTest.java`
- `bodyrecord/DailyRecordEvidenceProviderTest.java`

| TC | 标题 | 契约预期 | 验证方式（测试方法） | 状态 |
| --- | --- | --- | --- | --- |
| TC-01 | 确认现在有类似情况 | RELATED+CURRENT 创建 PENDING 线索；未达阈值不建草稿/主题/行动 | `MarkerSaveRules.relatedCurrentIsSavedAsPending`（PENDING + digestTask.triggered=false）；`AutoThreshold.belowThresholdDoesNotTrigger`（never insertTask） | COVERED |
| TC-02 | 以前出现过 | RELATED+PAST 保存自述；仅接受草稿后进入 knownFacts | `DigestDecisions.tc02PastSelfReportEntersKnownFactsOnAccept`（insertTopic 的 knownFactsJson 含「以前出现过」+ 线索翻 ORGANIZED） | PASS（本卡新增） |
| TC-03 | 不确定但想观察 | OBSERVE 可参与整理，但不能写入 knownFacts | `MarkerSaveRules.tc03RelatedObserveIsSavedAsPending`；`DigestDecisions.tc03ObserveCluesParticipateButNeverEnterKnownFacts`（knownFactsJson 精确等于 `[]`） | PASS（本卡新增） |
| TC-04 | 保存为知识 | KNOWLEDGE_ONLY 线索为 ORGANIZED；不触发身体草稿、不进健康事实 | `MarkerSaveRules.relationKnowledgeOnlyIsSavedOrganizedAndOutOfThreshold`；`AutoThreshold.questionAndKnowledgeOnlyNeverCountIntoThreshold` | COVERED |
| TC-05 | 我有疑问 | 显示在「我的疑问」；不能单独触发主题、不能写身体事实 | `MarkerSaveRules.questionIsSavedPendingAndNeverCreatesDigest`；`AutoThreshold.questionAndKnowledgeOnlyNeverCountIntoThreshold`（不查分组不建任务）；`FixedDigestGeneratorTest.questionClueIsNotRewrittenAsBodyFact` | COVERED |
| TC-06 | 主动帮我整理 | 创建 task 和 digest；固定生成器返回 READY；证据可追溯 | `DigestTasks.tc06UserRequestedTaskBuildsTraceableReadyDigest`（SUCCEEDED/READY + linkDigestClues/linkDigestEvidence/completeDigest 全链路，证据 sourceId=真实 clue id） | PASS（本卡新增） |
| TC-07 | 自动阈值 | 同候选主题 3 条 RELATED 或 2 条+1 确认身体记录；只创建一个开放任务 | `AutoThreshold.rule1TriggersOnThirdRelatedClueInCandidateGroup` / `rule2FiresWhenProviderConfirmsBodyRecord` / `rule2StaysInertWhenProviderConfirmsNothing` / `openDigestBlocksDuplicateAutomaticTask`；`DigestTasks.openDigestOnSameCluesBlocksNewTask`（主动撞开放草稿 TASK_RUNNING） | COVERED |
| TC-08 | 接受草稿 | 事务内更新草稿和线索，创建主题、证据和待完成行动 | `DigestDecisions.acceptDigestComputesMaturityAndDomainFromEvidence`（主题/行动/证据关联/计数/成熟度/domain 全断言） | COVERED |
| TC-09 | 只将草稿保存为知识 | 草稿 KEPT_AS_KNOWLEDGE；不创建主题和行动 | `DigestDecisions.keepAsKnowledgeNeverCreatesTopicOrAction` | COVERED |
| TC-10 | 拒绝草稿 | 草稿 REJECTED，源线索 DISMISSED；不创建主题和行动 | `DigestDecisions.rejectMarksSourceCluesDismissed` | COVERED |
| TC-11 | 重复决定 | 返回 COGNITION_STATE_CONFLICT；不产生重复主题/行动 | `DigestDecisions.decidedDigestCannotBeDecidedAgain`（带 currentStatus=ACCEPTED）；唯一约束兜底见 `CognitionExceptionHandlerTest.uniqueConstraintViolationMapsToStateConflict` | COVERED |
| TC-12 | 行动反馈出现了 | 行动完成、反馈入库、新增证据、主题版本和计数更新、首页刷新 | `ActionFeedbackRules.tc12OccurredCompletesActionCreatesEvidenceAndRefreshesTopic`（evidenceId 回写/计数 (2,1,1,1)/topicVersion/refreshRequired=true） | COVERED |
| TC-13 | 行动反馈没有出现 | 保存为观察证据；不能直接结束主题 | `ActionFeedbackRules.tc13NotOccurredCreatesObservationEvidenceWithoutEndingTopic`（OBSERVED 证据、action COMPLETED、主题无结束路径） | COVERED |
| TC-14 | 行动反馈不确定 | 保存反馈和证据，但不提高成熟度 | `ActionFeedbackRules.tc14UncertainKeepsEvidenceWithoutRaisingMaturity`（证据入库但成熟度保持 CLUE）；`MaturityCalculatorTest.uncertainFeedbackNeverRaisesMaturity` | COVERED |
| TC-15 | 跳过行动 | 行动 SKIPPED；不生成 evidenceId，不增加证据和身体记录数 | `ActionFeedbackRules.tc15SkippedHasNoEvidenceAndNoCounterIncrease`（evidenceId=null、never insertEvidence、计数原样、occurredAt 缺省取提交时间） | COVERED |
| TC-16 | 重复行动反馈 | 返回状态冲突，不重复计数 | `FailureAndRetry.duplicateFeedbackStillConflicts`（COGNITION_STATE_CONFLICT + never insertFeedback） | COVERED |
| TC-17 | 撤销未处理线索 | 成功撤销；线索页刷新 | `RevokeRules.pendingClueNotInDigestCanBeRevoked`；`RevokeRules.knowledgeOnlyClueNotInDigestCanBeRevoked` | COVERED |
| TC-18 | 撤销已进入草稿的线索 | 返回 COGNITION_CLUE_IN_USE，证据不丢失 | `RevokeRules.clueInDigestCannotBeRevoked`（409 + never logicalDeleteClue） | COVERED |
| TC-19 | 整理失败和重试 | 失败状态可查，源线索保留；重试后同一证据生成 READY 草稿 | `FailureAndRetry.failedGenerationKeepsFailureStateQueryableAndCluesProcessing` / `retryReusesOriginalDigestAndEvidence` / `secondRetryAfterSuccessConflictsWithoutSideEffects` / `retryOfNonFailedTaskReturnsStateConflict` | COVERED |
| TC-20 | 首页无数据 | GET /home 返回 200、summaryState=EMPTY、可空对象为 null | `HomeSummaryStates.tc20EmptyUserGetsEmptyStateWithNulls` | COVERED |
| TC-21 | 用户隔离 | 用户 A 请求用户 B 的对象返回统一「未找到」，不泄露 | `UserIsolation` 全部 5 个方法（foreignDigest/Topic/ClueRevoke/Decision 均 404 NOT_FOUND；feedbackWithMismatchedTopicReadsAsNotFoundWithoutLeak 验证不泄露 action 存在性）；`DigestTasks.taskRejectsClueIdsNotOwnedByCurrentUser` | PASS（本卡新增） |
| TC-22 | 广场隔离 | 伪造 source=SQUARE 或广场帖子 ID 被拒绝，不进入闭环 | `MarkerSaveRules.tc22ForgedNonArticleSourceIsRejected`（非文章类型拒绝入库）；`CognitionExceptionHandlerTest.tc22ForgedEnumValueFailsParsingAsInvalidArgument`（SQUARE 反序列化失败 → 400 COGNITION_INVALID_ARGUMENT）；服务端 source 列固定 KNOWLEDGE_ARTICLE | PASS（本卡新增） |
| TC-23 | 换设备和重新登录 | 设备 B 登录同账号能查到相同线索、草稿、主题和行动 | MANUAL，步骤见下 | MANUAL |

## TC-23 手动验证步骤

本质：数据全部服务端持久化（MySQL 11 表），客户端无本地状态依赖。

1. 设备 A 登录测试账号，依次完成：POST /clues 创建标记 → POST /digest-tasks 整理 → POST /digests/{id}/decision 接受 → POST /actions/{id}/feedback 反馈。
2. 退出登录或直接换设备 B，用同一账号重新登录（换新 Bearer token）。
3. 设备 B 依次调用：GET /clues?view=ALL、GET /inbox、GET /digests/{digestId}、GET /topics/{topicId}、GET /home。
4. 预期：各接口返回与设备 A 操作后完全一致的数据（ID 链 clueId→digestId→topicId→actionId→feedbackId/evidenceId 可追溯）；GET /home 的 summaryState 与设备 A 最后一次操作后的状态一致。
5. 数据库侧抽查：`cognition_clue/cognition_digest/cognition_topic/cognition_action/cognition_action_feedback` 中该 user_id 的记录齐全且 deleted=0。

## 备注

- 所有自动化测试均为 mock repository / mock Feign 的单元测试，不依赖真实 MySQL、Nacos 或 athena-record 实例；联调环境的端到端 HTTP 验证按契约 §14 阶段 C/D 另行执行。
- 测试与接口文档的一致性由 `.tools/yaml-check/` 的脚本（路径/枚举/字段 grep 交叉核对）保证，当前零矛盾。
