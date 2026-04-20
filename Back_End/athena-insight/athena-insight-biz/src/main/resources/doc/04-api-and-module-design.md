# 模块与接口设计稿

## 1. 模块结构建议

按照现有项目风格，建议 `athena-insight` 使用以下结构：

```text
athena-insight/
├─ athena-insight-api/
└─ athena-insight-biz/
```

---

## 2. API 模块设计

路径建议：

```text
athena-insight-api
└─ src/main/java/athena/insight/
   ├─ api/
   │  └─ InsightFeignApi.java
   └─ constant/
      └─ ApiConstants.java
```

### 2.1 `ApiConstants`

建议定义 insight 服务名与统一前缀。

示例：

- 服务名：`athena-insight-biz`
- 前缀：`/athena/insight`

### 2.2 `InsightFeignApi`

建议暴露：

- 查询推荐结果
- 查询分析报告
- 查询用户特征
- 刷新用户特征

主要供内部服务调用。

---

## 3. Biz 模块结构设计

```text
athena-insight-biz
└─ src/main/java/athena/insight/biz/
   ├─ config/
   ├─ controller/
   ├─ domain/
   │  ├─ dataobject/
   │  ├─ dto/
   │  ├─ mapper/
   │  └─ vo/
   ├─ rpc/
   ├─ service/
   │  └─ impl/
   ├─ feature/
   ├─ recommend/
   ├─ report/
   ├─ topic/
   └─ AthenaInsightApplication.java
```

---

## 4. Controller 设计

建议第一期只建设 3 个 controller。

### 4.1 `InsightRecommendController`

职责：推荐接口输出。

建议接口：

- `GET /athena/insight/recommend?type=0&pageNum=1&pageSize=10`
- `GET /athena/insight/recommend?type=1&pageNum=1&pageSize=10`
- `GET /athena/insight/recommend?type=2&pageNum=1&pageSize=10`

### 4.2 `InsightReportController`

职责：分析报告输出。

建议接口：

- `GET /athena/insight/report`

### 4.3 `InsightFeatureController`

职责：特征调试与刷新接口。

建议接口：

- `GET /athena/insight/feature`
- `POST /athena/insight/feature/refresh`

第一期主要用于开发联调和后台调试。

---

## 5. Domain 设计

### 5.1 `dataobject`

建议对象：

- `UserFeatureSnapshotDO`
- `UserInsightDO`
- `NoteFeatureDO`
- `TopicDO`
- `NoteTopicRelationDO`
- `RecordTopicRelationDO`

### 5.2 `dto`

建议对象：

- `RecommendQueryDTO`
- `FeatureRefreshDTO`
- `ReportQueryDTO`
- `TopicWeightDTO`

### 5.3 `vo`

建议对象：

- `RecommendItemVO`
- `RecommendResultVO`
- `UserFeatureSnapshotVO`
- `UserInsightVO`
- `UserAnalysisReportVO`

### 5.4 `mapper`

建议接口：

- `UserFeatureSnapshotMapper`
- `UserInsightMapper`
- `NoteFeatureMapper`
- `TopicMapper`
- `NoteTopicRelationMapper`
- `RecordTopicRelationMapper`

---

## 6. RPC 设计

由于 `athena-insight` 需要聚合其他服务数据，建议建立独立 `rpc` 包。

### 6.1 `UserAuthFeignApi`

职责：

- 拉用户基础信息
- 获取生日、性别、城市、等级等

### 6.2 `GroundFeignApi`

职责：

- 拉内容信息
- 拉互动行为聚合结果
- 拉 note 基础信息

### 6.3 `RecordFeignApi`

职责：

- 拉周期统计
- 拉每日记录
- 拉当前模式/预测信息

---

## 7. Service 设计

建议 service 接口分为 5 组。

### 7.1 `TopicService`

职责：

- topic 查询
- topic 关系管理
- topic 规则映射

建议方法：

- `listAllActiveTopics()`
- `listTopicsByNoteId(Long noteId)`
- `resolveTopicsFromRecord(...)`

### 7.2 `UserFeatureService`

