# Athena 认知闭环 V1：第一次前后端交接说明

> 文档状态：第一次交接基线，按 2026-08-13 的 Android 前端代码重写  
> 面向对象：Athena 后端开发者  
> 本次目标：把前端现有 Mock 闭环替换为真实数据库和 HTTP 接口

## 0. 先看这里：本次不接 Agent，但后续一定会接

当前 Android 前端已经做完认知闭环的页面和交互，但页面里显示的数据全部来自本地 Mock，不是服务器真实数据，也没有调用 AI。

整个项目分两次交接：

### 第一次交接：现在

后端现在负责：

```text
建表
-> 保存和查询真实数据
-> 实现状态流转
-> 提供 HTTP 接口
-> 接入登录用户隔离
-> 用固定、结构化的测试草稿跑通联调
```

这次先把业务骨架和真实数据链路做对。此时“整理草稿”可以由后端测试代码生成固定内容，但必须真实保存到数据库，并走正式接口和正式状态。

### 第二次交接：以后

Agent 工作流由前端负责人先完成。完成后会再次交给后端，交付内容包括：

```text
Agent 工作流
Prompt
每个节点的输入/输出 JSON Schema
阈值和安全校验规则
正确、错误及边界测试样例
本地可运行原型
```

后端收到第二次交接后负责部署 Agent 的正式运行环境，包括：

```text
从数据库组装 Agent 输入
保管模型密钥
调用模型和执行工作流
校验结构化输出
保存任务和草稿
处理异步执行、超时、重试、幂等、日志和监控
```

**不要在第一次交接中自行设计 Prompt 或 Agent，也不要等待 Agent 才开始开发。** 当前应先完成不依赖 AI 的数据库、接口和状态流转。第二次交接只替换“草稿如何生成”，不会推翻本文件的数据结构和接口。

---

## 1. 用一句话说明后端要做什么

把这条已经能在 Android 上点击演示的 Mock 链路，改成每一步都由真实服务器保存、读取和推进：

```text
文章选中一段文字
-> 用户说明“和我有关 / 我有疑问 / 保存为知识”
-> 保存线索
-> 满足条件或用户主动要求时生成整理草稿
-> 用户接受、只存知识或拒绝
-> 接受后创建认知主题和下一步行动
-> 用户提交行动反馈
-> 更新主题和健康首页
```

后端不需要先理解所有产品历史，只要先理解下面四个对象：

| 对象 | 通俗解释 | 例子 |
| --- | --- | --- |
| 线索 `clue` | 用户留下的一条原始信息 | 标记文章中的一句话，并选择“我现在有类似情况” |
| 整理草稿 `digest` | 系统根据多条线索做出的待确认整理 | “这些线索可能都与经前情绪变化有关，但还不能确定规律” |
| 认知主题 `topic` | 用户接受草稿后，决定持续观察的一件事 | “经前情绪变化” |
| 行动 `action` | 为主题补充证据的下一步任务 | “下次出现变化时记录时间和程度” |

四条硬规则：

1. 阅读文章不等于用户有这个症状。
2. “我有疑问”不等于用户确认自己出现过。
3. 整理草稿不等于正式主题，只有用户点击接受后才能创建主题。
4. 广场内容不进入本闭环，不参与健康首页和后续 Agent 判断。

---

## 2. 当前前端到底做到了什么

### 2.1 已完成页面和入口

当前 Android 已完成以下可操作页面：

| 页面 | 当前用户能做什么 |
| --- | --- |
| 文章详情 | 选中文字，选择“和我有关”“我有疑问”或“保存为知识” |
| 和我有关确认页 | 选择与自己的关系、希望获得的帮助，并可补充日期、周期关系、程度、是否缓解 |
| 我有疑问页 | 选择问题类型或输入自己的问题 |
| 我的身体线索 | 查看“待整理”“已整理”“我的疑问”，主动点击“帮我整理” |
| 整理草稿 | 查看共同点、可能联系、不确定性、证据和下一步建议 |
| 草稿决定 | 选择“加入正在观察”“只保存为知识”“这不是我想表达的” |
| 认知主题详情 | 查看阶段理解、身体事实、证据来源、待确认问题、下一步行动和反馈历史 |
| 行动反馈 | 选择“出现了”“没有出现”“不确定”“暂时跳过”，可填写备注 |
| 健康首页 | 根据线索、草稿、主题和行动显示聚合状态 |
| 我的 | 通过“我的身体线索”进入线索页 |

### 2.2 当前全部是 Mock

当前所有认知数据都保存在 Android 本地：

```text
CognitionRepositoryProvider
-> DemoCognitionRepository
-> SharedPreferences("cognition_demo_v1")
-> Gson 保存一份 Snapshot JSON
```

这套代码只用于证明页面和交互能跑通。它没有：

```text
服务器持久化
多设备同步
真实用户隔离
真实规则任务
真实 Agent
真实模型调用
```

后端完成后，前端会把 `DemoCognitionRepository` 替换为 HTTP 数据源。页面、字段和枚举不应再重新设计。

### 2.3 Mock 中有一个演示捷径，后端禁止照搬

为了让演示者第一次点击就能看到完整闭环，Mock 初始化时自动放入：

```text
2 条预置文章线索
1 条预置身体记录
```

用户再提交 1 条 `RELATED` 文章线索后，前端立即生成固定草稿。

这只是演示基线，不是真实业务规则。正式后端必须从空数据开始，不得给真实用户自动插入这三条数据，也不得写成“用户标记一次就一定生成草稿”。

### 2.4 只属于 Mock、不能进入正式 API 的字段和能力

以下内容只用于前端演示，不是后端契约：

```text
DemoStage
Snapshot
activeDigestId / activeTopicId / activeActionId 这种全局单例状态
loadDemoScenario(...)
resetDemoData()
completeDigestWithDemoDraft()
PRESET_GENERATION_FAILED
demo_preset_* 数据
mock-v1 生成器
SharedPreferences 迁移逻辑
```

正式后端可以有自己的当前主题选择规则，但不能把每个用户限制成永远只有一个草稿、一个主题和一个行动。

---

## 3. 第一次交接的准确范围

### 3.1 后端现在必须完成

1. 决定认知业务放在哪个现有服务，并配置网关路由。建议放入 `athena-insight`，因为该服务已经负责用户洞察和健康首页相关聚合；不要塞入问诊会话或旧分析报告表。
2. 建立线索、草稿、主题、主题证据、行动、行动反馈、整理任务所需数据表。
3. 沿用项目现有 Spring Boot 3、Java 17、MyBatis Plus、`Result<T>`、网关和 `UserIdHolder` 约定。
4. 实现本文第 8 节的全部 HTTP 接口。
5. 所有数据按登录用户隔离。`userId` 只能从 `UserIdHolder.getUserId()` 获取，不能接受前端传入的 `userId`。
6. 实现接受、只存知识、拒绝、反馈、撤销等状态流转及事务。
7. 实现健康首页聚合接口，不让 Android 自己请求多个接口后猜状态。
8. 第一次联调使用固定结构化草稿，但草稿必须真实建任务、真实入库、真实返回。
9. 为第二次 Agent 交接预留任务状态、生成器版本、失败码、重试次数和执行器接口。
10. 提供数据库迁移脚本、OpenAPI/Swagger、接口测试和可联调环境。

