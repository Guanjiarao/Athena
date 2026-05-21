

package com.nageoffer.ai.ragent.admin.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "DashboardPerformanceVO返回对象")
public class DashboardPerformanceVO {

@Schema(description = "window")
    private String window;

@Schema(description = "avgLatencyMs")
    private Long avgLatencyMs;

@Schema(description = "p95LatencyMs")
    private Long p95LatencyMs;

@Schema(description = "successRate")
    private Double successRate;

@Schema(description = "errorRate")
    private Double errorRate;

@Schema(description = "noDocRate")
    private Double noDocRate;

@Schema(description = "slowRate")
    private Double slowRate;
}
