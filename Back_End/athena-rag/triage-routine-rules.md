# 分诊系统 ROUTINE_RULES 规则文档

## 说明

本文档整理了所有测试用例对应的 ROUTINE_RULES 规则，用于补充到 `QuestionPlanSupport.java` 中。

## 规则格式

```java
GapRule.forSemanticSignal("语义信号", List.of(
    gapSpec(SlotCode.槽位名, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 优先级, "说明"),
    ...
))
```

## 已实现的规则（用例01-10）

### 用例01：急性肠胃炎（腹泻）
**语义信号**：腹泻、拉肚子、拉稀
**槽位序列**：
```java
GapRule.forSemanticSignal("腹泻", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "腹泻场景优先确认持续时间。"),
    gapSpec(SlotCode.DIARRHEA_FREQUENCY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "腹泻场景需确认频率。"),
    gapSpec(SlotCode.STOOL_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "腹泻场景需确认大便性状。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "腹泻场景需确认是否发热。"),
    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "腹泻场景需确认是否恶心。"),
    gapSpec(SlotCode.VOMITING_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "腹泻场景需确认是否呕吐。"),
    gapSpec(SlotCode.FOOD_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "腹泻场景需确认饮食史。")))
```

### 用例02：慢性胃炎（胃疼）
**语义信号**：胃疼、胃痛、胃不舒服、胃难受
**槽位序列**：
```java
GapRule.forSemanticSignal("胃疼", List.of(
    gapSpec(SlotCode.PAIN_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "胃疼场景优先确认疼痛时机。"),
    gapSpec(SlotCode.PAIN_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "胃疼场景需确认疼痛性质。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "胃疼场景需确认持续时间。"),
    gapSpec(SlotCode.ACID_REFLUX, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "胃疼场景需确认是否反酸。"),
    gapSpec(SlotCode.WEIGHT_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "胃疼场景需确认体重变化。"),
    gapSpec(SlotCode.STOOL_COLOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "胃疼场景需确认大便颜色。"),
    gapSpec(SlotCode.EXAM_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "胃疼场景需确认检查史。")))
```

### 用例03：急性阑尾炎（腹痛）
**说明**：高风险用例，由风险信号规则处理，不需要 ROUTINE_RULES

### 用例04：胃食管反流病（烧心）
**语义信号**：烧心、反酸、胃酸
**槽位序列**：
```java
GapRule.forSemanticSignal("烧心", List.of(
    gapSpec(SlotCode.ONSET_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "烧心场景优先确认发作时机。"),
    gapSpec(SlotCode.ACID_REFLUX, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "烧心场景需确认是否反酸。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "烧心场景需确认持续时间。"),
    gapSpec(SlotCode.CHEST_TIGHTNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "烧心场景需确认是否胸闷。"),
    gapSpec(SlotCode.DIET_HABITS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "烧心场景需确认饮食习惯。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "烧心场景需确认是否发热。"),
    gapSpec(SlotCode.WEIGHT_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "烧心场景需确认体重变化。")))
```

### 用例05：消化道出血（黑便）
**说明**：高风险用例，由风险信号规则处理，不需要 ROUTINE_RULES

### 用例06：普通感冒（流鼻涕）
**语义信号**：流鼻涕、鼻涕、感冒、鼻塞
**槽位序列**：
```java
GapRule.forSemanticSignal("流鼻涕", List.of(
    gapSpec(SlotCode.NASAL_DISCHARGE_COLOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "流鼻涕场景优先确认鼻涕颜色。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "流鼻涕场景需确认持续时间。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "流鼻涕场景需确认是否发热。"),
    gapSpec(SlotCode.THROAT_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "流鼻涕场景需确认是否咽痛。"),
    gapSpec(SlotCode.COUGH_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "流鼻涕场景需确认是否咳嗽。"),
    gapSpec(SlotCode.BODY_ACHE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "流鼻涕场景需确认是否全身酸痛。"),
    gapSpec(SlotCode.CONTACT_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "流鼻涕场景需确认接触史。")))
```

### 用例07：急性咽炎（喉咙痛）
**语义信号**：喉咙痛、咽痛、嗓子疼、嗓子不舒服
**槽位序列**：
```java
GapRule.forSemanticSignal("喉咙痛", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "喉咙痛场景优先确认持续时间。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "喉咙痛场景需确认是否发热。"),
    gapSpec(SlotCode.THROAT_APPEARANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "喉咙痛场景需确认咽喉外观。"),
    gapSpec(SlotCode.SWALLOWING_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "喉咙痛场景需确认吞咽痛。"),
    gapSpec(SlotCode.NECK_SWELLING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "喉咙痛场景需确认颈部肿胀。"),
    gapSpec(SlotCode.COUGH_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "喉咙痛场景需确认是否咳嗽。"),
    gapSpec(SlotCode.RECURRENCE_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "喉咙痛场景需确认复发史。")))
```

### 用例08：慢性支气管炎（咳嗽）
**语义信号**：咳嗽、咳
**槽位序列**：
```java
GapRule.forSemanticSignal("咳嗽", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "咳嗽场景优先确认持续时间。"),
    gapSpec(SlotCode.COUGH_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "咳嗽场景需确认咳嗽性质。"),
    gapSpec(SlotCode.SPUTUM_COLOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "咳嗽场景需确认痰液颜色。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "咳嗽场景需确认是否发热。"),
    gapSpec(SlotCode.SMOKING_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "咳嗽场景需确认吸烟史。"),
    gapSpec(SlotCode.NIGHT_COUGH, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "咳嗽场景需确认夜间咳嗽。"),
    gapSpec(SlotCode.DYSPNEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "咳嗽场景需确认是否气促。")))
```

### 用例09：肺炎（发热+咳嗽）
**说明**：高风险用例，由风险信号规则处理，不需要 ROUTINE_RULES