### 3.2 后端现在不要做

```text
不要自行写 Prompt
不要自行设计 Agent 工作流
不要把模型密钥放进 Android
不要让 Android 调模型
不要让 AI 直接创建正式主题
不要重构旧 AnalysisReportActivity
不要把广场帖子作为线索来源
不要用前端的 SharedPreferences 作为正式数据来源
```

### 3.3 第一次交接完成标准

第一次交接通过时，应满足：

```text
文章标记来自真实 POST 接口
线索页来自真实 GET 接口
草稿来自真实任务和数据库
草稿决定由后端事务处理
主题详情来自真实 GET 接口
行动反馈真实入库
健康首页来自真实聚合接口
重新登录或换设备后仍能看到同一账号的数据
```

此时只有“草稿文案如何智能生成”仍是固定测试实现，其余都必须是真实后端。

### 3.4 页面与正式接口对照

后端看前端页面时，可以直接按下表理解，不需要从 Activity 名猜业务：

| 前端页面或操作 | 后端接口 |
| --- | --- |
| 文章详情提交三种标记 | `POST /athena/cognition/clues` |
| 保存后立即撤销 | `DELETE /athena/cognition/clues/{clueId}` |
| 我的身体线索三个标签 | `GET /athena/cognition/inbox` |
| 点击“帮我整理” | `POST /athena/cognition/digest-tasks` |
| 查看草稿 | `GET /athena/cognition/digests/{digestId}` |
| 草稿接受、只存知识或拒绝 | `POST /athena/cognition/digests/{digestId}/decision` |
| 认知主题标签 | `GET /athena/cognition/topics` |
| 认知主题详情 | `GET /athena/cognition/topics/{topicId}` |
| 提交下一步行动 | `POST /athena/cognition/actions/{actionId}/feedback` |
| 健康首页 | `GET /athena/cognition/home` |

文章中选中文字、弹出标记菜单、选择表单等属于前端交互，不需要后端接口。用户点击最终提交时才请求后端。

---

## 4. 业务对象和正式状态

### 4.1 线索 `Clue`

线索是原始输入，不能被草稿或主题覆盖掉。正式后端至少保存：

| 字段 | 类型 | 必填 | 谁提供 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | string | 是 | 后端 | 线索 ID |
| `type` | enum | 是 | 前端/后端 | 线索类型 |
| `intent` | enum | 是 | 前端 | 用户意图 |
| `relationType` | enum/null | 条件必填 | 前端 | `RELATED` 时用户与内容的关系 |
| `helpRequestType` | enum | 是 | 前端 | 用户希望 Athena 怎样帮助 |
| `articleId` | string/null | 文章线索必填 | 前端 | 来源文章 ID |
| `articleTitle` | string/null | 文章线索必填 | 前端 | 来源文章标题快照 |
| `articleType` | int/null | 否 | 前端 | 现有文章类型，当前默认 100 |
| `selectedText` | string | 是 | 前端 | 用户选中的原文或记录摘要 |
| `questionType` | enum/null | 问题必填 | 前端 | 问题类型 |
| `questionText` | string/null | 问题必填 | 前端 | 用户的问题 |
| `occurredAt` | ISO 8601/null | 否 | 前端 | 用户选择的发生时间 |
| `cycleRelation` | enum | 是 | 前端 | 默认 `UNKNOWN` |
| `severity` | int/null | 否 | 前端 | 0 到 10 |
| `resolved` | boolean/null | 否 | 前端 | 是否已经缓解 |
| `source` | enum | 是 | 后端校验 | 本轮文章入口固定 `KNOWLEDGE_ARTICLE` |
| `status` | enum | 是 | 后端 | 线索处理状态 |
| `suggestedTopicId` | string/null | 否 | 后端 | 未来匹配已有主题时使用 |
| `suggestedTopicTitle` | string/null | 否 | 前端暂传/后端校验 | 前端当前会传简单主题标签，不应视为 AI 结果 |
| `originalLabel` | string | 是 | 前端 | 用于还原用户当时点击的中文入口 |
| `createdAt` | ISO 8601 | 是 | 后端 | 创建时间 |
| `updatedAt` | ISO 8601 | 是 | 后端 | 更新时间 |

正式表还必须有 `user_id`、逻辑删除字段和必要索引，但不要把 `userId` 暴露成创建接口参数。

### 4.2 整理草稿 `Digest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 草稿 ID |
| `title` | string | 是 | 建议的主题名称 |
| `status` | enum | 是 | 草稿状态 |
| `commonPoint` | string | `READY` 时是 | 线索共同点 |
| `possibleRelation` | string | `READY` 时是 | 可能联系，必须保留不确定性 |
| `uncertainty` | string | `READY` 时是 | 当前不能确认什么 |
| `suggestedAction` | string | `READY` 时是 | 建议的下一步 |
| `evidenceIds` | string[] | 是 | 使用的证据 ID，不能为空 |
| `sourceClueIds` | string[] | 是 | 参与整理的线索 ID |
| `generatorVersion` | string | 是 | 第一次联调如 `fixed-v1`；Agent 接入后换成真实版本 |
| `generatedAt` | ISO 8601/null | 否 | 完成生成时间 |
| `failureCode` | string/null | 否 | 失败码 |
| `expiresAt` | ISO 8601/null | 否 | 可选过期时间 |
| `version` | int | 是 | 并发控制 |

### 4.3 认知主题 `Topic`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 主题 ID |
| `sourceDigestId` | string | 是 | 创建它的草稿 ID |
| `title` | string | 是 | 主题名称 |
| `domain` | string | 是 | 如 `MOOD`、`CYCLE`、`SLEEP`、`SYMPTOM`、`SEXUAL_HEALTH`、`OTHER` |
| `maturity` | enum | 是 | 认知成熟度 |
| `userProgress` | enum | 是 | 用户观察进度 |
| `riskStatus` | enum | 是 | 风险提示轴，不等于诊断 |
| `stageUnderstanding` | string | 是 | 当前阶段理解 |
| `knownFacts` | string[] | 是 | 仅用户确认过的事实 |
| `openQuestions` | string[] | 是 | 仍待确认的问题 |
| `evidenceIds` | string[] | 是 | 当前主题使用的全部证据 |
| `evidenceCount` | int | 是 | 证据总数 |
| `articleClueCount` | int | 是 | 文章线索数 |
| `bodyRecordCount` | int | 是 | 身体记录和有效反馈数 |
| `cycleCount` | int | 是 | 覆盖周期数 |
| `nextActionId` | string/null | 否 | 当前行动 ID |
| `lastUpdatedAt` | ISO 8601 | 是 | 最近更新时间 |
| `version` | int | 是 | 乐观锁版本 |

