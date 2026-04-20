# 实施路线图

## 1. 目标

本文件用于把 `athena-insight` 从设计稿落地为可执行开发计划，避免一次性建设过多能力。

整体建议遵循：

- 先建 topic 和特征底座
- 再跑通推荐系统
- 再跑通分析报告
- 最后做缓存、调优、增强能力

---

## 2. 总体阶段划分

建议分为 4 个阶段：

1. 基础能力搭建
2. 推荐系统 V1
3. 分析报告 V1
4. 优化与增强

---

## 3. Phase 1：基础能力搭建

### 3.1 目标

把 `athena-insight` 的基本框架、数据库、RPC、topic、特征模型搭起来。

### 3.2 本阶段输出

- `athena-insight-api` 基础接口
- `athena-insight-biz` 基础目录结构
- 主题表/特征表建表 SQL
- Feign/RPC 接口定义
- 特征快照基础结构

### 3.3 任务拆分

#### 任务 A：父模块接入

- 将 `athena-insight` 加入根 `pom.xml`
- 确认依赖版本与现有服务一致

#### 任务 B：资源目录搭建

- `resources/config`
- `resources/mapper`
- `resources/sql`
- `resources/doc`

#### 任务 C：建表脚本

优先建：

- `tb_topic`
- `tb_note_topic_relation`
- `tb_record_topic_relation`
- `tb_user_feature_snapshot`
- `tb_note_feature`
- `tb_user_insight`

#### 任务 D：RPC 接口定义

- `UserAuthFeignApi`
- `GroundFeignApi`
- `RecordFeignApi`

#### 任务 E：DO/Mapper 落地

- Topic 相关 DO/Mapper
- Feature 相关 DO/Mapper
- Insight 相关 DO/Mapper

### 3.4 完成标准

- 服务能启动
- 数据库表可建
- RPC 接口定义完成
- Topic/Feature/Insight 的基础持久化能力准备好

---

## 4. Phase 2：推荐系统 V1

### 4.1 目标

跑通 `type=0/1/2` 的统一推荐接口。

### 4.2 本阶段输出

- 用户特征快照构建
- 内容特征快照构建
- 推荐接口可用
- 推荐理由基础输出

### 4.3 任务拆分

#### 任务 A：Topic 绑定能力

- 为内容建立 topic 绑定能力
- 为 recordItem 建立 topic 映射规则

#### 任务 B：内容特征构建

- 从 `ground` 拉内容基础信息
- 生成 `NoteFeature`
- 计算 `hotScore`
- 计算 `qualityScore`

#### 任务 C：用户特征构建

- 从 `userauth` 拉基础信息
- 从 `ground` 拉用户行为
- 从 `record` 拉周期和健康记录
- 生成 `UserFeatureSnapshot`

#### 任务 D：推荐引擎实现

- 候选召回
- 排序打分
- 打散
- 推荐理由生成

#### 任务 E：推荐接口实现

- `GET /athena/insight/recommend?type=0`
- `GET /athena/insight/recommend?type=1`
- `GET /athena/insight/recommend?type=2`

### 4.4 完成标准

- 三类 type 都能拿到推荐结果
- 推荐结果已基于 topic 与特征，而非纯随机/纯分页
- 推荐理由可以输出

---

## 5. Phase 3：分析报告 V1

### 5.1 目标

基于统一特征中心，输出一版可用的健康分析报告。

### 5.2 本阶段输出

- 周期概览
- 近期健康 topic 总结
- 内容偏好 topic 总结
- 建议关注方向
- 关联阅读建议

### 5.3 任务拆分

#### 任务 A：洞察生成

- 从 `UserFeatureSnapshot` 生成 `UserInsight`
- 产出健康关注点、内容关注点、风险标签

#### 任务 B：报告聚合

- 汇总周期统计
- 汇总 topic 主题
- 汇总建议方向

#### 任务 C：报告接口实现

- `GET /athena/insight/report`

### 5.4 完成标准

- 用户可获得一份结构化分析报告
- 报告内容来源统一、可解释
- 报告中的内容建议能复用推荐系统输出

---

## 6. Phase 4：优化与增强

### 6.1 目标

提升性能、可维护性与扩展性。

### 6.2 可能任务

- 推荐结果缓存
- 特征刷新任务
- MQ 驱动增量刷新
- topic 管理后台
- 推荐策略配置化
- 报告模板增强

---

## 7. 开发顺序建议

建议按下面顺序执行：

1. 表结构先定
2. DO / Mapper 先写
3. TopicService 先跑通
4. UserFeatureService 跑通
5. NoteFeatureService 跑通
6. RecommendationService 跑通
7. ReportService 跑通
8. 最后补缓存与优化

---

## 8. 第一批优先接口建议

### 必做

- `/athena/insight/recommend`
- `/athena/insight/report`
- `/athena/insight/feature`
- `/athena/insight/feature/refresh`

### 可选

- `/athena/insight/topic/list`
- `/athena/insight/topic/relation/...`

第一批先以内部调试和联调为主。

---

## 9. 风险点与建议

### 9.1 风险：topic 体系过大

建议：

- 第一版控制在 10~20 个 topic 内
- 先做核心健康主题

### 9.2 风险：推荐逻辑过重

建议：

- 第一版只做规则型推荐
- 不上复杂模型

### 9.3 风险：report 一次做太复杂

建议：

- 第一版只做结构化摘要
- 不做长篇 AI 文案

### 9.4 风险：实时聚合过慢

建议：

- 先允许实时聚合
- 后续再加快照和缓存优化

---

## 10. 第一版里程碑建议

### 里程碑 M1

- `athena-insight` 可启动
- 建表完成
- topic/feature/insight 基础持久化完成

### 里程碑 M2

- 推荐接口跑通
- 三类 type 均可返回结果

### 里程碑 M3

- 分析报告跑通
- 推荐与报告共用统一特征底座

### 里程碑 M4

- 特征刷新、缓存、优化完成

---

## 11. 最终建议

第一版最重要的不是“做得多全”，而是把这三件事真正跑通：

1. topic 成为统一语义中心
2. UserFeatureSnapshot 成为统一特征底座
3. 推荐和报告都只消费特征，不直接拼原始表

只要这三件事成立，后续无论接入 RAG、做运营后台、做大模型报告，都会非常顺。
