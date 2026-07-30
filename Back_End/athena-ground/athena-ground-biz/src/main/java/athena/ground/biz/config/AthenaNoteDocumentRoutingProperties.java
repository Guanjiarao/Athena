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
 * Athena note 文档上传路由配置
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "athena.rag.note-document")
public class AthenaNoteDocumentRoutingProperties {

    /**
     * Ragent 基础地址
     */
    @NotBlank
    private String baseUrl;

    /**
     * 类型区间到知识库配置映射
     */
    @Valid
    private List<TypeRangeMapping> mappings = new ArrayList<>();

    /**
     * 通用知识类型列表
     */
    private List<Integer> commonTypes = new ArrayList<>();

    /**
     * 通用知识库配置
     */
    @Valid
    @NotNull
    private KnowledgeTarget commonTarget;

    @Data
    public static class TypeRangeMapping {

        @NotNull
        private Integer typeRangeStart;

        @NotNull
        private Integer typeRangeEnd;

        @Valid
        @NotNull
        private KnowledgeTarget target;
    }

    @Data
    public static class KnowledgeTarget {

        /**
         * 业务知识库编码
         */
        @NotBlank
        private String kbCode;

        /**
         * ragent 知识库主键 ID
         */
        @NotBlank
        private String kbId;

        /**
         * 复用的 Athena note pipelineId
         */
        @NotBlank
        private String pipelineId;
    }
}
