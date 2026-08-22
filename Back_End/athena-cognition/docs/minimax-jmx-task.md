# 给 MiniMax 的任务书：生成 Athena 认知闭环 JMeter 功能测试计划

> 用法：把下面「任务书正文」整段发给 MiniMax，让它输出完整 .jmx 文件内容。
> 输出较长（约 90KB），如果一次输出被截断，让它「从 TS04-6 继续输出剩余部分」再拼接；
> 或者直接让它分两段生成：TS-01~TS-04 一段，TS-05~TS-07 + 监听器 + 闭合标签一段。
> 生成后运行：`jmeter -n -t cognition-functional-v1.jmx -l result.jtl -j jmeter.log`

---

## 任务书正文

你是资深测试工程师，精通 JMeter 5.6.x 的 .jmx 文件格式（XML）。你的输出必须是完整、可直接被 JMeter 5.6.3 打开执行的 .jmx 文件内容，不要输出任何解释文字、不要用 markdown 代码块包裹。

### 环境

- 网关 Base URL 用用户自定义变量：HOST=localhost，PORT=13715（User Defined Variables 元件，方便切到服务器 121.41.200.73）
- HTTP Header Manager（全局）：`Authorization: Bearer HPZTciG1qFwjU8q5wwmCgcsJuEYsKLlH`；`Content-Type: application/json`
- HTTP Request Defaults：protocol=http，domain=${HOST}，port=${PORT}
- 响应统一信封：`{"code":200,"message":"成功","data":...,"total":...}`；业务错误看 code + data.errorCode
- 一个线程组，1 线程 1 循环，必须顺序执行（后续请求依赖前面 JSONExtractor 提取的变量）

### 测试场景与断言（每个 HTTP 请求配 JSONPathAssertion 或 ResponseAssertion；JSON 提取用 JSONExtractor，Match No=1，Default=NOT_FOUND）

#### TS-01 线索创建

1. POST /athena/cognition/clues，body=`{"type":"ARTICLE_HIGHLIGHT","intent":"RELATED","relationType":"CURRENT","helpRequestType":"OBSERVE","articleId":"1024","articleTitle":"经期前情绪变化值得怎样记录","articleType":100,"selectedText":"经期前几天出现的情绪变化，需要结合时间和重复情况继续观察。","suggestedTopicTitle":"经前情绪变化","originalLabel":"和我有关","occurredAt":"2026-08-10T00:00:00+08:00","cycleRelation":"BEFORE_PERIOD","severity":3,"resolved":false}`
   断言 $.code=200、$.data.clue.status=PENDING、$.data.digestTask.triggered=false；提取 clueId_1=$.data.clue.id
2. POST 同路径，body=`{"type":"ARTICLE_HIGHLIGHT","intent":"QUESTION","helpRequestType":"KNOWLEDGE","articleId":"1024","articleTitle":"经期前情绪变化值得怎样记录","articleType":100,"selectedText":"经期前几天可能出现情绪变化。","suggestedTopicTitle":"经前情绪变化","originalLabel":"我有疑问","questionType":"IS_COMMON","questionText":"这是否常见？","cycleRelation":"UNKNOWN"}`
   断言 code=200、$.data.clue.status=PENDING；提取 clueId_q
3. POST 同路径，body=`{"type":"ARTICLE_HIGHLIGHT","intent":"KNOWLEDGE_ONLY","relationType":"KNOWLEDGE_ONLY","helpRequestType":"SAVE_ONLY","articleId":"1025","articleTitle":"经期护理基础","articleType":100,"selectedText":"经期保持清洁和休息很重要。","originalLabel":"保存为知识","cycleRelation":"UNKNOWN"}`
   断言 code=200、$.data.clue.status=ORGANIZED；提取 clueId_k
4. POST 同路径，body 同 1 但 severity=11 → 断言 code=400 且 $.data.errorCode=COGNITION_INVALID_ARGUMENT
5. POST 同路径，body 同 1 但 selectedText="" → 断言 code=400 且 errorCode=COGNITION_INVALID_ARGUMENT

#### TS-02 查询

6. GET /athena/cognition/clues?view=PENDING&page=1&pageSize=20 → 断言 code=200、$.total>=2
7. GET /athena/cognition/clues?view=QUESTIONS&page=1&pageSize=20 → 断言 code=200、$.total>=1
8. GET /athena/cognition/inbox → 断言 code=200、$.data.counts.pending>=1

#### TS-03 帮我整理