### 用例10：过敏性鼻炎（打喷嚏）
**语义信号**：打喷嚏、喷嚏、鼻痒、过敏
**槽位序列**：
```java
GapRule.forSemanticSignal("打喷嚏", List.of(
    gapSpec(SlotCode.SEASONALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "打喷嚏场景优先确认季节性。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "打喷嚏场景需确认持续时间。"),
    gapSpec(SlotCode.NASAL_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "打喷嚏场景需确认鼻部症状。"),
    gapSpec(SlotCode.EYE_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "打喷嚏场景需确认眼部症状。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "打喷嚏场景需确认是否发热。"),
    gapSpec(SlotCode.ALLERGY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "打喷嚏场景需确认过敏史。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "打喷嚏场景需确认用药史。")))
```

## 待补充的规则（用例11-50）

### 用例11-15：心血管系统

#### 用例11：高血压常规复查（血压高）
**语义信号**：血压高、高血压
**槽位序列**：
```java
GapRule.forSemanticSignal("血压高", List.of(
    gapSpec(SlotCode.BLOOD_PRESSURE_VALUE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "血压高场景优先确认血压值。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "血压高场景需确认持续时间。"),
    gapSpec(SlotCode.MEDICATION_COMPLIANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "血压高场景需确认用药情况。"),
    gapSpec(SlotCode.HEADACHE_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "血压高场景需确认是否头痛。"),
    gapSpec(SlotCode.DIZZINESS_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "血压高场景需确认是否头晕。"),
    gapSpec(SlotCode.CHEST_TIGHTNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "血压高场景需确认是否胸闷。"),
    gapSpec(SlotCode.DIAGNOSIS_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "血压高场景需确认诊断史。")))
```

#### 用例12：心悸待查（心跳快）
**语义信号**：心跳快、心慌、心悸
**槽位序列**：
```java
GapRule.forSemanticSignal("心跳快", List.of(
    gapSpec(SlotCode.HEART_RATE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "心跳快场景优先确认心率。"),
    gapSpec(SlotCode.PALPITATION_PATTERN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "心跳快场景需确认发作规律。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "心跳快场景需确认持续时间。"),
    gapSpec(SlotCode.CHEST_PAIN_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "心跳快场景需确认是否胸痛。"),
    gapSpec(SlotCode.TRIGGER_FACTORS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "心跳快场景需确认诱发因素。"),
    gapSpec(SlotCode.THYROID_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "心跳快场景需确认甲状腺病史。"),
    gapSpec(SlotCode.ECG_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "心跳快场景需确认心电图检查史。")))
```

#### 用例13：稳定型心绞痛（胸口疼）
**语义信号**：胸口疼、胸痛、胸闷
**槽位序列**：
```java
GapRule.forSemanticSignal("胸口疼", List.of(
    gapSpec(SlotCode.PAIN_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "胸口疼场景优先确认疼痛位置。"),
    gapSpec(SlotCode.PAIN_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "胸口疼场景需确认疼痛性质。"),
    gapSpec(SlotCode.PAIN_DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "胸口疼场景需确认疼痛持续时间。"),
    gapSpec(SlotCode.TRIGGER_FACTORS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "胸口疼场景需确认诱发因素。"),
    gapSpec(SlotCode.RELIEVING_FACTORS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "胸口疼场景需确认缓解方式。"),
    gapSpec(SlotCode.RADIATION_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "胸口疼场景需确认是否放射痛。"),
    gapSpec(SlotCode.CORONARY_DISEASE_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "胸口疼场景需确认冠心病史。")))
```

#### 用例14：心律失常/早搏（心脏漏跳）
**语义信号**：心脏漏跳、早搏、心律不齐
**槽位序列**：
```java
GapRule.forSemanticSignal("心脏漏跳", List.of(
    gapSpec(SlotCode.PALPITATION_FREQUENCY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "心脏漏跳场景优先确认发作频率。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "心脏漏跳场景需确认持续时间。"),
    gapSpec(SlotCode.CHEST_TIGHTNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "心脏漏跳场景需确认是否胸闷。"),
    gapSpec(SlotCode.DIZZINESS_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "心脏漏跳场景需确认是否头晕。"),
    gapSpec(SlotCode.TRIGGER_FACTORS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "心脏漏跳场景需确认诱发因素。"),
    gapSpec(SlotCode.SLEEP_QUALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "心脏漏跳场景需确认睡眠质量。"),
    gapSpec(SlotCode.ECG_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "心脏漏跳场景需确认心电图检查史。")))
```

#### 用例15：疑似心肌炎
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

### 用例16-20：神经系统

#### 用例16：紧张型头痛（头痛）
**语义信号**：头痛、头疼
**槽位序列**：
```java
GapRule.forSemanticSignal("头痛", List.of(
    gapSpec(SlotCode.HEADACHE_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "头痛场景优先确认疼痛位置。"),
    gapSpec(SlotCode.PAIN_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "头痛场景需确认疼痛性质。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "头痛场景需确认持续时间。"),
    gapSpec(SlotCode.TRIGGER_FACTORS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "头痛场景需确认诱发因素。"),
    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "头痛场景需确认是否恶心。"),
    gapSpec(SlotCode.SLEEP_QUALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "头痛场景需确认睡眠质量。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "头痛场景需确认用药史。")))
```

#### 用例17：偏头痛（头痛+恶心）
**语义信号**：偏头痛、半边头痛
**槽位序列**：
```java
GapRule.forSemanticSignal("偏头痛", List.of(
    gapSpec(SlotCode.HEADACHE_LATERALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "偏头痛场景优先确认单侧或双侧。"),
    gapSpec(SlotCode.PAIN_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "偏头痛场景需确认疼痛性质。"),
    gapSpec(SlotCode.PAIN_DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "偏头痛场景需确认发作持续时间。"),
    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "偏头痛场景需确认是否恶心。"),
    gapSpec(SlotCode.PHOTOPHOBIA, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "偏头痛场景需确认是否畏光。"),
    gapSpec(SlotCode.AURA_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "偏头痛场景需确认先兆症状。"),
    gapSpec(SlotCode.FAMILY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "偏头痛场景需确认家族史。")))
```

