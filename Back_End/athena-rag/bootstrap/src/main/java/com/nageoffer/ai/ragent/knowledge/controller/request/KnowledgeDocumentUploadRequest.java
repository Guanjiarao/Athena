

package com.nageoffer.ai.ragent.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeDocumentUploadRequest {

    /**
     * 来源类型：file / url
     */
    private String sourceType;

    /**
     * 来源位置（URL）
     */
    private String sourceLocation;

    /**
     * 是否开启定时拉取
     */
    private Boolean scheduleEnabled;

    /**
     * 定时表达式（cron）
     */
    private String scheduleCron;

    /**
     * 处理模式：chunk / pipeline
     * - chunk: 使用分块策略直接分块
     * - pipeline: 使用数据通道进行清洗处理
     */
    private String processMode;

    /**
     * 分块策略：fixed_size / structure_aware
     * 仅在 processMode=chunk 时有效
     */
    private String chunkStrategy;

    /**
     * 分块参数JSON，processMode=chunk 时必传
     * 如 {"chunkSize":512,"overlapSize":128} 或 {"targetChars":1400,"maxChars":1800,"minChars":600,"overlapChars":0}
     */
    private String chunkConfig;

    /**
     * 数据通道（Pipeline）ID
     * 仅在 processMode=pipeline 时有效
     */
    private String pipelineId;

    /**
     * 文档元数据（JSON 字符串）
     * 用于存储文档的自定义元数据，如 noteId、title、type、authorId 等
     * 示例：{"noteId":123,"title":"标题","type":50,"authorId":1024}
     */
    private String metadata;
}
