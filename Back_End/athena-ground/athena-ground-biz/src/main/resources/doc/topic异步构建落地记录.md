# Topic 与用户画像全链路落地记录

> 模块：`athena-ground-biz` + `athena-insight-biz`
> 状态：已完成第一版主链闭环
> 更新时间：2026-04-14

## 一、文档目的

这份文档用于统一记录当前 Athena 在“内容 topic 构建 + 用户画像 + 洞察 + 报告 + 推荐”这一条链路上的实际落地结果。

重点回答两个问题：

1. **整个链路现在是怎么跑的**
2. **当前用户画像方案（三层架构）到底是什么**

---

## 二、整体链路总览

当前已经落地的完整链路如下：

```text
用户发布内容（ground）
  -> 生成 note 主数据
  -> 异步构建内容 topic 关系（tb_note_topic_relation）

用户产生行为 / 健康记录
  -> ground 提供内容行为数据
  -> record 提供周期 / 预测 / 日记录数据
  -> userauth 提供基础用户信息

insight 聚合用户画像
  -> 生成 featureSnapshot
     - baseFeatureJson
     - behaviorFeatureJson
     - healthFeatureJson

insight 生成洞察
  -> 生成 insight
     - healthFocusJson
     - contentFocusJson
     - riskTagsJson
     - recommendationReasonsJson

report 输出用户报告
  -> 汇总 feature + insight

recommend 输出推荐结果
  -> 使用 noteFeature + 用户画像排序
```

---

## 三、内容 topic 链路（ground）

### 3.1 当前目标

保证：

- 新内容发布成功后，能够自动建立 `tb_note_topic_relation`
- 不依赖 `insight` 补洞
- topic 构建职责内聚在 `ground`

### 3.2 当前实现方式

采用：

- **发布成功后发 MQ 事件**
- **`ground` 内部异步消费**
- **规则提取 topic**
- **重建 `tb_note_topic_relation`**

### 3.3 当前链路

```text
POST /athena/blog/submit
  -> GroundServiceImpl.submitNote()
  -> NotePublishServiceImpl.publish()
  -> 发送 NoteTopicBuildEvent
  -> RocketMQ Consumer 异步消费
  -> NoteTopicBuildServiceImpl.rebuildTopicsForNote()
  -> RuleBasedNoteTopicExtractor 提取 topic
  -> 删除旧关系并重建 tb_note_topic_relation
```

### 3.4 当前 topic 提取规则

当前为规则版 V1，主要基于标题/正文关键词命中：

- `经期 / 姨妈 / 生理期 / 月经` -> `经期护理`
- `痛经 / 腹痛 / 热敷 / 缓解疼痛 / 姨妈痛` -> `痛经缓解`
- `失眠 / 睡不好 / 睡不着 / 熬夜 / 睡眠` -> `睡眠调节`
- `焦虑 / 烦躁 / 情绪低落 / 压力 / 崩溃` -> `情绪调节`
- `饮食 / 忌口 / 补铁 / 吃什么 / 食物` -> `饮食管理`

并做了简单权重处理：

- 标题命中权重高于正文
- `channelId` 有轻微加权
- 同一 topic 可累计权重

### 3.5 当前结果

当前 `ground` 已具备：

- 新内容发布后自动挂 topic 的基础能力
- topic 构建职责内聚在内容域内部
- 为 `insight` 的个性化特征、洞察与推荐提供稳定输入

---

## 四、用户画像方案：三层架构

当前 `athena-insight` 的用户画像采用 **三层结构**：

1. **基础层（Base Feature）**
2. **行为层（Behavior Feature）**
3. **健康层（Health Feature）**

这三层共同组成 `featureSnapshot`。

### 4.1 总体结构

```text
User Profile
  ├─ Base Feature      基础身份画像
  ├─ Behavior Feature  内容行为画像
  └─ Health Feature    健康状态画像
```

这三层各自职责不同：

- **Base**：回答“这个用户是谁”
- **Behavior**：回答“这个用户最近在看什么、偏好什么”
- **Health**：回答“这个用户当前健康状态与记录主题是什么”

---

## 五、第一层：基础层画像（Base Feature）

### 5.1 数据来源

来自：

- `userauth`

通过 `UserAuthRpc.findByUserId(userId)` 获取。

### 5.2 当前字段

当前存入 `baseFeatureJson` 的字段为：

- `userId`
- `nickName`
- `icon`
- `priority`

### 5.3 作用

基础层不直接驱动推荐排序，但它是整个画像快照的身份锚点，用于：

- 标识当前快照属于哪个用户
- 后续 UI / 报告展示引用用户身份信息
- 为后续扩展更多静态画像字段预留位置

### 5.4 当前特点

这一层比较“薄”，属于稳定低频变化数据。

---

## 六、第二层：行为层画像（Behavior Feature）

### 6.1 数据来源

来自：

- `ground` 的内容行为数据

当前会聚合以下四类：

- 用户自己发布的内容 `myList`
- 点赞列表 `likeList`
- 收藏列表 `collectList`
- 浏览历史 `viewHistory`

