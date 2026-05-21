

package com.nageoffer.ai.ragent.admin.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "DashboardOverviewGroupVO返回对象")
public class DashboardOverviewGroupVO {

@Schema(description = "totalUsers")
    private DashboardOverviewKpiVO totalUsers;

@Schema(description = "activeUsers")
    private DashboardOverviewKpiVO activeUsers;

@Schema(description = "totalSessions")
    private DashboardOverviewKpiVO totalSessions;

@Schema(description = "sessions24h")
    private DashboardOverviewKpiVO sessions24h;

@Schema(description = "totalMessages")
    private DashboardOverviewKpiVO totalMessages;

@Schema(description = "messages24h")
    private DashboardOverviewKpiVO messages24h;
}