### 4.4 主题证据 `Evidence`

前端 Mock 直接把 `clueId` 或 `feedback.evidenceId` 放进列表。正式后端应建立独立证据对象，并分别关联草稿和主题，保证来源可追溯：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 证据关联 ID |
| `sourceType` | enum | `CLUE`、`BODY_RECORD`、`ACTION_FEEDBACK`、未来可扩展 `DEVICE` |
| `sourceId` | string | 原始对象 ID |
| `factLevel` | enum | `KNOWLEDGE`、`QUESTION`、`SELF_REPORTED`、`OBSERVED` |
| `summary` | string | 前端展示摘要 |
| `occurredAt` | ISO 8601/null | 原始事件时间 |
| `linkedAt` | ISO 8601 | 关联时间 |
| `active` | boolean | 撤销或失效时置 false |

草稿通过 `cognition_digest_evidence` 关联证据，主题通过 `cognition_topic_evidence` 关联证据。接受草稿时，把草稿使用的有效证据关联到主题；不要复制或覆盖原始文章线索和身体记录。

`KNOWLEDGE` 和 `QUESTION` 不能写入 `knownFacts`；只有用户明确确认的经历或身体记录可成为 `SELF_REPORTED`。

### 4.5 行动 `Action`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 行动 ID |
| `topicId` | string | 所属主题 |
| `title` | string | 行动标题 |
| `description` | string | 为什么要做 |
| `actionType` | enum | 行动类型 |
| `status` | enum | 行动状态 |
| `dueAt` | ISO 8601/null | 可选截止时间 |
| `feedbackOptions` | enum[] | 当前前端需要四种反馈选项 |
| `createdAt` | ISO 8601 | 创建时间 |

### 4.6 行动反馈 `ActionFeedback`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 反馈 ID |
| `actionId` | string | 对应行动 |
| `topicId` | string | 所属主题 |
| `result` | enum | 用户选择结果 |
| `note` | string | 用户备注，可空 |
| `occurredAt` | ISO 8601 | 用户所指事件时间；前端未传时可取提交时间 |
| `createdAt` | ISO 8601 | 提交时间 |
| `evidenceId` | string/null | `SKIPPED` 时为空，其他结果生成证据 |

### 4.7 整理任务 `DigestTask`

第一次联调也应有任务记录，以便第二次接 Agent 时不改业务表：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 任务 ID |
| `userId` | long | 所属用户，只在服务端使用 |
| `digestId` | string | 对应草稿 |
| `status` | enum | `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` |
| `triggerType` | enum | `RULE_THRESHOLD`、`USER_REQUEST`、`RETRY` |
| `generatorVersion` | string | 第一次 `fixed-v1`，以后是 Agent 版本 |
| `retryCount` | int | 重试次数 |
| `failureCode` | string/null | 失败码 |
| `createdAt` / `updatedAt` | ISO 8601 | 时间 |

### 4.8 怎样接入现有身体记录

身体记录已经由 `athena-record` 保存到 `daily_record`，认知服务不能要求 Android 为同一条记录再提交一份，也不能复制整张记录表。

当前后端已经有这条内部读取链路：

```text
athena-insight
-> InsightRecordFeignApi
-> GET /athena/record/internal/insight/records
-> athena-record 的 daily_record
```

因此正式实现应当：

1. 文章标记继续保存为 `cognition_clue`。
2. 规则检查需要身体记录时，由认知服务通过现有 Feign 能力读取当前用户的 `daily_record`。
3. 选中的身体记录建立 `Evidence(sourceType=BODY_RECORD, sourceId=daily_record.id)`。
4. 证据中保存必要的展示摘要快照，但原始记录仍归 `athena-record` 管理。
5. 身体记录被删除或修改后，认知服务需要重新校验证据，不能继续把失效数据当作有效事实。

`ClueType.BODY_RECORD` 作为前端模型兼容值和未来手工导入能力保留；正常的健康记录联动优先使用 `Evidence` 引用真实 `daily_record.id`，不再制造一条重复的 `cognition_clue`。

当前 `daily_record` 只有 `recordItemId` 和 `recordValue` 等基础字段。后端需要维护稳定的项目含义映射，例如当前前端的症状为 `recordItemId=3`、心情为 `recordItemId=4`；不能让 Agent 或认知服务猜数字含义。映射应放在服务端常量或配置中，并纳入测试。

---

## 5. 枚举：名称必须与前端一致

### 5.1 线索枚举

```text
ClueType:
ARTICLE_HIGHLIGHT
USER_QUESTION
BODY_RECORD
ACTION_FEEDBACK

ClueIntent:
RELATED
QUESTION
KNOWLEDGE_ONLY

RelationType:
CURRENT
PAST
OBSERVE
KNOWLEDGE_ONLY

HelpRequestType:
OBSERVE
KNOWLEDGE
ATTENTION
SAVE_ONLY

QuestionType:
IS_COMMON
POSSIBLE_CAUSES
SELF_CARE
PROFESSIONAL_HELP
CUSTOM

CycleRelation:
BEFORE_PERIOD
DURING_PERIOD
AFTER_PERIOD
NO_RELATION
UNKNOWN

ClueStatus:
PENDING
PROCESSING
ORGANIZED
DISMISSED
```

### 5.2 草稿、主题和行动枚举

```text
DigestStatus:
PROCESSING
READY
ACCEPTED
KEPT_AS_KNOWLEDGE
REJECTED
FAILED
EXPIRED

DigestDecision:
ACCEPT_AS_TOPIC
KEEP_AS_KNOWLEDGE
REJECT

Maturity:
CLUE
INSUFFICIENT
EARLY_LINK
REPEATED_PATTERN
RELATIVELY_STABLE

UserProgress:
PENDING_CONFIRMATION
FOLLOWING
OBSERVING
PAUSED
ARCHIVED

RiskStatus:
NONE
WATCH
PROFESSIONAL_HELP

ActionType:
RECORD_BODY
RECORD_MOOD
RECORD_SLEEP
READ_CONTENT
ANSWER_QUESTION
CONFIRM_STATUS

ActionStatus:
PENDING
COMPLETED
SKIPPED
EXPIRED

ActionFeedbackResult:
OCCURRED
NOT_OCCURRED
UNCERTAIN
SKIPPED

HomeSummaryState:
EMPTY
BUILDING_BASELINE
DIGEST_PROCESSING
DIGEST_READY
OBSERVING
ACTION_COMPLETED
DIGEST_KEPT_AS_KNOWLEDGE
DIGEST_REJECTED
DIGEST_FAILED
```

不要把中文展示文案存成状态值。枚举使用上述英文值，中文由前端展示。

---

## 6. 三种文章标记到底怎样保存

### 6.1 “和我有关”

前端提交前，用户必须选择：