### 6.2 当前字段

当前 `behaviorFeatureJson` 中包含：

- `viewCount30d`
- `likeCount30d`
- `collectCount30d`
- `activeDays30d`
- `typePreference`
- `topicPreference`
- `recentViewedNoteIds`
- `strongPositiveNoteIds`

### 6.3 每类字段含义

#### 行为强度字段
- `viewCount30d`：浏览数量近似值
- `likeCount30d`：点赞数量近似值
- `collectCount30d`：收藏数量近似值
- `activeDays30d`：当前实现为活跃内容规模近似值，不是严格自然日活跃数

#### 类型偏好字段
- `typePreference`：对内容 type 的频次统计
- 表示用户更偏爱图文 / 视频 / 科普等内容形态

#### 主题偏好字段
- `topicPreference`：用户近期行为命中的 topic 频次统计
- 这是行为层最核心的“内容兴趣主题”表达

#### 过滤与强偏好字段
- `recentViewedNoteIds`：用于推荐去重/过滤最近看过的内容
- `strongPositiveNoteIds`：用于表示用户对某些内容有更强正反馈（点赞/收藏/高互动）

### 6.4 `topicPreference` 如何计算

行为层 topic 计算逻辑如下：

1. 遍历用户近期行为涉及到的内容
2. 从每条内容中取 `blogId`
3. 调用 `topicService.listTopicsByNoteId(blogId)`
4. 从 `tb_note_topic_relation -> tb_topic` 找到该内容挂载的 topic
5. 对 topicName 做频次统计

最终形成：

```json
"topicPreference": [
  {"topic": "经期护理", "count": 5},
  {"topic": "痛经缓解", "count": 3}
]
```

### 6.5 作用

行为层主要驱动：

- `contentFocusJson`
- `recommendationReasonsJson`
- 推荐排序中的 `typeMatchScore`
- 推荐排序中的 `topicMatchScore`
- 推荐过滤逻辑（最近浏览去重）

### 6.6 当前特点

这一层是**内容个性化的主力层**。

如果没有 topic 数据，这一层就会退化成：

- 只能按 type 粗粒度偏好
- 或回退到热度兜底

---

## 七、第三层：健康层画像（Health Feature）

### 7.1 数据来源

来自：

- `record` 模块内部接口

当前接入的数据源有：

- 周期统计 `cycle-stats`
- 周期预测 `prediction`
- 近 30 天记录 `records`

### 7.2 当前字段

当前 `healthFeatureJson` 中包含：

- `currentModeType`
- `averageCycleLength`
- `averageDurationDays`
- `todayInActualCycle`
- `todayInPredictedCycle`
- `predictedNextStartDate`
- `predictedNextEndDate`
- `symptomTopics`
- `recordDays30d`

### 7.3 每类字段含义

#### 周期结构字段
- `averageCycleLength`
- `averageDurationDays`
- `predictedNextStartDate`
- `predictedNextEndDate`

表示用户当前已经形成的周期统计与预测结果。

#### 当前状态字段
- `currentModeType`

表示当前健康记录主模式，如：
- 经期
- 备孕
- 怀孕

#### 记录连续性字段
- `recordDays30d`

表示近 30 天有多少个自然日产生了记录。

#### 健康主题字段
- `symptomTopics`

这是健康层最核心的主题表达。

### 7.4 `symptomTopics` 如何计算

健康层 topic 计算逻辑如下：

1. 获取近 30 天记录
2. 对每条记录取：
   - `modeType`
   - `recordItemId`
3. 调用 `topicService.listTopicsByModeAndRecordItem(modeType, recordItemId)`
4. 从 `tb_record_topic_relation -> tb_topic` 找到对应的健康 topic
5. 对 topicName 做频次聚合

最终形成：

```json
"symptomTopics": ["痛经缓解", "睡眠调节"]
```

### 7.5 作用

健康层主要驱动：

- `healthFocusJson`
- `contentFocusJson`（叠加 symptom topic）
- `riskTagsJson`
- `recommendationReasonsJson`
- 推荐排序中的 `healthMatchScore`

### 7.6 当前特点

这一层是**健康个性化的主力层**。

如果没有健康记录或 record-topic 映射不全，这一层就会退化成：

- 只能输出数据不足提示
- 推荐无法体现健康主题偏好

---

## 八、featureSnapshot：三层画像的产物

当前 `UserFeatureServiceImpl.refreshSnapshot(...)` 最终会写出一份 `featureSnapshot`：

```text
featureSnapshot
  ├─ baseFeatureJson
  ├─ behaviorFeatureJson
  └─ healthFeatureJson
```

这是当前整个用户画像体系的**事实快照层**。

特点：

- 接近原始聚合结果
- 结构化程度高于上游原始接口
- 低解释、高可计算

后续所有洞察、报告、推荐都建立在它之上。

---

## 九、洞察层（Insight）：从画像到解释

当前 `UserInsightServiceImpl` 基于 `featureSnapshot` 生成解释性洞察。

