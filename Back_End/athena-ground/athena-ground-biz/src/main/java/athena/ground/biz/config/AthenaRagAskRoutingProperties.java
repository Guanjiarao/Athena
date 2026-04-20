package athena.ground.biz.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Athena RAG 问答路由配置
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "athena.rag.ask-routing")
public class AthenaRagAskRoutingProperties {

    /**
     * 年龄段到知识库的映射配置
     */
    @Valid
    private List<AgeRangeMapping> ageRanges = new ArrayList<>();

    /**
     * 兜底知识库编码
     */
    @NotBlank
    private String fallbackKbCode = "common_kb";

    @Data
    public static class AgeRangeMapping {

        /**
         * 知识库编码
         */
        @NotBlank
        private String kbCode;

        /**
         * 最小年龄
         */
        @NotNull
        private Integer minAge;

        /**
         * 最大年龄
         */
        @NotNull
        private Integer maxAge;
    }
}