```text
relationType：CURRENT / PAST / OBSERVE / KNOWLEDGE_ONLY
helpRequestType：OBSERVE / KNOWLEDGE / ATTENTION / SAVE_ONLY
```

还可能补充：

```text
occurredAt
cycleRelation
severity（0 到 10）
resolved
```

后端规则：

- `CURRENT`、`PAST`、`OBSERVE` 保存为 `intent=RELATED`、`status=PENDING`。
- `KNOWLEDGE_ONLY` 保存为 `intent=KNOWLEDGE_ONLY`、`status=ORGANIZED`，不进入身体主题阈值。
- `CURRENT` 和 `PAST` 表示用户自述经历，可在接受草稿后进入 `knownFacts`。
- `OBSERVE` 表示不确定，只能作为观察线索，不能进入 `knownFacts`。
- 即使是 `CURRENT`，也只是用户自述，不是医学诊断。

### 6.2 “我有疑问”

保存为：

```text
type = ARTICLE_HIGHLIGHT
intent = QUESTION
helpRequestType = KNOWLEDGE
questionType = 用户选择
questionText = 预设问题或自定义问题
status = PENDING
originalLabel = "我有疑问"
```

问题显示在“我的疑问”，不能单独触发身体主题，不能写入 `knownFacts`。

### 6.3 “保存为知识”

保存为：

```text
type = ARTICLE_HIGHLIGHT
intent = KNOWLEDGE_ONLY
relationType = KNOWLEDGE_ONLY
helpRequestType = SAVE_ONLY
status = ORGANIZED
originalLabel = "保存为知识"
```

它显示在“已整理”，可以用于未来知识推荐，但不能参与身体事实、主题成熟度或风险判断。

### 6.4 撤销

用户保存后可以立即点击“撤销”。前端当前只允许撤销：

- 尚未进入任何草稿的 `PENDING` 线索。
- 尚未进入任何草稿、直接保存的 `KNOWLEDGE_ONLY` 线索。

一旦线索进入草稿证据，撤销接口必须拒绝并返回状态冲突，不能静默删除证据。

---

## 7. 正式状态流转

### 7.1 线索到草稿

```text
创建 RELATED 线索
-> clue.status = PENDING
-> 没达到阈值：停在待整理
-> 达到阈值或用户点击“帮我整理”
-> 建 task 和 digest
-> clue.status = PROCESSING
-> digest.status = PROCESSING
-> 固定生成器完成
-> digest.status = READY
```

第一次联调的固定生成器只负责填充结构化草稿字段。它不能跳过 `PROCESSING`、任务记录和数据库保存。

### 7.2 草稿决定

#### 接受 `ACCEPT_AS_TOPIC`

必须在一个数据库事务中完成：

```text
确认 digest.status == READY
-> digest.status = ACCEPTED
-> source clue.status = ORGANIZED
-> 创建或更新 topic
-> 建立 evidence 关联
-> 创建 PENDING action
-> topic.nextActionId = action.id
-> 提交事务
```

#### 只保存知识 `KEEP_AS_KNOWLEDGE`

```text
digest.status = KEPT_AS_KNOWLEDGE
source clue.status = ORGANIZED
不创建 topic
不创建 action
不写入身体事实
```

#### 拒绝 `REJECT`

V1 与当前前端一致：

```text
digest.status = REJECTED
source clue.status = DISMISSED
不创建 topic
不创建 action
不写入身体事实
```

### 7.3 行动反馈

```text
确认 action 属于 topic 且状态为 PENDING
-> 保存 feedback
-> OCCURRED / NOT_OCCURRED / UNCERTAIN：action.status = COMPLETED，并创建 evidence
-> SKIPPED：action.status = SKIPPED，不创建 evidence
-> 更新 topic.version、lastUpdatedAt、stageUnderstanding 和计数
-> 返回 refreshRequired = true
```

`NOT_OCCURRED` 也是一次有效观察，所以可以形成证据；它不能直接结束主题。`UNCERTAIN` 可以保存，但不能提高成熟度。`SKIPPED` 不增加证据数。

### 7.4 重复提交和并发

- 同一草稿只能决定一次。重复决定返回业务冲突，不能重复创建主题和行动。
- 同一行动只能反馈一次。重复反馈返回业务冲突。
- 草稿决定和行动反馈都必须使用事务和唯一约束兜底。
- `Digest` 和 `Topic` 使用 `version` 做乐观锁；前端提交决定时带 `clientVersion`。

---

## 8. HTTP 接口：第一次联调按这些实现

### 8.1 路径、鉴权和统一响应

项目当前外部接口统一以 `/athena/` 开头，Android 的 `BASE_URL` 也是 `.../athena/`。因此本模块使用：

```text
/athena/cognition/**
```

建议路由到 `athena-insight`。若后端决定使用独立服务，需要同步更新网关，但不要改变下面的外部路径。

请求头沿用现有方式：

```http
Authorization: Bearer <token>
Content-Type: application/json
```

响应沿用项目现有 `athena.athenaframework.result.Result<T>`：

```json
{
  "code": 200,
  "message": "成功",
  "data": {},
  "total": null
}
```

分页列表使用 `data` 存数组、`total` 存总数。本文后续示例为节省篇幅主要展示 `data` 内容，实际响应必须有上述外层。

### 8.2 创建文章线索

```http
POST /athena/cognition/clues
```

“和我有关”的请求示例：

```json
{
  "type": "ARTICLE_HIGHLIGHT",
  "intent": "RELATED",
  "relationType": "CURRENT",
  "helpRequestType": "OBSERVE",
  "articleId": "1024",
  "articleTitle": "经期前情绪变化值得怎样记录",
  "articleType": 100,
  "selectedText": "经期前几天出现的情绪变化，需要结合时间和重复情况继续观察。",
  "suggestedTopicTitle": "经前情绪变化",
  "originalLabel": "和我有关",
  "occurredAt": "2026-08-10T00:00:00+08:00",
  "cycleRelation": "BEFORE_PERIOD",
  "severity": 3,
  "resolved": false
}
```

“我有疑问”在相同接口提交：

```json
{
  "type": "ARTICLE_HIGHLIGHT",
  "intent": "QUESTION",
  "helpRequestType": "KNOWLEDGE",
  "articleId": "1024",
  "articleTitle": "经期前情绪变化值得怎样记录",
  "articleType": 100,
  "selectedText": "经期前几天可能出现情绪变化。",
  "suggestedTopicTitle": "经前情绪变化",
  "originalLabel": "我有疑问",
  "questionType": "IS_COMMON",
  "questionText": "这是否常见？",
  "cycleRelation": "UNKNOWN"
}
```

成功 `data`：