#### 用例18：疑似脑卒中
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

#### 用例19：眩晕/耳石症（头晕）
**语义信号**：头晕、眩晕、天旋地转
**槽位序列**：
```java
GapRule.forSemanticSignal("头晕", List.of(
    gapSpec(SlotCode.DIZZINESS_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "头晕场景优先确认眩晕性质。"),
    gapSpec(SlotCode.ONSET_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "头晕场景需确认发作时机。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "头晕场景需确认持续时间。"),
    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "头晕场景需确认是否恶心。"),
    gapSpec(SlotCode.HEARING_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "头晕场景需确认听力变化。"),
    gapSpec(SlotCode.HEADACHE_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "头晕场景需确认是否头痛。"),
    gapSpec(SlotCode.RECURRENCE_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "头晕场景需确认复发史。")))
```

#### 用例20：面神经炎/面瘫（嘴歪）
**语义信号**：嘴歪、面瘫、口眼歪斜
**槽位序列**：
```java
GapRule.forSemanticSignal("嘴歪", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "嘴歪场景优先确认发病时间。"),
    gapSpec(SlotCode.FACIAL_SYMMETRY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "嘴歪场景需确认面部对称性。"),
    gapSpec(SlotCode.EYE_CLOSURE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "嘴歪场景需确认眼睛闭合情况。"),
    gapSpec(SlotCode.FOREHEAD_WRINKLE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "嘴歪场景需确认抬眉纹额头。"),
    gapSpec(SlotCode.LIMB_WEAKNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "嘴歪场景需确认肢体无力。"),
    gapSpec(SlotCode.COLD_EXPOSURE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "嘴歪场景需确认受凉史。"),
    gapSpec(SlotCode.EAR_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "嘴歪场景需确认耳后疼痛。")))
```

### 用例21-25：骨科运动系统

#### 用例21：急性腰扭伤（腰痛）
**语义信号**：腰痛、腰疼、闪腰
**槽位序列**：
```java
GapRule.forSemanticSignal("腰痛", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "腰痛场景优先确认发病时间。"),
    gapSpec(SlotCode.INJURY_MECHANISM, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "腰痛场景需确认受伤机制。"),
    gapSpec(SlotCode.PAIN_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "腰痛场景需确认疼痛位置。"),
    gapSpec(SlotCode.MOVEMENT_LIMITATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "腰痛场景需确认活动受限。"),
    gapSpec(SlotCode.LEG_NUMBNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "腰痛场景需确认腿麻。"),
    gapSpec(SlotCode.TRAUMA_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "腰痛场景需确认外伤程度。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "腰痛场景需确认用药史。")))
```

#### 用例22：膝关节骨关节炎（膝盖疼）
**语义信号**：膝盖疼、膝关节痛、膝盖不舒服
**槽位序列**：
```java
GapRule.forSemanticSignal("膝盖疼", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "膝盖疼场景优先确认持续时间。"),
    gapSpec(SlotCode.PAIN_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "膝盖疼场景需确认疼痛时机。"),
    gapSpec(SlotCode.JOINT_SWELLING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "膝盖疼场景需确认关节肿胀。"),
    gapSpec(SlotCode.MORNING_STIFFNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "膝盖疼场景需确认晨僵。"),
    gapSpec(SlotCode.JOINT_SOUND, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "膝盖疼场景需确认关节响声。"),
    gapSpec(SlotCode.TRAUMA_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "膝盖疼场景需确认外伤史。"),
    gapSpec(SlotCode.IMAGING_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "膝盖疼场景需确认影像检查史。")))
```

#### 用例23：疑似骨折
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

#### 用例24：颈椎病（脖子疼）
**语义信号**：脖子疼、颈椎痛、颈部不适
**槽位序列**：
```java
GapRule.forSemanticSignal("脖子疼", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "脖子疼场景优先确认持续时间。"),
    gapSpec(SlotCode.PAIN_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "脖子疼场景需确认疼痛性质。"),
    gapSpec(SlotCode.ARM_NUMBNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "脖子疼场景需确认手臂麻木。"),
    gapSpec(SlotCode.DIZZINESS_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "脖子疼场景需确认是否头晕。"),
    gapSpec(SlotCode.WORK_POSTURE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "脖子疼场景需确认工作姿势。"),
    gapSpec(SlotCode.HEADACHE_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "脖子疼场景需确认是否头痛。"),
    gapSpec(SlotCode.IMAGING_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "脖子疼场景需确认影像检查史。")))
```

#### 用例25：痛风急性发作（脚趾疼）
**语义信号**：脚趾疼、大脚趾痛、痛风
**槽位序列**：
```java
GapRule.forSemanticSignal("脚趾疼", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "脚趾疼场景优先确认发病时间。"),
    gapSpec(SlotCode.PAIN_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "脚趾疼场景需确认疼痛位置。"),
    gapSpec(SlotCode.JOINT_SWELLING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "脚趾疼场景需确认关节肿胀。"),
    gapSpec(SlotCode.SKIN_COLOR_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "脚趾疼场景需确认皮肤颜色。"),
    gapSpec(SlotCode.DIET_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "脚趾疼场景需确认饮食史。"),
    gapSpec(SlotCode.GOUT_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "脚趾疼场景需确认痛风史。"),
    gapSpec(SlotCode.URIC_ACID_LEVEL, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "脚趾疼场景需确认尿酸水平。")))
```

### 用例26-30：皮肤科

