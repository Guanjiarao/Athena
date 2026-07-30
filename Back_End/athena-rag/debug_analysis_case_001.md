# 用例 001 调试分析报告

## 测试执行情况

✅ 测试成功运行，Redis 连接正常

## 关键发现

### 1. PRIMARY_SYMPTOM 槽位的值

从日志中可以看到，第一轮对话中提取的槽位：

```
answeredSlots: [SYMPTOM, DIARRHEA_PRESENCE, BODY_PART]
```

具体的 SYMPTOM 槽位值：
- **槽位 1**: `SYMPTOM` = "腹泻" (rawValue: "拉肚子", normalizedValue: "腹泻", confidence: 0.95)
- **槽位 2**: `SYMPTOM` = "腹痛" (rawValue: "肚子隐隐作痛", normalizedValue: "腹痛", confidence: 0.9)
- **槽位 3**: `BODY_PART` = "腹部" (confidence: 0.95)

**问题**：系统提取了 **SYMPTOM 槽位**，但 ROUTINE_RULES 中的规则检查的是 **PRIMARY_SYMPTOM** 槽位！

### 2. 语义信号匹配过程

从日志中可以看到，系统在检查常规规则时：

```
[QuestionPlanSupport] 检查常规规则, 规则数量: 10
[QuestionPlanSupport] 语义信号 '' 通过 semanticSignalResolver 匹配到: 腹泻
[QuestionPlanSupport] 语义信号 '腹泻' 匹配结果: false
[QuestionPlanSupport] 语义信号 '' 通过 semanticSignalResolver 匹配到: 腹泻
[QuestionPlanSupport] 语义信号 '腹泻' 匹配结果: false
[QuestionPlanSupport] 语义信号 '' 通过 semanticSignalResolver 匹配到: 腹痛
[QuestionPlanSupport] 语义信号 '腹痛' 匹配结果: false
[QuestionPlanSupport] 语义信号 '腹泻' 匹配槽位值或槽位值
```

**关键问题**：
- 语义信号 resolver 能够正确解析出 "腹泻" 和 "腹痛"
- 但是匹配结果都是 `false`
- 最后一条日志显示 "语义信号 '腹泻' 匹配槽位值或槽位值" 返回 `true`，但这是在检查 DURATION 槽位时的匹配

### 3. determineQuestionGaps() 生成的 gaps

第一轮对话后生成的 gaps：

```
[QuestionPlanSupport] 最终 gaps 数量: 6, gaps: [
  DIARRHEA_FREQUENCY(83), 
  STOOL_CHARACTER(81), 
  DURATION(80), 
  FEVER_PRESENCE(79), 
  FOOD_HISTORY(75), 
  NAUSEA_PRESENCE(73)
]
```

**问题**：
- 这些都是常规槽位，没有被常规规则触发
- 说明 "腹泻" 的常规规则没有生效

### 4. selectGapsByPolicy() 选择的 gaps

```
[QuestionPlanner] 开始生成询问计划, gaps 数量: 6
[QuestionPlanner] answeredSlots: [SYMPTOM, DIARRHEA_PRESENCE, BODY_PART], unresolvedRiskSlots: [], recentlyAskedSlots: []
[QuestionPlanner] 槽位 DIARRHEA_FREQUENCY 可以询问
[QuestionPlanner] 槽位 STOOL_CHARACTER 可以询问
[QuestionPlanner] 槽位 DURATION 可以询问
[QuestionPlanner] 槽位 FEVER_PRESENCE 可以询问
[QuestionPlanner] 槽位 FOOD_HISTORY 可以询问
[QuestionPlanner] 槽位 NAUSEA_PRESENCE 可以询问
[QuestionPlanner] 询问计划生成完成, 槽位数量: 6
[QuestionPlanner] 开始最终选择, askableGaps 数量: 6
[QuestionPlanner] unresolvedRiskSlots: [], confirmedRiskSlots: []
[QuestionPlanner] 选择策略: 按照标准优先级顺序, 选择槽位: DURATION, 优先级顺序: 2
[QuestionPlanner] 最终选择的 gaps 数量: 1, gaps: [DURATION]
```

**问题**：
- 系统选择了 DURATION 槽位（优先级顺序 2）
- 但是根据常规规则，应该优先询问 DIARRHEA_FREQUENCY、STOOL_CHARACTER 等腹泻相关槽位

## 根本原因分析

### 问题 1：槽位名称不匹配

**ROUTINE_RULES 中的规则**：
```java
new RoutineRule("腹泻", List.of("PRIMARY_SYMPTOM"), 
    List.of("DIARRHEA_FREQUENCY", "STOOL_CHARACTER", "DURATION", ...))
```

**实际提取的槽位**：
- 系统提取的是 `SYMPTOM` 槽位，值为 "腹泻"
- 但规则检查的是 `PRIMARY_SYMPTOM` 槽位

**结论**：槽位名称不匹配导致规则无法触发！

### 问题 2：hasRoutineSemanticSignal() 方法的实现问题

从日志可以看出：
1. `semanticSignalResolver` 能够正确解析语义信号（"腹泻"、"腹痛"）
2. 但是 `hasRoutineSemanticSignal()` 返回 `false`

可能的原因：
- 方法在检查槽位值时，使用的槽位名称是 `PRIMARY_SYMPTOM`
- 但实际的槽位名称是 `SYMPTOM`
- 导致无法找到匹配的槽位值

## 解决方案

### 方案 1：修改 ROUTINE_RULES 中的槽位名称

将所有规则中的 `PRIMARY_SYMPTOM` 改为 `SYMPTOM`：

```java
new RoutineRule("腹泻", List.of("SYMPTOM"), 
    List.of("DIARRHEA_FREQUENCY", "STOOL_CHARACTER", "DURATION", ...))
```

### 方案 2：修改槽位提取逻辑

在 TurnUnderstandingExecutionEngine 中，将提取的 `SYMPTOM` 槽位重命名为 `PRIMARY_SYMPTOM`。

### 方案 3：修改 hasRoutineSemanticSignal() 方法

让方法同时检查 `SYMPTOM` 和 `PRIMARY_SYMPTOM` 槽位。

## 推荐方案

**推荐方案 1**：修改 ROUTINE_RULES 中的槽位名称，因为：
1. 系统实际提取的槽位名称是 `SYMPTOM`
2. 修改规则配置比修改槽位提取逻辑更安全
3. 不需要修改核心引擎代码

## 验证步骤

1. 修改 `QuestionPlanSupport.java` 中的 ROUTINE_RULES
2. 重新运行 `debugSingleCase` 测试
3. 检查日志中 "语义信号匹配结果" 是否变为 `true`
4. 检查是否优先询问腹泻相关槽位（DIARRHEA_FREQUENCY、STOOL_CHARACTER）

## 附加发现

从日志中还可以看到：
- 系统在第一轮就提取了 `DIARRHEA_PRESENCE` 槽位（值为 "是"）
- 这说明系统能够识别用户有腹泻症状
- 但由于常规规则没有触发，系统没有优先询问腹泻相关的详细信息
