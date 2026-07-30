

package com.nageoffer.ai.ragent.triage.normalization;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ComplaintFallbackResolver {
    static final String GOVERNANCE_TAG = HeuristicGovernanceTags.LEGACY_FALLBACK;

    public String resolvePrimaryComplaint(String text) {
        if (StrUtil.isBlank(text)) return null;
        if (containsAny(text, List.of("肚子疼", "肚子痛", "腹痛"))) return "腹痛";
        if (containsAny(text, List.of("胸痛", "胸口痛", "心口痛")) && !containsAny(text, List.of("不是胸痛", "不是胸口痛", "不是心口痛"))) return "胸痛";
        if (containsAny(text, List.of("胸闷", "胸口闷"))) return "胸闷";
        if (containsAny(text, List.of("胸口不舒服", "胸口有点不舒服", "胸部不适", "胸前不适"))) return "胸部不适";
        if (containsAny(text, List.of("发热", "发烧")) && !containsAny(text, List.of("没有发热", "没发热", "不发热", "没有发烧", "没发烧", "不发烧", "不烧"))) return "发热";
        return null;
    }

    public String resolveWeakSymptomWithBodyCue(String text) {
        if (StrUtil.isBlank(text)) return null;
        boolean hasExplicitPainCue = SemanticParserSupport.containsAny(text, List.of("疼", "痛", "作痛"));
        boolean hasAbdominalBodyCue = SemanticParserSupport.containsAny(text, List.of("肚子", "胃", "上腹", "腹部", "小腹", "下腹"));
        if (hasExplicitPainCue && hasAbdominalBodyCue) return "腹痛";
        return null;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (StrUtil.isBlank(text) || keywords == null || keywords.isEmpty()) return false;
        for (String keyword : keywords) if (StrUtil.isNotBlank(keyword) && text.contains(keyword)) return true;
        return false;
    }
}