#### 用例26：荨麻疹（红疙瘩）
**语义信号**：红疙瘩、起疹子、风团
**槽位序列**：
```java
GapRule.forSemanticSignal("红疙瘩", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "红疙瘩场景优先确认发病时间。"),
    gapSpec(SlotCode.RASH_APPEARANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "红疙瘩场景需确认疙瘩外观。"),
    gapSpec(SlotCode.ITCHING_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "红疙瘩场景需确认瘙痒程度。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "红疙瘩场景需确认是否发烧。"),
    gapSpec(SlotCode.FOOD_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "红疙瘩场景需确认饮食史。"),
    gapSpec(SlotCode.ALLERGY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "红疙瘩场景需确认过敏史。"),
    gapSpec(SlotCode.DYSPNEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "红疙瘩场景需确认呼吸困难。")))
```

#### 用例27：湿疹（红疹脱皮）
**语义信号**：湿疹、红疹、脱皮
**槽位序列**：
```java
GapRule.forSemanticSignal("湿疹", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "湿疹场景优先确认持续时间。"),
    gapSpec(SlotCode.RASH_DISTRIBUTION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "湿疹场景需确认分布部位。"),
    gapSpec(SlotCode.ITCHING_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "湿疹场景需确认瘙痒程度。"),
    gapSpec(SlotCode.SKIN_EXUDATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "湿疹场景需确认皮肤渗水。"),
    gapSpec(SlotCode.ECZEMA_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "湿疹场景需确认湿疹史。"),
    gapSpec(SlotCode.IRRITANT_CONTACT, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "湿疹场景需确认刺激物接触。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "湿疹场景需确认用药史。")))
```

#### 用例28：带状疱疹（腰部红疹疼痛）
**语义信号**：带状疱疹、腰部红疹、水泡
**槽位序列**：
```java
GapRule.forSemanticSignal("带状疱疹", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "带状疱疹场景优先确认发病时间。"),
    gapSpec(SlotCode.PAIN_RASH_SEQUENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "带状疱疹场景需确认疼痛疹子顺序。"),
    gapSpec(SlotCode.RASH_APPEARANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "带状疱疹场景需确认疹子外观。"),
    gapSpec(SlotCode.RASH_LATERALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "带状疱疹场景需确认单侧或双侧。"),
    gapSpec(SlotCode.PAIN_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "带状疱疹场景需确认疼痛性质。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "带状疱疹场景需确认是否发烧。"),
    gapSpec(SlotCode.AGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "带状疱疹场景需确认年龄。")))
```

#### 用例29：足癣（脚气）
**语义信号**：脚气、脚趾缝痒、脱皮
**槽位序列**：
```java
GapRule.forSemanticSignal("脚气", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "脚气场景优先确认持续时间。"),
    gapSpec(SlotCode.TOE_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "脚气场景需确认脚趾位置。"),
    gapSpec(SlotCode.SKIN_APPEARANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "脚气场景需确认皮肤外观。"),
    gapSpec(SlotCode.ITCHING_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "脚气场景需确认瘙痒程度。"),
    gapSpec(SlotCode.FAMILY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "脚气场景需确认家人情况。"),
    gapSpec(SlotCode.FOOTWEAR_TYPE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "脚气场景需确认穿鞋透气性。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "脚气场景需确认用药史。")))
```

#### 用例30：蜂窝织炎
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

### 用例31-35：眼科耳鼻喉

#### 用例31：结膜炎（眼睛红）
**语义信号**：眼睛红、红眼、眼红
**槽位序列**：
```java
GapRule.forSemanticSignal("眼睛红", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "眼睛红场景优先确认发病时间。"),
    gapSpec(SlotCode.EYE_LATERALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "眼睛红场景需确认单眼或双眼。"),
    gapSpec(SlotCode.EYE_DISCHARGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "眼睛红场景需确认分泌物。"),
    gapSpec(SlotCode.ITCHING_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "眼睛红场景需确认瘙痒程度。"),
    gapSpec(SlotCode.VISION_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "眼睛红场景需确认视力变化。"),
    gapSpec(SlotCode.CONTACT_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "眼睛红场景需确认接触史。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "眼睛红场景需确认用药史。")))
```

#### 用例32：麦粒肿（眼皮肿）
**语义信号**：眼皮肿、眼睑肿、麦粒肿
**槽位序列**：
```java
GapRule.forSemanticSignal("眼皮肿", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "眼皮肿场景优先确认发病时间。"),
    gapSpec(SlotCode.SWELLING_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "眼皮肿场景需确认肿胀位置。"),
    gapSpec(SlotCode.PAIN_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "眼皮肿场景需确认是否疼痛。"),
    gapSpec(SlotCode.LUMP_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "眼皮肿场景需确认硬块。"),
    gapSpec(SlotCode.PUS_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "眼皮肿场景需确认化脓。"),
    gapSpec(SlotCode.VISION_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "眼皮肿场景需确认视力影响。"),
    gapSpec(SlotCode.RECURRENCE_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "眼皮肿场景需确认复发史。")))
```

#### 用例33：中耳炎（耳朵疼）
**语义信号**：耳朵疼、耳痛、耳朵不舒服
**槽位序列**：
```java
GapRule.forSemanticSignal("耳朵疼", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "耳朵疼场景优先确认发病时间。"),
    gapSpec(SlotCode.EAR_LATERALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "耳朵疼场景需确认单侧或双侧。"),
    gapSpec(SlotCode.EAR_DISCHARGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "耳朵疼场景需确认流脓流水。"),
    gapSpec(SlotCode.HEARING_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "耳朵疼场景需确认听力下降。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "耳朵疼场景需确认是否发烧。"),
    gapSpec(SlotCode.COLD_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "耳朵疼场景需确认感冒史。"),
    gapSpec(SlotCode.SWIMMING_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "耳朵疼场景需确认游泳史。")))
```

