# Triage 模块改造方案

## 背景

当前 `athena-rag` 已成功接入你同事新增的 `triage` 能力，且已验证：

- 原有智能科普 RAG 功能正常
- `triage/analyze` 可正常工作
- `triage` 自身多轮追问可工作
- 当前未发现 `triage` 与科普 RAG 的历史对话直接串线

但当前实现仍有几个明显问题：

1. `triage` 仍复用通用 `LLMService` 的全局默认模型配置，后续容易和科普 RAG 的模型策略互相影响
2. `triage` 会话上下文目前仅存在内存 `ConcurrentHashMap` 中，不适合生产
3. `triage` 的终态存储仍是内存 Mock Repository，不具备持久化能力
4. 追问文案、风险提示、兜底提示中仍存在英文内容，且中文表达不够自然
5. `triage` 当前更像“外挂功能”，需要形成更清晰的可维护边界

---

## 本次改造目标

本次改造目标分为四块：

### 1. Triage 独立模型配置

为 `triage` 单独提供模型配置，避免与原有智能科普 RAG 共享同一套默认模型选择策略。

目标：

- `triage` 文本解析可指定独立文本模型
- `triage` 报告生成可指定独立报告模型
- `triage` 视觉分析可指定独立视觉模型
- 默认情况下不影响原有 `/rag/v3/chat` 的模型选择逻辑

### 2. Triage 会话从内存迁移到 Redis

将 `TriageSessionManager` 从内存 `ConcurrentHashMap` 迁移为 Redis 存储，并增加 TTL。

目标：

- 支持服务重启后继续多轮追问
- 支持会话自动过期清理
- 避免 JVM 内存中无限累积 session

### 3. Triage 审计/终态结果落 MySQL

将当前 `MockTriageRepository` 替换为 MySQL 持久化实现，用于保存终态问诊结果与审计信息。

目标：

- 保存 triage 最终结果
- 保存结构化症状、缺失字段、风险结果、最终回复、状态轨迹
- 为后续分析、追踪、运营、合规留基础数据

### 4. 中文化与更自然的追问文案

将 `triage` 中英文兜底文案全面收口为中文，并让追问更自然、更符合产品语境。

目标：

- 消除英文提示
- 提升追问可读性
- 在不改变状态机主体逻辑的前提下优化用户体验

---

## 当前现状分析

## 1. 模型层

`triage` 当前直接依赖通用 `LLMService`，但没有自己的模型配置前缀。

表现为：

- 语义解析 Worker
- SOP 校验 Worker
- 风险分层 Worker
- 报告生成
- 文本补全代理
- 视觉分析代理

都共享当前 `athena-rag` 的基础 AI 配置。

这在短期是可用的，但后续存在问题：

- 科普 RAG 默认模型调整后，`triage` 行为会一起变化
- `triage` 对稳定性、成本、输出风格的要求和科普 RAG 不完全一致
- 不同场景下对“思考模型”“快模型”“视觉模型”的期望不同

## 2. 会话层

当前 `TriageSessionManager` 基于 `ConcurrentHashMap<String, TriageContext>`。

问题：

- 应用重启后 session 丢失
- 无 TTL
- 多实例下会话不共享
- 无法做稳定生产扩展

## 3. 存储层

当前 `TriageRepository` 的实现是 `MockTriageRepository`，本质仍是内存 Map。

问题：

- 终态数据无法落库
- 无法回溯分析 triage 过程
- 无法统计使用情况
- 不利于后续合规与运营

## 4. 文案层

当前英文文案主要集中在：

- 缺失信息追问
- 风险提示
- 系统忙兜底
- 报告生成提示

问题：

- 与当前中文产品场景不一致
- 前端展示不自然
- 对用户不友好

---

## 详细设计方案

## 一、Triage 独立模型配置设计

### 配置目标

在现有 `application.yaml` / `application-prod.yaml` 中增加：

```yaml
triage:
  ai:
    text-model: qwen-plus
    report-model: qwen3-max
    vision-model: qwen-vl-max
    temperature:
      parse: 0.1
      validate: 0.1
      risk: 0.1
      report: 0.2
```

### 配置含义

- `text-model`: 用于语义抽取、SOP 校验、风险分层、文本代理补全
- `report-model`: 用于最终分诊报告生成
- `vision-model`: 用于视觉分析代理
- `temperature.*`: 将当前硬编码温度参数逐步转为可配置

### 实现建议

新增配置类，例如：

- `com.nageoffer.ai.ragent.triage.config.TriageAiProperties`

建议结构：

- `String textModel`
- `String reportModel`
- `String visionModel`
- `Double parseTemperature`
- `Double validateTemperature`
- `Double riskTemperature`
- `Double reportTemperature`

### 服务层改造建议

不要直接重写整套 `LLMService`。

建议新增一个轻量级 triage 专用门面：

- `TriageModelGateway`

职责：

- 根据 triage 配置构造模型目标
- 对通用 `LLMService` 做场景化封装
- 避免每个 Worker 都自己写模型选择逻辑

