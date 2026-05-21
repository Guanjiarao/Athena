

package com.nageoffer.ai.ragent.admin.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "DashboardOverviewKpiVO返回对象")
public class DashboardOverviewKpiVO {

@Schema(description = "value")
    private Long value;

@Schema(description = "delta")
    private Long delta;

@Schema(description = "deltaPct")
    private Double deltaPct;
}
