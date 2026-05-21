

package com.nageoffer.ai.ragent.admin.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "DashboardTrendSeriesVO返回对象")
public class DashboardTrendSeriesVO {

@Schema(description = "name")
    private String name;

@Schema(description = "data")
    private List<DashboardTrendPointVO> data;
}