职责：

- 获取用户特征快照
- 刷新用户特征快照

建议方法：

- `getSnapshot(Long userId)`
- `refreshSnapshot(Long userId)`

### 7.3 `UserInsightService`

职责：

- 从用户特征生成洞察结果

建议方法：

- `getInsight(Long userId)`
- `refreshInsight(Long userId)`

### 7.4 `RecommendationService`

职责：

- 统一推荐输出

建议方法：

- `recommend(Long userId, Byte type, Integer pageNum, Integer pageSize)`

### 7.5 `ReportService`

职责：

- 生成健康分析报告

建议方法：

- `generateReport(Long userId)`

---

## 8. Feature 子域内部建议

建议增加独立包：

```text
feature/
├─ model/
├─ assembler/
└─ calculator/
```

### 8.1 `model`

建议对象：

- `UserBaseFeature`
- `UserBehaviorFeature`
- `UserHealthFeature`
- `UserFeatureSnapshot`
- `NoteFeature`

### 8.2 `assembler`

职责：

- 把 DO / JSON / 远程数据组装成 Feature 对象

### 8.3 `calculator`

职责：

- 计算行为权重
- 计算 topic 偏好
- 计算健康主题特征
- 计算内容热度与质量分

---

## 9. Recommend 子域内部建议

建议增加包：

```text
recommend/
├─ recall/
├─ rank/
├─ diversify/
└─ reason/
```

### 9.1 `recall`

职责：候选集召回。

### 9.2 `rank`

职责：候选内容打分排序。

### 9.3 `diversify`

职责：结果打散。

### 9.4 `reason`

职责：生成推荐理由。

---

## 10. Report 子域内部建议

建议增加包：

```text
report/
├─ builder/
├─ analyzer/
└─ template/
```

### 10.1 `builder`

负责组装最终报告对象。

### 10.2 `analyzer`

负责周期分析、记录分析、主题分析。

### 10.3 `template`

负责沉淀报告固定结构与描述模板。

---

## 11. 核心对象草案

### 11.1 `RecommendQueryDTO`

建议字段：

- `Byte type`
- `Integer pageNum`
- `Integer pageSize`

### 11.2 `RecommendItemVO`

建议字段：

- `Long noteId`
- `Byte type`
- `String title`
- `String coverUrl`
- `Long authorId`
- `List<String> topics`
- `String reason`
- `Double score`

### 11.3 `UserAnalysisReportVO`

建议字段：

- `Integer currentModeType`
- `Integer averageCycleLength`
- `Integer averageDurationDays`
- `String summary`
- `List<String> healthFocuses`
- `List<String> riskTags`
- `List<String> recommendTopics`
- `List<RecommendItemVO> readingSuggestions`

---

## 12. 推荐接口响应建议

统一响应结构仍沿用现有 `Result`。

### 推荐 data 示例

```json
{
  "items": [
    {
      "noteId": 101,
      "type": 0,
      "title": "经期腹痛缓解指南",
      "coverUrl": "...",
      "authorId": 1001,
      "topics": ["经期护理", "痛经缓解"],
      "reason": "根据你近期关注的经期护理推荐",
      "score": 92.4
    }
  ],
  "nextCursor": null
}
```

---

## 13. 特征刷新方式建议

第一期建议同时保留两种方式：

### 13.1 被动刷新

- 查询时发现无快照，实时构建

### 13.2 主动刷新

- 提供 `/feature/refresh`
- 后续可接 MQ 或定时任务

---

## 14. 第一版实施建议

优先级建议：

1. DO / Mapper / SQL 先落表
2. TopicService 先实现
3. UserFeatureService 实现快照生成
4. RecommendationService 跑通
5. ReportService 跑通
6. 再补 FeatureController 方便调试

---

## 15. 命名风格建议

保持与你现有项目一致：

- 接口放 `service`
- 实现放 `service.impl`
- 持久化对象放 `domain.dataobject`
- MyBatis 接口放 `domain.mapper`
- controller 直接放 `controller`

这样能保证整个仓库风格统一。
