# Topic 标签体系设计

## 1. 设计目标

在 `athena-insight` 中，`topic` 作为统一语义标签，服务于：

- 用户兴趣建模
- 内容主题表达
- 健康记录语义映射
- 推荐召回与排序
- 推荐理由生成
- 健康分析报告总结

`topic` 不是页面栏目，也不是前端导航概念，而是跨域的语义标签中心。

---

## 2. 为什么要引入 topic

当前系统已经存在 `channel` 概念，但 `channel` 更偏业务栏目语义，适合：

- 导航 tab
- 列表入口
- 内容归类
- 运营配置

而推荐和分析更需要细粒度语义标签，例如：

- 经期护理
- 痛经缓解
- 睡眠调节
- 情绪波动
- 备孕知识
- 孕期营养

这些标签显然不适合直接由 `channel` 承担，因此需要单独建设 `topic` 体系。

---

## 3. topic 的设计原则

### 3.1 语义统一

同一个 `topic` 要能被：

- 内容使用
- 用户偏好使用
- 健康记录使用
- 报告总结使用

### 3.2 允许层级

例如：

- 女性健康
  - 经期护理
    - 痛经缓解
    - 经期饮食
  - 睡眠健康
  - 情绪管理

### 3.3 允许多对多关系

- 一篇内容可以对应多个 topic
- 一个健康记录项可以映射多个 topic
- 一个用户可以偏好多 topic

### 3.4 保持可解释性

topic 名称需要可直接对用户展示，不建议全是内部编码语义。

---

## 4. topic 分类建议

可以先把 topic 分成三类：

### 4.1 内容主题类 `content`

如：

- 经期护理
- 睡眠调节
- 饮食管理
- 情绪调节
- 妇科护理

### 4.2 健康状态类 `health`

如：

- 痛经缓解
- 月经不规律
- 白带异常
- 失眠困扰
- 情绪波动

### 4.3 通用类 `common`

如：

- 新手入门
- 高关注内容
- 基础知识

第一期如果不想太复杂，也可以先统一放一张表，通过 `topic_type` 区分。

---

## 5. 数据表建议

### 5.1 `tb_topic`

建议字段：

- `id`
- `topic_code`
- `topic_name`
- `parent_id`
- `topic_type`
- `status`
- `sort`
- `description`
- `create_time`
- `update_time`

说明：

- `topic_code`：内部唯一编码，便于程序识别
- `topic_name`：展示名
- `parent_id`：支持 topic 树
- `topic_type`：内容类/健康类/通用类

---

### 5.2 `tb_note_topic_relation`

用于建立内容与 topic 的关系。

建议字段：

- `id`
- `note_id`
- `topic_id`
- `weight`
- `source_type`
- `create_time`

说明：

- `weight`：topic 在该内容中的强度
- `source_type`：来源，如 `manual` / `rule` / `system`

---

### 5.3 `tb_record_topic_relation`

用于建立健康记录与 topic 的映射规则。

建议字段：

- `id`
- `mode_type`
- `record_item_id`
- `record_value_pattern`
- `topic_id`
- `weight`
- `create_time`

说明：

- `mode_type`：1 经期 / 2 备孕 / 3 怀孕
- `record_item_id`：日记录项 ID
- `record_value_pattern`：可选，用于支持细粒度值映射
- `weight`：该记录项对 topic 的贡献权重

---

## 6. topic 与内容的关系

一篇内容可绑定多个 topic。

### 示例

某篇科普文章：

- 标题：经期腹痛应该怎么缓解
- type：0
- channel：经期护理栏目
- topics：
  - 经期护理（0.9）
  - 痛经缓解（1.0）
  - 饮食建议（0.2）

某篇广场视频：

- 标题：我缓解姨妈痛的几个小动作
- type：2
- topics：
  - 痛经缓解（0.9）
  - 经期运动（0.7）

---

## 7. topic 与健康记录的关系

这部分是后续推荐与分析报告最有价值的桥梁。

### 示例映射

- `recordItemId=腹痛` -> `topic=痛经缓解`
- `recordItemId=失眠` -> `topic=睡眠调节`
- `recordItemId=情绪低落` -> `topic=情绪波动`
- `recordItemId=食欲差` -> `topic=饮食管理`

如果后续有枚举型 `recordValue`，还可以进一步细化：

- `recordItemId=腹痛, recordValue=严重` -> `topic=痛经缓解, weight=1.0`
- `recordItemId=腹痛, recordValue=轻微` -> `topic=痛经缓解, weight=0.5`

---

## 8. topic 与用户偏好的关系

用户偏好不再直接围绕 `channel`，而是围绕 `topic` 计算。

### 计算来源

- 浏览过哪些 topic 内容
- 点赞过哪些 topic 内容
- 收藏过哪些 topic 内容
- 最近的身体记录映射出哪些 topic

### 用户 topic 偏好示例

```json
{
  "经期护理": 0.92,
  "痛经缓解": 0.85,
  "睡眠调节": 0.43
}
```

---

## 9. topic 在推荐中的作用

### 9.1 召回

优先召回与用户高权重 topic 匹配的内容。

### 9.2 排序

根据用户偏好 topic、健康状态 topic 与内容 topic 的匹配程度打分。

### 9.3 推荐理由

例如：

- 根据你近期关注的 `经期护理` 推荐
- 根据你最近的记录主题 `睡眠调节` 推荐

---

## 10. topic 在分析报告中的作用

topic 可用于把分散的健康记录和内容偏好总结成更可读的主题结论。

### 例如报告里可以输出

- 近期关注主题：经期护理、睡眠调节
- 身体状态主题：痛经缓解、情绪波动
- 建议优先阅读主题：饮食管理、睡眠调节

---

## 11. topic 建设策略建议

### 第一阶段

先由人工预设 topic，并建立：

- 内容与 topic 的人工绑定规则
- recordItem 与 topic 的人工映射规则

### 第二阶段

再逐步引入：

- 半自动标注
- topic 权重调整
- topic 层级扩展

### 第三阶段

后续如果接入 RAG/语义能力，可再引入：

- 自动 topic 抽取
- 主题聚类
- 相似主题归并

---

## 12. 第一版 topic 体系建议

建议第一版先控制在 10~20 个 topic 内，优先覆盖你的核心健康场景。

示例：

- 经期护理
- 痛经缓解
- 月经不规律
- 睡眠调节
- 情绪波动
- 饮食管理
- 妇科护理
- 备孕知识
- 孕期护理
- 运动恢复

---

## 13. 最终结论

在 `athena-insight` 中：

- `channel` 继续保留原业务用途
- `topic` 作为统一兴趣和语义标签
- 推荐系统围绕 topic 做召回与排序
- 分析报告围绕 topic 做总结与建议

**topic 是推荐和报告的共同语言。**