推荐接口：

```java
String chatWithTextModel(List<ChatMessage> messages, Double temperature);
String chatWithReportModel(List<ChatMessage> messages, Double temperature);
String getVisionModel();
```

### 为什么这样设计

好处：

- `triage` 与 `rag` 在“模型选择”层解耦
- 不会破坏你现有智能科普 RAG 的模型调用方式
- 后续若要给 triage 接专门模型，只改一层即可

---

## 二、Triage 会话改为 Redis + TTL

### 改造目标

把：

- `TriageSessionManager`

从内存 Map 改为 Redis 存储。

### 推荐 Redis Key 设计

```text
triage:session:{sessionId}
```

值内容：

- 序列化后的 `TriageContext` JSON

### TTL 建议

建议默认：

- 30 分钟 或 2 小时

可配置为：

```yaml
triage:
  session:
    ttl-minutes: 120
    key-prefix: triage:session:
```

### 新增配置类建议

- `TriageSessionProperties`

字段建议：

- `String keyPrefix`
- `Long ttlMinutes`

### 实现建议

将 `TriageSessionManager` 改造成基于 Redis 的实现，例如注入：

- `StringRedisTemplate`
- 或 `RedisTemplate<String, Object>`

推荐做法：

- 用 `ObjectMapper` 将 `TriageContext` 序列化为 JSON
- `saveContext()` 时写 Redis 并刷新 TTL
- `getContext()` 时读 Redis 并反序列化

推荐保留原类名 `TriageSessionManager`，只替换内部实现，减少调用方改动。

### 为什么 Redis 适合会话层

因为 triage 会话的特征是：

- 频繁读写
- 生命周期短
- 多轮追问强依赖低延迟
- 过期即可删除

这类数据更适合 Redis，而不是直接用 MySQL 做高频状态存取。

---

## 三、Triage 终态结果落 MySQL

### 设计原则

Redis 存“会话态”，MySQL 存“业务结果态”。

也就是：

- 过程中的上下文：Redis
- 最终问诊结果与审计：MySQL

### 建议表设计

建议至少建两张表。

### 表 1：triage_session_record

保存一次 session 的终态摘要。

建议字段：

- `id` bigint 主键
- `session_id` varchar(64) 唯一
- `user_id` varchar(64) 可空
- `current_state` varchar(64)
- `next_action` varchar(64)
- `risk_level` int
- `risk_score` decimal 可空
- `final_reply` text
- `user_input_snapshot` text
- `conversation_history_json` json / text
- `extracted_symptoms_json` json / text
- `missing_fields_json` json / text
- `risk_assessment_json` json / text
- `state_log_json` json / text
- `audit_trail_json` json / text
- `created_at`
- `updated_at`
- `deleted`

### 表 2：triage_audit_log（可选）

如果希望后续审计粒度更细，可拆分审计表。

字段建议：

- `id`
- `session_id`
- `previous_state`
- `trigger_event`
- `current_state`
- `decision_basis`
- `timestamp`

### 短期与长期建议

短期建议：

- 先一张主表即可
- 把复杂字段统一存 JSON

长期建议：

- 再拆审计明细表
- 支持统计和可视化分析

### Repository 层改造建议

当前：

- `TriageRepository`
- `MockTriageRepository`

改造为：

- 保留 `TriageRepository` 接口
- 新增 `MysqlTriageRepository`
- 下线 `MockTriageRepository` 或仅保留给测试 profile

### 持久化时机建议

当前是：

- 仅在 terminal state 时保存

建议保留该策略，避免写库过频。

但可以增强：

- 当 session 进入 terminal state 时落 MySQL
- 当 Redis session 过期前可选做一次兜底落库

---

## 四、中文化与更自然的追问文案

## 1. 当前问题

当前追问模板偏机械，例如：

- `To continue the triage flow, please clarify these fields: ...`

用户看到会觉得像调试文案，不像产品回复。

## 2. 优化目标

从“字段驱动”改为“自然语言追问”。

### 示例

当前：

- 请补充这些字段：腹痛位置、疼痛性质、是否伴随发热

建议改为：

- 为了更准确判断，请再补充一下：肚子具体是哪个位置疼？是绞痛、隐痛还是持续痛？另外有没有发热？

## 3. 文案分层建议

### A. 缺失信息追问

保留结构化 `missingFields` 给前端，但 `message` / `followUpQuestion` 改成自然语言。

建议新增一个文案组装器：

- `TriageClarificationMessageBuilder`

职责：

- 输入：`missingFields`、`extractedSymptoms`
- 输出：更自然的中文追问

这样可以避免把自然语言生成逻辑写死在状态机里。

### B. 风险提示

当前英文风险提示统一改中文，并区分风险等级。

例如：

- 高风险：
  - `根据当前症状描述，存在较高风险，建议尽快前往线下医院就诊；如果症状持续加重，请及时急诊处理。`