9. POST /athena/cognition/digest-tasks，body=`{"triggerType":"USER_REQUEST","clueIds":["${clueId_1}"],"suggestedTitle":"经前情绪变化"}` → 断言 code=200、$.data.status=SUCCEEDED、$.data.digestStatus=READY；提取 taskId_1=$.data.taskId、digestId_1=$.data.digestId
10. 立即重复同一请求 → 断言 code=409 且 errorCode=COGNITION_TASK_RUNNING（若返回 200 允许该断言失败并记录）
11. GET /athena/cognition/digests/${digestId_1} → 断言 code=200、$.data.status=READY、$.data.generatorVersion=fixed-v1、$.data.evidence 非空；提取 digestVersion_1=$.data.version

#### TS-04 草稿决定

12. POST /athena/cognition/digests/${digestId_1}/decision，body=`{"decision":"ACCEPT_AS_TOPIC","reason":null,"clientVersion":${digestVersion_1}}` → 断言 code=200、$.data.digest.status=ACCEPTED、$.data.topic.id 非空、$.data.action.id 非空、$.data.topic.nextActionId=$.data.action.id；提取 topicId=$.data.topic.id、actionId_1=$.data.action.id
13. 重复提交同一 decision → 断言 code=409 且 errorCode=COGNITION_STATE_CONFLICT
14. 新建 RELATED 线索（body 同 1，selectedText 改为"又一条经前情绪线索"）提取 clueId_2 → POST /digest-tasks 提取 digestId_2 与其 version → decision body=`{"decision":"KEEP_AS_KNOWLEDGE","reason":null,"clientVersion":<提取值>}` → 断言 code=200、$.data.topic 为 null、$.data.action 为 null、$.data.digest.status=KEPT_AS_KNOWLEDGE
15. 同样新建 clueId_3 → digestId_3 → decision REJECT → 断言 code=200、topic/action 为 null、digest.status=REJECTED
16. GET /athena/cognition/clues?view=ALL&page=1&pageSize=50 → 断言响应包含 "DISMISSED"

#### TS-05 行动反馈

17. POST /athena/cognition/actions/${actionId_1}/feedback，body=`{"topicId":"${topicId}","result":"OCCURRED","note":"今天下午轻微情绪低落","occurredAt":"2026-08-21T18:20:00+08:00"}` → 断言 code=200、$.data.feedback.evidenceId 非空、$.data.actionStatus=COMPLETED、$.data.refreshRequired=true
18. 重复同一反馈 → 断言 code=409 且 errorCode=COGNITION_STATE_CONFLICT

#### TS-06 首页

19. GET /athena/cognition/home → 断言 code=200、$.data.summaryState 匹配正则 (ACTION_COMPLETED|OBSERVING)、$.data.activeTopic.id=${topicId}

#### TS-07 撤销与安全

20. 新建 RELATED 线索（body 同 1，selectedText="待撤销的线索"）提取 clueId_4 → DELETE /athena/cognition/clues/${clueId_4} → 断言 code=200
21. DELETE /athena/cognition/clues/${clueId_1}（已入草稿）→ 断言 code=409 且 errorCode=COGNITION_CLUE_IN_USE
22. POST /clues body 同 1 但加 "source":"SQUARE" → 断言 code=400 且 errorCode=COGNITION_INVALID_ARGUMENT
23. POST /clues body 同 1 但 intent="FOO" → 断言 code=400 且 errorCode=COGNITION_INVALID_ARGUMENT
24. GET /athena/cognition/home 但不带 Authorization（该 sampler 单独挂一个不含 Authorization 的 Header Manager 覆盖全局）→ 断言响应不包含 "\"code\":200"

### 其他要求

- 加 View Results Tree 和 Summary Report 两个监听器（ResultCollector）
- 每个 sampler 命名：TS编号-步骤-中文名，如 TS01-1-创建RELATED线索
- clientVersion 注意：decision 前必须先从对应 digest 详情提取最新 version
- 整个文件必须是合法 XML（jmeterTestPlan 根元素，JMeter 5.6.3 兼容）

---

## 备注

- 人类可读的测试计划说明在仓库里：`Back_End/athena-cognition/docs/test-plan-v1.md`
- 我这还有 MiniMax 已生成的前半段（TS-01~TS04-5，18 个 sampler，67KB）：`D:\aitool\athenaworktwo\.tools\cognition-functional-v1.part1`，可以只让 MiniMax 补后半段
- 跑完把 `result.jtl` 或 Summary Report 的截图/数字发我，失败项我来定位修
