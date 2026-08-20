package athena.cognition.biz.domain;

import java.util.List;

/**
 * Deterministic keyword mapping to the contract topic domains (section 4.3:
 * MOOD / CYCLE / SLEEP / SYMPTOM / SEXUAL_HEALTH / OTHER). Server-side
 * constant table, no model involved; the first matching rule wins, unmatched
 * input falls back to OTHER.
 */
public final class TopicDomainClassifier {

    public static final String OTHER = "OTHER";

    private record Rule(String domain, List<String> keywords) {
        boolean matches(String text) {
            return keywords.stream().anyMatch(text::contains);
        }
    }

    // Evaluation order matters. MOOD precedes CYCLE so that "经前情绪变化"
    // maps to MOOD exactly like the contract section 8.8 example; CYCLE
    // precedes the generic SYMPTOM bucket.
    private static final List<Rule> RULES = List.of(
            new Rule("MOOD", List.of("情绪", "心情", "焦虑", "抑郁", "烦躁", "低落", "压力", "紧张", "易怒")),
            new Rule("SEXUAL_HEALTH", List.of("性生活", "私处", "阴道", "白带", "避孕", "亲密")),
            new Rule("SLEEP", List.of("睡眠", "失眠", "入睡", "熬夜", "早醒", "多梦")),
            new Rule("CYCLE", List.of("经期", "月经", "例假", "经前", "排卵", "痛经", "周期")),
            new Rule("SYMPTOM", List.of("疼痛", "头痛", "腹痛", "胀气", "恶心", "发热", "乏力", "腰酸", "胀痛"))
    );

    private TopicDomainClassifier() {
    }

    public static String classify(String... texts) {
        if (texts == null) return OTHER;
        StringBuilder haystack = new StringBuilder();
        for (String text : texts) {
            if (text != null) haystack.append(text).append('\n');
        }
        String combined = haystack.toString();
        return RULES.stream().filter(rule -> rule.matches(combined)).findFirst()
                .map(Rule::domain).orElse(OTHER);
    }
}
