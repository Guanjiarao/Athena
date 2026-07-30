# Topic 实现方案

> 模块：`athena-ground-biz`
> 目标：新内容发布成功后，通过 `ground` 内部异步事件自动构建并重建 `tb_note_topic_relation`，避免 `insight` 侧因 topic 关系缺失导致个性化特征为空。

## 一、方案结论

本次采用：

- **发布成功后发事件**
- **在 `ground` 内部异步消费**
- **由 `ground` 负责 topic 提取与 `tb_note_topic_relation` 重建**
- **`insight` 只消费结果，不负责补建内容 topic**

不采用跨服务调用 `insight` 来构建 topic，原因：

- 内容主数据在 `ground`
- 发布主链在 `ground`
- 跨服务查询内容详情更容易出错
- 失败链路更长，排查更复杂

---

## 二、目标能力

本方案要保证以下结果：

1. 新内容发布成功后，一定会进入 topic 构建流程
2. 构建成功后，`tb_note_topic_relation` 中存在该 `noteId` 的 topic 关系
3. 构建失败时可以依赖 MQ 重试
4. 后续可以增加定时补偿任务，扫描没有 topic 关系的新内容并补建
5. 后续可以平滑升级 topic 提取算法，而不改表结构和主链路

---

## 三、整体链路

```text
前端提交内容
  │
  └─ POST /athena/blog/submit
            │
            ▼
GroundController.submitNote()
            │
            ▼
GroundServiceImpl.submitNote()
            │
            ├─ 调用 NotePublishServiceImpl.publish() 完成落库
            ├─ 拿到 noteId
            └─ 发送 NoteTopicBuildEvent
                        │
                        ▼
               NoteTopicBuildConsumer
                        │
                        ├─ 幂等校验
                        ├─ 调用 NoteTopicBuildService
                        ├─ 提取 topic
                        ├─ 删除旧关系
                        └─ 重建 tb_note_topic_relation
```

---

## 四、代码改造范围

### 4.1 新增事件对象

路径建议：

- `athena-ground-biz/src/main/java/athena/ground/biz/mq/event/NoteTopicBuildEvent.java`

