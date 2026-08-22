# Athena 认知闭环 V1：前端联调交接文档

> 面向：Android 前端开发者
> 状态：后端已实现并通过网关全链路功能测试（JMeter 31/31 全绿，2026-08-22）
> 配套文档：业务契约 `Back_End/docs/newfunction/cognition-contract-v1.md`（先读这份）、接口 Schema `Back_End/athena-cognition/openapi-v1.yaml`

## 0. 一句话现状

契约第一次交接范围的后端**已全部实现并部署到联调环境**：14 个接口、11 张表、状态机、错误码、用户隔离都已生效。唯一仍是占位的是**草稿文案**（固定生成器 `fixed-v1`，结构完整、内容固定，第二次 Agent 交接才换成真实智能整理）。

## 1. 联调环境

| 项 | 值 |
|---|---|
| 网关 Base URL | `http://121.41.200.73:13715` |
| 接口前缀 | `/athena/cognition/**` |
| 鉴权头 | `Authorization: Bearer <token>` |
| 测试账号 | userId=1024（手机号见团队记录；也可以用新手机号走登录流程自动注册） |

**dev 环境拿 token（验证码在响应里直接回显，不发短信）**：

```http
POST /athena/login/code?phone=13900000001     # 返回 data 就是 6 位验证码
POST /athena/login                            # body: {"phone":"13900000001","code":"<上一步的验证码>"}
# 返回 data.token，之后所有请求带 Authorization: Bearer <token>
```

新手机号首次登录会自动注册（firstLogin=true）。**建议每个联调人用独立手机号**，避免共享账号互相干扰测试数据（认知数据按 userId 隔离）。

## 2. 响应信封与错误处理（重要）

所有响应统一信封：

```json
{"code": 200, "message": "成功", "data": {}, "total": null}
```

- 分页列表：`data` 是数组，`total` 是总数
- **业务错误**：HTTP 200，但信封 `code` 为语义值（400/404/409/500），稳定错误码在 `data.errorCode`——**前端按 code + errorCode 分支，不要解析中文 message**
- 网关/鉴权失败（未带 token、token 失效）：返回真实 HTTP 错误或 `{"code":500,"message":"未能读取到有效 token"}`，无 errorCode

8 个业务错误码（契约 §12）：

| errorCode | code | 场景 | 前端建议 |
|---|---|---|---|
| COGNITION_INVALID_ARGUMENT | 400 | 字段/枚举非法 | 提示检查输入 |
| COGNITION_NOT_FOUND | 404 | 对象不存在或不属于当前用户 | 按"不存在"处理 |
| COGNITION_STATE_CONFLICT | 409 | 草稿已决定、行动已反馈 | 提示刷新后重试 |
| COGNITION_VERSION_CONFLICT | 409 | clientVersion 过期 | 重新拉详情再提交 |
| COGNITION_CLUE_IN_USE | 409 | 线索已进入草稿不能撤销 | 隐藏撤销入口 |
| COGNITION_NO_VALID_EVIDENCE | 400 | 没有可整理的 RELATED 线索 | 引导先标记 |
| COGNITION_TASK_RUNNING | 409 | 同主题已有开放草稿/任务 | 提示已有待确认草稿 |
| COGNITION_GENERATION_FAILED | 500 | 生成失败 | 提示稍后重试 |

## 3. 页面 ↔ 接口对照