#### 用例34：过敏性鼻炎（鼻痒打喷嚏）
**语义信号**：鼻痒、鼻子痒、过敏性鼻炎
**槽位序列**：
```java
GapRule.forSemanticSignal("鼻痒", List.of(
    gapSpec(SlotCode.SEASONALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "鼻痒场景优先确认季节性。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "鼻痒场景需确认持续时间。"),
    gapSpec(SlotCode.NASAL_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "鼻痒场景需确认鼻部症状。"),
    gapSpec(SlotCode.EYE_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "鼻痒场景需确认眼部症状。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "鼻痒场景需确认是否发热。"),
    gapSpec(SlotCode.ALLERGY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "鼻痒场景需确认过敏史。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "鼻痒场景需确认用药史。")))
```

#### 用例35：突发性耳聋
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

### 用例36-40：泌尿系统

#### 用例36：尿路感染（尿频尿痛）
**语义信号**：尿频、尿痛、小便疼
**槽位序列**：
```java
GapRule.forSemanticSignal("尿频", List.of(
    gapSpec(SlotCode.ONSET_TIME, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "尿频场景优先确认发病时间。"),
    gapSpec(SlotCode.URINATION_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "尿频场景需确认排尿疼痛。"),
    gapSpec(SlotCode.URINATION_FREQUENCY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "尿频场景需确认排尿次数。"),
    gapSpec(SlotCode.URINE_COLOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "尿频场景需确认尿液颜色。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "尿频场景需确认是否发烧。"),
    gapSpec(SlotCode.LOWER_BACK_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "尿频场景需确认腰痛。"),
    gapSpec(SlotCode.UTI_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "尿频场景需确认尿路感染史。")))
```

#### 用例37：肾结石
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

#### 用例38：前列腺增生（排尿困难）
**语义信号**：排尿困难、尿不出、尿等待
**槽位序列**：
```java
GapRule.forSemanticSignal("排尿困难", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "排尿困难场景优先确认持续时间。"),
    gapSpec(SlotCode.URINATION_DIFFICULTY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "排尿困难场景需确认排尿表现。"),
    gapSpec(SlotCode.NOCTURIA_FREQUENCY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "排尿困难场景需确认夜尿次数。"),
    gapSpec(SlotCode.URINE_STREAM, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "排尿困难场景需确认尿线情况。"),
    gapSpec(SlotCode.RESIDUAL_URINE_FEELING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "排尿困难场景需确认残余尿感。"),
    gapSpec(SlotCode.AGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "排尿困难场景需确认年龄。"),
    gapSpec(SlotCode.PROSTATE_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "排尿困难场景需确认前列腺病史。")))
```

#### 用例39：精索静脉曲张（阴囊坠胀）
**语义信号**：阴囊坠胀、阴囊不适、睾丸坠胀
**槽位序列**：
```java
GapRule.forSemanticSignal("阴囊坠胀", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "阴囊坠胀场景优先确认持续时间。"),
    gapSpec(SlotCode.SYMPTOM_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "阴囊坠胀场景需确认症状时机。"),
    gapSpec(SlotCode.SCROTAL_SWELLING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "阴囊坠胀场景需确认阴囊肿大。"),
    gapSpec(SlotCode.PAIN_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "阴囊坠胀场景需确认疼痛程度。"),
    gapSpec(SlotCode.FERTILITY_CONCERN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "阴囊坠胀场景需确认生育问题。"),
    gapSpec(SlotCode.VARICOCELE_APPEARANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "阴囊坠胀场景需确认蚯蚓状团块。"),
    gapSpec(SlotCode.EXAM_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "阴囊坠胀场景需确认检查史。")))
```

#### 用例40：急性尿潴留
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

### 用例41-45：妇产科

#### 用例41：痛经（肚子疼）
**语义信号**：痛经、经期腹痛、大姨妈疼
**槽位序列**：
```java
GapRule.forSemanticSignal("痛经", List.of(
    gapSpec(SlotCode.DYSMENORRHEA_PATTERN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "痛经场景优先确认疼痛规律。"),
    gapSpec(SlotCode.PAIN_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "痛经场景需确认疼痛程度。"),
    gapSpec(SlotCode.MENSTRUAL_REGULARITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "痛经场景需确认月经规律。"),
    gapSpec(SlotCode.MENSTRUAL_FLOW, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "痛经场景需确认出血量。"),
    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "痛经场景需确认恶心腹泻。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "痛经场景需确认用药史。"),
    gapSpec(SlotCode.GYNECOLOGICAL_EXAM_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "痛经场景需确认妇科检查史。")))
```

#### 用例42：阴道炎（白带异常）
**语义信号**：白带异常、下面痒、阴道炎
**槽位序列**：
```java
GapRule.forSemanticSignal("白带异常", List.of(
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "白带异常场景优先确认持续时间。"),
    gapSpec(SlotCode.DISCHARGE_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "白带异常场景需确认白带性状。"),
    gapSpec(SlotCode.ODOR_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "白带异常场景需确认异味。"),
    gapSpec(SlotCode.VULVAR_ITCHING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "白带异常场景需确认外阴瘙痒。"),
    gapSpec(SlotCode.URINATION_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "白带异常场景需确认小便疼痛。"),
    gapSpec(SlotCode.ANTIBIOTIC_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "白带异常场景需确认抗生素使用史。"),
    gapSpec(SlotCode.SEXUAL_ACTIVITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "白带异常场景需确认性生活史。")))
```

#### 用例43：疑似宫外孕
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

