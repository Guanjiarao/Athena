# Athena Insight 整体架构方案

> 文档目标：给 `athena-insight` 微服务建立统一的设计基线，后续数据库、接口、实现、联调均以本方案为主。

## 1. 设计背景

当前系统已经存在多个相互独立但业务上高度关联的数据域：

- `athena-userauth`：用户基础信息真源
- `athena-ground`：内容、互动行为、广场/科普内容真源
- `athena-record`：身体记录、周期记录真源

接下来系统要同时支持：

1. `type=0` 科普推荐
2. `type=1` 广场图文推荐
3. `type=2` 广场视频推荐
4. 基于身体记录的用户分析报告
5. 基于用户状态与兴趣的内容分发

如果继续把这些能力分散在 `ground` 或 `record` 里，后续会出现职责混乱、代码耦合、演进困难的问题，因此需要单独建设 `athena-insight` 微服务。

---

## 2. 微服务定位

`athena-insight` 的定位：

**用户洞察与内容分发中心**。

它不是业务真源服务，而是：

- 聚合用户、内容、健康数据
- 构建统一用户特征
- 构建统一内容特征
- 输出推荐结果
- 输出分析报告
- 输出推荐理由/洞察结论

---

## 3. 核心设计原则

### 3.1 真源归属不变

原始数据依然保留在各自服务中：

- 用户资料真源在 `athena-userauth`
- 内容真源在 `athena-ground`
- 健康记录真源在 `athena-record`

`athena-insight` 只消费数据，不接管真源写入职责。

### 3.2 统一特征，不统一真源

所有推荐和分析能力，都以 `UserFeatureSnapshot` / `NoteFeature` 为中间层，不直接耦合原始业务表。

### 3.3 使用 topic 而不是 channel 做兴趣标签

- `channel` 保留原有栏目、页面导航、运营分类语义
- `topic` 作为推荐、兴趣、洞察、分析报告的统一语义标签

### 3.4 一个引擎，多种内容类型

推荐系统只保留一套主流程，但针对不同 `type` 使用不同候选池和不同排序权重：

- `type=0`：科普推荐
- `type=1`：广场图文推荐
- `type=2`：广场视频推荐

### 3.5 报告与推荐共用底座

推荐系统与分析报告共用：

- 用户特征快照
- 用户洞察结论
- topic 标签体系
- 内容特征

---

## 4. 总体逻辑架构

```text
athena-userauth        athena-ground              athena-record
     │                      │                         │
     │                      │                         │
     └──── 用户基础信息 ────┼──── 内容与互动行为 ────┼──── 身体记录与周期
                            │                         │
                            ▼
                   athena-insight Feature Center
                            │
            ┌───────────────┼────────────────┐
            │               │                │
            ▼               ▼                ▼
      User Feature      Note Feature      Topic System
            │               │                │
            └───────────────┴────────────────┘
                            │
                            ▼
                    Insight / Recommendation / Report
```

---

## 5. 微服务内部子域划分

建议 `athena-insight` 内部拆成 4 个子域：

### 5.1 Topic 子域

职责：

- 管理 topic 定义
- 建立内容与 topic 关系
- 建立健康记录与 topic 的映射关系
- 为推荐、特征、报告提供统一语义标签

### 5.2 Feature 子域

职责：

- 聚合用户基础信息、行为信息、健康信息
- 生成 `UserFeatureSnapshot`
- 生成 `NoteFeature`

### 5.3 Recommend 子域

职责：

- 针对 `type=0/1/2` 输出推荐结果
- 负责召回、排序、打散、推荐理由

### 5.4 Report 子域

职责：

- 基于用户特征和洞察生成分析报告
- 输出周期分析、近期状态分析、推荐主题建议

---

## 6. 统一用户特征模型

建议统一输出对象为：`UserFeatureSnapshot`

包含三层特征：

### 6.1 基础特征 `UserBaseFeature`

- `userId`
- `age`
- `gender`
- `city`
- `level`
- `creator`
- `registerDays`

### 6.2 行为特征 `UserBehaviorFeature`

- `viewCount30d`
- `likeCount30d`
- `collectCount30d`
- `activeDays30d`
- `typePreference`
- `topicPreference`
- `recentViewedNoteIds`
- `strongPositiveNoteIds`

### 6.3 健康特征 `UserHealthFeature`

- `currentModeType`
- `averageCycleLength`
- `averageDurationDays`
- `todayInActualCycle`
- `todayInPredictedCycle`
- `predictedNextStartDate`
- `predictedNextEndDate`
- `symptomTopics`
- `recordDays30d`

---

## 7. 统一内容特征模型

建议统一输出对象为：`NoteFeature`

核心字段：

- `noteId`
- `type`
- `authorId`
- `channelId`
- `status`
- `topicFeature`
- `qualityScore`
- `hotScore`
- `featureVersion`

说明：

- `channelId` 仍保留，但不再作为兴趣标签核心字段
- `topicFeature` 承担推荐语义中心角色

---

## 8. 推荐系统总体设计

统一推荐入口：

- `type=0`：科普推荐
- `type=1`：广场图文推荐
- `type=2`：广场视频推荐

统一流程：

1. 获取用户特征快照
2. 按 `type` 召回候选内容
3. 过滤无效内容
4. 排序打分
5. 打散
6. 补充推荐理由
7. 返回结果

---

## 9. 分析报告总体设计

统一报告入口：

- 面向用户输出健康分析报告
- 基于近期健康记录和特征快照生成

统一结构建议：

1. 周期概览
2. 身体状态摘要
3. topic 主题总结
4. 建议关注方向
5. 关联推荐内容

---

## 10. 第一期建设范围

建议 V1 范围控制在：

1. topic 体系基础表
2. 用户特征快照表
3. 内容特征表
4. `type=0/1/2` 推荐接口
5. 基础分析报告接口
6. 推荐理由基础输出

暂不纳入：

- 复杂规则后台
- AB 实验系统
- 机器学习排序
- RAG 召回
- 运营可视化配置后台

---

## 11. 后续演进方向

V2 可继续建设：

- topic 管理后台
- 特征定时刷新 + 事件驱动刷新
- 推荐缓存
- 报告模版化/解释增强
- RAG 融合召回
- 智能问答与洞察结果联动

---

## 12. 文档拆分说明

本目录下其他文档说明：

- `01-service-boundary.md`：微服务拆分规则与服务边界
- `02-topic-model-design.md`：topic 标签体系设计
- `03-database-design.md`：数据库设计稿
- `04-api-and-module-design.md`：模块与接口设计稿
- `05-recommendation-design.md`：推荐系统详细设计
- `06-report-design.md`：分析报告详细设计
- `07-implementation-roadmap.md`：落地阶段规划
