package athena.ground.biz.config;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "athena.topic.extractor")
public class NoteTopicExtractorProperties {

    @Valid
    private List<TopicRule> rules = defaultRules();

    @Valid
    private List<ChannelBoostRule> channelBoosts = defaultChannelBoosts();

    @Data
    public static class TopicRule {
        private String topicName;
        private BigDecimal titleWeight = new BigDecimal("1.0");
        private BigDecimal contentWeight = new BigDecimal("0.6");
        private List<String> keywords = new ArrayList<>();
    }

    @Data
    public static class ChannelBoostRule {
        private Integer channelId;
        private String topicName;
        private BigDecimal weight = BigDecimal.ZERO;
    }

    private static List<TopicRule> defaultRules() {
        List<TopicRule> rules = new ArrayList<>();
        rules.add(rule("经期护理", List.of(
                "经期", "姨妈", "生理期", "月经", "经血", "周期", "例假", "来月经", "月经问题", "生理卫生", "乳房变化", "孕期护理", "怀孕后"
        )));
        rules.add(rule("痛经缓解", List.of(
                "痛经", "腹痛", "热敷", "缓解疼痛", "姨妈痛", "肚子疼", "经期疼", "腰酸", "坠痛", "止痛", "缓解不适"
        )));
        rules.add(rule("睡眠调节", List.of(
                "失眠", "睡不好", "睡不着", "熬夜", "睡眠", "入睡困难", "早醒", "睡眠差", "作息", "休息不好"
        )));
        rules.add(rule("情绪调节", List.of(
                "焦虑", "烦躁", "情绪低落", "压力", "崩溃", "抑郁", "情绪波动", "易怒", "拖延", "紧张", "内耗", "心态", "压力过大", "焦虑症", "抑郁症"
        )));
        rules.add(rule("饮食管理", List.of(
                "饮食", "忌口", "补铁", "吃什么", "食物", "减肥", "膳食", "纤维", "血糖", "营养", "健身餐", "吃辣", "暴食", "食欲", "减脂"
        )));
        rules.add(rule("新手入门", List.of(
                "指南", "科普", "小课堂", "入门", "常识", "知识", "全攻略", "怎么办", "如何", "须知"
        )));
        return rules;
    }

    private static List<ChannelBoostRule> defaultChannelBoosts() {
        List<ChannelBoostRule> boosts = new ArrayList<>();
        boosts.add(channelBoost(4, "经期护理", "0.4"));
        boosts.add(channelBoost(2, "饮食管理", "0.2"));
        boosts.add(channelBoost(3, "情绪调节", "0.3"));
        boosts.add(channelBoost(1, "新手入门", "0.2"));
        return boosts;
    }

    private static TopicRule rule(String topicName, List<String> keywords) {
        TopicRule rule = new TopicRule();
        rule.setTopicName(topicName);
        rule.setKeywords(new ArrayList<>(keywords));
        return rule;
    }

    private static ChannelBoostRule channelBoost(Integer channelId, String topicName, String weight) {
        ChannelBoostRule rule = new ChannelBoostRule();
        rule.setChannelId(channelId);
        rule.setTopicName(topicName);
        rule.setWeight(new BigDecimal(weight));
        return rule;
    }
}
