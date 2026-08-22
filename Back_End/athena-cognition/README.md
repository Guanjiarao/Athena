# Athena Cognition

`athena-cognition` 承载认知闭环 V1：把用户在文章上的三种标记（和我有关 / 我有疑问 / 保存为知识）
保存为线索，经整理草稿、用户决定、认知主题、行动反馈到健康首页聚合，全部按登录用户隔离。

## 契约

唯一验收基准：`Back_End/docs/newfunction/cognition-contract-v1.md`
（§4 数据对象、§5 枚举、§6 标记保存规则、§7 状态流转、§8 接口、§10 触发规则与成熟度、
§11 建表约束、§12 错误码、§13 权限隐私）。

接口清单与 schema 见本目录 `openapi-v1.yaml`（以代码实际行为为准维护）。

## 接口入口

- 路径前缀：`/athena/cognition/**`（网关已配置路由，鉴权沿用现有 Bearer token）
- 用户隔离：`userId` 只从 `UserIdHolder.getUserId()` 获取，请求体/参数中的 userId 一律忽略
- 统一响应：`athena.athenaframework.result.Result<T>` 信封；业务错误 HTTP 200 +
  外层语义 code + `data.errorCode`（8 个 `COGNITION_*` 错误码，见契约 §12 与 openapi 文档）

## 固定生成器与 Agent 替换点

当前草稿由 `FixedDigestGenerator`（`generatorVersion = "fixed-v1"`）生成：不调用模型，只填充
结构完整的 commonPoint / possibleRelation / uncertainty / suggestedAction，且不会把疑问或
保存为知识写成身体事实。第二次交接时实现 `generator/DigestGenerator` 接口替换为
`AgentCognitionDigestGenerator` 即可，Service / Controller / 数据库 / Android 接口不变。

身体记录联动：`bodyrecord/BodyRecordEvidenceProvider` 经 Feign 读取 athena-record 的
daily_record（RULE_2 阈值与证据失效校验）；`recordItemId` 含义映射在
`bodyrecord/RecordItemMeaning`（当前 3=症状、4=心情，与 athena-record 的服务端约定）。

## 数据库

建表脚本：`athena-cognition-biz/src/main/resources/sql/cognition_v1.sql`
（MySQL 8+，11 张表，含逻辑删除、索引与唯一约束；原始线索/草稿/证据只逻辑删除）。

## 本地运行

1. 创建 MySQL 8 数据库并执行上面的建表脚本。
2. 配置共享 Nacos `database.yaml`（与其他 Athena 服务一致）。
3. 启动 Nacos 与网关，运行 `AthenaCognitionApplication`。
4. 经网关访问 `/athena/cognition/**`。

## 验证

```bash
cd Back_End
mvn -q -pl athena-cognition/athena-cognition-biz -am compile
mvn -q -pl athena-cognition/athena-cognition-biz test
```

单测覆盖：三种标记保存规则、撤销规则、自动阈值 RULE_1/2、整理失败与重试、草稿三种决定、
行动反馈四种结果、成熟度五档、recordItemId 映射与失效校验、首页 9 态选择（对应契约 §15 各 TC）。
