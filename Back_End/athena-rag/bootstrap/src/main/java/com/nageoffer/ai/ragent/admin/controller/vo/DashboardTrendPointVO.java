

package com.nageoffer.ai.ragent.admin.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "DashboardTrendPointVO返回对象")
public class DashboardTrendPointVO {

@Schema(description = "ts")
    private Long ts;

@Schema(description = "value")
    private Double value;
}
