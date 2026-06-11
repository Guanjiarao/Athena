package athena.rank.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "athena.rag")
public class AthenaRagProperties {

    /**
     * RAG 网关基础地址，例如 http://localhost:8080
     */
    private String baseUrl;
}
