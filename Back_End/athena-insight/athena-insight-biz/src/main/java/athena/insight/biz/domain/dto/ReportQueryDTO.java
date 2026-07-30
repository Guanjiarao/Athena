package athena.insight.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "分析报告查询请求")
public class ReportQueryDTO {

    @Schema(description = "用户ID，不传时默认取当前登录用户")
    private Long userId;
}
