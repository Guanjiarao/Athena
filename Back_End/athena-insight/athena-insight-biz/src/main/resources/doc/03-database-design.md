# 数据库设计稿

## 1. 设计目标

`athena-insight` 数据库只保存：

- topic 语义体系
- 特征快照
- 洞察结果
- 内容特征
- 可选的推荐缓存

不保存原始用户真源、原始内容真源、原始健康记录真源。

---

## 2. 表清单

第一期建议建设以下表：

1. `tb_topic`
2. `tb_note_topic_relation`
3. `tb_record_topic_relation`
4. `tb_user_feature_snapshot`
5. `tb_note_feature`
6. `tb_user_insight`

可选扩展：

7. `tb_user_recommend_cache`

---

## 3. `tb_topic`

用途：topic 主表。

建议字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| topic_code | VARCHAR(64) | topic 编码，唯一 |
| topic_name | VARCHAR(64) | topic 名称 |
| parent_id | BIGINT | 父 topic，可空 |
| topic_type | TINYINT | 1内容类 2健康类 3通用类 |
| status | TINYINT | 1启用 0停用 |
| sort | INT | 排序 |
| description | VARCHAR(255) | 描述 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引建议：

- 唯一索引：`uk_topic_code(topic_code)`
- 普通索引：`idx_parent_id(parent_id)`
- 普通索引：`idx_topic_type(topic_type)`

---

## 4. `tb_note_topic_relation`

用途：内容与 topic 的关系表。

建议字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| note_id | BIGINT | 内容 ID |
| topic_id | BIGINT | topic ID |
| weight | DECIMAL(6,4) | 权重 |
| source_type | TINYINT | 1人工 2规则 3系统 |
| create_time | DATETIME | 创建时间 |

索引建议：

- 唯一索引：`uk_note_topic(note_id, topic_id)`
- 普通索引：`idx_topic_id(topic_id)`

说明：

- 一篇内容可以绑定多个 topic
- 第一版可由人工或规则写入

---

## 5. `tb_record_topic_relation`

用途：健康记录项与 topic 的映射规则。

建议字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| mode_type | TINYINT | 1经期 2备孕 3怀孕 |
| record_item_id | INT | 记录项 ID |
| record_value_pattern | VARCHAR(128) | 记录值匹配规则，可空 |
| topic_id | BIGINT | topic ID |
| weight | DECIMAL(6,4) | 权重 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引建议：

- 普通索引：`idx_mode_item(mode_type, record_item_id)`
- 普通索引：`idx_topic_id(topic_id)`

说明：

- 用于把 `daily_record` 语义映射为 topic
- `record_value_pattern` 第一版可为空，后续再扩展

---

## 6. `tb_user_feature_snapshot`

用途：保存用户特征快照。

建议字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 用户 ID |
| base_feature_json | JSON / LONGTEXT | 基础特征 |
| behavior_feature_json | JSON / LONGTEXT | 行为特征 |
| health_feature_json | JSON / LONGTEXT | 健康特征 |
| generated_at | DATETIME | 生成时间 |
| feature_version | INT | 特征版本 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引建议：

- 唯一索引：`uk_user_id(user_id)`
- 普通索引：`idx_generated_at(generated_at)`

### JSON 示例

`base_feature_json`

```json
{
  "age": 24,
  "gender": 2,
  "city": "杭州",
  "level": 3,
  "creator": true,
  "registerDays": 320
}
```

`behavior_feature_json`

```json
{
  "viewCount30d": 82,
  "likeCount30d": 13,
  "collectCount30d": 7,
  "activeDays30d": 19,
  "typePreference": {
    "0": 0.42,
    "1": 0.38,
    "2": 0.20
  },
  "topicPreference": {
    "经期护理": 0.91,
    "痛经缓解": 0.85,
    "睡眠调节": 0.43
  },
  "recentViewedNoteIds": [11, 17, 22]
}
```

`health_feature_json`