```json
{
  "clue": {
    "id": "clue_1001",
    "type": "ARTICLE_HIGHLIGHT",
    "intent": "RELATED",
    "status": "PENDING",
    "articleId": "1024",
    "articleTitle": "经期前情绪变化值得怎样记录",
    "selectedText": "经期前几天出现的情绪变化，需要结合时间和重复情况继续观察。",
    "createdAt": "2026-08-13T10:00:00+08:00",
    "updatedAt": "2026-08-13T10:00:00+08:00"
  },
  "digestTask": {
    "triggered": false,
    "taskId": null,
    "digestId": null,
    "status": null
  }
}
```

校验要求：`selectedText` 和 `articleTitle` 非空；`severity` 只能为 0 到 10；枚举非法返回参数错误；忽略请求中的任何 `userId`。

### 8.3 查询线索

```http
GET /athena/cognition/clues?view=PENDING&page=1&pageSize=20
```

`view` 与页面对应：

```text
PENDING：intent=RELATED 且 status=PENDING
ORGANIZED：正式主题、已整理知识另由对应字段返回；也可用 intent/status 精确筛选
QUESTIONS：intent=QUESTION
ALL：全部线索
```

建议支持额外参数：`intent`、`status`、`articleId`。每条线索返回第 4.1 节字段。

分页响应示例：

```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": "clue_1001",
      "type": "ARTICLE_HIGHLIGHT",
      "intent": "RELATED",
      "relationType": "CURRENT",
      "status": "PENDING",
      "articleId": "1024",
      "articleTitle": "经期前情绪变化值得怎样记录",
      "articleType": 100,
      "selectedText": "经期前几天出现的情绪变化，需要结合时间和重复情况继续观察。",
      "suggestedTopicTitle": "经前情绪变化",
      "originalLabel": "和我有关",
      "createdAt": "2026-08-13T10:00:00+08:00",
      "updatedAt": "2026-08-13T10:00:00+08:00"
    }
  ],
  "total": 1
}
```

### 8.4 查询“我的身体线索”聚合页

```http
GET /athena/cognition/inbox
```

前端页面有“待整理”“已整理”“我的疑问”三个标签。为了避免页面首次打开连续请求线索、草稿、主题三到四个接口，后端提供一次聚合响应：

```json
{
  "pendingClues": [],
  "activeDigest": null,
  "topics": [],
  "knowledgeDigests": [],
  "knowledgeClues": [],
  "questions": [],
  "counts": {
    "pending": 0,
    "organizedTopics": 0,
    "organizedKnowledge": 0,
    "questions": 0
  }
}
```

对象内容仍使用本文定义的 `Clue`、`Digest`、`Topic`，不要再为聚合接口发明另一套字段。数据较多时各列表可只返回首屏，并附 `hasMore`；用户切换标签后再调用列表接口分页。

### 8.5 撤销线索

```http
DELETE /athena/cognition/clues/{clueId}
```

成功返回被撤销的 ID。若线索已进入草稿证据，返回 `COGNITION_CLUE_IN_USE`，不能物理删除。

### 8.6 用户主动“帮我整理”

```http
POST /athena/cognition/digest-tasks
```

请求：

```json
{
  "triggerType": "USER_REQUEST",
  "clueIds": ["clue_1001"],
  "suggestedTitle": "经前情绪变化"
}
```

后端不能只整理请求中的一条线索。应以该线索为入口，查找同一用户、同一候选主题下可用的 `RELATED + PENDING` 线索，形成完整 `sourceClueIds`。

成功 `data`：

```json
{
  "taskId": "task_3001",
  "digestId": "digest_5001",
  "status": "SUCCEEDED",
  "digestStatus": "READY"
}
```

第一次联调可以同步完成并直接返回 `SUCCEEDED/READY`。第二次接 Agent 后允许异步返回 `PENDING/RUNNING`，前端再轮询草稿；外部接口和字段不变。

### 8.7 查询草稿列表和详情

```http
GET /athena/cognition/digests?status=READY&page=1&pageSize=20
GET /athena/cognition/digests/{digestId}
```

详情 `data` 示例：

```json
{
  "id": "digest_5001",
  "title": "经前情绪变化",
  "status": "READY",
  "commonPoint": "你标记的文章内容和一条身体记录都与经前情绪变化有关。",
  "possibleRelation": "这些线索在内容和时间上出现联系，但目前只能视为待确认的初步联系。",
  "uncertainty": "还不能确定这种联系是否会重复，也不能仅凭这些信息形成身体结论。",
  "suggestedAction": "下次出现相关变化时，再记录一次时间和程度。",
  "evidenceIds": ["evidence_1", "evidence_2", "evidence_3"],
  "sourceClueIds": ["clue_1001", "clue_1002"],
  "evidence": [
    {
      "id": "evidence_1",
      "sourceType": "CLUE",
      "sourceId": "clue_1001",
      "factLevel": "SELF_REPORTED",
      "summary": "经期前几天出现的情绪变化，需要结合时间和重复情况继续观察。",
      "articleId": "1024",
      "articleTitle": "经期前情绪变化值得怎样记录"
    },
    {
      "id": "evidence_3",
      "sourceType": "BODY_RECORD",
      "sourceId": "2001",
      "factLevel": "SELF_REPORTED",
      "summary": "经期前记录过轻微情绪波动",
      "occurredAt": "2026-08-10T00:00:00+08:00"
    }
  ],
  "generatorVersion": "fixed-v1",
  "generatedAt": "2026-08-13T10:01:00+08:00",
  "failureCode": null,
  "expiresAt": null,
  "version": 1
}
```

前端草稿页必须能直接展示证据来源，所以详情接口应返回 `evidence` 展示对象，不能只返回无法解析来源的 ID。

### 8.8 提交草稿决定

```http
POST /athena/cognition/digests/{digestId}/decision
```

请求：

```json
{
  "decision": "ACCEPT_AS_TOPIC",
  "reason": null,
  "clientVersion": 1
}
```

接受后的 `data`：

```json
{
  "digest": {
    "id": "digest_5001",
    "status": "ACCEPTED",
    "version": 2
  },
  "topic": {
    "id": "topic_7001",
    "sourceDigestId": "digest_5001",
    "title": "经前情绪变化",
    "domain": "MOOD",
    "maturity": "EARLY_LINK",
    "userProgress": "OBSERVING",
    "riskStatus": "NONE",
    "stageUnderstanding": "这些线索出现初步联系，但仍需继续观察。",
    "knownFacts": ["用户确认现在出现过与该线索类似的情况"],
    "openQuestions": ["还不能确定这种联系是否会重复"],
    "evidenceCount": 3,
    "articleClueCount": 2,
    "bodyRecordCount": 1,
    "cycleCount": 1,
    "nextActionId": "action_8001",
    "version": 1
  },
  "action": {
    "id": "action_8001",
    "topicId": "topic_7001",
    "title": "记录一次相关身体变化",
    "description": "补充出现时间和程度，帮助确认这种变化是否会再次出现。",
    "actionType": "RECORD_BODY",
    "status": "PENDING",
    "feedbackOptions": ["OCCURRED", "NOT_OCCURRED", "UNCERTAIN", "SKIPPED"]
  }
}
```

