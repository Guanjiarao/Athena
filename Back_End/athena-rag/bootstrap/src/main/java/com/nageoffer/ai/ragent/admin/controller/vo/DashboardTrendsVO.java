

package com.nageoffer.ai.ragent.admin.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "DashboardTrendsVO返回对象")
public class DashboardTrendsVO {

@Schema(description = "metric")
    private String metric;

@Schema(description = "window")
    private String window;

@Schema(description = "granularity")
    private String granularity;

@Schema(description = "series")
    private List<DashboardTrendSeriesVO> series;
}
