CREATE TABLE IF NOT EXISTS t_triage_slot_rule (
    id VARCHAR(64) PRIMARY KEY,
    signal VARCHAR(128) NOT NULL,
    slot_code VARCHAR(64) NOT NULL,
    gap_type VARCHAR(64) NOT NULL DEFAULT 'FOLLOW_UP_REQUIRED',
    source VARCHAR(64) NOT NULL DEFAULT 'PATTERN',
    priority INT NOT NULL DEFAULT 70,
    reason TEXT,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    options_json TEXT,
    enabled INT NOT NULL DEFAULT 1,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

ALTER TABLE t_triage_slot_rule ADD COLUMN IF NOT EXISTS options_json TEXT;

CREATE INDEX IF NOT EXISTS idx_triage_slot_rule_signal_enabled
    ON t_triage_slot_rule (signal, enabled, deleted, confidence DESC, priority DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_triage_slot_rule_signal_slot
    ON t_triage_slot_rule (signal, slot_code)
    WHERE deleted = 0;

CREATE OR REPLACE FUNCTION trg_set_triage_slot_rule_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS triage_slot_rule_set_update_time ON t_triage_slot_rule;

CREATE TRIGGER triage_slot_rule_set_update_time
BEFORE UPDATE ON t_triage_slot_rule
FOR EACH ROW
EXECUTE FUNCTION trg_set_triage_slot_rule_update_time();

INSERT INTO t_triage_slot_rule (id, signal, slot_code, gap_type, source, priority, reason, confidence, options_json, enabled)
VALUES
    ('triage_slot_rule_leg_pain_001', '腿疼', 'PAIN_SEVERITY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 86, '腿疼场景优先确认疼痛程度。', 0.95,
     '[{"label":"轻微","value":"mild","targetSlot":"PAIN_SEVERITY"},{"label":"中等","value":"moderate","targetSlot":"PAIN_SEVERITY"},{"label":"严重","value":"severe","targetSlot":"PAIN_SEVERITY"},{"label":"难以忍受","value":"unbearable","targetSlot":"PAIN_SEVERITY"},{"label":"其他","value":"other","targetSlot":"PAIN_SEVERITY"}]', 1),
    ('triage_slot_rule_leg_pain_002', '腿疼', 'PAIN_CHARACTER', 'FOLLOW_UP_REQUIRED', 'PATTERN', 84, '腿疼场景需要确认疼痛性质。', 0.93,
     '[{"label":"刺痛","value":"sharp","targetSlot":"PAIN_CHARACTER"},{"label":"钝痛","value":"dull","targetSlot":"PAIN_CHARACTER"},{"label":"胀痛","value":"distending","targetSlot":"PAIN_CHARACTER"},{"label":"酸痛","value":"sore","targetSlot":"PAIN_CHARACTER"},{"label":"其他","value":"other","targetSlot":"PAIN_CHARACTER"}]', 1),
    ('triage_slot_rule_leg_pain_003', '腿疼', 'BODY_PART', 'FOLLOW_UP_REQUIRED', 'PATTERN', 82, '腿疼场景需要确认具体疼痛部位。', 0.92,
     '[{"label":"大腿","value":"thigh","targetSlot":"BODY_PART"},{"label":"小腿","value":"calf","targetSlot":"BODY_PART"},{"label":"膝盖","value":"knee","targetSlot":"BODY_PART"},{"label":"脚踝/足部","value":"ankle_foot","targetSlot":"BODY_PART"},{"label":"其他","value":"other","targetSlot":"BODY_PART"}]', 1),
    ('triage_slot_rule_leg_pain_004', '腿疼', 'AGGRAVATING_FACTORS', 'FOLLOW_UP_REQUIRED', 'PATTERN', 78, '腿疼场景需要确认活动、负重等加重因素。', 0.88,
     '[{"label":"走路/活动加重","value":"activity","targetSlot":"AGGRAVATING_FACTORS"},{"label":"按压加重","value":"pressing","targetSlot":"AGGRAVATING_FACTORS"},{"label":"夜间加重","value":"night","targetSlot":"AGGRAVATING_FACTORS"},{"label":"不明显","value":"none","targetSlot":"AGGRAVATING_FACTORS"},{"label":"其他","value":"other","targetSlot":"AGGRAVATING_FACTORS"}]', 1),
    ('triage_slot_rule_leg_pain_005', '腿疼', 'RELIEVING_FACTORS', 'FOLLOW_UP_REQUIRED', 'PATTERN', 74, '腿疼场景需要确认休息或用药后是否缓解。', 0.84,
     '[{"label":"休息后缓解","value":"rest","targetSlot":"RELIEVING_FACTORS"},{"label":"热敷/冷敷缓解","value":"compress","targetSlot":"RELIEVING_FACTORS"},{"label":"用药后缓解","value":"medication","targetSlot":"RELIEVING_FACTORS"},{"label":"没有缓解","value":"none","targetSlot":"RELIEVING_FACTORS"},{"label":"其他","value":"other","targetSlot":"RELIEVING_FACTORS"}]', 1),
    ('triage_slot_rule_leg_pain_006', '腿疼', 'ASSOCIATED_SYMPTOMS', 'FOLLOW_UP_REQUIRED', 'PATTERN', 72, '腿疼场景需要确认是否伴随麻木、肿胀、发热等表现。', 0.82,
     '[{"label":"麻木","value":"numbness","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"肿胀","value":"swelling","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"发热/发红","value":"red_hot","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"没有其他不适","value":"none","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"其他","value":"other","targetSlot":"ASSOCIATED_SYMPTOMS"}]', 1),

    ('triage_slot_rule_abdominal_pain_001', '腹痛', 'BODY_PART', 'FOLLOW_UP_REQUIRED', 'PATTERN', 78, '腹痛场景优先确认疼痛部位。', 1.0, NULL, 1),
    ('triage_slot_rule_abdominal_pain_002', '腹痛', 'PAIN_SEVERITY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 76, '腹痛场景还需确认疼痛程度。', 1.0, NULL, 1),
    ('triage_slot_rule_abdominal_pain_003', '腹痛', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 70, '腹痛场景需要确认是否伴随发热。', 1.0, NULL, 1),
    ('triage_slot_rule_abdominal_pain_004', '腹痛', 'NAUSEA_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 68, '腹痛场景需要确认是否伴随恶心。', 1.0, NULL, 1),
    ('triage_slot_rule_abdominal_pain_005', '腹痛', 'VOMITING_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 66, '腹痛场景需要确认是否伴随呕吐。', 1.0, NULL, 1),

    ('triage_slot_rule_rlq_pain_001', '右下腹痛', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 90, '右下腹痛场景优先确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_rlq_pain_002', '右下腹痛', 'PAIN_MIGRATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 88, '右下腹痛场景需确认疼痛转移（脐周→右下腹）。', 1.0, NULL, 1),
    ('triage_slot_rule_rlq_pain_003', '右下腹痛', 'PAIN_LOCATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 86, '右下腹痛场景需确认疼痛位置是否固定。', 1.0, NULL, 1),
    ('triage_slot_rule_rlq_pain_004', '右下腹痛', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 84, '右下腹痛场景需确认是否发热。', 1.0, NULL, 1),
    ('triage_slot_rule_rlq_pain_005', '右下腹痛', 'NAUSEA_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 82, '右下腹痛场景需确认是否恶心呕吐。', 1.0, NULL, 1),
    ('triage_slot_rule_rlq_pain_006', '右下腹痛', 'REBOUND_TENDERNESS', 'RISK_REQUIRED', 'RISK_POLICY', 95, '右下腹痛场景需确认反跳痛（阑尾炎高危信号）。', 1.0, NULL, 1),
    ('triage_slot_rule_rlq_pain_007', '右下腹痛', 'APPETITE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 80, '右下腹痛场景需确认食欲。', 1.0, NULL, 1),

    ('triage_slot_rule_black_stool_001', '黑便', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 92, '黑便场景优先确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_black_stool_002', '黑便', 'STOOL_CHARACTER', 'RISK_REQUIRED', 'RISK_POLICY', 96, '黑便场景需确认大便性状（柏油样是消化道出血信号）。', 1.0, NULL, 1),
    ('triage_slot_rule_black_stool_003', '黑便', 'FOOD_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 90, '黑便场景需排除猪血/铁剂等食物因素。', 1.0, NULL, 1),
    ('triage_slot_rule_black_stool_004', '黑便', 'BODY_PART', 'FOLLOW_UP_REQUIRED', 'PATTERN', 88, '黑便场景需确认是否伴随腹痛。', 1.0, NULL, 1),
    ('triage_slot_rule_black_stool_005', '黑便', 'ASSOCIATED_SYMPTOMS', 'RISK_REQUIRED', 'RISK_POLICY', 94, '黑便场景需确认是否头晕乏力（贫血信号）。', 1.0, NULL, 1),
    ('triage_slot_rule_black_stool_006', '黑便', 'DIAGNOSIS_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 86, '黑便场景需确认胃溃疡病史。', 1.0, NULL, 1),
    ('triage_slot_rule_black_stool_007', '黑便', 'MEDICATION_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 84, '黑便场景需确认阿司匹林等药物史。', 1.0, NULL, 1),

    ('triage_slot_rule_chest_pain_001', '胸痛', 'DYSPNEA_PRESENCE', 'RISK_REQUIRED', 'RISK_POLICY', 95, '胸痛场景需优先确认呼吸困难等高危信号。', 1.0, NULL, 1),
    ('triage_slot_rule_chest_pain_002', '胸痛', 'BODY_PART', 'FOLLOW_UP_REQUIRED', 'PATTERN', 72, '胸痛场景仍需确认具体部位。', 1.0, NULL, 1),
    ('triage_slot_rule_fever_001', '发热', 'TEMPERATURE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '发热场景优先确认体温。', 1.0, NULL, 1),

    ('triage_slot_rule_diarrhea_001', '腹泻', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 85, '腹泻场景优先确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_diarrhea_002', '腹泻', 'DIARRHEA_FREQUENCY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 83, '腹泻场景需确认腹泻次数。', 1.0, NULL, 1),
    ('triage_slot_rule_diarrhea_003', '腹泻', 'STOOL_CHARACTER', 'FOLLOW_UP_REQUIRED', 'PATTERN', 81, '腹泻场景需确认大便性状。', 1.0, NULL, 1),
    ('triage_slot_rule_diarrhea_004', '腹泻', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 79, '腹泻场景需确认是否发热。', 1.0, NULL, 1),
    ('triage_slot_rule_diarrhea_005', '腹泻', 'BODY_PART', 'FOLLOW_UP_REQUIRED', 'PATTERN', 77, '腹泻场景需确认腹痛部位。', 1.0, NULL, 1),
    ('triage_slot_rule_diarrhea_006', '腹泻', 'FOOD_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '腹泻场景需确认饮食史。', 1.0, NULL, 1),
    ('triage_slot_rule_diarrhea_007', '腹泻', 'NAUSEA_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 73, '腹泻场景需确认是否恶心呕吐。', 1.0, NULL, 1),

    ('triage_slot_rule_stomach_pain_001', '胃疼', 'PAIN_TIMING', 'FOLLOW_UP_REQUIRED', 'PATTERN', 85, '胃疼场景优先确认疼痛时机。', 1.0, NULL, 1),
    ('triage_slot_rule_stomach_pain_002', '胃疼', 'PAIN_CHARACTER', 'FOLLOW_UP_REQUIRED', 'PATTERN', 83, '胃疼场景需确认疼痛性质。', 1.0, NULL, 1),
    ('triage_slot_rule_stomach_pain_003', '胃疼', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 81, '胃疼场景需确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_stomach_pain_004', '胃疼', 'ACID_REFLUX', 'FOLLOW_UP_REQUIRED', 'PATTERN', 79, '胃疼场景需确认是否反酸。', 1.0, NULL, 1),
    ('triage_slot_rule_stomach_pain_005', '胃疼', 'WEIGHT_CHANGE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 77, '胃疼场景需确认体重变化。', 1.0, NULL, 1),
    ('triage_slot_rule_stomach_pain_006', '胃疼', 'STOOL_COLOR', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '胃疼场景需确认大便颜色。', 1.0, NULL, 1),
    ('triage_slot_rule_stomach_pain_007', '胃疼', 'EXAM_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 73, '胃疼场景需确认检查史。', 1.0, NULL, 1),

    ('triage_slot_rule_heartburn_001', '烧心', 'ONSET_TIMING', 'FOLLOW_UP_REQUIRED', 'PATTERN', 85, '烧心场景优先确认发作时机。', 1.0, NULL, 1),
    ('triage_slot_rule_heartburn_002', '烧心', 'ACID_REFLUX', 'FOLLOW_UP_REQUIRED', 'PATTERN', 83, '烧心场景需确认是否反酸。', 1.0, NULL, 1),
    ('triage_slot_rule_heartburn_003', '烧心', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 81, '烧心场景需确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_heartburn_004', '烧心', 'CHEST_TIGHTNESS', 'FOLLOW_UP_REQUIRED', 'PATTERN', 79, '烧心场景需确认是否胸闷。', 1.0, NULL, 1),
    ('triage_slot_rule_heartburn_005', '烧心', 'DIET_HABITS', 'FOLLOW_UP_REQUIRED', 'PATTERN', 77, '烧心场景需确认饮食习惯。', 1.0, NULL, 1),
    ('triage_slot_rule_heartburn_006', '烧心', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '烧心场景需确认是否发热。', 1.0, NULL, 1),
    ('triage_slot_rule_heartburn_007', '烧心', 'NAUSEA_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 73, '烧心场景需确认是否恶心。', 1.0, NULL, 1),

    ('triage_slot_rule_runny_nose_001', '流鼻涕', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 85, '流鼻涕场景优先确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_runny_nose_002', '流鼻涕', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 83, '流鼻涕场景需确认是否发热。', 1.0, NULL, 1),
    ('triage_slot_rule_runny_nose_003', '流鼻涕', 'NASAL_DISCHARGE_COLOR', 'FOLLOW_UP_REQUIRED', 'PATTERN', 81, '流鼻涕场景需确认鼻涕颜色。', 1.0, NULL, 1),
    ('triage_slot_rule_runny_nose_004', '流鼻涕', 'THROAT_PAIN', 'FOLLOW_UP_REQUIRED', 'PATTERN', 79, '流鼻涕场景需确认嗓子是否疼。', 1.0, NULL, 1),
    ('triage_slot_rule_runny_nose_005', '流鼻涕', 'COUGH_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 77, '流鼻涕场景需确认是否咳嗽。', 1.0, NULL, 1),
    ('triage_slot_rule_runny_nose_006', '流鼻涕', 'BODY_ACHE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '流鼻涕场景需确认是否全身酸痛。', 1.0, NULL, 1),
    ('triage_slot_rule_runny_nose_007', '流鼻涕', 'CONTACT_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 73, '流鼻涕场景需确认接触史。', 1.0, NULL, 1),

    ('triage_slot_rule_throat_pain_001', '喉咙痛', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 85, '喉咙痛场景优先确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_throat_pain_002', '喉咙痛', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 83, '喉咙痛场景需确认是否发热。', 1.0, NULL, 1),
    ('triage_slot_rule_throat_pain_003', '喉咙痛', 'THROAT_APPEARANCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 81, '喉咙痛场景需确认咽喉外观。', 1.0, NULL, 1),
    ('triage_slot_rule_throat_pain_004', '喉咙痛', 'SWALLOWING_PAIN', 'FOLLOW_UP_REQUIRED', 'PATTERN', 79, '喉咙痛场景需确认吞咽痛。', 1.0, NULL, 1),
    ('triage_slot_rule_throat_pain_005', '喉咙痛', 'NECK_SWELLING', 'FOLLOW_UP_REQUIRED', 'PATTERN', 77, '喉咙痛场景需确认颈部肿胀。', 1.0, NULL, 1),
    ('triage_slot_rule_throat_pain_006', '喉咙痛', 'COUGH_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '喉咙痛场景需确认是否咳嗽。', 1.0, NULL, 1),
    ('triage_slot_rule_throat_pain_007', '喉咙痛', 'RECURRENCE_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 73, '喉咙痛场景需确认复发史。', 1.0, NULL, 1),

    ('triage_slot_rule_cough_001', '咳嗽', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 85, '咳嗽场景优先确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_cough_002', '咳嗽', 'COUGH_CHARACTER', 'FOLLOW_UP_REQUIRED', 'PATTERN', 83, '咳嗽场景需确认咳嗽性质。', 1.0, NULL, 1),
    ('triage_slot_rule_cough_003', '咳嗽', 'SPUTUM_COLOR', 'FOLLOW_UP_REQUIRED', 'PATTERN', 81, '咳嗽场景需确认痰液颜色。', 1.0, NULL, 1),
    ('triage_slot_rule_cough_004', '咳嗽', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 79, '咳嗽场景需确认是否发热。', 1.0, NULL, 1),
    ('triage_slot_rule_cough_005', '咳嗽', 'DYSPNEA_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 77, '咳嗽场景需确认是否气喘。', 1.0, NULL, 1),
    ('triage_slot_rule_cough_006', '咳嗽', 'SMOKING_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '咳嗽场景需确认吸烟史。', 1.0, NULL, 1),
    ('triage_slot_rule_cough_007', '咳嗽', 'NIGHT_COUGH', 'FOLLOW_UP_REQUIRED', 'PATTERN', 73, '咳嗽场景需确认夜间咳嗽。', 1.0, NULL, 1),

    ('triage_slot_rule_sneeze_001', '打喷嚏', 'SEASONALITY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 85, '打喷嚏场景优先确认季节性。', 1.0, NULL, 1),
    ('triage_slot_rule_sneeze_002', '打喷嚏', 'DURATION', 'FOLLOW_UP_REQUIRED', 'PATTERN', 83, '打喷嚏场景需确认持续时间。', 1.0, NULL, 1),
    ('triage_slot_rule_sneeze_003', '打喷嚏', 'NASAL_SYMPTOMS', 'FOLLOW_UP_REQUIRED', 'PATTERN', 81, '打喷嚏场景需确认鼻部症状。', 1.0, NULL, 1),
    ('triage_slot_rule_sneeze_004', '打喷嚏', 'EYE_SYMPTOMS', 'FOLLOW_UP_REQUIRED', 'PATTERN', 79, '打喷嚏场景需确认眼部症状。', 1.0, NULL, 1),
    ('triage_slot_rule_sneeze_005', '打喷嚏', 'FEVER_PRESENCE', 'FOLLOW_UP_REQUIRED', 'PATTERN', 77, '打喷嚏场景需确认是否发热。', 1.0, NULL, 1),
    ('triage_slot_rule_sneeze_006', '打喷嚏', 'ALLERGY_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 75, '打喷嚏场景需确认过敏史。', 1.0, NULL, 1),
    ('triage_slot_rule_sneeze_007', '打喷嚏', 'MEDICATION_HISTORY', 'FOLLOW_UP_REQUIRED', 'PATTERN', 73, '打喷嚏场景需确认用药史。', 1.0, NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    signal = EXCLUDED.signal,
    slot_code = EXCLUDED.slot_code,
    gap_type = EXCLUDED.gap_type,
    source = EXCLUDED.source,
    priority = EXCLUDED.priority,
    reason = EXCLUDED.reason,
    confidence = EXCLUDED.confidence,
    options_json = EXCLUDED.options_json,
    enabled = EXCLUDED.enabled,
    deleted = 0;

-- 补全迁移规则的选项。按 slot_code 统一提供 4 个常用选项 + 其他，避免前端依赖 OptionGenerator。
UPDATE t_triage_slot_rule
SET options_json = CASE slot_code
    WHEN 'DURATION' THEN '[{"label":"几小时内","value":"hours","targetSlot":"DURATION"},{"label":"1-2天","value":"1_2_days","targetSlot":"DURATION"},{"label":"3-7天","value":"3_7_days","targetSlot":"DURATION"},{"label":"超过一周","value":"over_week","targetSlot":"DURATION"},{"label":"其他","value":"other","targetSlot":"DURATION"}]'
    WHEN 'BODY_PART' THEN '[{"label":"上腹部","value":"upper_abdomen","targetSlot":"BODY_PART"},{"label":"下腹部","value":"lower_abdomen","targetSlot":"BODY_PART"},{"label":"左侧","value":"left","targetSlot":"BODY_PART"},{"label":"右侧","value":"right","targetSlot":"BODY_PART"},{"label":"其他","value":"other","targetSlot":"BODY_PART"}]'
    WHEN 'PAIN_SEVERITY' THEN '[{"label":"轻微","value":"mild","targetSlot":"PAIN_SEVERITY"},{"label":"中等","value":"moderate","targetSlot":"PAIN_SEVERITY"},{"label":"严重","value":"severe","targetSlot":"PAIN_SEVERITY"},{"label":"难以忍受","value":"unbearable","targetSlot":"PAIN_SEVERITY"},{"label":"其他","value":"other","targetSlot":"PAIN_SEVERITY"}]'
    WHEN 'PAIN_CHARACTER' THEN '[{"label":"刺痛","value":"sharp","targetSlot":"PAIN_CHARACTER"},{"label":"钝痛","value":"dull","targetSlot":"PAIN_CHARACTER"},{"label":"绞痛","value":"cramping","targetSlot":"PAIN_CHARACTER"},{"label":"胀痛","value":"distending","targetSlot":"PAIN_CHARACTER"},{"label":"其他","value":"other","targetSlot":"PAIN_CHARACTER"}]'
    WHEN 'FEVER_PRESENCE' THEN '[{"label":"有发热","value":"yes","targetSlot":"FEVER_PRESENCE"},{"label":"没有发热","value":"no","targetSlot":"FEVER_PRESENCE"},{"label":"发冷/寒战","value":"chills","targetSlot":"FEVER_PRESENCE"},{"label":"不确定","value":"uncertain","targetSlot":"FEVER_PRESENCE"},{"label":"其他","value":"other","targetSlot":"FEVER_PRESENCE"}]'
    WHEN 'NAUSEA_PRESENCE' THEN '[{"label":"有恶心","value":"yes","targetSlot":"NAUSEA_PRESENCE"},{"label":"没有恶心","value":"no","targetSlot":"NAUSEA_PRESENCE"},{"label":"轻微恶心","value":"mild","targetSlot":"NAUSEA_PRESENCE"},{"label":"明显恶心","value":"obvious","targetSlot":"NAUSEA_PRESENCE"},{"label":"其他","value":"other","targetSlot":"NAUSEA_PRESENCE"}]'
    WHEN 'VOMITING_PRESENCE' THEN '[{"label":"有呕吐","value":"yes","targetSlot":"VOMITING_PRESENCE"},{"label":"没有呕吐","value":"no","targetSlot":"VOMITING_PRESENCE"},{"label":"偶尔呕吐","value":"occasional","targetSlot":"VOMITING_PRESENCE"},{"label":"频繁呕吐","value":"frequent","targetSlot":"VOMITING_PRESENCE"},{"label":"其他","value":"other","targetSlot":"VOMITING_PRESENCE"}]'
    WHEN 'STOOL_CHARACTER' THEN '[{"label":"水样便","value":"watery","targetSlot":"STOOL_CHARACTER"},{"label":"稀便","value":"loose","targetSlot":"STOOL_CHARACTER"},{"label":"黑便/柏油样","value":"black_tarry","targetSlot":"STOOL_CHARACTER"},{"label":"带血","value":"bloody","targetSlot":"STOOL_CHARACTER"},{"label":"其他","value":"other","targetSlot":"STOOL_CHARACTER"}]'
    WHEN 'DIARRHEA_FREQUENCY' THEN '[{"label":"1-2次/天","value":"1_2_per_day","targetSlot":"DIARRHEA_FREQUENCY"},{"label":"3-5次/天","value":"3_5_per_day","targetSlot":"DIARRHEA_FREQUENCY"},{"label":"6次以上/天","value":"over_6_per_day","targetSlot":"DIARRHEA_FREQUENCY"},{"label":"次数不确定","value":"uncertain","targetSlot":"DIARRHEA_FREQUENCY"},{"label":"其他","value":"other","targetSlot":"DIARRHEA_FREQUENCY"}]'
    WHEN 'FOOD_HISTORY' THEN '[{"label":"吃过生冷/不洁食物","value":"unclean_food","targetSlot":"FOOD_HISTORY"},{"label":"吃过猪血/动物血","value":"animal_blood","targetSlot":"FOOD_HISTORY"},{"label":"吃过铁剂/深色食物","value":"iron_dark_food","targetSlot":"FOOD_HISTORY"},{"label":"没有特殊饮食","value":"none","targetSlot":"FOOD_HISTORY"},{"label":"其他","value":"other","targetSlot":"FOOD_HISTORY"}]'
    WHEN 'ASSOCIATED_SYMPTOMS' THEN '[{"label":"头晕乏力","value":"dizzy_fatigue","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"麻木或放射痛","value":"numbness_radiating","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"发热或寒战","value":"fever_chills","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"没有其他不适","value":"none","targetSlot":"ASSOCIATED_SYMPTOMS"},{"label":"其他","value":"other","targetSlot":"ASSOCIATED_SYMPTOMS"}]'
    WHEN 'COUGH_CHARACTER' THEN '[{"label":"干咳","value":"dry","targetSlot":"COUGH_CHARACTER"},{"label":"有痰咳嗽","value":"productive","targetSlot":"COUGH_CHARACTER"},{"label":"阵发性咳嗽","value":"paroxysmal","targetSlot":"COUGH_CHARACTER"},{"label":"夜间明显","value":"night","targetSlot":"COUGH_CHARACTER"},{"label":"其他","value":"other","targetSlot":"COUGH_CHARACTER"}]'
    WHEN 'SPUTUM_COLOR' THEN '[{"label":"白色","value":"white","targetSlot":"SPUTUM_COLOR"},{"label":"黄色","value":"yellow","targetSlot":"SPUTUM_COLOR"},{"label":"绿色","value":"green","targetSlot":"SPUTUM_COLOR"},{"label":"带血","value":"bloody","targetSlot":"SPUTUM_COLOR"},{"label":"其他","value":"other","targetSlot":"SPUTUM_COLOR"}]'
    WHEN 'THROAT_APPEARANCE' THEN '[{"label":"发红","value":"red","targetSlot":"THROAT_APPEARANCE"},{"label":"有白点/脓点","value":"white_spots","targetSlot":"THROAT_APPEARANCE"},{"label":"肿胀","value":"swollen","targetSlot":"THROAT_APPEARANCE"},{"label":"没注意","value":"unknown","targetSlot":"THROAT_APPEARANCE"},{"label":"其他","value":"other","targetSlot":"THROAT_APPEARANCE"}]'
    WHEN 'SWALLOWING_PAIN' THEN '[{"label":"吞咽时疼","value":"yes","targetSlot":"SWALLOWING_PAIN"},{"label":"不吞咽也疼","value":"constant","targetSlot":"SWALLOWING_PAIN"},{"label":"不明显","value":"no","targetSlot":"SWALLOWING_PAIN"},{"label":"不确定","value":"uncertain","targetSlot":"SWALLOWING_PAIN"},{"label":"其他","value":"other","targetSlot":"SWALLOWING_PAIN"}]'
    WHEN 'DYSPNEA_PRESENCE' THEN '[{"label":"有呼吸困难","value":"yes","targetSlot":"DYSPNEA_PRESENCE"},{"label":"没有呼吸困难","value":"no","targetSlot":"DYSPNEA_PRESENCE"},{"label":"活动后气短","value":"exertional","targetSlot":"DYSPNEA_PRESENCE"},{"label":"胸闷伴气短","value":"chest_tightness","targetSlot":"DYSPNEA_PRESENCE"},{"label":"其他","value":"other","targetSlot":"DYSPNEA_PRESENCE"}]'
    WHEN 'TEMPERATURE' THEN '[{"label":"37-38℃","value":"low_fever","targetSlot":"TEMPERATURE"},{"label":"38-39℃","value":"moderate_fever","targetSlot":"TEMPERATURE"},{"label":"39℃以上","value":"high_fever","targetSlot":"TEMPERATURE"},{"label":"不确定","value":"uncertain","targetSlot":"TEMPERATURE"},{"label":"其他","value":"other","targetSlot":"TEMPERATURE"}]'
    ELSE '[{"label":"有","value":"yes","targetSlot":"' || slot_code || '"},{"label":"没有","value":"no","targetSlot":"' || slot_code || '"},{"label":"轻微","value":"mild","targetSlot":"' || slot_code || '"},{"label":"明显","value":"obvious","targetSlot":"' || slot_code || '"},{"label":"其他","value":"other","targetSlot":"' || slot_code || '"}]'
END
WHERE signal IN ('腹痛','右下腹痛','黑便','胸痛','发热','腹泻','胃疼','烧心','流鼻涕','喉咙痛','咳嗽','打喷嚏')
  AND options_json IS NULL;