- 中低风险：
  - `目前暂未看到明确的高危信号，但仍建议继续补充关键信息，以便进一步判断。`

### C. 系统兜底

当前类似：

- `System busy...`
- `Please provide more details...`

统一改为中文，例如：

- `系统当前较忙，请稍后重试，并尽量补充症状、持续时间和不适部位。`
- `为了继续判断，请再补充一些关键信息。`

## 4. 文案改造范围

重点检查这些类：

- `TriageOrchestratorServiceImpl`
- `TriageStateMachine`
- `TriageController`
- `TriageAiProxyServiceImpl`

---

## 推荐实施顺序

建议按以下顺序改，风险最低。

## Phase 1：中文文案收口

目标：

- 英文追问全部改中文
- 追问表达更自然

改动小，见效快，风险低。

### 交付标准

- 所有 `triage` 返回消息为中文
- `ASK_CLARIFICATION` 返回更加自然
- 不改动状态机主流程

## Phase 2：Triage 独立模型配置

目标：

- 增加 `triage.ai.*` 配置
- 新增 `TriageModelGateway`
- Worker 和报告生成走 triage 场景模型

### 交付标准

- 改 `triage.ai.text-model` 不影响科普 RAG
- 改 `triage.ai.report-model` 只影响 triage 报告
- 视觉模型单独可配

## Phase 3：Redis 会话替换内存 Map

目标：

- `TriageSessionManager` Redis 化
- 支持 TTL

### 交付标准

- 同一 `sessionId` 可跨请求恢复
- 应用重启后 Redis 中 session 可继续读取
- session 超时后自动清理

## Phase 4：MySQL 终态持久化

目标：

- 引入 `MysqlTriageRepository`
- 保存终态 triage 结果

### 交付标准

- terminal state 数据成功入库
- 可按 `session_id` 查询最终结果
- JSON 字段能完整还原结构化数据

---

## 数据流建议（最终形态）

```text
前端请求
  -> TriageController
  -> TriageOrchestratorServiceImpl
  -> Redis TriageSessionManager 读取 session
  -> TriageStateMachine 编排
       -> SemanticParserWorker
       -> SOPValidatorWorker
       -> RiskStratifierWorker
       -> ReportGenerator
  -> Redis 刷新 session TTL
  -> 若终态则 MySQL 持久化
  -> 返回中文自然化响应
```

---

## 风险与注意事项

## 1. 不要破坏原有科普 RAG

这次改造应严格限制在 `triage` 包及其轻量依赖层，避免影响：

- `/rag/v3/chat`
- `ConversationMemoryService`
- 原知识库检索与科普问答流程

## 2. Redis 与 MySQL 分工要清晰

不要把高频会话态直接落 MySQL。

否则会导致：

- 读写频率过高
- 实时追问性能下降
- 数据模型复杂化

## 3. 文案优化不应侵入状态机核心

自然语言追问建议封装成单独 Builder / Formatter。

不要把大量 if-else 文案逻辑堆进 `TriageStateMachine`。

## 4. 模型配置建议允许回退

如果 `triage.ai.*` 未配置，建议回退到当前默认模型，避免因配置缺失导致无法启动。

---

## 验收清单

### 功能验收

- [ ] 原 `/rag/v3/chat` 行为不变
- [ ] `POST /triage/analyze` 正常工作
- [ ] `triage` 多轮追问可跨请求恢复
- [ ] `triage` 重启后 session 可继续
- [ ] `triage` 终态结果可在 MySQL 查询

### 配置验收

- [ ] 支持 `triage.ai.text-model`
- [ ] 支持 `triage.ai.report-model`
- [ ] 支持 `triage.ai.vision-model`
- [ ] 支持 `triage.session.ttl-minutes`

### 体验验收

- [ ] 无英文追问
- [ ] 无英文系统兜底
- [ ] 缺失信息追问更自然
- [ ] 风险提示更符合中文场景

---

## 我建议的落地顺序

如果后续由我继续直接改代码，我建议按这个顺序提交：

1. **提交 1：中文文案优化**
   - 只改消息文本与追问构造

2. **提交 2：triage 独立模型配置**
   - 新增 `triage.ai.*`
   - 接入 `TriageModelGateway`

3. **提交 3：Redis 化 TriageSessionManager**
   - 会话存储改 Redis
   - 增加 TTL

4. **提交 4：MySQL 化 TriageRepository**
   - 建表
   - Mapper / Repository / 持久化接入

这样做的好处是：

- 每一步都能单独验证
- 出问题容易回退
- 不会一次性把 RAG 主线和 triage 一起搅乱

---

## 结论

当前最合理的改造方向是：

- **模型隔离**：让 `triage` 有自己独立的 AI 模型配置
- **会话 Redis 化**：替换内存 `ConcurrentHashMap`
- **结果 MySQL 化**：替换 Mock Repository
- **中文体验收口**：统一中文并自然化追问

这是在不破坏你现有智能科普 RAG、AI 报告、推荐系统、Feign 主线前提下，最稳妥的后续演进方案。
