# Athena Insight 数据库设计文档

## 1. 文档说明
本文件详细记录了 `athena-insight` 模块的数据库表结构设计。该模块主要负责内容语义体系（Topic）、特征快照、用户洞察以及推荐辅助数据的存储。

### 设计原则
- **轻量化存储**：仅保存洞察结果与特征，不保存原始业务流水。
- **灵活性**：通过 JSON 字段存储复杂特征，减少表结构变更频率。
- **高性能**：关键查询路径均建立索引，支持高效的推荐召回。

---

## 2. 数据库概览
所有表均使用 `InnoDB` 存储引擎，字符集为 `utf8mb4`。

| 表名 | 说明 | 核心用途 |
| :--- | :--- | :--- |
| `tb_topic` | Topic 主表 | 定义系统的语义标签体系 |
| `tb_note_topic_relation` | 内容与 Topic 关系表 | 标注内容所属的主题及权重 |
| `tb_record_topic_relation` | 记录与 Topic 映射表 | 将用户健康记录映射为语义 Topic |
| `tb_user_feature_snapshot` | 用户特征快照表 | 存储多维度的用户行为与状态快照 |
| `tb_note_feature` | 内容特征快照表 | 存储内容的质量分、热度及语义特征 |
| `tb_user_insight` | 用户洞察结果表 | 存储最终推导出的用户偏好与风险标签 |

---

## 3. 表结构详解

### 3.1 Topic 主表 (`tb_topic`)
用于维护整个系统的语义树，支持多级分类。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | 是 | - | 主键 ID |
| `topic_code` | VARCHAR(64) | 是 | - | 唯一编码 (如: menstrual_care) |
| `topic_name` | VARCHAR(64) | 是 | - | 显示名称 (如: 经期护理) |
| `parent_id` | BIGINT | 否 | NULL | 父节点 ID |
| `topic_type` | TINYINT | 是 | 1 | 类型：1内容类, 2健康类, 3通用类 |
| `status` | TINYINT | 是 | 1 | 状态：1启用, 0停用 |
| `sort` | INT | 是 | 0 | 排序值，越小越靠前 |
| `description` | VARCHAR(255)| 否 | NULL | 详细描述 |
| `create_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引：**
- `uk_topic_code`: `topic_code` (Unique)
- `idx_parent_id`: `parent_id`
- `idx_topic_type`: `topic_type`

---

### 3.2 内容与 Topic 关系表 (`tb_note_topic_relation`)
记录内容（Note）与主题（Topic）的多对多关系。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | 是 | - | 主键 ID |
| `note_id` | BIGINT | 是 | - | 内容 ID |
| `topic_id` | BIGINT | 是 | - | Topic ID |
| `weight` | DECIMAL(6,4) | 是 | 1.0000 | 权重 (0-1) |
| `source_type` | TINYINT | 是 | 1 | 来源：1人工, 2规则, 3系统 |
| `create_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

**索引：**
- `uk_note_topic`: `(note_id, topic_id)` (Unique)
- `idx_topic_id`: `topic_id`

---

### 3.3 记录与 Topic 映射规则表 (`tb_record_topic_relation`)
定义如何将底层健康记录项（Record Item）映射到高层语义 Topic。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | 是 | - | 主键 ID |
| `mode_type` | TINYINT | 是 | - | 模式：1经期, 2备孕, 3怀孕 |
| `record_item_id` | INT | 是 | - | 记录项 ID |
| `record_value_pattern`| VARCHAR(128)| 否 | NULL | 匹配规则 (正则或特定值) |
| `topic_id` | BIGINT | 是 | - | 映射到的 Topic ID |
| `weight` | DECIMAL(6,4) | 是 | 1.0000 | 映射权重 |
| `create_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引：**
- `idx_mode_item`: `(mode_type, record_item_id)`
- `idx_topic_id`: `topic_id`

---

### 3.4 用户特征快照表 (`tb_user_feature_snapshot`)
保存用户在特定时刻的特征全集，主要用于推荐引擎。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | 是 | - | 主键 ID |
| `user_id` | BIGINT | 是 | - | 用户 ID |
| `base_feature_json` | JSON | 否 | NULL | 基础特征 (年龄、城市等) |
| `behavior_feature_json`| JSON | 否 | NULL | 行为特征 (活跃度、点击偏好) |
| `health_feature_json` | JSON | 否 | NULL | 健康特征 (当前周期阶段等) |
| `generated_at` | DATETIME | 是 | CURRENT_TIMESTAMP | 快照生成时间 |
| `feature_version` | INT | 是 | 1 | 版本号 |
| `create_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引：**
- `uk_user_id`: `user_id` (Unique)
- `idx_generated_at`: `generated_at`

---

### 3.5 内容特征快照表 (`tb_note_feature`)
保存内容的核心推荐特征。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | 是 | - | 主键 ID |
| `note_id` | BIGINT | 是 | - | 内容 ID |
| `type` | TINYINT | 是 | - | 0科普, 1图文, 2视频 |
| `author_id` | BIGINT | 是 | - | 作者 ID |
| `title` | VARCHAR(255)| 否 | NULL | 冗余标题，方便查看 |
| `cover_url` | VARCHAR(512)| 否 | NULL | 冗余封面图 |
| `channel_id` | INT | 否 | NULL | 原始栏目 ID |
| `status` | TINYINT | 是 | 1 | 状态 |
| `topic_feature_json` | JSON | 否 | NULL | 语义特征数组 |
| `quality_score` | DECIMAL(10,4)| 是 | 0.0000 | 质量分 |
| `hot_score` | DECIMAL(10,4)| 是 | 0.0000 | 热度分 |
| `feature_version` | INT | 是 | 1 | 版本号 |
| `create_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引：**
- `uk_note_id`: `note_id` (Unique)
- `idx_type_status`: `(type, status)`
- `idx_hot_score`: `hot_score`

---

### 3.6 用户洞察结果表 (`tb_user_insight`)
存储通过 AI 或算法推导出的深度洞察结论。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | 是 | - | 主键 ID |
| `user_id` | BIGINT | 是 | - | 用户 ID |
| `health_focus_json` | JSON | 否 | NULL | 健康关注点 (如: 痛经) |
| `content_focus_json` | JSON | 否 | NULL | 内容偏好点 (如: 饮食) |
| `risk_tags_json` | JSON | 否 | NULL | 风险标签 (如: 周期不稳) |
| `recommendation_reasons_json`| JSON| 否| NULL | 推荐理由话术 |
| `generated_at` | DATETIME | 是 | CURRENT_TIMESTAMP | 生成时间 |
| `insight_version` | INT | 是 | 1 | 版本号 |
| `create_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引：**
- `uk_user_id`: `user_id` (Unique)

---
out/insight/insight.png

## 4. 关键数据示例

### 4.1 Topic 示例
```sql
-- 经期护理
INSERT INTO tb_topic (topic_code, topic_name, topic_type) VALUES ('menstrual_care', '经期护理', 1);
```

### 4.2 特征 JSON 示例
**用户行为特征 (`behavior_feature_json`):**
```json
{
  "activeDays30d": 19,
  "topicPreference": {
    "经期护理": 0.91,
    "痛经缓解": 0.85
  }
}
```

**用户洞察风险标签 (`risk_tags_json`):**
```json
["周期波动", "经期不适"]
```
