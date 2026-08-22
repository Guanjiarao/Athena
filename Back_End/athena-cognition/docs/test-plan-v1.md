# Athena 认知闭环 V1 功能测试计划（经网关）

> 执行方式：JMeter 5.6.3，单线程组顺序执行（后续步骤依赖前面提取的 ID）
> 网关入口：`http://localhost:13715`（云上联调时换 `http://121.41.200.73:13715`）
> 请求头：`Authorization: Bearer HPZTciG1qFwjU8q5wwmCgcsJuEYsKLlH`（userId=1024）、`Content-Type: application/json`
> 响应信封：`{code, message, data, total}`；业务错误断言 `code` + `data.errorCode`，不看中文 message
> 前置状态：userId=1024 在 cognition 库为空（已通过 GET /home 返回 EMPTY 验证）

## TS-01 线索创建（三种标记 + 参数校验）

| # | 请求 | 断言 |
|---|---|---|
| 1 | POST /athena/cognition/clues：RELATED + CURRENT，带 articleId/articleTitle/selectedText/originalLabel="和我有关"、severity=3、cycleRelation=BEFORE_PERIOD | code=200；data.clue.status=PENDING；data.digestTask.triggered=false；提取 clueId_1 |
| 2 | 同上：QUESTION + IS_COMMON + questionText | code=200；status=PENDING；提取 clueId_q |
| 3 | 同上：KNOWLEDGE_ONLY | code=200；status=ORGANIZED；提取 clueId_k |
| 4 | severity=11（越界） | code=400；data.errorCode=COGNITION_INVALID_ARGUMENT |
| 5 | selectedText 为空 | code=400；COGNITION_INVALID_ARGUMENT |

## TS-02 线索查询与收件箱

| # | 请求 | 断言 |
|---|---|---|
| 1 | GET /clues?view=PENDING&page=1&pageSize=20 | code=200；total≥2；含 clueId_1、clueId_q |
| 2 | GET /clues?view=QUESTIONS | 含 clueId_q |
| 3 | GET /inbox | code=200；data.pendingClues 非空；data.counts.pending≥1；字段名与契约 §8.4 一致 |

## TS-03 帮我整理（USER_REQUEST）

| # | 请求 | 断言 |
|---|---|---|
| 1 | POST /digest-tasks：{triggerType:USER_REQUEST, clueIds:[clueId_1], suggestedTitle:"经前情绪变化"} | code=200；data.status=SUCCEEDED；data.digestStatus=READY；提取 taskId_1、digestId_1 |
| 2 | 立即重复提交同一线索整理 | code=409；data.errorCode=COGNITION_TASK_RUNNING |
| 3 | GET /digests/{digestId_1} | code=200；status=READY；evidence 数组非空且元素含 sourceType/summary；generatorVersion=fixed-v1；commonPoint/possibleRelation/uncertainty/suggestedAction 均非空 |

## TS-04 草稿三种决定

| # | 请求 | 断言 |
|---|---|---|
| 1 | POST /digests/{digestId_1}/decision：ACCEPT_AS_TOPIC，clientVersion=当前 version | code=200；data.digest.status=ACCEPTED；data.topic 非空（提取 topicId）；data.action 非空（提取 actionId_1）；topic.nextActionId=actionId_1 |
| 2 | 同 digestId_1 再提交任意决定 | code=409；COGNITION_STATE_CONFLICT |
| 3 | 用错误 clientVersion 对新草稿提交决定 | code=409；COGNITION_VERSION_CONFLICT |
| 4 | 新建 RELATED 线索 clueId_2 → 整理得 digestId_2 → KEEP_AS_KNOWLEDGE | code=200；data.topic=null 且 data.action=null；digest.status=KEPT_AS_KNOWLEDGE |
| 5 | 新建 RELATED 线索 clueId_3 → 整理得 digestId_3 → REJECT | code=200；topic/action=null；GET /clues 查 clueId_3 status=DISMISSED |

## TS-05 行动反馈

| # | 请求 | 断言 |
|---|---|---|
| 1 | POST /actions/{actionId_1}/feedback：{topicId, result:OCCURRED, note:"今天下午轻微情绪低落"} | code=200；data.feedback.evidenceId 非空；data.actionStatus=COMPLETED；data.refreshRequired=true；data.topicVersion 递增 |
| 2 | 同 actionId_1 再反馈 | code=409；COGNITION_STATE_CONFLICT |

## TS-06 健康首页

| # | 请求 | 断言 |
|---|---|---|
| 1 | GET /home（反馈后） | code=200；summaryState ∈ {ACTION_COMPLETED, OBSERVING}；activeTopic.id=topicId；latestInsight 非空；headline 非空 |

## TS-07 撤销与安全

| # | 请求 | 断言 |
|---|---|---|
| 1 | 新建 RELATED 线索 clueId_4 → DELETE /clues/{clueId_4} | code=200 |
| 2 | DELETE /clues/{clueId_1}（已入草稿） | code=409；COGNITION_CLUE_IN_USE |
| 3 | POST /clues 伪造 source=SQUARE | code=400；COGNITION_INVALID_ARGUMENT |
| 4 | 非法枚举 intent=FOO | code=400；COGNITION_INVALID_ARGUMENT |
| 5 | 不带 Authorization 请求 GET /home | code≠200（网关拦截） |

## TS-08 跨用户隔离（如可拿到第二个 token）

| # | 请求 | 断言 |
|---|---|---|
| 1 | 用第二个账号请求 userId=1024 的 digestId_1/topicId | code=404；COGNITION_NOT_FOUND；不泄露对象存在 |

## 备注

- digest 生成目前是 fixed-v1 固定文案，文案内容不做断言，只断言结构与状态
- RULE_2（身体记录联动）依赖 daily_record 数据，userId=1024 若无记录则该规则不触发，属预期
- 失败/重试（TC-19）在 fixed 生成器下不会自然失败，已由单测覆盖，JMeter 不测