#### 用例44：更年期综合征（潮热出汗）
**语义信号**：潮热、出汗、更年期
**槽位序列**：
```java
GapRule.forSemanticSignal("潮热", List.of(
    gapSpec(SlotCode.AGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "潮热场景优先确认年龄。"),
    gapSpec(SlotCode.MENSTRUAL_STATUS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "潮热场景需确认月经状态。"),
    gapSpec(SlotCode.HOT_FLASH_FREQUENCY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "潮热场景需确认发热次数。"),
    gapSpec(SlotCode.NIGHT_SWEATS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "潮热场景需确认夜间出汗。"),
    gapSpec(SlotCode.MOOD_CHANGES, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "潮热场景需确认情绪变化。"),
    gapSpec(SlotCode.CHRONIC_DISEASE_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "潮热场景需确认慢性病史。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "潮热场景需确认用药史。")))
```

#### 用例45：乳腺增生（乳房胀痛）
**语义信号**：乳房胀痛、乳房疼、乳腺增生
**槽位序列**：
```java
GapRule.forSemanticSignal("乳房胀痛", List.of(
    gapSpec(SlotCode.MENSTRUAL_RELATIONSHIP, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "乳房胀痛场景优先确认与月经关系。"),
    gapSpec(SlotCode.BREAST_LUMP, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "乳房胀痛场景需确认肿块情况。"),
    gapSpec(SlotCode.LUMP_MOBILITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "乳房胀痛场景需确认肿块活动度。"),
    gapSpec(SlotCode.NIPPLE_DISCHARGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "乳房胀痛场景需确认乳头溢液。"),
    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "乳房胀痛场景需确认持续时间。"),
    gapSpec(SlotCode.BREAST_EXAM_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "乳房胀痛场景需确认乳腺检查史。"),
    gapSpec(SlotCode.BREAST_CANCER_FAMILY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "乳房胀痛场景需确认乳腺癌家族史。")))
```

### 用例46-50：儿科

#### 用例46：幼儿急疹（发烧）
**语义信号**：幼儿发烧、小儿高热、婴儿发热
**槽位序列**：
```java
GapRule.forSemanticSignal("幼儿发烧", List.of(
    gapSpec(SlotCode.AGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "幼儿发烧场景优先确认年龄。"),
    gapSpec(SlotCode.TEMPERATURE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "幼儿发烧场景需确认体温。"),
    gapSpec(SlotCode.FEVER_DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "幼儿发烧场景需确认发烧天数。"),
    gapSpec(SlotCode.MENTAL_STATE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "幼儿发烧场景需确认精神状态。"),
    gapSpec(SlotCode.RESPIRATORY_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "幼儿发烧场景需确认呼吸道症状。"),
    gapSpec(SlotCode.RASH_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "幼儿发烧场景需确认出疹情况。"),
    gapSpec(SlotCode.FEEDING_STATUS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "幼儿发烧场景需确认饮食饮水。")))
```

#### 用例47：小儿急性肠胃炎（又吐又拉）
**语义信号**：小儿呕吐、幼儿腹泻、又吐又拉
**槽位序列**：
```java
GapRule.forSemanticSignal("又吐又拉", List.of(
    gapSpec(SlotCode.AGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "又吐又拉场景优先确认年龄。"),
    gapSpec(SlotCode.SYMPTOM_SEQUENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "又吐又拉场景需确认呕吐腹泻顺序。"),
    gapSpec(SlotCode.DIARRHEA_FREQUENCY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "又吐又拉场景需确认腹泻次数。"),
    gapSpec(SlotCode.STOOL_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "又吐又拉场景需确认大便性状。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "又吐又拉场景需确认是否发烧。"),
    gapSpec(SlotCode.URINE_OUTPUT, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "又吐又拉场景需确认尿量。"),
    gapSpec(SlotCode.FLUID_INTAKE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "又吐又拉场景需确认喝水情况。")))
```

#### 用例48：热性惊厥
**说明**：高风险用例,由风险信号规则处理,不需要 ROUTINE_RULES

#### 用例49：小儿咳嗽变异性哮喘（慢性咳嗽）
**语义信号**：小儿慢性咳嗽、儿童长期咳嗽
**槽位序列**：
```java
GapRule.forSemanticSignal("小儿慢性咳嗽", List.of(
    gapSpec(SlotCode.AGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "小儿慢性咳嗽场景优先确认年龄。"),
    gapSpec(SlotCode.COUGH_DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "小儿慢性咳嗽场景需确认咳嗽时长。"),
    gapSpec(SlotCode.COUGH_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "小儿慢性咳嗽场景需确认咳嗽性质。"),
    gapSpec(SlotCode.COUGH_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "小儿慢性咳嗽场景需确认咳嗽时机。"),
    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "小儿慢性咳嗽场景需确认是否发烧。"),
    gapSpec(SlotCode.ATOPY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "小儿慢性咳嗽场景需确认过敏史。"),
    gapSpec(SlotCode.FAMILY_ASTHMA_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "小儿慢性咳嗽场景需确认哮喘家族史。")))
```

#### 用例50：婴儿湿疹（红疹）
**语义信号**：婴儿湿疹、宝宝红疹、婴儿皮疹
**槽位序列**：
```java
GapRule.forSemanticSignal("婴儿湿疹", List.of(
    gapSpec(SlotCode.AGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "婴儿湿疹场景优先确认月龄。"),
    gapSpec(SlotCode.RASH_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "婴儿湿疹场景需确认疹子位置。"),
    gapSpec(SlotCode.RASH_APPEARANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "婴儿湿疹场景需确认疹子外观。"),
    gapSpec(SlotCode.ITCHING_BEHAVIOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "婴儿湿疹场景需确认搔抓行为。"),
    gapSpec(SlotCode.FEEDING_TYPE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "婴儿湿疹场景需确认喂养方式。"),
    gapSpec(SlotCode.MATERNAL_DIET, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "婴儿湿疹场景需确认母亲饮食。"),
    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "婴儿湿疹场景需确认用药史。")))
```

## 同义词映射

