

package com.nageoffer.ai.ragent.triage.battle.common;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * battle 基线链路的 JSON 提取与反序列化工具。
 */
@Component
@RequiredArgsConstructor
public class BattleJsonSupport {

    private final ObjectMapper objectMapper;

    public BattleTriageResult parseResult(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String json = extractJsonObject(raw);
        try {
            return objectMapper.readValue(json, BattleTriageResult.class);
        } catch (Exception ex) {
            return BattleTriageResult.builder()
                    .action("ASK_CLARIFICATION")
                    .message("我还需要更多信息才能判断。请补充主要症状、持续时间、严重程度以及是否有发热/胸痛/呼吸困难等危险信号。")
                    .riskLevel(0)
                    .build();
        }
    }

    private String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
