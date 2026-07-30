package athena.insight.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "athena.ai.report")
public class AiReportProperties {
    private boolean enabled = false;
    private String url;
    private String apiKey;
    private String model;
    private long connectTimeoutMs = 3000L;
    private long readTimeoutMs = 10000L;
    private double temperature = 0.7D;
    private int maxTokens = 280;
    private boolean appendDisclaimer = true;
    private String disclaimer = "以上内容仅作健康管理参考，不替代专业医生建议。";
}