```java
Map<String, List<String>> synonyms = Map.ofEntries(
    // 消化系统（用例01-05）
    Map.entry("腹泻", List.of("拉肚子", "腹泻", "拉稀")),
    Map.entry("胃疼", List.of("胃疼", "胃痛", "胃不舒服", "胃难受")),
    Map.entry("烧心", List.of("烧心", "反酸", "胃酸")),
    
    // 呼吸系统（用例06-10）
    Map.entry("流鼻涕", List.of("流鼻涕", "鼻涕", "感冒", "鼻塞")),
    Map.entry("喉咙痛", List.of("喉咙痛", "咽痛", "嗓子疼", "嗓子不舒服")),
    Map.entry("咳嗽", List.of("咳嗽", "咳")),
    Map.entry("打喷嚏", List.of("打喷嚏", "喷嚏", "鼻痒", "过敏")),
    
    // 心血管系统（用例11-15）
    Map.entry("血压高", List.of("血压高", "高血压")),
    Map.entry("心跳快", List.of("心跳快", "心慌", "心悸")),
    Map.entry("胸口疼", List.of("胸口疼", "胸痛", "胸闷")),
    Map.entry("心脏漏跳", List.of("心脏漏跳", "早搏", "心律不齐")),
    
    // 神经系统（用例16-20）
    Map.entry("头痛", List.of("头痛", "头疼")),
    Map.entry("偏头痛", List.of("偏头痛", "半边头痛")),
    Map.entry("头晕", List.of("头晕", "眩晕", "天旋地转")),
    Map.entry("嘴歪", List.of("嘴歪", "面瘫", "口眼歪斜")),
    
    // 骨科运动系统（用例21-25）
    Map.entry("腰痛", List.of("腰痛", "腰疼", "闪腰")),
    Map.entry("膝盖疼", List.of("膝盖疼", "膝关节痛", "膝盖不舒服")),
    Map.entry("脖子疼", List.of("脖子疼", "颈椎痛", "颈部不适")),
    Map.entry("脚趾疼", List.of("脚趾疼", "大脚趾痛", "痛风")),
    
    // 皮肤科（用例26-30）
    Map.entry("红疙瘩", List.of("红疙瘩", "起疹子", "风团")),
    Map.entry("湿疹", List.of("湿疹", "红疹", "脱皮")),
    Map.entry("带状疱疹", List.of("带状疱疹", "腰部红疹", "水泡")),
    Map.entry("脚气", List.of("脚气", "脚趾缝痒", "脱皮")),
    
    // 眼科耳鼻喉（用例31-35）
    Map.entry("眼睛红", List.of("眼睛红", "红眼", "眼红")),
    Map.entry("眼皮肿", List.of("眼皮肿", "眼睑肿", "麦粒肿")),
    Map.entry("耳朵疼", List.of("耳朵疼", "耳痛", "耳朵不舒服")),
    Map.entry("鼻痒", List.of("鼻痒", "鼻子痒", "过敏性鼻炎")),
    
    // 泌尿系统（用例36-40）
    Map.entry("尿频", List.of("尿频", "尿痛", "小便疼")),
    Map.entry("排尿困难", List.of("排尿困难", "尿不出", "尿等待")),
    Map.entry("阴囊坠胀", List.of("阴囊坠胀", "阴囊不适", "睾丸坠胀")),
    
    // 妇产科（用例41-45）
    Map.entry("痛经", List.of("痛经", "经期腹痛", "大姨妈疼")),
    Map.entry("白带异常", List.of("白带异常", "下面痒", "阴道炎")),
    Map.entry("潮热", List.of("潮热", "出汗", "更年期")),
    Map.entry("乳房胀痛", List.of("乳房胀痛", "乳房疼", "乳腺增生")),
    
    // 儿科（用例46-50）
    Map.entry("幼儿发烧", List.of("幼儿发烧", "小儿高热", "婴儿发热")),
    Map.entry("又吐又拉", List.of("又吐又拉", "小儿呕吐", "幼儿腹泻")),
    Map.entry("小儿慢性咳嗽", List.of("小儿慢性咳嗽", "儿童长期咳嗽")),
    Map.entry("婴儿湿疹", List.of("婴儿湿疹", "宝宝红疹", "婴儿皮疹"))
);
```

## 需要新增的槽位

以下槽位在当前 `SlotCode.java` 中不存在,需要添加:

### 心血管系统相关
- `BLOOD_PRESSURE_VALUE` - 血压值
- `MEDICATION_COMPLIANCE` - 用药依从性
- `HEADACHE_PRESENCE` - 是否头痛
- `DIZZINESS_PRESENCE` - 是否头晕
- `HEART_RATE` - 心率
- `PALPITATION_PATTERN` - 心悸发作规律
- `CHEST_PAIN_PRESENCE` - 是否胸痛
- `THYROID_HISTORY` - 甲状腺病史
- `ECG_HISTORY` - 心电图检查史
- `PAIN_DURATION` - 疼痛持续时间
- `RADIATION_PAIN` - 放射痛
- `CORONARY_DISEASE_HISTORY` - 冠心病史
- `PALPITATION_FREQUENCY` - 心悸发作频率
- `SLEEP_QUALITY` - 睡眠质量

### 神经系统相关
- `HEADACHE_LOCATION` - 头痛位置
- `HEADACHE_LATERALITY` - 头痛单侧或双侧
- `PHOTOPHOBIA` - 畏光
- `AURA_SYMPTOMS` - 先兆症状
- `FAMILY_HISTORY` - 家族史
- `DIZZINESS_CHARACTER` - 眩晕性质
- `HEARING_CHANGE` - 听力变化
- `HEADACHE_PRESENCE` - 是否头痛
- `FACIAL_SYMMETRY` - 面部对称性
- `EYE_CLOSURE` - 眼睛闭合情况
- `FOREHEAD_WRINKLE` - 抬眉纹额头
- `LIMB_WEAKNESS` - 肢体无力
- `COLD_EXPOSURE` - 受凉史
- `EAR_PAIN` - 耳后疼痛

