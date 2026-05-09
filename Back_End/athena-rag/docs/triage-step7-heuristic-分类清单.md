# Triage Step 7 Heuristic 分类清单

> 这份文档是 Step 7 当前代码状态下的“真实 heuristic 分类盘点”。
> 目标不是讨论理想架构，而是明确：哪些规则仍保留、属于什么性质、现在在哪里、后续该怎么处理。
> 本文档描述的是当前阶段真实结构，不代表这些边界已经是终局设计。

## 1. 分类口径

当前统一按以下四类看待 heuristic / fallback 组件：

- `KEEP_GUARDRAIL`
  - 必须保留的安全护栏
  - 目的：防止高危 case 漏报或主链失效时完全失守
- `LEGACY_FALLBACK`
  - 暂时保留的过渡性 fallback
  - 目的：在主链未完全覆盖前提供有限保底
- `COMPATIBILITY_ONLY`
  - 仅用于兼容旧链路、旧评测种子、旧输出形态
  - 不应继续扩展为主路径能力
- `TEXT SEAM`
  - 结构化状态尚未完全覆盖时保留的文本补缝输入
  - 目标是逐步降权，而不是继续扩写

---

## 2. 当前真实分类

### 2.1 `KEEP_GUARDRAIL`

#### `RiskHeuristicHelper.hardRedFlagFallback(...)`

标签：`KEEP_GUARDRAIL`

当前保留的硬护栏：

- 大出血
- 呼吸困难
- 抽搐 / 惊厥
- 意识障碍
- 胸痛 + 呼吸困难
- 妊娠相关出血

当前特征：

- 可直接中断
- 优先级高于普通 fallback
- 是 risk 主链之外仍必须保留的安全底线
- 已开始转向 `riskSignalState` / `slotState` first，文本只做补缝

后续策略：

- 保留
- 允许做“防漏报式”极保守增强
- 不允许顺手扩成普通灰区扫词器

---

### 2.2 `LEGACY_FALLBACK`

#### `ComplaintFallbackResolver`

标签：`LEGACY_FALLBACK`

当前保留内容：

- 明确腹痛表达 -> `腹痛`
- 明确胸痛表达 -> `胸痛`
- 明确发热表达 -> `发热`
- 弱症状词 + 腹部 body cue -> `腹痛`

当前定位：

- 只在主诉理解主链缺位时兜底
- 只保留高置信、一跳映射

后续策略：

- 继续收缩
- 不新增 case-specific 强归类规则
- 能被 `TurnUnderstandingWorker` 稳定覆盖的规则，后续应退出

#### `LegacyRiskFallback`

标签：`LEGACY_FALLBACK`

当前保留内容：

- 右下腹痛 + `VOMITING_PRESENCE` 未知 -> 提示关键补问
- chest pain only -> 高优先级线下评估 fallback
- 对 `CHEST_PAIN` risk signal 已可直接触发胸痛 fallback
- 其余未命中 -> 保持低级别、不抬高主判断

当前定位：

- 不再负责普通灰区升级
- 只保留少量明确、保守、风险侧可解释 fallback
- 已开始转向 canonical-state-first / risk-signal-first，但仍未完全脱离文本 snapshot

后续策略：

- 逐步让结构化 risk 主链接管
- `RLQ unresolved` 和 `chest pain only` 是否继续保留，取决于主链覆盖稳定性

---

### 2.3 `COMPATIBILITY_ONLY`

#### `FactHeuristicExtractor`

标签：`COMPATIBILITY_ONLY`

当前保留内容：

- 仅在 `lastAskedSlots` / `pendingSlots` 范围内补最小 compatibility fact
- `TurnUnderstanding` 已覆盖的 slot，不再重复补 fact
- primary complaint fallback 仍保留一条 compatibility bridge

当前定位：

- 不是主路径语义解释器
- 不是主状态推进器
- 是兼容层事实补丁 adapter

当前相关结构：

- `FactHeuristicExtractor`
- `CompatibilityFactScope`
- `CompatibilityFactPatternMatcher`

后续策略：

- 不再新增普通事实抽取规则
- 可继续缩小覆盖范围
- 待主链完全稳定后，进一步下沉或删除残余分支

#### `SemanticSignalResolver`

标签：`COMPATIBILITY_ONLY`

当前保留内容：

- 仅保留 `factHistory` 中 `PRIMARY_SYMPTOM` 级别 bridge

当前定位：

- planner 的极窄 compatibility fact bridge
- 不是 planner 主要语义发现来源

后续策略：

- 继续退出 planner 主路径
- 若 primary-signal fact bridge 也被替代，可继续删除

