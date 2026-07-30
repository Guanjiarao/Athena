# 微服务拆分规则与服务边界

## 1. 目标

本文件用于明确 `athena-insight` 与其他微服务之间的职责边界，避免后续开发中出现：

- 原始数据写入职责混乱
- 推荐逻辑散落在多个服务中
- 报告逻辑直接耦合底层表
- topic 语义体系重复建设

---

## 2. 服务职责总览

### 2.1 `athena-userauth`

职责：

- 用户注册/登录
- 用户身份信息
- 用户基础资料维护
- 用户等级、积分、基础账户信息

真源数据示例：

- 用户账号信息
- 用户生日、性别、城市
- 用户等级、积分

### 2.2 `athena-ground`

职责：

- 内容发布
- 内容审核
- 内容详情查询
- 广场/科普内容基础信息
- 点赞、收藏、浏览行为真源

真源数据示例：

- `tb_note_basic`
- `tb_note`
- `tb_note_content`
- `tb_note_count`
- `tb_note_like`
- `tb_note_collection`
- `user_view_record`

### 2.3 `athena-record`

职责：

- 每日健康记录
- 周期记录与预测
- 模式切换（经期 / 备孕 / 怀孕）
- 身体记录真源维护

真源数据示例：

- `daily_record`
- `menstruation_cycle`

### 2.4 `athena-insight`

职责：

- 聚合 userauth / ground / record 数据
- 建立 topic 标签语义体系
- 生成用户特征快照
- 生成内容特征快照
- 统一输出推荐结果
- 统一输出分析报告
- 输出推荐理由与洞察结论

---

## 3. `athena-insight` 负责什么

`athena-insight` 负责“计算型能力”，不负责原始业务维护。

### 3.1 负责

- topic 定义与映射规则
- 用户兴趣特征计算
- 用户健康特征计算
- 内容 topic 特征计算
- 推荐召回/排序/打散
- 报告摘要生成
- 推荐原因生成
- 特征快照存储

### 3.2 不负责

- 用户注册登录
- 笔记发布与审核
- 点赞收藏浏览写入
- 身体记录保存/修改/删除
- 经期开始/结束写入

---

## 4. 真源与快照的关系

### 4.1 真源原则

原始数据在原服务中维护，`insight` 不重复造真源。

### 4.2 快照原则

`insight` 只维护：

- 特征快照
- topic 关系
- 推荐结果缓存（可选）
- 洞察结果

### 4.3 允许冗余，不允许反向写真源

`insight` 可以冗余：

- 用户特征 JSON
- 内容特征 JSON
- 健康洞察结果

但不应反向写入：

- `tb_note_basic`
- `daily_record`
- `tb_user_info`

---

## 5. 调用关系建议

### 5.1 上游输入服务

`athena-insight` 依赖：

- `athena-userauth`
- `athena-ground`
- `athena-record`

### 5.2 对外输出对象

`athena-insight` 对外输出：

- 推荐接口
- 报告接口
- 特征查询接口（内部/调试）
- 洞察查询接口（内部/后台）

### 5.3 建议调用方式

优先通过 Feign 或统一 RPC 接口拉取聚合数据，避免跨服务直接访问彼此数据库。

---

## 6. 为什么不用 `channel` 做兴趣标签

### 6.1 `channel` 的定位

`channel` 更适合：

- 页面导航
- 栏目分类
- 运营分组
- 内容挂载入口

### 6.2 `topic` 的定位

`topic` 更适合：

- 用户兴趣表达
- 内容语义表达
- 健康状态映射
- 推荐匹配
- 报告主题总结

### 6.3 结论

- `channel` 继续保留现有用法
- `topic` 作为兴趣标签和推荐语义中心

---

## 7. 数据流原则

### 7.1 用户侧数据流

```text
userauth -> base feature
```

### 7.2 内容侧数据流

```text
ground -> behavior feature / note feature
```

### 7.3 健康侧数据流

```text
record -> health feature
```

### 7.4 汇总侧数据流

```text
base feature + behavior feature + health feature -> user feature snapshot -> insight/recommend/report
```

---

## 8. 微服务边界的关键约束

### 8.1 `ground` 不内建复杂推荐

`ground` 只负责内容域能力，不承接洞察引擎主逻辑。

### 8.2 `record` 不内建报告渲染

`record` 只提供健康真源和统计能力，不直接承担最终分析报告生成。

### 8.3 `insight` 不维护原始业务表

`insight` 的定位是消费型中台，而不是业务真源服务。

---

## 9. 典型业务场景归属

### 场景 A：用户浏览/点赞/收藏内容

归属：`athena-ground`

### 场景 B：用户新增每日健康记录

归属：`athena-record`

### 场景 C：用户获取科普推荐/广场推荐

归属：`athena-insight`

### 场景 D：用户查看健康分析报告

归属：`athena-insight`

### 场景 E：运营维护 topic 规则

归属：`athena-insight`

---

## 10. 后续演进建议

随着系统演进，`athena-insight` 可以逐步扩展：

- topic 管理后台
- 推荐策略配置
- 特征刷新任务
- 报告模板管理
- 推荐理由解释增强

但仍应坚持：

**原始真源不迁移到 insight，insight 只做消费、计算、输出。**
