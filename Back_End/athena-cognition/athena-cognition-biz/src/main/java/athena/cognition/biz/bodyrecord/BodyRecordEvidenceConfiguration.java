package athena.cognition.biz.bodyrecord;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the RULE_2 extension point. The default provider confirms no body
 * records; P3-3 replaces it with a daily_record Feign-backed implementation by
 * declaring its own {@link BodyRecordEvidenceProvider} bean.
 */
@Configuration
public class BodyRecordEvidenceConfiguration {

    @Bean
    @ConditionalOnMissingBean(BodyRecordEvidenceProvider.class)
    public BodyRecordEvidenceProvider emptyBodyRecordEvidenceProvider() {
        return (userId, suggestedTopicId, suggestedTopicTitle) -> List.of();
    }
}
