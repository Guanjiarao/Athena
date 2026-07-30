

package com.nageoffer.ai.ragent.knowledge.config;

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
 * Athena 知识同步配置
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "athena.knowledge-sync")
public class AthenaKnowledgeSyncProperties {

    /**
     * 知识库区间映射
     */
    @Valid
    private List<TypeRangeMapping> mappings = new ArrayList<>();

    /**
     * 通用知识类型列表
     */
    private List<Integer> commonTypes = new ArrayList<>();

    /**
     * 通用知识库编码
     */
    @NotBlank
    private String commonKbCode = "kbcommon";

    @Data
    public static class TypeRangeMapping {

        /**
         * 知识库编码
         */
        @NotBlank
        private String kbCode;

        /**
         * 知识库名称
         */
        @NotBlank
        private String kbName;

        /**
         * 类型区间起始值
         */
        @NotNull
        private Integer typeRangeStart;

        /**
         * 类型区间结束值
         */
        @NotNull
        private Integer typeRangeEnd;
    }
}