字段建议：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteTopicBuildEvent implements Serializable {

    private String eventId;

    private Long noteId;

    private Long authorId;

    private String title;

    private String content;

    private Integer type;

    private Integer channelId;
}
```

字段说明：

- `eventId`：用于 MQ 消费幂等
- `noteId`：内容主键，重建关系的核心标识
- `authorId`：可用于日志与后续策略扩展
- `title` / `content`：topic 提取直接输入，减少一次回表依赖
- `type` / `channelId`：作为 topic 规则加权的辅助特征

---

### 4.2 新增 Producer

路径建议：

- `athena-ground-biz/src/main/java/athena/ground/biz/mq/producer/NoteTopicBuildProducer.java`

建议风格直接对齐现有：

- `AthenaNoteSyncProducer`
- `NoteInteractionProducer`
- `ViewRecordProducer`

建议常量：

- topic：`note-topic-build`
- biz desc：`笔记Topic构建`

接口形状建议：

```java
public void send(NoteTopicBuildEvent event)
```

行为：

- 使用 `noteId` 作为 MQ keys
- 成功打印 info 日志
- 失败打印 error 日志并抛异常

---

### 4.3 新增 Consumer

路径建议：

- `athena-ground-biz/src/main/java/athena/ground/biz/mq/consumer/NoteTopicBuildConsumer.java`

消费逻辑建议对齐现有 `NoteInteractionConsumer`：

1. 解析消息体
2. 提取 `NoteTopicBuildEvent`
3. 基于 `eventId` 做 Redis 幂等
4. 调用 `NoteTopicBuildService.rebuildTopicsForNote(event)`
5. 成功后打印日志
6. 失败删除幂等 key，抛异常让 MQ 重试

幂等 key 建议：

- `ground:note:topic:build:{eventId}`

TTL 建议：

- 7 天

---

### 4.4 新增 Service

路径建议：

- `athena-ground-biz/src/main/java/athena/ground/biz/service/NoteTopicBuildService.java`
- `athena-ground-biz/src/main/java/athena/ground/biz/service/impl/NoteTopicBuildServiceImpl.java`

接口建议：

```java
public interface NoteTopicBuildService {
    void rebuildTopicsForNote(NoteTopicBuildEvent event);
}
```

核心职责：

1. 根据事件拿到内容数据
2. 调用 `NoteTopicExtractor` 提取 topic
3. 删除该 `noteId` 现有 topic 关系
4. 插入新的 topic 关系
5. 打印构建摘要日志

---

### 4.5 新增 Topic 提取器

路径建议：

- `athena-ground-biz/src/main/java/athena/ground/biz/service/NoteTopicExtractor.java`
- `athena-ground-biz/src/main/java/athena/ground/biz/service/impl/RuleBasedNoteTopicExtractor.java`

建议接口：

```java
public interface NoteTopicExtractor {
    List<TopicMatchResult> extract(NoteTopicBuildEvent event);
}
```

返回结构建议：

```java
public record TopicMatchResult(Long topicId, String topicName, BigDecimal weight) {}
```

第一版采用 **规则提取**，不要一开始就接模型。

---

## 五、topic 提取规则建议

第一版先做规则匹配，输入来源：

- `title`
- `content`
- `type`
- `channelId`

### 5.1 关键词规则示例

| 关键词 | topic |
|------|------|
| 经期、姨妈、生理期 | 经期护理 |
| 痛经、腹痛、热敷、缓解疼痛 | 痛经缓解 |
| 失眠、睡不好、睡不着、熬夜 | 睡眠调节 |
| 焦虑、烦躁、情绪低落、压力 | 情绪调节 |
| 饮食、忌口、补铁、吃什么 | 饮食管理 |

### 5.2 加权建议

可先采用简单加权：

- 标题命中：`1.0`
- 正文命中：`0.6`
- `channelId` 命中：`0.4`
- 多次命中可累加

最终对同一 topic 合并权重。

### 5.3 第一版规则原则

- 命中即入库
- 同一 `noteId + topicId` 只保留一条关系
- 权重保留到 4 位小数
- 允许多 topic 共存
- 第一版不做复杂分词，不做模型打分

---

## 六、数据库写入策略

目标表：

- `tb_note_topic_relation`

当前表结构已满足第一版需要：

- `note_id`
- `topic_id`
- `weight`
- `source_type`
- 唯一键：`uk_note_topic (note_id, topic_id)`

### 建议写入方式

使用“**先删后建**”的重建策略：

1. `delete from tb_note_topic_relation where note_id = ?`
2. 批量插入新关系

优点：

- 实现简单
- 幂等明确
- 与“重建”语义一致
- 易于后续重新跑 topic 逻辑

### `source_type` 建议

当前建议：

- `1`：人工
- `2`：规则
- `3`：系统

如果不新增枚举值，第一版可先把异步构建归为：

- `2` 规则

如果愿意更清晰，也可以新增：

- `4` 发布异步构建

---

## 七、事件发布点建议

### 推荐位置

放在：

- `GroundServiceImpl.submitNote(...)`

具体时机：

- `notePublishService.publish(noteSubmitDTO)` 成功之后
- HTTP 返回之前发 MQ 事件

伪代码：

```java
Long noteId = notePublishService.publish(noteSubmitDTO);

NoteTopicBuildEvent event = NoteTopicBuildEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .noteId(noteId)
        .authorId(noteSubmitDTO.getUserId())
        .title(noteSubmitDTO.getTitle())
        .content(noteSubmitDTO.getContent())
        .type(noteSubmitDTO.getType())
        .channelId(noteSubmitDTO.getChannelId())
        .build();

