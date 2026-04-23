/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 系统设置视图对象
 * 包含RAG和AI相关的配置信息
 */
@Setter
@Getter
@Schema(description = "系统设置视图对象")
public class SystemSettingsVO {

@Schema(description = "rag")
    private RagSettings rag;
@Schema(description = "ai")
    private AISettings ai;
@Schema(description = "upload")
    private UploadSettings upload;

    public SystemSettingsVO(RagSettings rag, AISettings ai, UploadSettings upload) {
        this.rag = rag;
        this.ai = ai;
        this.upload = upload;
    }

    public static SystemSettingsVOBuilder builder() {
        return new SystemSettingsVOBuilder();
    }

    public static class SystemSettingsVOBuilder {
@Schema(description = "rag")
        private RagSettings rag;
@Schema(description = "ai")
        private AISettings ai;
@Schema(description = "upload")
        private UploadSettings upload;

        public SystemSettingsVOBuilder rag(RagSettings rag) {
            this.rag = rag;
            return this;
        }

        public SystemSettingsVOBuilder ai(AISettings ai) {
            this.ai = ai;
            return this;
        }

        public SystemSettingsVOBuilder upload(UploadSettings upload) {
            this.upload = upload;
            return this;
        }

        public SystemSettingsVO build() {
            return new SystemSettingsVO(rag, ai, upload);
        }
    }

    @Data
    @Builder
    public static class UploadSettings {
@Schema(description = "maxFileSize")
        private Long maxFileSize;
@Schema(description = "maxRequestSize")
        private Long maxRequestSize;
    }

    @Data
    @Builder
    public static class AISettings {
@Schema(description = "providers")
        private Map<String, ProviderConfig> providers;
@Schema(description = "chat")
        private ModelGroup chat;
@Schema(description = "embedding")
        private ModelGroup embedding;
@Schema(description = "rerank")
        private ModelGroup rerank;
@Schema(description = "selection")
        private Selection selection;
@Schema(description = "stream")
        private Stream stream;

        @Data
        @Builder
        public static class ProviderConfig {
@Schema(description = "url")
            private String url;
@Schema(description = "apiKey")
            private String apiKey;
@Schema(description = "endpoints")
            private Map<String, String> endpoints;
        }

        @Data
        @Builder
        public static class ModelGroup {
@Schema(description = "defaultModel")
            private String defaultModel;
@Schema(description = "deepThinkingModel")
            private String deepThinkingModel;
@Schema(description = "candidates")
            private List<ModelCandidate> candidates;
        }

        @Data
        @Builder
        public static class ModelCandidate {
@Schema(description = "id")
            private String id;
@Schema(description = "provider")
            private String provider;
@Schema(description = "model")
            private String model;
@Schema(description = "url")
            private String url;
@Schema(description = "dimension")
            private Integer dimension;
@Schema(description = "priority")
            private Integer priority;
@Schema(description = "enabled")
            private Boolean enabled;
@Schema(description = "supportsThinking")
            private Boolean supportsThinking;
        }

        @Data
        @Builder
        public static class Selection {
@Schema(description = "failureThreshold")
            private Integer failureThreshold;
@Schema(description = "openDurationMs")
            private Long openDurationMs;
        }

        @Data
        @Builder
        public static class Stream {
@Schema(description = "messageChunkSize")
            private Integer messageChunkSize;
        }
    }

    @Data
    @Builder
    public static class DefaultSettings {
@Schema(description = "collectionName")
        private String collectionName;
@Schema(description = "dimension")
        private Integer dimension;
@Schema(description = "metricType")
        private String metricType;
    }

    @Data
    @Builder
    public static class MemorySettings {
@Schema(description = "historyKeepTurns")
        private Integer historyKeepTurns;
@Schema(description = "ttlMinutes")
        private Integer ttlMinutes;
@Schema(description = "summaryEnabled")
        private Boolean summaryEnabled;
@Schema(description = "summaryStartTurns")
        private Integer summaryStartTurns;
@Schema(description = "summaryMaxChars")
        private Integer summaryMaxChars;
@Schema(description = "titleMaxLength")
        private Integer titleMaxLength;
    }

    @Setter
    @Getter
    public static class RagSettings {
        @JsonProperty("default")
@Schema(description = "defaultConfig")
        private DefaultSettings defaultConfig;
@Schema(description = "queryRewrite")
        private QueryRewriteSettings queryRewrite;
@Schema(description = "rateLimit")
        private RateLimitSettings rateLimit;
@Schema(description = "memory")
        private MemorySettings memory;

        public RagSettings(DefaultSettings defaultConfig, QueryRewriteSettings queryRewrite,
                           RateLimitSettings rateLimit, MemorySettings memory) {
            this.defaultConfig = defaultConfig;
            this.queryRewrite = queryRewrite;
            this.rateLimit = rateLimit;
            this.memory = memory;
        }

        public static RagSettingsBuilder builder() {
            return new RagSettingsBuilder();
        }

        public static class RagSettingsBuilder {
@Schema(description = "defaultConfig")
            private DefaultSettings defaultConfig;
@Schema(description = "queryRewrite")
            private QueryRewriteSettings queryRewrite;
@Schema(description = "rateLimit")
            private RateLimitSettings rateLimit;
@Schema(description = "memory")
            private MemorySettings memory;

