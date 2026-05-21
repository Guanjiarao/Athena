

package com.nageoffer.ai.ragent.admin.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "DashboardOverviewVO返回对象")
public class DashboardOverviewVO {

@Schema(description = "window")
    private String window;

@Schema(description = "compareWindow")
    private String compareWindow;

@Schema(description = "updatedAt")
    private Long updatedAt;

@Schema(description = "kpis")
    private DashboardOverviewGroupVO kpis;
}
