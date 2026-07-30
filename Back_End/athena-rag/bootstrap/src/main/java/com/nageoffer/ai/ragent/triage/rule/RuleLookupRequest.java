

package com.nageoffer.ai.ragent.triage.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则模块查询请求，只携带 Redis/DB lookup 所需信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleLookupRequest {

    /**
     * 症状信号，如：腿疼、腹痛、咳嗽。按顺序查询，通常更具体的 signal 排在前面。
     */
    @Builder.Default
    private List<String> signals = new ArrayList<>();
}