```json
{
  "currentModeType": 1,
  "averageCycleLength": 29,
  "averageDurationDays": 5,
  "todayInActualCycle": false,
  "todayInPredictedCycle": true,
  "predictedNextStartDate": "2026-04-18",
  "predictedNextEndDate": "2026-04-22",
  "symptomTopics": {
    "痛经缓解": 0.80,
    "睡眠调节": 0.45
  },
  "recordDays30d": 16
}
```

---

## 7. `tb_note_feature`

用途：保存内容特征快照。

建议字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| note_id | BIGINT | 内容 ID |
| type | TINYINT | 0科普 1图文 2视频 |
| author_id | BIGINT | 作者 ID |
| channel_id | INT | 栏目 ID，可保留原业务维度 |
| status | TINYINT | 内容状态 |
| topic_feature_json | JSON / LONGTEXT | topic 特征 |
| quality_score | DECIMAL(10,4) | 质量分 |
| hot_score | DECIMAL(10,4) | 热度分 |
| feature_version | INT | 特征版本 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引建议：

- 唯一索引：`uk_note_id(note_id)`
- 普通索引：`idx_type_status(type, status)`
- 普通索引：`idx_hot_score(hot_score)`

### JSON 示例

```json
{
  "topics": {
    "经期护理": 0.9,
    "痛经缓解": 0.8,
    "睡眠调节": 0.2
  },
  "suitableModes": [1],
  "contentStyle": "science"
}
```

说明：

- `channel_id` 仅保留兼容与辅助排序用途
- 推荐主语义使用 `topic_feature_json`

---

## 8. `tb_user_insight`

用途：保存从特征推导出的用户洞察结果。

建议字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 用户 ID |
| health_focus_json | JSON / LONGTEXT | 当前健康关注点 |
| content_focus_json | JSON / LONGTEXT | 当前内容偏好焦点 |
| risk_tags_json | JSON / LONGTEXT | 风险标签 |
| recommendation_reasons_json | JSON / LONGTEXT | 推荐原因模板 |
| generated_at | DATETIME | 生成时间 |
| insight_version | INT | 洞察版本 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引建议：

- 唯一索引：`uk_user_id(user_id)`

### JSON 示例

`health_focus_json`

```json
["经期护理", "痛经缓解", "睡眠调节"]
```

`risk_tags_json`

```json
["周期波动", "经期不适"]
```

---

## 9. `tb_user_recommend_cache`（可选）

用途：缓存推荐结果，减少重复计算。

建议字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 用户 ID |
| recommend_type | TINYINT | 0科普 1图文 2视频 |
| result_json | JSON / LONGTEXT | 推荐内容结果 |
| generated_at | DATETIME | 生成时间 |
| expire_at | DATETIME | 过期时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引建议：

- 唯一索引：`uk_user_type(user_id, recommend_type)`
- 普通索引：`idx_expire_at(expire_at)`

第一期可以先不建。

---

## 10. 为什么推荐使用 JSON 字段

### 优点

- 特征结构演进成本低
- topic 数量变化时无需频繁改表
- 支持快速试错
- 适合快照型数据

### 注意点

- 若后续要做复杂统计，可再拆热点字段做冗余列
- 第一版优先保证迭代效率，不追求过度范式化

---

## 11. 第一版落地建议

第一阶段建议优先落地：

1. `tb_topic`
2. `tb_note_topic_relation`
3. `tb_record_topic_relation`
4. `tb_user_feature_snapshot`
5. `tb_note_feature`
6. `tb_user_insight`

这样已经足够支撑：

- topic 管理
- 用户特征快照
- 内容特征快照
- 推荐系统
- 分析报告

---

## 12. 后续扩展建议

后续可继续扩展：

- `tb_feature_refresh_task`
- `tb_recommend_strategy_config`
- `tb_topic_alias`
- `tb_report_template`

但第一期不建议一次性建太多表，避免过度设计。
