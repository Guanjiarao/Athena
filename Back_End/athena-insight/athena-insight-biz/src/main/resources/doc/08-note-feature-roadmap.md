# Insight 下一阶段落地记录

> 模块：`athena-insight-biz`
> 状态：已补齐 `NoteFeature` 第一版真实刷新能力
> 更新时间：2026-04-14

## 一、本次补齐目标

在已有 topic、用户画像、洞察、报告、推荐基础上，补齐 `insight` 当前最关键的内容特征底座：

- 将 `tb_note_feature` 从演示数据表，推进为可刷新的内容特征快照表
- 为 `report` 补上 `readingSuggestions`
- 让推荐优先消费 `tb_note_feature.topicFeatureJson`

---

## 二、本次新增能力

### 1. `NoteFeatureService`

新增：

- `getByNoteId(Long noteId)`
- `refreshByNoteId(Long noteId)`
- `refreshPublicPool(Integer pageNum, Integer pageSize)`

当前实现方式：

1. 先从 `ground` 公共列表中找到目标内容基础信息
2. 再调用 `ground` 内容详情接口补充详情字段
3. 从 `tb_note_topic_relation` 读取 topic，构建 `topicFeatureJson`
4. 计算 `hotScore`
5. 计算 `qualityScore`
6. 写入 `tb_note_feature`

---

### 2. 新增调试入口

在 `InsightFeatureController` 中新增：

- `POST /athena/insight/note-feature/refresh`

支持两种用法：

#### 刷新单条内容

```json
{
  "noteId": 123
}
```

#### 刷新公共内容池

```json
{
  "pageNum": 1,
  "pageSize": 50
}
```

---

### 3. `GroundRpc` 能力补充

新增对 `ground` 的公共内容拉取能力：

- 公共列表 `/athena/blog/list`
- 按类型列表 `/athena/blog/listByTypeId`

用于支持后续：

- 内容特征批量刷新
- 推荐候选池准备

---

### 4. `ReportService` 已接推荐阅读建议

当前 `ReportServiceImpl` 不再返回空的 `readingSuggestions`。

改为：

- 默认调用一次 `type=0` 的推荐结果
- 取前 3 条作为报告中的阅读建议

这意味着 report 已从“静态描述报告”升级为“可行动报告”。

---

### 5. 推荐优先消费 `topicFeatureJson`

当前 `RecommendationServiceImpl` 已调整为：

- 优先解析 `tb_note_feature.topicFeatureJson`
- 若为空，再回退查询 `topicService.listTopicsByNoteId(noteId)`

这样推荐与内容特征底座保持一致，避免每次推荐都重新回 topic 关系表。

---

## 三、当前评分逻辑（第一版）

### 1. `hotScore`

当前基于：

- `likeTotal`
- `collectTotal`
- `commentTotal`

做简单加权，再通过 `log1p` 压缩到 `0~10`。

### 2. `qualityScore`

当前基于以下规则加分：

- 标题长度达标
- 有封面
- 正文长度达标
- 有频道
- 状态有效
- 有 topic

最终封顶 `10` 分。

说明：

- 当前是规则版 V1
- 目的不是做复杂质量评估，而是先把内容特征底座跑通

---

## 四、当前仍然未做

本轮仍未实现：

- 基于 MQ 的内容特征增量刷新
- `freshnessScore`
- 多路召回（topic/健康/热门/新内容）
- 推荐打散
- `type=1/2` 报告建议内容切换
- 内容删除/下架后的 `tb_note_feature` 清理
- 批量全库刷新任务

这些可以在当前底座稳定后继续迭代。

---

## 五、当前结论

现在 `insight` 的主链已经从：

- 仅有用户画像和推荐排序

推进到：

- 有真实内容特征底座
- 有 `NoteFeature` 刷新能力
- report 已可输出阅读建议
- 推荐开始真正消费内容特征快照

这意味着下一步可以自然进入：

1. 推荐召回升级
2. 内容特征增量刷新
3. freshness / 打散 / 新内容曝光优化