noteTopicBuildProducer.send(event);
```

### 为什么不建议放在 `NotePublishServiceImpl.publish()` 内部

因为 `publish()` 当前职责非常纯：

- 只做落库
- 有事务边界

如果把发送 MQ 也揉进去，会让方法变成：

- 事务落库
- 业务副作用触发

这样会让职责变混，不利于后续维护。

---

## 八、Consumer 处理细节

### 8.1 幂等

参考现有 `NoteInteractionConsumer` 设计：

- 用 Redis 记录 `eventId`
- 首次消费成功占位
- 处理失败删除占位，允许 MQ 重试

### 8.2 失败处理

如果出现以下情况，应抛异常让 MQ 重试：

- topic 提取器异常
- 数据库删除/插入异常
- topic 映射非法

### 8.3 非重试类场景

以下情况可以按“正常完成但结果为空”处理，不一定重试：

- 内容没有命中任何 topic 规则
- 标题/正文为空

建议日志记录为 warn，而不是抛异常。

---

## 九、建议增加的日志

### 9.1 Producer 日志

- 发送开始：`noteId / eventId / type / channelId`
- 发送成功：`noteId / eventId`
- 发送失败：`noteId / eventId / exception`

### 9.2 Consumer 日志

- 收到消息：`noteId / eventId`
- 幂等命中：`eventId`
- 提取完成：`noteId / topicCount / topics`
- 重建完成：`noteId / insertedCount`
- 处理失败：`noteId / eventId / exception`

### 9.3 Service 日志

- 删除旧关系数量
- 新增关系数量
- 没命中 topic 的 noteId

---

## 十、建议补充的 Mapper / DO

如果 `ground` 目前还没有 topic 相关表映射，需要在 `ground` 内新增：

### 10.1 DO

- `TopicDO`
- `NoteTopicRelationDO`

### 10.2 Mapper

- `TopicMapper`
- `NoteTopicRelationMapper`

### 10.3 XML

- `TopicMapper.xml`
- `NoteTopicRelationMapper.xml`

### 10.4 最少需要支持的方法

#### `TopicMapper`
- 按 `topic_code` / `topic_name` / `status` 查询
- 批量按 `id` 查询

#### `NoteTopicRelationMapper`
- 按 `noteId` 删除
- 批量插入
- 按 `noteId` 查询现有关联

---

## 十一、建议的数据归属

### `ground` 负责

- 内容发布
- 内容详情真源
- topic 提取
- `tb_note_topic_relation` 写入

### `insight` 负责

- 读取 `tb_note_topic_relation`
- 基于 topic 做用户 feature / insight / recommend

这样职责清晰，后续不会互相补洞。

---

## 十二、定时补偿方案（建议第二阶段补上）

即使有异步事件，仍建议加一个兜底任务。

### 扫描目标

最近 N 天发布的内容中：

- 已存在 `tb_note_basic` / `tb_note`
- 但 `tb_note_topic_relation` 为空

### 补偿行为

重新调用：

- `rebuildTopicsForNote(noteId)`

### 价值

- 防止 MQ 丢消息导致 topic 永久缺失
- 支持历史数据补建
- 支持规则升级后的回刷

---

## 十三、实施顺序建议

### Phase 1：先跑通

1. 新增 `NoteTopicBuildEvent`
2. 新增 `NoteTopicBuildProducer`
3. 新增 `NoteTopicBuildConsumer`
4. 新增 `NoteTopicBuildService`
5. 新增 `NoteTopicExtractor` 与规则版实现
6. `submitNote()` 成功后发事件
7. 成功写入 `tb_note_topic_relation`

### Phase 2：增强稳态

1. 增加消费幂等
2. 增加 topic 构建日志
3. 增加补偿任务

### Phase 3：效果优化

1. 扩展规则词表
2. 增加按频道/类型加权
3. 后续可替换为模型分类，但保留事件链路不变

---

## 十四、第一版明确不做

为了控制范围，第一版不做以下内容：

- 不跨服务调用 `insight` 构建 topic
- 不接入大模型自动打 topic
- 不做内容编辑后的自动重建
- 不做内容删除后的 topic 清理
- 不做 topic 人工运营后台
- 不做复杂召回/embedding 语义匹配

这些都可以在 topic 构建链跑通之后再补。

---

## 十五、最终建议

本次就按下面这条主线落地：

- `submitNote()` 成功
- 发送 `NoteTopicBuildEvent`
- `ground` 内 consumer 异步消费
- 规则提取 topic
- 重建 `tb_note_topic_relation`
- 后续加补偿任务

这是当前最贴合 `ground` 现有架构、改动最小、最不容易出错的实现路径。