| 页面/操作 | 接口 | 备注 |
|---|---|---|
| 文章详情提交三种标记 | `POST /athena/cognition/clues` | 返回 `{clue, digestTask{triggered,taskId,digestId,status}}`；达到阈值会自动触发整理（triggered=true） |
| 保存后立即撤销 | `DELETE /athena/cognition/clues/{clueId}` | 已入草稿返回 COGNITION_CLUE_IN_USE |
| 我的身体线索三个标签 | `GET /athena/cognition/inbox` | 一次聚合返回，字段见契约 §8.4 |
| 线索列表分页 | `GET /athena/cognition/clues?view=PENDING&page=1&pageSize=20` | view: PENDING/ORGANIZED/QUESTIONS/ALL；PENDING 只含 RELATED 未整理线索 |
| 帮我整理 | `POST /athena/cognition/digest-tasks` | 同步完成，直接返回 SUCCEEDED/READY |
| 查看草稿 | `GET /athena/cognition/digests/{digestId}` | 含 evidence 展示对象（带来源文章信息） |
| 草稿决定 | `POST /athena/cognition/digests/{digestId}/decision` | 必须带 `clientVersion`（取草稿详情的 version） |
| 认知主题列表/详情 | `GET /athena/cognition/topics`、`GET /athena/cognition/topics/{topicId}` | 详情一次给全 |
| 行动反馈 | `POST /athena/cognition/actions/{actionId}/feedback` | 返回 refreshRequired=true 后刷新主题和首页 |
| 健康首页 | `GET /athena/cognition/home` | summaryState 九态由后端算好，直接展示 |
| 整理失败重试 | `POST /athena/cognition/digest-tasks/{taskId}/retry` | fixed-v1 阶段不会失败，预留给 Agent |

## 4. 前端替换 Mock 时的关键差异

1. **`DemoCognitionRepository` 换成 HTTP 源**即可，页面字段/枚举与契约 §5 完全一致（后端枚举名=前端枚举名，英文值，中文文案前端展示）
2. **Mock 的演示捷径不存在了**：没有预置的 2 线索 + 1 身体记录；新用户进来就是空态（`summaryState=EMPTY`，可空字段为 null 或缺省）
3. **标记一次不会立刻出草稿**：需要同主题 3 条 RELATED 线索（RULE_1）、2 条+1 条身体记录（RULE_2），或用户点"帮我整理"（RULE_3）。联调演示走"帮我整理"
4. **决定接口必须带 clientVersion**：先 GET 草稿详情取 `version`，提交时带上；重复提交会收到 409，按错误码提示即可，不要静默重试
5. **行动只能反馈一次**：重复反馈 409；SKIPPED 不产生证据
6. **首页不要自己拼状态**：`GET /home` 的 `summaryState/activeTopic/nextAction/latestInsight` 直接用；"只存知识/拒绝"只是 24 小时内的短期反馈态，之后回落
7. **草稿文案是固定的**（fixed-v1）：commonPoint/possibleRelation/uncertainty/suggestedAction 四段结构是稳定的，内容先不要求智能
8. 注意 Jackson 是 non_null：可空字段在 JSON 里可能**直接缺省**而不是 null（如 KEEP_AS_KNOWLEDGE 时响应里没有 topic/action 键），Gson 解析不受影响

## 5. 建议的联调验证顺序（契约阶段 C）

```text
POST /clues 拿 clueId
→ POST /digest-tasks 拿 taskId、digestId
→ GET /digests/{digestId}
→ POST decision(ACCEPT_AS_TOPIC) 拿 topicId、actionId
→ POST /actions/{actionId}/feedback 拿 feedbackId、evidenceId
→ GET /home 看首页状态变化
```

三个决定分支各验证一次（接受/只存知识/拒绝结果不同）。参考用例：契约 §15 的 23 条 TC，其中 22 条后端已自动化验证通过（`Back_End/athena-cognition/docs/tc-verification-v1.md`），TC-23 换设备一致性需要联调时手动验证一次。

## 6. 已知边界（不是 bug）

- RULE_2 依赖 `daily_record` 里 recordItemId=3（症状）/4（心情）的记录；测试账号没记过身体记录时 RULE_2 不触发
- athena-record 服务不可用时 RULE_2 自动降级不触发，不影响主链路
- 自动阈值触发后创建线索接口的 `digestTask.triggered=true`，前端可据此提示"Athena 已开始整理"
- 同一候选主题同时最多一个开放草稿，重复点"帮我整理"返回 COGNITION_TASK_RUNNING

## 7. 问题反馈

联调中发现字段对不上：改契约文档或单侧实现，**不要前端静默兼容多套字段**。直接在后端群里 @后端，或提 issue 到 GitHub 仓库（Guanjiarao/Athena，backend-master 分支）。