### 9.1 当前输出字段

- `healthFocusJson`
- `contentFocusJson`
- `riskTagsJson`
- `recommendationReasonsJson`

### 9.2 这层的定位

如果说 `featureSnapshot` 是“事实层”，那么 `insight` 就是“解释层”。

它负责把机器可计算的字段转成：

- 可展示
- 可读懂
- 可直接反馈给用户或前端

### 9.3 各字段作用

#### `healthFocusJson`
描述健康层重点，例如：
- 周期统计已形成
- 当前处于经期模式
- 近期健康主题集中在某些方向
- 记录连续性较高

#### `contentFocusJson`
描述用户当前的内容关注方向，来源于：
- `topicPreference`
- `symptomTopics`

#### `riskTagsJson`
输出当前数据完整度或风险提示，例如：
- `内容行为数据较少`
- `健康记录数据不足`
- `周期统计样本不足`
- `当前整体状态稳定`

#### `recommendationReasonsJson`
输出推荐系统为什么这么推荐，例如：
- 近期偏好图文内容
- 近期关注某类主题
- 当前更关注某个健康主题
- 或退回热度/质量兜底

---

## 十、报告层（Report）：从洞察到展示结果

当前 `ReportServiceImpl` 基于：

- `featureSnapshot`
- `insight`

输出用户报告对象。

### 10.1 当前作用

这层不是重新计算画像，而是：

- 聚合已有结果
- 形成更适合前端展示的 VO
- 给出 summary、focus、risk、recommendTopics

### 10.2 当前 summary 规则

- 有 feature 且有 insight 且 focus 非空：输出个性化分析报告
- 有 feature 且有 insight 但 focus 为空：输出基础分析报告
- 只有其中之一：提示不完整

### 10.3 当前特点

report 层是**展示整合层**，不承担重计算。

---

## 十一、推荐层（Recommend）：从画像到排序

当前 `RecommendationServiceImpl` 使用：

- note 特征 `tb_note_feature`
- 用户行为画像
- 用户健康画像

进行综合排序。

### 11.1 当前排序因子

当前综合使用：

- `hotScore`
- `qualityScore`
- `typeMatchScore`
- `topicMatchScore`
- `healthMatchScore`

### 11.2 当前健康与主题如何参与推荐

#### 行为侧参与
- `typePreference` 决定类型偏好匹配
- `topicPreference` 决定内容主题匹配
- `recentViewedNoteIds` 决定去重过滤

#### 健康侧参与
- `symptomTopics` 决定健康主题匹配
- `currentModeType` 提供轻度模式加权

### 11.3 当前推荐理由优先级

推荐理由大致优先顺序：

1. 命中健康主题
2. 命中行为主题
3. 命中类型偏好
4. 热度兜底

### 11.4 当前特点

推荐层是**消费画像能力最强的一层**。

它既使用行为层，也使用健康层，是“画像价值是否真正转化成用户体验”的最终体现。

---

## 十二、当前架构关系总结

可以把现在的方案理解成：

```text
Ground / Record / UserAuth
      │
      ▼
Feature Snapshot（事实层）
  ├─ Base Feature
  ├─ Behavior Feature
  └─ Health Feature
      │
      ▼
Insight（解释层）
  ├─ Health Focus
  ├─ Content Focus
  ├─ Risk Tags
  └─ Recommendation Reasons
      │
      ├─ Report（展示层）
      └─ Recommend（决策层）
```

---

## 十三、当前已完成的闭环

当前已经完成以下闭环：

1. 新内容发布后自动构建 topic 关系
2. `ground` 行为能映射到 `topicPreference`
3. `record` 健康记录能映射到 `symptomTopics`
4. `featureSnapshot` 能形成三层画像快照
5. `insight` 能从画像快照产出解释型结果
6. `report` 能汇总画像与洞察
7. `recommend` 能消费行为/健康画像做个性化排序

---

## 十四、当前明确未做

为了控制范围，当前仍未实现：

- topic 异步构建补偿任务
- 内容编辑后的 topic 重建
- 内容删除后的 topic 关系清理
- topic 构建失败告警
- 规则配置化后台
- 模型 / NLP 自动打 topic
- `RecommendationServiceImpl` 中推荐侧 JSON 解析统一替换为 JsonHelper
- 更严格的画像版本化 / 重建回刷机制

这些可以在当前主链稳定后继续扩展。

---

## 十五、当前结论

当前 Athena 的用户画像方案已经不是单点接口，而是一套完整链路：

- `ground` 负责内容与内容 topic 真源
- `userauth` 提供基础身份信息
- `record` 提供健康记录真源
- `insight` 负责把多源数据聚合为三层用户画像
- `insight` 再把画像转成洞察、报告与推荐输入

其中，三层用户画像是当前系统的核心中台表达：

- **基础层**：身份锚点
- **行为层**：内容兴趣与互动偏好
- **健康层**：健康状态与记录主题

在这个基础上，当前已经具备第一版个性化能力，并且具备继续往补偿、规则升级、效果优化方向迭代的条件。