`KEEP_AS_KNOWLEDGE` 和 `REJECT` 时，`topic`、`action` 必须为 `null`。

### 8.9 查询主题列表和详情

```http
GET /athena/cognition/topics?page=1&pageSize=20
GET /athena/cognition/topics/{topicId}
```

主题详情必须一次返回页面需要的完整内容：

```json
{
  "topic": {
    "id": "topic_7001",
    "sourceDigestId": "digest_5001",
    "title": "经前情绪变化",
    "domain": "MOOD",
    "maturity": "EARLY_LINK",
    "userProgress": "OBSERVING",
    "riskStatus": "NONE",
    "stageUnderstanding": "这些线索出现初步联系，但仍需继续观察。",
    "knownFacts": ["用户确认现在出现过与该线索类似的情况"],
    "openQuestions": ["还不能确定这种联系是否会重复"],
    "evidenceCount": 3,
    "articleClueCount": 2,
    "bodyRecordCount": 1,
    "cycleCount": 1,
    "nextActionId": "action_8001",
    "lastUpdatedAt": "2026-08-13T10:03:00+08:00",
    "version": 1
  },
  "sourceDigest": {
    "id": "digest_5001",
    "possibleRelation": "这些线索在内容和时间上出现联系。"
  },
  "evidence": [],
  "nextAction": {},
  "relatedArticles": [],
  "recentFeedback": [],
  "recentChange": "主题已建立，正在等待第一次行动反馈。"
}
```

`evidence` 中的文章线索要带 `articleId`、`articleTitle`、`articleType`，这样前端才能跳回原文章。身体记录和反馈要带摘要及时间。

### 8.10 提交行动反馈

```http
POST /athena/cognition/actions/{actionId}/feedback
```

请求：

```json
{
  "topicId": "topic_7001",
  "result": "OCCURRED",
  "note": "今天下午出现过轻微情绪低落",
  "occurredAt": "2026-08-13T18:20:00+08:00"
}
```

成功 `data`：

```json
{
  "feedback": {
    "id": "feedback_9001",
    "actionId": "action_8001",
    "topicId": "topic_7001",
    "result": "OCCURRED",
    "note": "今天下午出现过轻微情绪低落",
    "occurredAt": "2026-08-13T18:20:00+08:00",
    "createdAt": "2026-08-13T18:21:00+08:00",
    "evidenceId": "evidence_9101"
  },
  "actionStatus": "COMPLETED",
  "topicVersion": 2,
  "refreshRequired": true
}
```

### 8.11 健康首页聚合

```http
GET /athena/cognition/home
```

后端负责选择当前最值得展示的草稿、主题和行动。Android 不负责从多条数据中猜“哪一条是 active”。

有主题时的 `data`：

```json
{
  "asOf": "2026-08-13T18:21:00+08:00",
  "summaryState": "OBSERVING",
  "headline": "Athena 正在理解你的身体变化",
  "latestInsight": {
    "title": "经前情绪变化",
    "body": "这些线索出现初步联系，但仍需继续观察。",
    "evidenceCount": 3,
    "uncertainty": "还不能确定这种联系是否会重复。"
  },
  "activeTopic": {
    "id": "topic_7001",
    "title": "经前情绪变化",
    "maturity": "EARLY_LINK",
    "userProgress": "OBSERVING",
    "riskStatus": "NONE",
    "evidenceCount": 3,
    "cycleCount": 1,
    "nextActionId": "action_8001"
  },
  "pendingDigestCount": 0,
  "nextAction": {
    "id": "action_8001",
    "topicId": "topic_7001",
    "title": "记录一次相关身体变化",
    "description": "补充出现时间和程度，帮助确认这种变化是否会再次出现。",
    "actionType": "RECORD_BODY",
    "status": "PENDING",
    "feedbackOptions": ["OCCURRED", "NOT_OCCURRED", "UNCERTAIN", "SKIPPED"]
  },
  "failedTaskCount": 0
}
```

完全无数据时必须返回 200，而不是 404：

```json
{
  "asOf": "2026-08-13T10:00:00+08:00",
  "summaryState": "EMPTY",
  "headline": "还没有可展示的身体认知摘要",
  "latestInsight": null,
  "activeTopic": null,
  "pendingDigestCount": 0,
  "nextAction": null,
  "failedTaskCount": 0
}
```

`summaryState` 的最低选择规则：

```text
有 READY 草稿                         -> DIGEST_READY
有 PROCESSING 任务                    -> DIGEST_PROCESSING
最近整理失败且无更高优先级内容          -> DIGEST_FAILED
有正式主题且行动已完成                  -> ACTION_COMPLETED
有正式主题                            -> OBSERVING
只有 PENDING RELATED 线索              -> BUILDING_BASELINE
最近决定是只存知识且没有主题/待处理草稿  -> DIGEST_KEPT_AS_KNOWLEDGE
最近决定是拒绝且没有主题/待处理草稿      -> DIGEST_REJECTED
什么都没有                            -> EMPTY
```

不要长期把“最近拒绝/只存知识”作为首页唯一状态；它们只是刚完成操作后的短期反馈状态。

---

## 9. 第一次联调的固定草稿怎样实现

本阶段没有 Agent，但不能在 Controller 中随手拼一段字符串。建议定义稳定接口：

```java
public interface CognitionDigestGenerator {
    DigestGenerationResult generate(DigestGenerationInput input);
}
```

第一次交接实现：

```text
FixedCognitionDigestGenerator
generatorVersion = "fixed-v1"
输入真实 sourceClueIds 和证据
输出固定但结构完整的 commonPoint / possibleRelation / uncertainty / suggestedAction
```

第二次交接替换为：

```text
AgentCognitionDigestGenerator
```

Service、Controller、数据库和 Android 接口都不变。

固定草稿也必须满足：

1. `sourceClueIds` 和 `evidenceIds` 非空且都属于当前用户。
2. `QUESTION`、`KNOWLEDGE_ONLY` 不得写成用户身体事实。
3. 文案包含不确定性，不能出现“你一定”“你患有”等诊断表达。
4. 草稿生成失败时保存任务失败状态和失败码，保留原始线索。

---

## 10. 触发规则和成熟度

### 10.1 何时生成草稿

满足任一条件：

```text
RULE_1：同一候选主题至少 3 条有效 RELATED 文章线索
RULE_2：至少 2 条有效 RELATED 文章线索 + 1 条用户确认的身体记录
RULE_3：用户主动点击“帮我整理”
```

第一次联调应优先确保 `RULE_3` 可用，因为前端已有明确按钮。`RULE_1/2` 由后端保存线索后自动检查。

以下不计入身体阈值：

```text
QUESTION
KNOWLEDGE_ONLY
广场帖子
只浏览、点赞或普通收藏
DISMISSED 线索
已失效证据
```

为了避免重复任务，同一用户、同一候选主题最多允许一个 `PENDING/RUNNING/READY` 的开放草稿。