#### `SlotManager`

标签：`COMPATIBILITY_ONLY`

当前保留内容：

- 当 reducer 结果缺失时，才从 facts merge state
- compatibility answered 重建必须满足：
  - 本轮 fact
  - `lastAskedSlots` 命中
  - `pendingSlots` 也命中

当前定位：

- 更接近 projection orchestrator + fallback seam
- 不再是 answered/pending 的主判断器

当前相关结构：

- `SlotManager`
- `CompatibilitySlotFallback`

后续策略：

- 正常主链继续向 pure projection 靠拢
- compatibility answered 重建仍可继续收窄

---

### 2.4 `TEXT SEAM`

#### `RiskTextSnapshotBuilder`

标签：`TEXT SEAM`

当前保留内容：

- 把 `userInput`、`slotState`、presence slots、`extractedSymptoms` 组装成 risk 文本 snapshot
- 供 `hardRedFlagFallback(...)` 与 `LegacyRiskFallback` 做文本补缝判断

当前定位：

- 不是 risk 判断主体
- 是 risk 侧尚未完全结构化前的补缝输入 seam

后续策略：

- 继续降权
- 继续让 hard guardrail / legacy fallback 优先读 canonical state 与 risk signal
- 不应继续扩写成新的核心决策入口

---

## 3. 已明显退出主链的旧能力

以下能力虽然代码还在，但相较前几轮已经明显退出主链：

### `FactHeuristicExtractor`

已退出：

- 基于 heuristic fact 与 `TurnUnderstanding` 抢主状态解释权
- 无条件为未覆盖 slot 广泛补 fact

### `SemanticSignalResolver`

已退出：

- 普通 fact bridge 直接驱动 planner 语义发现
- `FEVER_PRESENCE=YES -> 发热` 这类泛化桥接
- `BODY_PART=胸前区 -> 胸痛` 这类推断桥接
- `riskSignalState` 关键判断桥接

### `RiskHeuristicHelper`

已退出：

- `moderate symptom load`
- `moderate slot load`
- 普通灰区 heuristic 升级
- 把重文本拼装逻辑继续放在 helper 主体内部

### `SlotManager`

已退出：

- 仅凭 lastAsked 风格事实就较宽泛地重建 answered slots
- 把 projection 与 fallback 责任继续揉在一个执行体里

---

## 4. 当前最需要继续盯的残余风险点

### 4.1 `RiskTextSnapshotBuilder` 仍在

虽然关键 fallback 已开始转向 canonical state first，但它仍是：

- hard red flag 的文本补缝来源之一
- legacy fallback 的补充文本来源

结论：

- 已被隔离成 seam
- 已降权
- 但尚未完全退出关键分支

### 4.2 primary complaint compatibility fact 仍保留

`FactHeuristicExtractor` 里 primary complaint fallback 还在。

结论：

- 当前仍有必要，但属于继续可收缩对象

### 4.3 `ComplaintFallbackResolver.resolveWeakSymptomWithBodyCue(...)`

弱症状 + body cue -> `腹痛` 仍是一个经验性 fallback。

结论：

- 已经比之前收窄很多
- 但仍属于 legacy，不应继续扩写

---

## 5. 当前 review 审查约束

### 对 `KEEP_GUARDRAIL`

允许：

- 防漏报增强
- 明确高危同义表达补充

不允许：

- 扩写成普通灰区启发式分类器

### 对 `LEGACY_FALLBACK`

允许：

- 收缩
- 删除
- 改为 canonical state first / risk-signal first

默认不允许：

- 为单个 case 新增映射短语
- 扩大覆盖面

### 对 `COMPATIBILITY_ONLY`

允许：

- 继续收窄适用范围
- 增加退出条件
- 从主路径进一步降权

默认不允许：

- 重新承担主链职责
- 新增主判断语义

### 对 `TEXT SEAM`

允许：

- 把文本补缝从业务判断主体里剥离
- 缩小文本补缝覆盖面
- 用 canonical state / risk signal 替代文本判断

默认不允许：

- 继续把新的核心判断压进 text snapshot 逻辑

---

## 6. 下一步衔接

这份分类清单之后，后续更合理的方向是：

1. 继续压缩 `RiskTextSnapshotBuilder` 的判断权重
2. 继续让 `LegacyRiskFallback` 与 hard guardrail 向 state-first 推进
3. 继续减少 primary complaint compatibility fact 与 complaint fallback 残余

目标不是把所有 heuristic 立刻删空，而是让团队能够明确：

- 什么必须保留
- 什么只是过渡
- 什么只是 seam
- 什么已经可以继续删
