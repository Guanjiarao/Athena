# Case 001 轮次分析报告

## 【轮次1】
- **用户输入**: 肚子疼，拉肚子好几次了
- **已回答槽位**: SYMPTOM, DIARRHEA_PRESENCE, BODY_PART
- **候选 gaps**: [DIARRHEA_FREQUENCY(83), STOOL_CHARACTER(81), DURATION(80), FEVER_PRESENCE(79), FOOD_HISTORY(75), NAUSEA_PRESENCE(73)]
- **选择槽位**: DURATION (priority 80)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 症状持续多久了？

**分析**: ✅ 正常。选择了候选 gaps 中优先级第3的槽位 DURATION。

---

## 【轮次2】
- **用户输入**: 昨天开始的
- **已回答槽位**: SYMPTOM, DIARRHEA_PRESENCE, BODY_PART, ONSET_TIME, DURATION
- **候选 gaps**: [DIARRHEA_FREQUENCY(83), STOOL_CHARACTER(81), FEVER_PRESENCE(79), FOOD_HISTORY(75), NAUSEA_PRESENCE(73)]
- **选择槽位**: FEVER_PRESENCE (priority 79)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 有没有发烧？

**分析**: ⚠️ 异常！候选 gaps 中优先级最高的是 DIARRHEA_FREQUENCY(83)，但选择了 FEVER_PRESENCE(79)。

---

## 【轮次3】
- **用户输入**: 3天
- **已回答槽位**: SYMPTOM, DIARRHEA_PRESENCE, BODY_PART, ONSET_TIME, DURATION
- **候选 gaps**: [DIARRHEA_FREQUENCY(83), STOOL_CHARACTER(81), FEVER_PRESENCE(79), FOOD_HISTORY(75), NAUSEA_PRESENCE(73)]
- **选择槽位**: NAUSEA_PRESENCE (priority 73)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 有没有恶心呕吐？

**分析**: ⚠️ 严重异常！候选 gaps 中有 DIARRHEA_FREQUENCY(83)、STOOL_CHARACTER(81)、FEVER_PRESENCE(79)，但选择了优先级最低的 NAUSEA_PRESENCE(73)。

**注意**: 已回答槽位中没有 FEVER_PRESENCE，说明轮次2的回答没有被正确解析。

---

## 【轮次4】
- **用户输入**: 水样
- **已回答槽位**: SYMPTOM, DIARRHEA_PRESENCE, BODY_PART, ONSET_TIME, STOOL_CHARACTER, DURATION
- **候选 gaps**: [DIARRHEA_FREQUENCY(83), FEVER_PRESENCE(79), FOOD_HISTORY(75), NAUSEA_PRESENCE(73)]
- **选择槽位**: FEVER_PRESENCE (priority 79)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 有没有发烧？

**分析**: ⚠️ 异常！候选 gaps 中优先级最高的是 DIARRHEA_FREQUENCY(83)，但选择了 FEVER_PRESENCE(79)。

**注意**: STOOL_CHARACTER 被填充了（用户回答"水样"），说明轮次3的回答被误解析为大便性状。

---

## 【轮次5】
- **用户输入**: 没有
- **已回答槽位**: SYMPTOM, FEVER_PRESENCE, DIARRHEA_PRESENCE, BODY_PART, ONSET_TIME, STOOL_CHARACTER, DURATION
- **候选 gaps**: [DIARRHEA_FREQUENCY(83), FOOD_HISTORY(75), NAUSEA_PRESENCE(73)]
- **选择槽位**: NAUSEA_PRESENCE (priority 73)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 有没有恶心呕吐？

**分析**: ⚠️ 异常！候选 gaps 中优先级最高的是 DIARRHEA_FREQUENCY(83)，但选择了优先级最低的 NAUSEA_PRESENCE(73)。

---

## 【轮次6】
- **用户输入**: 肚脐周围
- **已回答槽位**: SYMPTOM, FEVER_PRESENCE, DIARRHEA_PRESENCE, BODY_PART, ONSET_TIME, STOOL_CHARACTER, DURATION
- **候选 gaps**: [DIARRHEA_FREQUENCY(83), FOOD_HISTORY(75), NAUSEA_PRESENCE(73)]
- **选择槽位**: DIARRHEA_FREQUENCY (priority 83)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 一天拉几次？

**分析**: ✅ 正常！终于选择了优先级最高的 DIARRHEA_FREQUENCY。

**注意**: 已回答槽位没有变化，说明轮次5的回答"没有"没有被解析到任何槽位。

---