第一次交接尚未接入 Agent，候选主题先按确定性规则分组：

```text
suggestedTopicId 非空      -> 按 suggestedTopicId 分组
否则 suggestedTopicTitle 非空 -> 去除首尾空格后按标题精确分组
两者都为空                -> 不做自动阈值合并，只允许用户主动“帮我整理”
```

本阶段不要在后端用模糊关键词或大模型猜主题，也不要把两个相似标题擅自合并。第二次 Agent 交接会替换主题匹配能力，但不会改变线索和草稿结构。

### 10.2 主题成熟度

V1 基础规则：

```text
0 条确认身体记录                    -> CLUE 或 INSUFFICIENT
1 条确认身体记录                    -> INSUFFICIENT
跨 2 个不同日期出现相近记录          -> EARLY_LINK
跨 2 个周期重复                     -> REPEATED_PATTERN
至少 3 个周期且记录方向相对稳定       -> RELATIVELY_STABLE
```

当前 Mock 接受草稿后直接显示 `EARLY_LINK` 只是演示。正式后端应根据真实证据计算，同时返回计数，不能只返回枚举。

风险状态与成熟度是两条独立轴。成熟度高不等于风险高；风险状态也不能由文章阅读直接提高。

---

## 11. 推荐建表与约束

表名可按团队规范调整，但职责不要合并：

```text
cognition_clue
cognition_digest
cognition_digest_clue
cognition_evidence
cognition_digest_evidence
cognition_digest_task
cognition_topic
cognition_topic_evidence
cognition_action
cognition_action_feedback
cognition_decision_log
```

关键约束：

- 所有用户数据表包含 `user_id`，常用查询建立 `(user_id, status, created_at)` 索引。
- `cognition_digest_clue(digest_id, clue_id)` 唯一。
- `cognition_topic.source_digest_id` 在 V1 中唯一，防止重复接受创建多个主题。
- `cognition_action_feedback.action_id` 唯一，保证一个行动只反馈一次。
- 决定请求建议带 `request_id` 或使用 `(digest_id, decision)` 幂等记录。
- 原始线索、草稿和证据优先逻辑删除，避免破坏追溯关系。
- JSON 数组可以先用 JSON 列，但 `sourceClueIds` 和证据关联必须有关系表，不能只埋在 JSON 中。

后端若把认知模块放在 `athena-insight`，需要：

```text
新增 controller / service / mapper / dataobject / dto / vo
新增数据库迁移脚本
给网关增加 /athena/cognition/** -> athena-insight 路由
沿用 HeaderUserId2ContextFilter 和 UserIdHolder
沿用 Result<T>
```

---

## 12. 错误、失败和重试

项目现有 `Result<T>` 只有 `code`、`message`、`data`、`total`。认知模块不要让 Android 靠中文猜错误类型。为保持现有外层不变，失败时把稳定业务错误码放进 `data.errorCode`：

```json
{
  "code": 409,
  "message": "草稿已经处理，请刷新后重试",
  "data": {
    "errorCode": "COGNITION_STATE_CONFLICT",
    "objectId": "digest_5001",
    "currentStatus": "ACCEPTED"
  },
  "total": null
}
```

`code` 使用可区分的语义值：参数错误 400、未登录 401、未找到 404、状态或版本冲突 409、生成失败 500/503。V1 沿用当前项目控制器直接返回 `Result<T>` 的方式：已处理的业务错误可以保持 HTTP 200，但 JSON 外层 `code` 必须使用上述值；网关、鉴权和未处理异常仍可返回真实 HTTP 4xx/5xx。Android 以“HTTP 可达且响应体可解析时优先读取 `Result.code` 和 `data.errorCode`”为准。

当前 `Result` 只有 `ok()` 和固定 500 的 `fail(String)`。后端应增加可复用的 `fail(code, message, data)` 工厂或统一异常处理器，不要在每个 Controller 手工拼错误对象。

| 业务错误码 | 场景 |
| --- | --- |
| `COGNITION_INVALID_ARGUMENT` | 字段或枚举非法 |
| `COGNITION_NOT_FOUND` | 对象不存在或不属于当前用户 |
| `COGNITION_STATE_CONFLICT` | 草稿已决定、行动已反馈等状态冲突 |
| `COGNITION_VERSION_CONFLICT` | `clientVersion` 已过期 |
| `COGNITION_CLUE_IN_USE` | 线索已进入草稿，不能撤销 |
| `COGNITION_NO_VALID_EVIDENCE` | 没有可用于整理的 RELATED 线索 |
| `COGNITION_TASK_RUNNING` | 相同主题已有开放任务 |
| `COGNITION_GENERATION_FAILED` | 固定生成器或未来 Agent 失败 |

整理失败时：

```text
digest.status = FAILED
task.status = FAILED
保存 failureCode
相关 clue 保持 PROCESSING 或恢复 PENDING，必须统一确定；V1 建议保持 PROCESSING 并允许按原 task 重试
不创建 topic
不创建 action
不丢失 sourceClueIds
```

重试接口：

```http
POST /athena/cognition/digest-tasks/{taskId}/retry
```

第一次联调可同步重试；第二次 Agent 接入后最多自动重试 3 次，超过后等待用户再次触发。

---

## 13. 数据权限和隐私底线

1. 所有外部接口从 `UserIdHolder.getUserId()` 获取用户，不接受请求中的 `userId`。
2. 查询、更新和删除必须同时带对象 ID 与 `user_id` 条件。
3. 用户 A 请求用户 B 的线索、草稿、主题或行动时，返回统一“未找到”，不泄露对象是否存在。
4. 日志禁止打印完整文章摘录、问题文本、身体备注和模型 Prompt，只记录 ID、状态、耗时、版本和失败码。
5. Android 不保存模型密钥；第二次交接后也只能调用后端。
6. 广场来源在本轮直接拒绝或隔离，不能写入认知表。
7. 文章 ID 必须在保存时校验存在；标题保存快照用于历史展示，但不能替代文章关系。

---

## 14. 后端严格执行顺序

### 阶段 A：先看懂前端演示

后端开发者先实际走一遍：

```text
主路径：文章标记 -> 草稿 -> 接受 -> 主题 -> 行动反馈 -> 健康首页更新
分支一：我有疑问
分支二：保存为知识
分支三：只将草稿保存为知识
分支四：拒绝草稿
分支五：整理失败后重试
```

看完后必须能用自己的话回答：

```text
用户点“和我有关”后保存什么？
为什么不能直接创建主题？
“我有疑问”和“用户有症状”有什么区别？
接受、只存知识、拒绝分别会创建什么？
行动反馈后首页为什么要刷新？
```

### 阶段 B：搭建数据层和普通接口