            public RagSettingsBuilder defaultConfig(DefaultSettings defaultConfig) {
                this.defaultConfig = defaultConfig;
                return this;
            }

            public RagSettingsBuilder queryRewrite(QueryRewriteSettings queryRewrite) {
                this.queryRewrite = queryRewrite;
                return this;
            }

            public RagSettingsBuilder rateLimit(RateLimitSettings rateLimit) {
                this.rateLimit = rateLimit;
                return this;
            }

            public RagSettingsBuilder memory(MemorySettings memory) {
                this.memory = memory;
                return this;
            }

            public RagSettings build() {
                return new RagSettings(defaultConfig, queryRewrite, rateLimit, memory);
            }
        }
    }

    @Setter
    @Getter
    public static class QueryRewriteSettings {
@Schema(description = "enabled")
        private Boolean enabled;
@Schema(description = "maxHistoryMessages")
        private Integer maxHistoryMessages;
@Schema(description = "maxHistoryChars")
        private Integer maxHistoryChars;

        public QueryRewriteSettings(Boolean enabled, Integer maxHistoryMessages, Integer maxHistoryChars) {
            this.enabled = enabled;
            this.maxHistoryMessages = maxHistoryMessages;
            this.maxHistoryChars = maxHistoryChars;
        }

        public static QueryRewriteSettingsBuilder builder() {
            return new QueryRewriteSettingsBuilder();
        }

        public static class QueryRewriteSettingsBuilder {
@Schema(description = "enabled")
            private Boolean enabled;
@Schema(description = "maxHistoryMessages")
            private Integer maxHistoryMessages;
@Schema(description = "maxHistoryChars")
            private Integer maxHistoryChars;

            public QueryRewriteSettingsBuilder enabled(Boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public QueryRewriteSettingsBuilder maxHistoryMessages(Integer maxHistoryMessages) {
                this.maxHistoryMessages = maxHistoryMessages;
                return this;
            }

            public QueryRewriteSettingsBuilder maxHistoryChars(Integer maxHistoryChars) {
                this.maxHistoryChars = maxHistoryChars;
                return this;
            }

            public QueryRewriteSettings build() {
                return new QueryRewriteSettings(enabled, maxHistoryMessages, maxHistoryChars);
            }
        }
    }

    @Setter
    @Getter
    public static class RateLimitSettings {
@Schema(description = "global")
        private GlobalRateLimit global;

        public RateLimitSettings(GlobalRateLimit global) {
            this.global = global;
        }

        public static RateLimitSettingsBuilder builder() {
            return new RateLimitSettingsBuilder();
        }

        public static class RateLimitSettingsBuilder {
@Schema(description = "global")
            private GlobalRateLimit global;

            public RateLimitSettingsBuilder global(GlobalRateLimit global) {
                this.global = global;
                return this;
            }

            public RateLimitSettings build() {
                return new RateLimitSettings(global);
            }
        }
    }

    @Setter
    @Getter
    public static class GlobalRateLimit {
@Schema(description = "enabled")
        private Boolean enabled;
@Schema(description = "maxConcurrent")
        private Integer maxConcurrent;
@Schema(description = "maxWaitSeconds")
        private Integer maxWaitSeconds;
@Schema(description = "leaseSeconds")
        private Integer leaseSeconds;
@Schema(description = "pollIntervalMs")
        private Integer pollIntervalMs;

        public GlobalRateLimit(Boolean enabled, Integer maxConcurrent, Integer maxWaitSeconds,
                               Integer leaseSeconds, Integer pollIntervalMs) {
            this.enabled = enabled;
            this.maxConcurrent = maxConcurrent;
            this.maxWaitSeconds = maxWaitSeconds;
            this.leaseSeconds = leaseSeconds;
            this.pollIntervalMs = pollIntervalMs;
        }

        public static GlobalRateLimitBuilder builder() {
            return new GlobalRateLimitBuilder();
        }

        public static class GlobalRateLimitBuilder {
@Schema(description = "enabled")
            private Boolean enabled;
@Schema(description = "maxConcurrent")
            private Integer maxConcurrent;
@Schema(description = "maxWaitSeconds")
            private Integer maxWaitSeconds;
@Schema(description = "leaseSeconds")
            private Integer leaseSeconds;
@Schema(description = "pollIntervalMs")
            private Integer pollIntervalMs;

            public GlobalRateLimitBuilder enabled(Boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public GlobalRateLimitBuilder maxConcurrent(Integer maxConcurrent) {
                this.maxConcurrent = maxConcurrent;
                return this;
            }

            public GlobalRateLimitBuilder maxWaitSeconds(Integer maxWaitSeconds) {
                this.maxWaitSeconds = maxWaitSeconds;
                return this;
            }

            public GlobalRateLimitBuilder leaseSeconds(Integer leaseSeconds) {
                this.leaseSeconds = leaseSeconds;
                return this;
            }

            public GlobalRateLimitBuilder pollIntervalMs(Integer pollIntervalMs) {
                this.pollIntervalMs = pollIntervalMs;
                return this;
            }

            public GlobalRateLimit build() {
                return new GlobalRateLimit(enabled, maxConcurrent, maxWaitSeconds, leaseSeconds, pollIntervalMs);
            }
        }
    }
}