### 骨科运动系统相关
- `INJURY_MECHANISM` - 受伤机制
- `MOVEMENT_LIMITATION` - 活动受限
- `LEG_NUMBNESS` - 腿麻
- `TRAUMA_SEVERITY` - 外伤程度
- `JOINT_SWELLING` - 关节肿胀
- `MORNING_STIFFNESS` - 晨僵
- `JOINT_SOUND` - 关节响声
- `TRAUMA_HISTORY` - 外伤史
- `IMAGING_HISTORY` - 影像检查史
- `ARM_NUMBNESS` - 手臂麻木
- `WORK_POSTURE` - 工作姿势
- `SKIN_COLOR_CHANGE` - 皮肤颜色变化
- `DIET_HISTORY` - 饮食史
- `GOUT_HISTORY` - 痛风史
- `URIC_ACID_LEVEL` - 尿酸水平

### 皮肤科相关
- `RASH_APPEARANCE` - 疹子外观
- `ITCHING_SEVERITY` - 瘙痒程度
- `RASH_DISTRIBUTION` - 疹子分布部位
- `SKIN_EXUDATION` - 皮肤渗水
- `ECZEMA_HISTORY` - 湿疹史
- `IRRITANT_CONTACT` - 刺激物接触
- `PAIN_RASH_SEQUENCE` - 疼痛疹子顺序
- `RASH_LATERALITY` - 疹子单侧或双侧
- `TOE_LOCATION` - 脚趾位置
- `SKIN_APPEARANCE` - 皮肤外观
- `FOOTWEAR_TYPE` - 穿鞋类型

### 眼科耳鼻喉相关
- `EYE_LATERALITY` - 单眼或双眼
- `EYE_DISCHARGE` - 眼部分泌物
- `VISION_CHANGE` - 视力变化
- `SWELLING_LOCATION` - 肿胀位置
- `PAIN_PRESENCE` - 是否疼痛
- `LUMP_PRESENCE` - 硬块
- `PUS_PRESENCE` - 化脓
- `EAR_LATERALITY` - 单侧或双侧耳朵
- `EAR_DISCHARGE` - 耳部流脓流水
- `COLD_HISTORY` - 感冒史
- `SWIMMING_HISTORY` - 游泳史

### 泌尿系统相关
- `URINATION_PAIN` - 排尿疼痛
- `URINATION_FREQUENCY` - 排尿次数
- `URINE_COLOR` - 尿液颜色
- `LOWER_BACK_PAIN` - 腰痛
- `UTI_HISTORY` - 尿路感染史
- `URINATION_DIFFICULTY` - 排尿困难表现
- `NOCTURIA_FREQUENCY` - 夜尿次数
- `URINE_STREAM` - 尿线情况
- `RESIDUAL_URINE_FEELING` - 残余尿感
- `PROSTATE_HISTORY` - 前列腺病史
- `SYMPTOM_TIMING` - 症状时机
- `SCROTAL_SWELLING` - 阴囊肿大
- `FERTILITY_CONCERN` - 生育问题
- `VARICOCELE_APPEARANCE` - 蚯蚓状团块外观
- `EXAM_HISTORY` - 检查史

### 妇产科相关
- `DYSMENORRHEA_PATTERN` - 痛经规律
- `MENSTRUAL_REGULARITY` - 月经规律
- `MENSTRUAL_FLOW` - 月经出血量
- `GYNECOLOGICAL_EXAM_HISTORY` - 妇科检查史
- `DISCHARGE_CHARACTER` - 白带性状
- `ODOR_PRESENCE` - 异味
- `VULVAR_ITCHING` - 外阴瘙痒
- `ANTIBIOTIC_HISTORY` - 抗生素使用史
- `SEXUAL_ACTIVITY` - 性生活史
- `MENSTRUAL_STATUS` - 月经状态
- `HOT_FLASH_FREQUENCY` - 潮热发作次数
- `NIGHT_SWEATS` - 夜间出汗
- `MOOD_CHANGES` - 情绪变化
- `CHRONIC_DISEASE_HISTORY` - 慢性病史
- `MENSTRUAL_RELATIONSHIP` - 与月经关系
- `BREAST_LUMP` - 乳房肿块
- `LUMP_MOBILITY` - 肿块活动度
- `NIPPLE_DISCHARGE` - 乳头溢液
- `BREAST_EXAM_HISTORY` - 乳腺检查史
- `BREAST_CANCER_FAMILY_HISTORY` - 乳腺癌家族史

### 儿科相关
- `MENTAL_STATE` - 精神状态
- `RESPIRATORY_SYMPTOMS` - 呼吸道症状
- `RASH_PRESENCE` - 出疹情况
- `FEEDING_STATUS` - 饮食饮水状况
- `SYMPTOM_SEQUENCE` - 症状顺序
- `URINE_OUTPUT` - 尿量
- `FLUID_INTAKE` - 喝水情况
- `COUGH_DURATION` - 咳嗽时长
- `COUGH_TIMING` - 咳嗽时机
- `ATOPY_HISTORY` - 过敏体质史
- `FAMILY_ASTHMA_HISTORY` - 哮喘家族史
- `RASH_LOCATION` - 疹子位置
- `ITCHING_BEHAVIOR` - 搔抓行为
- `FEEDING_TYPE` - 喂养方式
- `MATERNAL_DIET` - 母亲饮食
- `FEVER_DURATION` - 发烧持续天数

## 注意事项

1. **高风险用例**：用例03（阑尾炎）、05（黑便）、09（肺炎）等高风险用例由风险信号规则处理，不需要添加到 ROUTINE_RULES
2. **优先级设置**：从85开始递减，每个槽位间隔2
3. **槽位顺序**：按照临床推理顺序排列（时间→性质→伴随症状→病史）
4. **同义词覆盖**：确保覆盖用户可能使用的各种表达方式