1. 确定模块归属和网关路由。
2. 建表、索引、唯一约束和迁移脚本。
3. 建枚举、DO、DTO、VO、Mapper、Service、Controller。
4. 完成用户隔离和对象归属校验。
5. 实现线索创建、查询、收件箱聚合和撤销。
6. 实现草稿任务、列表、详情和固定生成器。
7. 实现草稿三种决定的事务。
8. 实现主题列表和详情聚合。
9. 实现行动反馈事务。
10. 实现健康首页聚合。

### 阶段 C：第一次真实 HTTP 联调

按顺序验证 ID 链：

```text
clueId
-> taskId
-> digestId
-> topicId
-> actionId
-> feedbackId / evidenceId
```

字段不一致时修改契约或单侧实现，不允许 Android 同时兼容多套猜测字段。

### 阶段 D：第一次交接验收

第一次验收重点是：

```text
数据库数据正确
状态流转正确
用户隔离正确
接口幂等和事务正确
页面能从真实接口完整刷新
```

不以固定草稿的文案质量作为第一次验收重点。

### 阶段 E：等待第二次 Agent 交接

后端普通业务完成后保持 `CognitionDigestGenerator` 边界。前端负责人同时开发 Agent，不阻塞第一次联调。

### 阶段 F：第二次交接后部署 Agent

后端将 `FixedCognitionDigestGenerator` 替换为正式 Agent 执行器，完成密钥、异步任务、输出校验、重试、日志和监控，再重新执行所有验收用例。

---

## 15. 第一次交接测试用例

### TC-01：确认现在有类似情况

输入：`RELATED + CURRENT`。

预期：创建 `PENDING` 线索；未达到阈值时不创建草稿、主题或行动。

### TC-02：以前出现过

输入：`RELATED + PAST`。

预期：保存用户自述；只有接受草稿后才能进入主题 `knownFacts`。

### TC-03：不确定但想观察

输入：`RELATED + OBSERVE`。

预期：可参与观察整理，但不能写入 `knownFacts`。

### TC-04：保存为知识

输入：`KNOWLEDGE_ONLY`。

预期：线索为 `ORGANIZED`；不触发身体草稿，不进入健康事实。

### TC-05：我有疑问

输入：`QUESTION + IS_COMMON`。

预期：显示在“我的疑问”；不能单独触发主题，不能写入身体事实。

### TC-06：主动帮我整理

输入：一个可用 `RELATED + PENDING` 线索，调用任务接口。

预期：创建 task 和 digest；固定生成器返回 `READY`；证据可追溯。

### TC-07：自动阈值

输入：同一候选主题 3 条有效 RELATED 文章线索，或 2 条文章线索加 1 条确认身体记录。

预期：只创建一个开放任务，不重复生成草稿。

### TC-08：接受草稿

输入：`ACCEPT_AS_TOPIC`。

预期：事务内更新草稿和线索，创建主题、证据和待完成行动。

### TC-09：只将草稿保存为知识

输入：`KEEP_AS_KNOWLEDGE`。

预期：草稿已整理为知识；不创建主题和行动。

### TC-10：拒绝草稿

输入：`REJECT`。

预期：草稿 `REJECTED`，源线索 `DISMISSED`；不创建主题和行动。

### TC-11：重复决定

输入：同一草稿再次提交决定。

预期：返回 `COGNITION_STATE_CONFLICT`；不能产生重复主题或行动。

### TC-12：行动反馈出现了

输入：`OCCURRED`。

预期：行动完成、反馈入库、新增证据、主题版本和计数更新、首页刷新。

### TC-13：行动反馈没有出现

输入：`NOT_OCCURRED`。

预期：保存为观察证据；不能直接结束主题。

### TC-14：行动反馈不确定

输入：`UNCERTAIN`。

预期：保存反馈和证据，但不提高成熟度。

### TC-15：跳过行动

输入：`SKIPPED`。

预期：行动 `SKIPPED`；不生成 `evidenceId`，不增加证据和身体记录数。

### TC-16：重复行动反馈

输入：同一 `actionId` 再次提交。

预期：返回状态冲突，不重复计数。

### TC-17：撤销未处理线索

输入：未进入草稿的 `PENDING` 线索。

预期：成功撤销；线索页刷新。

### TC-18：撤销已进入草稿的线索

输入：已被 `digest` 引用的线索。

预期：返回 `COGNITION_CLUE_IN_USE`，证据不丢失。

### TC-19：整理失败和重试

输入：固定生成器模拟失败，再调用重试。

预期：失败状态可查询，源线索保留；重试后同一证据生成 `READY` 草稿。

### TC-20：首页无数据

输入：新用户，无任何认知数据。

预期：`GET /home` 返回 200、`summaryState=EMPTY`，可空对象为 null。

### TC-21：用户隔离

输入：用户 A 请求用户 B 的对象 ID。

预期：返回未找到，不泄露数据。

### TC-22：广场隔离

输入：伪造 `source=SQUARE` 或广场帖子 ID。

预期：拒绝进入认知闭环，不参与首页和草稿。

### TC-23：换设备和重新登录

输入：账号在设备 A 完成标记，设备 B 登录同一账号。

预期：设备 B 能从服务器查询到相同线索、草稿、主题和行动。

---

## 16. 联调交付物

### 前端负责人第一次交付

```text
当前 Android 源码
可安装 APK
主路径与分支操作录屏
页面截图
本契约文档
前端模型与枚举源码
前端状态机单元测试
```

关键前端源码位置：

```text
athena_app_front/app/src/main/java/com/whu/software/athena/cognition/CognitionModels.java
athena_app_front/app/src/main/java/com/whu/software/athena/cognition/CognitionRepository.java
athena_app_front/app/src/main/java/com/whu/software/athena/cognition/DemoCognitionRepository.java
athena_app_front/app/src/main/java/com/whu/software/athena/cognition/CognitionStateMachine.java
athena_app_front/app/src/main/java/com/whu/software/athena/cognition/CognitionHomeMapper.java
```

后端阅读这些文件是为了理解当前页面契约，不是为了复制 Android 的 Mock 存储。

### 后端第一次交付

```text
数据库迁移脚本和表说明
全部接口实现
Swagger/OpenAPI
接口请求响应样例
服务端单元测试和集成测试
网关路由
可联调环境地址
测试账号
固定生成器及其开关说明
错误码表
```

### 第二次 Agent 交付

由前端负责人另行提供 Agent 工作流包。后端收到后部署正式执行环境，并把固定生成器切换为 Agent 生成器。

---

## 17. 最终验收句

第一次交接完成不等于“接口能返回 200”，而是必须证明同一个用户的一条数据可以完整追溯：

```text
clueId
-> digestId
-> topicId
-> actionId
-> feedbackId
-> evidenceId
-> 健康首页最新状态
```

同时必须证明三个决定有三种不同结果：

```text
接受       -> 创建主题和行动
只保存知识 -> 不创建主题和行动
拒绝       -> 不创建主题和行动，并撤下源线索
```

第二次 Agent 交接只负责把固定草稿替换成真实智能整理。数据库、接口、用户确认门槛和状态流转在第一次交接就必须做成正式版本。
