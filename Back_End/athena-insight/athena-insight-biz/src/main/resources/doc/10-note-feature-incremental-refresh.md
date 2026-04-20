# 发布后增量刷新 NoteFeature 记录

> 模块：`athena-ground-biz` + `athena-insight-biz`
> 状态：已接通发布后增量刷新链路
> 更新时间：2026-04-14

## 一、目标

在内容发布成功并完成 topic 构建之后，自动触发 `insight` 刷新该内容的 `NoteFeature`，避免 `tb_note_feature` 长时间依赖手动刷新。

同时，在审核状态变化时，再次刷新 `NoteFeature`，让推荐底座里的内容状态尽快与审核状态保持一致。

---

## 二、当前链路

```text
用户发布内容
  -> ground.submitNote()
  -> 异步构建 topic
  -> NoteTopicBuildServiceImpl 重建 tb_note_topic_relation
  -> ground 调用 insight 内部接口
  -> insight.NoteFeatureService.refreshByNoteId(noteId)
  -> 刷新 tb_note_feature

后台审核通过 / 拒绝
  -> NoteReviewServiceImpl
  -> 更新 tb_note_basic.status
  -> ground 调用 insight 内部接口
  -> insight.NoteFeatureService.refreshByNoteId(noteId)
  -> 刷新 tb_note_feature.status
```

---

## 三、本次新增内容

### 1. `insight` 内部接口

新增：

- `POST /athena/insight/internal/note-feature/refresh`

用途：

- 仅供服务间调用
- 根据 `noteId` 增量刷新单条内容特征

### 2. `ground` 侧 Feign / RPC

新增：

- `InsightFeignApi`
- `InsightRpc`

用途：

- 在 `ground` 侧调用 `insight` 内部刷新接口

### 3. topic 重建成功后触发增量刷新

在：

- `NoteTopicBuildServiceImpl.rebuildTopicsForNote(...)`

完成 topic 关系重建之后，调用：

- `insightRpc.refreshNoteFeature(noteId)`

### 4. 审核流状态变化后触发增量刷新

在：

- `NoteReviewServiceImpl.approve(...)`
- `NoteReviewServiceImpl.reject(...)`

更新审核状态后，调用：

- `insightRpc.refreshNoteFeature(noteId)`

这样可以让 `tb_note_feature.status` 更快同步到最新审核状态。

---

## 四、当前效果

现在一篇内容从发布到进入推荐底座的链路已经缩短为：

1. 发布成功
2. topic 构建成功
3. 自动刷新 `tb_note_feature`
4. 审核状态变化时再次刷新 `tb_note_feature`
5. 后续推荐可直接消费

这意味着：

- 新内容不再必须等手动刷新接口
- 推荐候选池能更快看到新内容
- `topicFeatureJson` 与 `tb_note_topic_relation` 更容易保持同步
- `tb_note_feature.status` 与审核状态更容易保持一致

---

## 五、当前设计取舍

### 1. 为什么挂在 topic 构建成功后

因为 `NoteFeature` 依赖：

- 内容基础信息
- topicFeatureJson
- 质量分 / 热度分

其中 `topicFeatureJson` 是关键输入。

如果在 topic 还没建好前就刷新 `NoteFeature`，会出现：

- 内容特征已生成
- 但 topic 为空

所以现在选择：

- **先构建 topic**
- **再增量刷新 `NoteFeature`**

### 2. 为什么审核流还要再刷一次

因为发布后第一时间生成的 `NoteFeature`，其 `status` 可能还是“待审核”。

而推荐系统只会消费有效状态内容。

所以审核通过/拒绝之后，再刷一次 `NoteFeature`，可以保证：

- 推荐侧的内容状态尽快更新
- 避免 `tb_note_feature` 长时间持有旧状态

### 3. 为什么先用同步 RPC

当前阶段更适合：

- 实现简单
- 调试直观
- 链路短

后续如果刷新频率很高，再考虑切 MQ 化。

---

## 六、当前仍未做

本轮仍未实现：

- `NoteFeature` 增量刷新失败补偿
- 批量回刷任务
- MQ 化的内容特征刷新链路
- 审核通过后再统一重建特征的更严格异步编排

这些可以在当前版本稳定后继续增强。

---

## 七、当前结论

现在推荐系统的内容底座已经从：

- 手动刷新为主

推进到：

- 发布成功 -> topic 构建 -> 自动增量刷新 `NoteFeature`
- 审核状态变化 -> 自动再次刷新 `NoteFeature`

这使得 `ground`、`topic`、`insight`、`recommend` 之间的主链进一步闭环。