## 【轮次7】
- **用户输入**: 路边摊
- **已回答槽位**: SYMPTOM, FEVER_PRESENCE, DIARRHEA_FREQUENCY, DIARRHEA_PRESENCE, BODY_PART, ONSET_TIME, ASSOCIATED_SYMPTOMS, STOOL_CHARACTER, DURATION
- **候选 gaps**: [FOOD_HISTORY(75), NAUSEA_PRESENCE(73)]
- **选择槽位**: NAUSEA_PRESENCE (priority 73)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 有没有恶心呕吐？

**分析**: ⚠️ 异常！候选 gaps 中优先级最高的是 FOOD_HISTORY(75)，但选择了 NAUSEA_PRESENCE(73)。

---

## 【轮次8】
- **用户输入**: 有点恶心
- **已回答槽位**: SYMPTOM, FEVER_PRESENCE, DIARRHEA_FREQUENCY, DIARRHEA_PRESENCE, BODY_PART, ONSET_TIME, ASSOCIATED_SYMPTOMS, STOOL_CHARACTER, DURATION, NAUSEA_PRESENCE
- **候选 gaps**: [FOOD_HISTORY(75)]
- **选择槽位**: FOOD_HISTORY (priority 75)
- **选择策略**: 按标准槽位优先级顺序
- **系统提问**: 吃过什么不干净的东西吗？

**分析**: ✅ 正常。只剩一个候选 gap。

---

## 异常情况汇总

### 问题1: 选择策略不符合优先级
在多个轮次中，系统声称"按标准槽位优先级顺序"选择，但实际选择的槽位**不是**候选 gaps 中优先级最高的：

| 轮次 | 候选 gaps (按优先级) | 应选择 | 实际选择 | 偏差 |
|------|---------------------|--------|---------|------|
| 2 | DIARRHEA_FREQUENCY(83), STOOL_CHARACTER(81), FEVER_PRESENCE(79), ... | DIARRHEA_FREQUENCY | FEVER_PRESENCE | -4 |
| 3 | DIARRHEA_FREQUENCY(83), STOOL_CHARACTER(81), FEVER_PRESENCE(79), FOOD_HISTORY(75), NAUSEA_PRESENCE(73) | DIARRHEA_FREQUENCY | NAUSEA_PRESENCE | -10 |
| 4 | DIARRHEA_FREQUENCY(83), FEVER_PRESENCE(79), ... | DIARRHEA_FREQUENCY | FEVER_PRESENCE | -4 |
| 5 | DIARRHEA_FREQUENCY(83), FOOD_HISTORY(75), NAUSEA_PRESENCE(73) | DIARRHEA_FREQUENCY | NAUSEA_PRESENCE | -10 |
| 7 | FOOD_HISTORY(75), NAUSEA_PRESENCE(73) | FOOD_HISTORY | NAUSEA_PRESENCE | -2 |

### 问题2: 候选 gaps 中不包含已填充的槽位
✅ 这个问题已解决。所有轮次的候选 gaps 都不包含已填充的槽位。

### 问题3: 重复询问同一槽位
- 轮次2 和轮次4 都询问了 FEVER_PRESENCE
- 轮次3、轮次5、轮次7 都询问了 NAUSEA_PRESENCE

---

## 根本原因诊断

### 原因1: 选择逻辑错误
`QuestionPlanner` 的选择逻辑存在问题。虽然日志显示"按标准槽位优先级顺序"，但实际选择的槽位不是候选 gaps 中优先级最高的。

**可能的代码问题**:
1. 选择逻辑中可能有其他隐藏的排序规则（如槽位类型、依赖关系等）
2. 可能使用了错误的排序字段
3. 可能在选择前对候选 gaps 进行了二次过滤，但没有记录日志

### 原因2: 槽位填充状态不一致
某些槽位被询问后，用户的回答没有被正确解析，导致：
- 轮次2 询问 FEVER_PRESENCE，但轮次3的已回答槽位中没有它
- 轮次3 询问 NAUSEA_PRESENCE，但轮次4的已回答槽位中没有它
- 轮次5 询问 NAUSEA_PRESENCE，但轮次6的已回答槽位中没有它

这导致系统重复询问同一槽位。

---

## 建议修复方案

1. **修复选择逻辑**: 确保 `QuestionPlanner` 真正按照候选 gaps 的优先级顺序选择槽位
2. **增强日志**: 在选择前后记录更详细的信息，包括排序依据、过滤条件等
3. **修复槽位填充**: 检查 `TurnUnderstandingExecutionEngine` 的解析逻辑，确保用户回答被正确解析到对应槽位
4. **防止重复询问**: 在选择前检查 `recentlyAskedSlots`，避免短期内重复询问同一槽位
