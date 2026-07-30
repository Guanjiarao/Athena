

package com.nageoffer.ai.ragent.triage.battle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 三路 battle 批量运行请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleRunRequest {

    /**
     * 最多运行多少个用例，默认 10。
     */
    private Integer limit;

    /**
     * 最大对话轮次，包含初始轮；默认 7。
     */
    private Integer maxTurns;

    /**
     * 是否启用 LLM Judge；默认 true。
     */
    private Boolean judgeEnabled;

    /**
     * 可选：指定用例编号；为空时从评测集顺序取前 limit 个。
     */
    @Builder.Default
    private List<String> caseIds = new ArrayList<>();
}
