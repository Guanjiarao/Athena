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
